package com.roomies.expensetracker.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.roomies.expensetracker.model.AppSettings
import com.roomies.expensetracker.model.Group
import com.roomies.expensetracker.model.Member
import com.roomies.expensetracker.model.ShoppingItem
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.RecurringExpense
import com.roomies.expensetracker.model.Settlement
import com.roomies.expensetracker.model.SplitMode
import com.roomies.expensetracker.model.inferredSplitMode
import com.roomies.expensetracker.util.DateUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * All reads are real-time Firestore listeners (callbackFlow), so when any
 * group member adds an expense, everyone else's app updates automatically.
 *
 * expenses/recurring/shopping_list/config all live under groups/{groupId}/...,
 * so every method takes a groupId. paidBy/addedBy hold a uid (Phase 5) rather
 * than a free-text name; use observeUsersByIds to resolve uid -> displayName.
 */
object FirestoreRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val groupsRef by lazy { db.collection("groups") }
    private val invitesRef by lazy { db.collection("invites") }

    private fun groupDoc(groupId: String) = groupsRef.document(groupId)
    private fun expensesRef(groupId: String) = groupDoc(groupId).collection("expenses")
    private fun recurringRef(groupId: String) = groupDoc(groupId).collection("recurring")
    private fun settingsDoc(groupId: String) = groupDoc(groupId).collection("config").document("settings")
    private fun shoppingRef(groupId: String) = groupDoc(groupId).collection("shopping_list")
    private fun settlementsRef(groupId: String) = groupDoc(groupId).collection("settlements")

    /** Groups the given user belongs to. Phase 4's group switcher/join flow builds on this. */
    fun observeGroupsForUser(uid: String): Flow<List<Group>> = callbackFlow {
        val listener = groupsRef
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Group::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Resolves uids to display names for the members dropdown/labels.
     * Falls back to the email prefix, then the uid itself, if displayName is blank.
     * Firestore's whereIn caps at 30 values, comfortably above any household size.
     */
    fun observeUsersByIds(uids: List<String>): Flow<Map<String, String>> = callbackFlow {
        if (uids.isEmpty()) {
            trySend(emptyMap())
            awaitClose { }
            return@callbackFlow
        }
        val listener = db.collection("users")
            .whereIn(FieldPath.documentId(), uids)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val map = snapshot?.documents?.associate { doc ->
                    val displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                    val emailPrefix = doc.getString("email")?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    doc.id to (displayName ?: emailPrefix ?: doc.id)
                } ?: emptyMap()
                trySend(map)
            }
        awaitClose { listener.remove() }
    }

    /**
     * One-shot (not a live listener): uid -> that user's registered FCM
     * tokens, fetched right when we're about to fan out a push. A user can
     * have more than one token (multiple devices signed in).
     */
    suspend fun getFcmTokens(uids: List<String>): Map<String, List<String>> {
        if (uids.isEmpty()) return emptyMap()
        val snapshot = db.collection("users")
            .whereIn(FieldPath.documentId(), uids)
            .get()
            .await()
        return snapshot.documents.associate { doc ->
            val tokens = (doc.get("fcmTokens") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            doc.id to tokens
        }
    }

    /** Removes a single stale token (app uninstalled / FCM rotated it away) from a user's doc. */
    fun removeFcmToken(uid: String, token: String) {
        db.collection("users").document(uid)
            .update("fcmTokens", FieldValue.arrayRemove(token))
    }

    /**
     * Full member details (name + email) for the Members dialog, as opposed to
     * observeUsersByIds' bare uid -> displayName map used by pickers. Sorted
     * by name so the list reads the same as the "Paid By" pickers do.
     */
    fun observeMemberDetails(uids: List<String>): Flow<List<Member>> = callbackFlow {
        if (uids.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = db.collection("users")
            .whereIn(FieldPath.documentId(), uids)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.map { doc ->
                    val displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                    val email = doc.getString("email") ?: ""
                    val emailPrefix = email.substringBefore("@").takeIf { it.isNotBlank() }
                    Member(uid = doc.id, displayName = displayName ?: emailPrefix ?: doc.id, email = email)
                }?.sortedBy { it.displayName.lowercase() } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Removes a member other than the creator from the group. Intended for
     * creator-only use from the UI; the Firestore security rules (managed in
     * the console, not this repo) are the actual enforcement boundary and
     * should confirm request.auth.uid == createdBy before allowing this write.
     */
    suspend fun removeMember(groupId: String, uid: String) {
        groupDoc(groupId).update("members", FieldValue.arrayRemove(uid)).await()
    }

    suspend fun createGroup(name: String, uid: String): String {
        val doc = groupsRef.document()
        val data = mapOf(
            "name" to name,
            "members" to listOf(uid),
            "createdBy" to uid,
            "admins" to emptyList<String>()
        )
        doc.set(data).await()
        return doc.id
    }

    suspend fun renameGroup(groupId: String, newName: String) {
        groupDoc(groupId).update("name", newName).await()
    }

    suspend fun leaveGroup(groupId: String, uid: String) {
        groupDoc(groupId).update("members", FieldValue.arrayRemove(uid)).await()
    }

    /** Codes avoid 0/O/1/I/L to prevent misreads when someone types one in by hand. */
    private fun generateInviteCode(length: Int = 6): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    /**
     * Creates a code valid for 72 hours that lets anyone who has it join this
     * group. [liableForThisMonth] is the admin's call, made before the code
     * is ever shared -- whoever joins with it can't change it themselves.
     * true: they're retroactively added to this month's Equal-split expenses.
     * false: this month's Equal-split expenses are locked to exclude them,
     * even ones that were still relying on the legacy "split among current
     * members" fallback (which would otherwise have auto-included them the
     * moment they joined).
     */
    suspend fun createInvite(groupId: String, uid: String, liableForThisMonth: Boolean): String {
        val code = generateInviteCode()
        val expiresAt = Timestamp(java.util.Date(System.currentTimeMillis() + 72 * 3600_000L))
        val data = mapOf(
            "groupId" to groupId,
            "createdBy" to uid,
            "expiresAt" to expiresAt,
            "liableForThisMonth" to liableForThisMonth
        )
        invitesRef.document(code).set(data).await()
        return code
    }

    /**
     * Looks up the code, checks it hasn't expired, then adds uid to that group's
     * members. The "joinCode" field written alongside is what the security rule
     * checks to confirm a real, unexpired invite for this exact group was used —
     * without it, anyone who merely knew a groupId could add themselves.
     *
     * Also applies the admin's liableForThisMonth choice from when the invite
     * was created (see createInvite) -- the new member has no say in this.
     */
    suspend fun joinGroupByCode(code: String, uid: String): Result<String> {
        return try {
            val inviteSnap = invitesRef.document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("That invite code doesn't exist."))
            }
            val groupId = inviteSnap.getString("groupId")
                ?: return Result.failure(IllegalStateException("This invite is malformed."))
            val expiresAt = inviteSnap.getTimestamp("expiresAt")
            if (expiresAt != null && expiresAt.toDate().before(java.util.Date())) {
                return Result.failure(IllegalStateException("This invite code has expired."))
            }
            groupDoc(groupId).update(
                mapOf(
                    "members" to FieldValue.arrayUnion(uid),
                    "joinCode" to code
                )
            ).await()
            val liableForThisMonth = if (inviteSnap.contains("liableForThisMonth")) {
                inviteSnap.getBoolean("liableForThisMonth")
            } else {
                null
            }
            if (liableForThisMonth != null) {
                applyMonthLiabilityForNewMember(groupId, uid, liableForThisMonth)
            }
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retroactively locks this month's Equal-split expenses to either include
     * or exclude [newUid], per the admin's choice at invite time. Manual and
     * On-Me expenses are never touched -- those were deliberate per-expense
     * choices, not "whoever's in the group".
     *
     * - liable=true: only expenses that already have a locked-in split need
     *   updating (add newUid, recompute shares). Legacy expenses with an
     *   empty splitAmong already fall back to splitting among ALL current
     *   members, so they already include newUid automatically -- nothing to
     *   do there.
     * - liable=false: every Equal-split expense this month, including legacy
     *   empty-split ones, gets an explicit split that excludes newUid --
     *   otherwise a legacy one would silently start including them the
     *   moment they join, via that same current-members fallback.
     */
    private suspend fun applyMonthLiabilityForNewMember(groupId: String, newUid: String, liable: Boolean) {
        val currentKey = DateUtils.monthYearKey(System.currentTimeMillis())
        val expenses = getExpensesOnce(groupId)
        val thisMonthEqual = expenses.filter {
            DateUtils.monthYearKey(it.dateMillis) == currentKey && it.inferredSplitMode() == SplitMode.EQUAL
        }
        if (thisMonthEqual.isEmpty()) return

        val currentMembers = if (!liable) {
            groupDoc(groupId).get().await().toObject(Group::class.java)?.members ?: emptyList()
        } else {
            emptyList()
        }

        thisMonthEqual.forEach { expense ->
            val isEmptyLegacy = expense.splitAmong.isEmpty()
            if (liable) {
                if (isEmptyLegacy || expense.splitAmong.containsKey(newUid)) return@forEach
                val participants = (expense.splitAmong.keys + newUid).toList()
                val share = expense.amount / participants.size
                updateExpense(groupId, expense.copy(splitAmong = participants.associateWith { share }))
            } else {
                val participants = if (isEmptyLegacy) {
                    currentMembers.filter { it != newUid }
                } else {
                    expense.splitAmong.keys.filter { it != newUid }
                }
                if (participants.isEmpty() || (!isEmptyLegacy && !expense.splitAmong.containsKey(newUid))) {
                    return@forEach
                }
                val share = expense.amount / participants.size
                updateExpense(groupId, expense.copy(splitAmong = participants.associateWith { share }))
            }
        }
    }

    fun observeExpenses(groupId: String): Flow<List<Expense>> = callbackFlow {
        val listener = expensesRef(groupId)
            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Expense::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * One-shot (non-listening) fetch of every expense a group has ever had.
     * Used to check a member is settled up before letting them leave a group
     * that may not be the currently active one (whose _expenses StateFlow
     * only ever holds the active group's data).
     */
    suspend fun getExpensesOnce(groupId: String): List<Expense> {
        val snapshot = expensesRef(groupId).get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Expense::class.java)?.apply { id = doc.id }
        }
    }

    suspend fun addExpense(groupId: String, expense: Expense) {
        expensesRef(groupId).add(expense).await()
    }

    suspend fun updateExpense(groupId: String, expense: Expense) {
        expensesRef(groupId).document(expense.id).set(expense).await()
    }

    suspend fun deleteExpense(groupId: String, id: String) {
        expensesRef(groupId).document(id).delete().await()
    }

    fun observeSettlements(groupId: String): Flow<List<Settlement>> = callbackFlow {
        val listener = settlementsRef(groupId)
            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Settlement::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * One-shot fetch of every settlement a group has ever recorded, for the
     * same reason getExpensesOnce exists: checking a member's lifetime
     * balance for a group that may not be the currently active one.
     */
    suspend fun getSettlementsOnce(groupId: String): List<Settlement> {
        val snapshot = settlementsRef(groupId).get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Settlement::class.java)?.apply { id = doc.id }
        }
    }

    suspend fun addSettlement(groupId: String, settlement: Settlement) {
        settlementsRef(groupId).add(settlement).await()
    }

    suspend fun deleteSettlement(groupId: String, id: String) {
        settlementsRef(groupId).document(id).delete().await()
    }

    fun observeRecurring(groupId: String): Flow<List<RecurringExpense>> = callbackFlow {
        val listener = recurringRef(groupId).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(RecurringExpense::class.java)?.apply { id = doc.id }
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun addRecurring(groupId: String, r: RecurringExpense) {
        recurringRef(groupId).add(r).await()
    }

    suspend fun deleteRecurring(groupId: String, id: String) {
        recurringRef(groupId).document(id).delete().await()
    }

    fun observeSettings(groupId: String): Flow<AppSettings> = callbackFlow {
        val listener = settingsDoc(groupId).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val settings = snapshot?.toObject(AppSettings::class.java) ?: AppSettings()
            trySend(settings)
        }
        awaitClose { listener.remove() }
    }

    suspend fun updateSettings(groupId: String, settings: AppSettings) {
        settingsDoc(groupId).set(settings).await()
    }

    fun observeShoppingList(groupId: String): Flow<List<ShoppingItem>> = callbackFlow {
        val listener = shoppingRef(groupId)
            .orderBy("addedAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ShoppingItem::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addShoppingItem(groupId: String, item: ShoppingItem) {
        shoppingRef(groupId).add(item).await()
    }

    suspend fun updateShoppingItem(groupId: String, item: ShoppingItem) {
        shoppingRef(groupId).document(item.id).set(item).await()
    }

    suspend fun deleteShoppingItem(groupId: String, id: String) {
        shoppingRef(groupId).document(id).delete().await()
    }
}
