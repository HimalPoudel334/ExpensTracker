package com.roomies.expensetracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomies.expensetracker.data.AuthManager
import com.roomies.expensetracker.data.FirestoreRepository
import com.roomies.expensetracker.model.AppSettings
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.RecurringExpense
import com.roomies.expensetracker.model.ShoppingItem
import com.roomies.expensetracker.util.DateUtils
import com.roomies.expensetracker.util.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()

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
            try { AuthManager.ensureSignedIn() } catch (e: Exception) { }
        }
        viewModelScope.launch {
            FirestoreRepository.observeExpenses().collect { _expenses.value = it }
        }
        viewModelScope.launch {
            FirestoreRepository.observeRecurring().collect { _recurring.value = it }
        }
        viewModelScope.launch {
            FirestoreRepository.observeSettings().collect { _settings.value = it }
        }
        viewModelScope.launch {
            FirestoreRepository.observeShoppingList().collect { items ->
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

    fun addShoppingItem(item: ShoppingItem) = viewModelScope.launch {
        FirestoreRepository.addShoppingItem(item)
    }

    fun updateShoppingItem(item: ShoppingItem) = viewModelScope.launch {
        FirestoreRepository.updateShoppingItem(item)
    }

    fun deleteShoppingItem(id: String) = viewModelScope.launch {
        FirestoreRepository.deleteShoppingItem(id)
        seenShoppingIds.remove(id)
    }

    /** Called when user marks an item purchased and wants to save it as an expense. */
    fun setPendingShoppingItem(item: ShoppingItem) {
        _pendingShoppingItem.value = item
    }

    /** Called by AddExpenseScreen after it has consumed the pending item. */
    fun clearPendingShoppingItem() {
        _pendingShoppingItem.value = null
    }

    fun addExpense(expense: Expense) = viewModelScope.launch {
        FirestoreRepository.addExpense(expense)
    }

    fun updateExpense(expense: Expense) = viewModelScope.launch {
        FirestoreRepository.updateExpense(expense)
    }

    fun deleteExpense(id: String) = viewModelScope.launch {
        FirestoreRepository.deleteExpense(id)
    }

    fun addRecurring(r: RecurringExpense) = viewModelScope.launch {
        FirestoreRepository.addRecurring(r)
    }

    fun deleteRecurring(id: String) = viewModelScope.launch {
        FirestoreRepository.deleteRecurring(id)
    }

    fun updateSettings(settings: AppSettings) = viewModelScope.launch {
        FirestoreRepository.updateSettings(settings)
    }

    /** Creates this month's expense entries from active recurring templates, skipping ones already generated. */
    fun generateRecurringForCurrentMonth() = viewModelScope.launch {
        val currentKey = DateUtils.currentMonthYearKey()
        val now = System.currentTimeMillis()
        _recurring.value.filter { it.active }.forEach { template ->
            val alreadyExists = _expenses.value.any {
                it.recurringId == template.id && DateUtils.monthYearKey(it.dateMillis) == currentKey
            }
            if (!alreadyExists) {
                FirestoreRepository.addExpense(
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
