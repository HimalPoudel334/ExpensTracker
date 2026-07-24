package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

@Composable
fun ExpensesListScreen(viewModel: MainViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    val filtered = expenses.filter {
        searchQuery.isBlank() ||
                it.item.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.paidBy.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Expenses", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by item, category, or person") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No expenses found")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { expense ->
                    ExpenseRow(expense = expense, onClick = { editingExpense = expense })
                }
            }
        }
    }

    editingExpense?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            viewModel = viewModel,
            onDismiss = { editingExpense = null }
        )
    }
}

@Composable
fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(expense.item, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${expense.category} \u2022 ${expense.paymentMethod} \u2022 ${expense.paidBy}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(DateUtils.formatNepali(expense.dateMillis), style = MaterialTheme.typography.bodySmall)
                Text(DateUtils.formatDate(expense.dateMillis), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Rs. ${"%.2f".format(expense.amount)}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun EditExpenseDialog(expense: Expense, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var item by remember { mutableStateOf(expense.item) }
    var category by remember { mutableStateOf(expense.category) }
    var paymentMethod by remember { mutableStateOf(expense.paymentMethod) }
    var paidBy by remember { mutableStateOf(expense.paidBy) }
    var notes by remember { mutableStateOf(expense.notes) }
    var selectedDateMillis by remember { mutableLongStateOf(expense.dateMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = item,
                    onValueChange = { item = it },
                    label = { Text("Item") },
                    modifier = Modifier.fillMaxWidth()
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
                Text(
                    "AD: ${DateUtils.formatDate(selectedDateMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownField("Category", Constants.CATEGORIES, category) { category = it }
                DropdownField("Payment Method", Constants.PAYMENT_METHODS, paymentMethod) { paymentMethod = it }
                DropdownField("Paid By", listOf(settings.personAName, settings.personBName), paidBy) { paidBy = it }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense.id)
                    onDismiss()
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0 && item.isNotBlank()) {
                        viewModel.updateExpense(
                            expense.copy(
                                amount = amt,
                                item = item.trim(),
                                category = category,
                                paymentMethod = paymentMethod,
                                paidBy = paidBy,
                                dateMillis = selectedDateMillis,
                                notes = notes.trim()
                            )
                        )
                        onDismiss()
                    }
                }) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

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
