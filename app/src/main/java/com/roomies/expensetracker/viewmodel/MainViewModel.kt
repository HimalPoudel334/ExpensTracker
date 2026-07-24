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
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.Group
import com.roomies.expensetracker.model.RecurringExpense
import com.roomies.expensetracker.model.ShoppingItem
import com.roomies.expensetracker.util.DateUtils
import com.roomies.expensetracker.util.NotificationHelper
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

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _recurring = MutableStateFlow<List<RecurringExpense>>(emptyList())
    val recurring: StateFlow<List<RecurringExpense>> = _recurring.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _shoppingList = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val shoppingList: StateFlow<List<ShoppingItem>> = _shoppingList.asStateFlow()

    /** Set when user marks a shopping item as purchased and chooses to save as expense. */
    private val _pendingShoppingItem = MutableStateFlow<ShoppingItem?>(null)
    val pendingShoppingItem: StateFlow<ShoppingItem?> = _pendingShoppingItem.asStateFlow()

    /** IDs seen during this session — used to detect truly new items for notifications. */
    private val seenShoppingIds = mutableSetOf<String>()
    private var shoppingInitialized = false

    init {
        viewModelScope.launch {
            AuthManager.currentUser.collect { user ->
                _selectedGroupId.value = user?.uid?.let { GroupPreference.getSelectedGroupId(context, it) }
            }
        }

        val groupIdFlow = activeGroup.map { it?.id }

        viewModelScope.launch {
            groupIdFlow.filterNotNull()
                .flatMapLatest { groupId -> FirestoreRepository.observeExpenses(groupId) }
                .collect { _expenses.value = it }
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
                .flatMapLatest { groupId -> FirestoreRepository.observeShoppingList(groupId) }
                .collect { items ->
                    if (!shoppingInitialized) {
                        // First load — seed seen set without notifying
                        seenShoppingIds.addAll(items.map { it.id })
                        shoppingInitialized = true
                    } else {
                        items.filter { it.id !in seenShoppingIds }.forEach { newItem ->
                            if (!newItem.addedByMe) {
                                NotificationHelper.notifyNewShoppingItem(context, newItem.name, newItem.addedBy)
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
        viewModelScope.launch { FirestoreRepository.addShoppingItem(groupId, item) }
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

    /** Leaving the currently active group falls back to whichever group is next in the list, if any. */
    fun leaveGroup(groupId: String) {
        val uid = AuthManager.currentUser.value?.uid ?: return
        viewModelScope.launch {
            FirestoreRepository.leaveGroup(groupId, uid)
            if (_selectedGroupId.value == groupId) {
                _selectedGroupId.value = null
                GroupPreference.clearSelectedGroupId(context, uid)
            }
        }
    }

    /** Generates a 72-hour invite code for the given group and returns it via callback. */
    fun createInvite(groupId: String, onResult: (Result<String>) -> Unit) {
        val uid = AuthManager.currentUser.value?.uid
            ?: return onResult(Result.failure(IllegalStateException("Not signed in")))
        viewModelScope.launch {
            try {
                val code = FirestoreRepository.createInvite(groupId, uid)
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

    fun addExpense(expense: Expense) {
        val groupId = activeGroupId ?: return
        viewModelScope.launch { FirestoreRepository.addExpense(groupId, expense) }
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
                            recurringId = template.id
                        )
                    )
                }
            }
        }
    }

    // ---------- Reports ----------

    fun expensesForMonth(refMillis: Long): List<Expense> =
        _expenses.value.filter { DateUtils.isSameBsMonth(it.dateMillis, refMillis) }

    fun totalForMonth(refMillis: Long): Double =
        expensesForMonth(refMillis).sumOf { it.amount }

    fun byPersonForMonth(refMillis: Long): Map<String, Double> =
        expensesForMonth(refMillis).groupBy { it.paidBy }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

    fun byCategoryForMonth(refMillis: Long): Map<String, Double> =
        expensesForMonth(refMillis).groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

    fun byPaymentMethodForMonth(refMillis: Long): Map<String, Double> =
        expensesForMonth(refMillis).groupBy { it.paymentMethod }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

    fun topItemsByAmount(refMillis: Long, limit: Int = 10): List<Pair<String, Double>> =
        expensesForMonth(refMillis).groupBy { it.item }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)

    fun topItemsByFrequency(refMillis: Long, limit: Int = 10): List<Pair<String, Int>> =
        expensesForMonth(refMillis).groupBy { it.item }
            .mapValues { entry -> entry.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)

    /** Assumes an equal 50/50 split between the two people. Returns (A paid, B paid, message). */
    fun settlementForMonth(refMillis: Long): Triple<Double, Double, String> {
        val settings = _settings.value
        val byPerson = byPersonForMonth(refMillis)
        val total = byPerson.values.sum()
        val fairShare = total / 2.0
        val aPaid = byPerson[settings.personAName] ?: 0.0
        val bPaid = byPerson[settings.personBName] ?: 0.0
        val aBalance = aPaid - fairShare
        val message = when {
            aBalance > 0.5 -> "${settings.personBName} owes ${settings.personAName} ${"%.2f".format(aBalance)}"
            aBalance < -0.5 -> "${settings.personAName} owes ${settings.personBName} ${"%.2f".format(-aBalance)}"
            else -> "Settled up \u2014 no one owes anything"
        }
        return Triple(aPaid, bPaid, message)
    }
}
