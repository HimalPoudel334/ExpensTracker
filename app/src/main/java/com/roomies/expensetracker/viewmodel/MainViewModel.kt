@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.roomies.expensetracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomies.expensetracker.data.AuthManager
import com.roomies.expensetracker.data.FirestoreRepository
import com.roomies.expensetracker.data.GroupPreference
import com.roomies.expensetracker.model.AppSettings
import com.roomies.expensetracker.model.BalanceDetail
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.Group
import com.roomies.expensetracker.model.ItemStat
import com.roomies.expensetracker.model.Member
import com.roomies.expensetracker.model.RecurringExpense
import com.roomies.expensetracker.model.Settlement
import com.roomies.expensetracker.model.ShoppingItem
import com.roomies.expensetracker.model.SuggestedSettlement
import com.roomies.expensetracker.model.isPersonalOnly
import com.roomies.expensetracker.model.shareFor
import com.roomies.expensetracker.util.DateUtils
import com.roomies.expensetracker.util.NotificationHelper
import com.roomies.expensetracker.util.NotifyWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()

    /** All groups the signed-in user belongs to. Drives the Groups screen list. */
    val groups: StateFlow<List<Group>> = AuthManager.currentUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid == null) flowOf(emptyList())
            else FirestoreRepository.observeGroupsForUser(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Explicit user selection, loaded from on-device preference when the signed-in user changes. */
    private val _selectedGroupId = MutableStateFlow<String?>(null)

    /**
     * The group whose data is currently shown: the user's explicit selection if
     * still valid, otherwise the first group they belong to. A user with zero
     * groups (no invite accepted yet) sees empty everything and every write
     * silently no-ops -- expected until Phase 4's join-by-code flow ships.
     */
    val activeGroup: StateFlow<Group?> = combine(groups, _selectedGroupId) { groupList, selectedId ->
        groupList.find { it.id == selectedId } ?: groupList.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val activeGroupId: String? get() = activeGroup.value?.id

    /** uid -> display name for everyone in the active group. Empty map while loading. */
    val memberNames: StateFlow<Map<String, String>> = activeGroup
        .map { it?.members ?: emptyList() }
        .flatMapLatest { uids -> FirestoreRepository.observeUsersByIds(uids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** (uid, displayName) pairs for the active group, sorted by name — feeds the "Paid By" pickers. */
    val members: StateFlow<List<Pair<String, String>>> = combine(activeGroup, memberNames) { group, names ->
        (group?.members ?: emptyList()).map { uid -> uid to (names[uid] ?: uid) }
            .sortedBy { it.second.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Signed-in user's uid, for comparing against a group's createdBy in the UI. */
    val currentUserId: StateFlow<String?> = AuthManager.currentUser
        .map { it?.uid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthManager.currentUser.value?.uid)

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements.asStateFlow()

    private val _recurring = MutableStateFlow<List<RecurringExpense>>(emptyList())
    val recurring: StateFlow<List<RecurringExpense>> = _recurring.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _shoppingList = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val shoppingList: StateFlow<List<ShoppingItem>> = _shoppingList.asStateFlow()

    /** Set when user marks a shopping item as purchased and chooses to save as expense. */
    private val _pendingShoppingItem = MutableStateFlow<ShoppingItem?>(null)
    val pendingShoppingItem: StateFlow<ShoppingItem?> = _pendingShoppingItem.asStateFlow()

    /** IDs seen for the CURRENT group — reset whenever the active group changes, so switching
     *  groups doesn't treat that group's existing items as "new" and spam notifications. */
    private val seenShoppingIds = mutableSetOf<String>()
    private var shoppingInitializedForGroup: String? = null

    private val seenExpenseIds = mutableSetOf<String>()
    private var expensesInitializedForGroup: String? = null

    init {
        viewModelScope.launch {
            AuthManager.currentUser.collect { user ->
                _selectedGroupId.value = user?.uid?.let { GroupPreference.getSelectedGroupId(context, it) }
            }
        }

        val groupIdFlow = activeGroup.map { it?.id }

        viewModelScope.launch {
            groupIdFlow.filterNotNull()
                .flatMapLatest { groupId ->
                    FirestoreRepository.observeExpenses(groupId).map { list -> groupId to list }
                }
                .collect { (groupId, list) ->
                    if (expensesInitializedForGroup != groupId) {
                        seenExpenseIds.clear()
                        seenExpenseIds.addAll(list.map { it.id })
                        expensesInitializedForGroup = groupId
                    } else {
                        val currentUid = AuthManager.currentUser.value?.uid
                        list.filter { it.id !in seenExpenseIds }.forEach { newExpense ->
                            if (newExpense.paidBy != currentUid) {
                                val payerName = memberNames.value[newExpense.paidBy] ?: newExpense.paidBy
                                val amountText = "${_settings.value.currencySymbol} ${"%.2f".format(newExpense.amount)}"
                                NotificationHelper.notifyNewExpense(context, newExpense.item, amountText, payerName)
                            }
                            seenExpenseIds.add(newExpense.id)
                        }
                    }
                    _expenses.value = list
                }
        }
        viewModelScope.launch {
            groupIdFlow.filterNotNull()
                .flatMapLatest { groupId -> FirestoreRepository.observeSettlements(groupId) }
                .collect { _settlements.value = it }
        }
        viewModelScope.launch {
            groupIdFlow.filterNotNull()
                .flatMapLatest { groupId -> FirestoreRepository.observeRecurring(groupId) }
                .collect { _recurring.value = it }
        }
        viewModelScope.launch {
            groupIdFlow.filterNotNull()
                .flatMapLatest { groupId -> FirestoreRepository.observeSettings(groupId) }
                .collect { _settings.value = it }
        }
        viewModelScope.launch {
            groupIdFlow.filterNotNull()
                .flatMapLatest { groupId ->
                    FirestoreRepository.observeShoppingList(groupId).map { list -> groupId to list }
                }
                .collect { (groupId, items) ->
                    if (shoppingInitializedForGroup != groupId) {
                        seenShoppingIds.clear()
                        seenShoppingIds.addAll(items.map { it.id })
                        shoppingInitializedForGroup = groupId
                    } else {
                        items.filter { it.id !in seenShoppingIds }.forEach { newItem ->
                            if (!newItem.addedByMe) {
                                val addedByName = memberNames.value[newItem.addedBy] ?: newItem.addedBy
                                NotificationHelper.notifyNewShoppingItem(context, newItem.name, addedByName)
                            }
                            seenShoppingIds.add(newItem.id)
                        }
                    }
                    _shoppingList.value = items
                }
        }
    }

    fun addShoppingItem(item: ShoppingItem) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch {
            FirestoreRepository.addShoppingItem(groupId, item)
            notifyOtherMembers(
                excludeUid = item.addedBy,
                title = "New Shopping Item",
                body = "${memberNames.value[item.addedBy] ?: "Someone"} added \"${item.name}\" to the list",
                channelId = "shopping_list",
                data = mapOf("type" to "shopping_item", "groupId" to groupId)
            )
        }
    }

    fun updateShoppingItem(item: ShoppingItem) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch { FirestoreRepository.updateShoppingItem(groupId, item) }
    }

    fun deleteShoppingItem(id: String) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch {
            FirestoreRepository.deleteShoppingItem(groupId, id)
            seenShoppingIds.remove(id)
        }
    }

    /** Called when user marks an item purchased and wants to save it as an expense. */
    fun setPendingShoppingItem(item: ShoppingItem) {
        _pendingShoppingItem.value = item
    }

    /** Called by AddExpenseScreen after it has consumed the pending item. */
    fun clearPendingShoppingItem() {
        _pendingShoppingItem.value = null
    }

    fun selectGroup(groupId: String) {
        _selectedGroupId.value = groupId
        AuthManager.currentUser.value?.uid?.let { uid ->
            GroupPreference.setSelectedGroupId(context, uid, groupId)
        }
    }

    fun createGroup(name: String) {
        val uid = AuthManager.currentUser.value?.uid ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val newGroupId = FirestoreRepository.createGroup(name.trim(), uid)
            selectGroup(newGroupId)
        }
    }

    fun renameGroup(groupId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { FirestoreRepository.renameGroup(groupId, newName.trim()) }
    }

    /**
     * Leaving the currently active group falls back to whichever group is
     * next in the list, if any. Blocked until the member's lifetime balance
     * in this group is near zero -- onBlocked receives a human-readable
     * reason so the UI can surface it instead of silently doing nothing.
     */
    fun leaveGroup(group: Group, onBlocked: (String) -> Unit = {}) {
        val uid = AuthManager.currentUser.value?.uid ?: return
        viewModelScope.launch {
            val balance = lifetimeBalance(group, uid)
            if (kotlin.math.abs(balance) > 0.5) {
                onBlocked(
                    if (balance > 0)
                        "You're still owed money in \"${group.name}\". Settle up before leaving."
                    else
                        "You still owe money in \"${group.name}\". Settle up before leaving."
                )
                return@launch
            }
            FirestoreRepository.leaveGroup(group.id, uid)
            if (_selectedGroupId.value == group.id) {
                _selectedGroupId.value = null
                GroupPreference.clearSelectedGroupId(context, uid)
            }
        }
    }

    /** Full member details (name + email) for any group in the list — feeds the Members dialog. */
    fun observeMemberDetails(uids: List<String>): Flow<List<Member>> =
        FirestoreRepository.observeMemberDetails(uids)

    /**
     * Creator-only: removes another member from the given group. No-ops if
     * the caller isn't that group's creator or is trying to remove
     * themselves (the creator leaves via deleting/leaving the group, not
     * this action) -- this is a UI-level guard, not a security boundary, so
     * Firestore rules must independently enforce createdBy on this write.
     */
    fun removeMember(group: Group, uid: String) {
        val currentUid = AuthManager.currentUser.value?.uid ?: return
        if (group.createdBy != currentUid || uid == group.createdBy) return
        viewModelScope.launch { FirestoreRepository.removeMember(group.id, uid) }
    }

    /**
     * Generates a 72-hour invite code for the given group and returns it via
     * callback. [liableForThisMonth] decides, up front, whether whoever joins
     * with this code will be retroactively included in this month's
     * Equal-split expenses or excluded from them -- see
     * FirestoreRepository.createInvite / applyMonthLiabilityForNewMember.
     */
    fun createInvite(groupId: String, liableForThisMonth: Boolean, onResult: (Result<String>) -> Unit) {
        val uid = AuthManager.currentUser.value?.uid
            ?: return onResult(Result.failure(IllegalStateException("Not signed in")))
        viewModelScope.launch {
            try {
                val code = FirestoreRepository.createInvite(groupId, uid, liableForThisMonth)
                onResult(Result.success(code))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    /** Joins the group referenced by the code and switches to it on success. */
    fun joinGroup(code: String, onResult: (Result<Unit>) -> Unit) {
        val uid = AuthManager.currentUser.value?.uid
            ?: return onResult(Result.failure(IllegalStateException("Not signed in")))
        viewModelScope.launch {
            val result = FirestoreRepository.joinGroupByCode(code.trim().uppercase(), uid)
            result.onSuccess { groupId -> selectGroup(groupId) }
            onResult(result.map { })
        }
    }

    /**
     * Fans a push out to every current group member except [excludeUid]
     * (the person who just made the change -- no point notifying yourself).
     * Enqueued as WorkManager work (see [NotifyWorker]) rather than run
     * inline in viewModelScope: viewModelScope is cancelled the instant the
     * user backgrounds/closes the app, which is exactly when this needs to
     * keep running, and WorkManager work isn't tied to that lifecycle.
     * Failures are logged inside PushNotifier/NotifyWorker, never shown to
     * the user, since this is purely the "your phone was closed" fallback on
     * top of the live in-app notification that already covers everyone with
     * the app open (see the listener setup above).
     */
    private fun notifyOtherMembers(
        excludeUid: String,
        title: String,
        body: String,
        channelId: String,
        data: Map<String, String>
    ) {
        val otherUids = (activeGroup.value?.members ?: emptyList()).filter { it != excludeUid }
        if (otherUids.isEmpty()) return
        NotifyWorker.enqueue(context, otherUids, title, body, channelId, data)
    }

    fun addExpense(expense: Expense) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch {
            FirestoreRepository.addExpense(groupId, expense)
            notifyOtherMembers(
                excludeUid = expense.paidBy,
                title = "New Expense",
                body = "${memberNames.value[expense.paidBy] ?: "Someone"} added \"${expense.item}\" \u2014 " +
                    "${settings.value.currencySymbol} ${"%.2f".format(expense.amount)}",
                channelId = "expense_updates",
                data = mapOf("type" to "expense", "groupId" to groupId)
            )
        }
    }

    fun updateExpense(expense: Expense) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch { FirestoreRepository.updateExpense(groupId, expense) }
    }

    fun deleteExpense(id: String) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch { FirestoreRepository.deleteExpense(groupId, id) }
    }

    fun addRecurring(r: RecurringExpense) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch { FirestoreRepository.addRecurring(groupId, r) }
    }

    fun deleteRecurring(id: String) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch { FirestoreRepository.deleteRecurring(groupId, id) }
    }

    fun updateSettings(settings: AppSettings) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch { FirestoreRepository.updateSettings(groupId, settings) }
    }

    /** Creates this month's expense entries from active recurring templates, skipping ones already generated. */
    fun generateRecurringForCurrentMonth() {
        val groupId = activeGroupId ?: return
        viewModelScope.launch {
            val currentKey = DateUtils.currentMonthYearKey()
            val now = System.currentTimeMillis()
            _recurring.value.filter { it.active }.forEach { template ->
                val alreadyExists = _expenses.value.any {
                    it.recurringId == template.id && DateUtils.monthYearKey(it.dateMillis) == currentKey
                }
                if (!alreadyExists) {
                    val memberUids = activeGroup.value?.members ?: emptyList()
                    val splitAmong = if (memberUids.isEmpty()) {
                        emptyMap()
                    } else {
                        val share = template.amount / memberUids.size
                        memberUids.associateWith { share }
                    }
                    FirestoreRepository.addExpense(
                        groupId,
                        Expense(
                            amount = template.amount,
                            item = template.item,
                            category = template.category,
                            paymentMethod = template.paymentMethod,
                            paidBy = template.paidBy,
                            dateMillis = DateUtils.millisForDayInMonth(now, template.dayOfMonth),
                            notes = "Auto-generated recurring expense",
                            recurringId = template.id,
                            splitAmong = splitAmong
                        )
                    )
                }
            }
        }
    }

    // ---------- Reports ----------

    fun expensesForMonth(refMillis: Long): List<Expense> =
        _expenses.value.filter { DateUtils.isSameBsMonth(it.dateMillis, refMillis) }

    /** Both bounds inclusive; expects local-noon millis per our storage convention. */
    fun expensesInRange(startMillis: Long, endMillis: Long): List<Expense> =
        _expenses.value.filter { it.dateMillis in startMillis..endMillis }

    fun totalOf(expenses: List<Expense>): Double = expenses.sumOf { it.amount }

    fun byPersonOf(expenses: List<Expense>): Map<String, Double> =
        expenses.groupBy { it.paidBy }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

    /**
     * Same as [byPersonOf] but excludes "this is on me" expenses (a custom
     * split where the payer kept the whole amount) -- so this reflects only
     * spending that was actually shared with the group.
     */
    fun byPersonSharedOf(expenses: List<Expense>): Map<String, Double> =
        expenses.filterNot { it.isPersonalOnly }
            .groupBy { it.paidBy }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

    fun byCategoryOf(expenses: List<Expense>): Map<String, Double> =
        expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

    fun byPaymentMethodOf(expenses: List<Expense>): Map<String, Double> =
        expenses.groupBy { it.paymentMethod }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

    fun topItemsByAmountOf(expenses: List<Expense>, limit: Int = 10): List<Pair<String, Double>> =
        expenses.groupBy { it.item }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)

    fun topItemsByFrequencyOf(expenses: List<Expense>, limit: Int = 10): List<Pair<String, Int>> =
        expenses.groupBy { it.item }
            .mapValues { entry -> entry.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)

    /** Per-item amount + frequency together, for the sortable Top Items table. */
    fun topItemStatsOf(expenses: List<Expense>): List<ItemStat> =
        expenses.groupBy { it.item }
            .map { (item, list) -> ItemStat(item = item, amount = list.sumOf { it.amount }, count = list.size) }

    /**
     * Per-member paid vs. share breakdown behind a balance, so the UI can
     * show group members how the number was actually calculated (e.g.
     * "paid ₹1200, share ₹800 of ₹2400 split equally among 3 members").
     * Honors any custom splitAmong via Expense.shareFor, same as
     * computeExpenseBalances.
     */
    fun balanceDetailsOf(expenses: List<Expense>): Map<String, BalanceDetail> {
        val memberUids = activeGroup.value?.members ?: return emptyMap()
        if (memberUids.isEmpty()) return emptyMap()
        return memberUids.associateWith { uid ->
            val paid = expenses.filter { it.paidBy == uid }.sumOf { it.amount }
            val share = expenses.sumOf { it.shareFor(uid, memberUids) }
            BalanceDetail(paid = paid, share = share, net = paid - share)
        }
    }

    /**
     * Shared balance math used by both the monthly report and the lifetime
     * settlement check. Uses Expense.shareFor, so an expense with a custom
     * splitAmong is honored; otherwise it falls back to an equal split
     * across memberUids.
     */
    private fun computeExpenseBalances(memberUids: List<String>, expenses: List<Expense>): Map<String, Double> {
        if (memberUids.isEmpty()) return emptyMap()
        val balances = memberUids.associateWith { 0.0 }.toMutableMap()
        expenses.forEach { expense ->
            memberUids.forEach { uid ->
                balances[uid] = (balances[uid] ?: 0.0) - expense.shareFor(uid, memberUids)
            }
            balances[expense.paidBy] = (balances[expense.paidBy] ?: 0.0) + expense.amount
        }
        return balances
    }

    /**
     * Applies recorded settlements on top of expense-derived balances: paying
     * someone moves the payer's balance up (less owed) and the payee's
     * balance down (less owed to them), regardless of which month the
     * underlying expenses were in.
     */
    private fun applySettlements(balances: Map<String, Double>, settlements: List<Settlement>): Map<String, Double> {
        val result = balances.toMutableMap()
        settlements.forEach { s ->
            result[s.fromUid] = (result[s.fromUid] ?: 0.0) + s.amount
            result[s.toUid] = (result[s.toUid] ?: 0.0) - s.amount
        }
        return result
    }

    /**
     * Net balance per member for an arbitrary set of expenses (a month or a
     * custom range), using the group's CURRENT member list and honoring any
     * custom splitAmong via Expense.shareFor. Settlements aren't applied
     * here since they're not period-scoped the same way expenses are — see
     * netBalances for the all-time, settlement-aware view used by Settle Up.
     */
    fun balancesOf(expenses: List<Expense>): Map<String, Double> {
        val memberUids = activeGroup.value?.members ?: return emptyMap()
        return computeExpenseBalances(memberUids, expenses)
    }

    /**
     * Net balance per member for the month, assuming every expense is split
     * equally among all current group members regardless of who was in the
     * group when the expense happened. Positive = owed money overall,
     * negative = owes money overall. Uses the group's CURRENT member list
     * (not historical), which is the simplest correct behavior for a
     * household that rarely changes members mid-month. Settlements aren't
     * applied here -- they're not month-scoped in the same way expenses are
     * (see netBalances for the all-time, settlement-aware view).
     */
    fun balancesForMonth(refMillis: Long): Map<String, Double> =
        balancesOf(expensesForMonth(refMillis))

    /**
     * All-time balance per member for the active group: every expense ever
     * recorded, adjusted by every settlement ever recorded. This is the
     * "who actually owes whom right now" view that Settle Up acts on.
     */
    val netBalances: StateFlow<Map<String, Double>> = combine(activeGroup, _expenses, _settlements) { group, expenses, settlements ->
        val memberUids = group?.members ?: return@combine emptyMap()
        applySettlements(computeExpenseBalances(memberUids, expenses), settlements)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Simplifies netBalances into the smallest practical set of payments
     * that would settle the group up completely -- e.g. instead of "A owes
     * B ₹200, B owes C ₹200" this suggests "A pays C ₹200" directly. Greedy
     * largest-debtor-pays-largest-creditor heuristic: not guaranteed to be
     * the mathematically globally-minimal transaction count in every case,
     * but it's what most bill-splitting apps use and never needs more than
     * (members with a nonzero balance - 1) transactions.
     */
    val simplifiedSettlements: StateFlow<List<SuggestedSettlement>> = netBalances
        .map { balances -> simplifyDebts(balances) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun simplifyDebts(balances: Map<String, Double>): List<SuggestedSettlement> {
        val creditors = balances.filter { it.value > 0.5 }
            .map { it.key to it.value }
            .sortedByDescending { it.second }
            .toMutableList()
        val debtors = balances.filter { it.value < -0.5 }
            .map { it.key to -it.value }
            .sortedByDescending { it.second }
            .toMutableList()

        val result = mutableListOf<SuggestedSettlement>()
        var ci = 0
        var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val (creditorUid, creditAmt) = creditors[ci]
            val (debtorUid, debtAmt) = debtors[di]
            val settleAmt = minOf(creditAmt, debtAmt)
            if (settleAmt > 0.5) {
                result.add(SuggestedSettlement(fromUid = debtorUid, toUid = creditorUid, amount = settleAmt))
            }
            creditors[ci] = creditorUid to (creditAmt - settleAmt)
            debtors[di] = debtorUid to (debtAmt - settleAmt)
            if (creditors[ci].second <= 0.5) ci++
            if (debtors[di].second <= 0.5) di++
        }
        return result
    }

    /** Records a direct payment between two members of the active group, adjusting netBalances immediately. */
    fun recordSettlement(fromUid: String, toUid: String, amount: Double, note: String = "") {
        val groupId = activeGroupId ?: return
        if (fromUid == toUid || amount <= 0.0) return
        viewModelScope.launch {
            FirestoreRepository.addSettlement(
                groupId,
                Settlement(fromUid = fromUid, toUid = toUid, amount = amount, note = note.trim())
            )
        }
    }

    fun deleteSettlement(id: String) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch { FirestoreRepository.deleteSettlement(groupId, id) }
    }

    /**
     * This member's lifetime balance for the given group (not just the
     * active month), fetched fresh so it works even for a group that isn't
     * currently active. Includes settlements, same as netBalances. A
     * threshold of 0.5 currency units treats tiny rounding drift as
     * settled. Used to block "Leave" until settled up.
     */
    private suspend fun lifetimeBalance(group: Group, uid: String): Double {
        if (group.members.isEmpty()) return 0.0
        val expenses = FirestoreRepository.getExpensesOnce(group.id)
        val settlements = FirestoreRepository.getSettlementsOnce(group.id)
        return applySettlements(computeExpenseBalances(group.members, expenses), settlements)[uid] ?: 0.0
    }
}
