package com.roomies.expensetracker.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.roomies.expensetracker.model.AppSettings
import com.roomies.expensetracker.model.Group
import com.roomies.expensetracker.model.ShoppingItem
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.RecurringExpense
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * All reads are real-time Firestore listeners (callbackFlow), so when Person A
 * adds an expense, Person B's phone updates automatically as long as both
 * devices are online and pointed at the same Firebase project.
 *
 * As of Phase 3, expenses/recurring/shopping_list/config all live under
 * groups/{groupId}/... instead of top-level, so every method now takes a
 * groupId. paidBy/addedBy remain free-text names for now (unchanged from
 * before) — switching those to uid-based group members is Phase 5.
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

    suspend fun createGroup(name: String, uid: String): String {
        val doc = groupsRef.document()
        val data = mapOf(
            "name" to name,
            "members" to listOf(uid),
            "createdBy" to uid
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

    /** Creates a code valid for 72 hours that lets anyone who has it join this group. */
    suspend fun createInvite(groupId: String, uid: String): String {
        val code = generateInviteCode()
        val expiresAt = Timestamp(java.util.Date(System.currentTimeMillis() + 72 * 3600_000L))
        val data = mapOf(
            "groupId" to groupId,
            "createdBy" to uid,
            "expiresAt" to expiresAt
        )
        invitesRef.document(code).set(data).await()
        return code
    }

    /**
     * Looks up the code, checks it hasn't expired, then adds uid to that group's
     * members. The "joinCode" field written alongside is what the security rule
     * checks to confirm a real, unexpired invite for this exact group was used —
     * without it, anyone who merely knew a groupId could add themselves.
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
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun addExpense(groupId: String, expense: Expense) {
        expensesRef(groupId).add(expense).await()
    }

    suspend fun updateExpense(groupId: String, expense: Expense) {
        expensesRef(groupId).document(expense.id).set(expense).await()
    }

    suspend fun deleteExpense(groupId: String, id: String) {
        expensesRef(groupId).document(id).delete().await()
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
