package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.util.Constants
import com.roomies.expensetracker.util.DateUtils
import com.roomies.expensetracker.viewmodel.MainViewModel
import dev.shivathapaa.nepalidatepickerkmp.NepaliDatePicker
import dev.shivathapaa.nepalidatepickerkmp.NepaliDatePickerDialog
import dev.shivathapaa.nepalidatepickerkmp.calendar_model.NepaliDatePickerDefaults
import dev.shivathapaa.nepalidatepickerkmp.rememberNepaliDatePickerState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AddExpenseScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val pendingItem by viewModel.pendingShoppingItem.collectAsState()

    var amount by remember { mutableStateOf("") }
    var item by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Constants.CATEGORIES.first()) }
    var paymentMethod by remember { mutableStateOf(Constants.PAYMENT_METHODS.first()) }
    var paidBy by remember { mutableStateOf(settings.personAName) }
    var notes by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableLongStateOf(DateUtils.todayLocalNoon()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }

    // Pre-fill from shopping list if navigated from "Add as Expense"
    LaunchedEffect(pendingItem) {
        pendingItem?.let {
            item = it.name
            notes = buildString {
                if (it.quantity.isNotBlank()) append("Qty: ${it.quantity}")
                if (it.note.isNotBlank()) {
                    if (isNotEmpty()) append(" | ")
                    append(it.note)
                }
            }
            paidBy = it.addedBy.ifBlank { settings.personAName }
            viewModel.clearPendingShoppingItem()
        }
    }

    LaunchedEffect(settings) {
        if (paidBy.isBlank()) paidBy = settings.personAName
    }

    val itemSuggestions = remember(expenses, item) {
        val query = item.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            expenses.map { it.item }
                .distinctBy { it.lowercase() }
                .filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
                .sortedBy { it.lowercase() }
                .take(6)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add Expense", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (${settings.currencySymbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        AutocompleteTextField(
            label = "Item / Description",
            value = item,
            suggestions = itemSuggestions,
            onValueChange = { item = it },
            onSuggestionSelected = { item = it }
        )

        OutlinedTextField(
            value = DateUtils.formatNepali(selectedDateMillis),
            onValueChange = {},
            readOnly = true,
            label = { Text("Date (BS)") },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        DropdownField(
            label = "Category",
            options = Constants.CATEGORIES,
            selected = category,
            onSelected = { category = it }
        )

        DropdownField(
            label = "Payment Method",
            options = Constants.PAYMENT_METHODS,
            selected = paymentMethod,
            onSelected = { paymentMethod = it }
        )

        DropdownField(
            label = "Paid By",
            options = listOf(settings.personAName, settings.personBName),
            selected = paidBy,
            onSelected = { paidBy = it }
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt != null && amt > 0 && item.isNotBlank()) {
                    viewModel.addExpense(
                        Expense(
                            amount = amt,
                            item = item.trim(),
                            category = category,
                            paymentMethod = paymentMethod,
                            paidBy = paidBy,
                            dateMillis = selectedDateMillis,
                            notes = notes.trim()
                        )
                    )
                    amount = ""
                    item = ""
                    notes = ""
                    selectedDateMillis = DateUtils.todayLocalNoon()
                    showConfirmation = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Expense")
        }

        if (showConfirmation) {
            Text("Expense saved \u2713", color = MaterialTheme.colorScheme.primary)
            LaunchedEffect(showConfirmation) {
                delay(2000.milliseconds)
                showConfirmation = false
            }
        }
    }

    if (showDatePicker) {
        val nepaliDatePickerState = rememberNepaliDatePickerState(
            initialSelectedDate = DateUtils.toNepaliSimpleDate(selectedDateMillis)
        )
        NepaliDatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                NepaliDatePickerDefaults.DialogButton(
                    text = "OK",
                    onButtonClick = {
                        nepaliDatePickerState.selectedEnglishDate?.let { english ->
                            selectedDateMillis = DateUtils.fromEnglishCustomCalendar(
                                english.year, english.month, english.dayOfMonth
                            )
                        }
                        showDatePicker = false
                    }
                )
            },
            dismissButton = {
                NepaliDatePickerDefaults.DialogButton(
                    text = "Cancel",
                    onButtonClick = { showDatePicker = false }
                )
            }
        ) {
            NepaliDatePicker(state = nepaliDatePickerState)
        }
    }
}
