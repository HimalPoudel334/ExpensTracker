package com.roomies.expensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomies.expensetracker.data.AuthManager
import com.roomies.expensetracker.data.FirestoreRepository
import com.roomies.expensetracker.model.AppSettings
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.RecurringExpense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.roomies.expensetracker.util.DateUtils

class MainViewModel : ViewModel() {

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _recurring = MutableStateFlow<List<RecurringExpense>>(emptyList())
    val recurring: StateFlow<List<RecurringExpense>> = _recurring.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                AuthManager.ensureSignedIn()
            } catch (e: Exception) {
                // no internet right now, will retry on its own
            }
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
        _expenses.value.filter { DateUtils.isSameMonth(it.dateMillis, refMillis) }

    fun totalForMonth(refMillis: Long): Double =
        expensesForMonth(refMillis).sumOf { it.amount }

    fun byPersonForMonth(refMillis: Long): Map<String, Double> =
        expensesForMonth(refMillis).groupBy { it.paidBy }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

    fun byCategoryForMonth(refMillis: Long): Map<String, Double> =
        expensesForMonth(refMillis).groupBy{it.category}
            .mapValues{entry -> entry.value.sumOf {it.amount}}

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