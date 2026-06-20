package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roomies.expensetracker.model.RecurringExpense
import com.roomies.expensetracker.util.Constants
import com.roomies.expensetracker.viewmodel.MainViewModel

@Composable
fun RecurringScreen(viewModel: MainViewModel) {
    val recurring by viewModel.recurring.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recurring Expenses", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add recurring expense")
            }
        }
        Text(
            "These repeat every month (e.g. rent, wifi). Tap below to add them as actual expenses for this month.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.generateRecurringForCurrentMonth() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate This Month's Recurring Expenses")
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (recurring.isEmpty()) {
            Text("No recurring expenses set up yet")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recurring, key = { it.id }) { r ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(r.item, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${r.category} \u2022 Day ${r.dayOfMonth} \u2022 ${r.paidBy} \u2022 ${r.paymentMethod}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Rs. ${"%.2f".format(r.amount)}")
                                IconButton(onClick = { viewModel.deleteRecurring(r.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecurringDialog(viewModel = viewModel, onDismiss = { showAddDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var item by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Constants.CATEGORIES.first()) }
    var paymentMethod by remember { mutableStateOf(Constants.PAYMENT_METHODS.first()) }
    var paidBy by remember { mutableStateOf(settings.personAName) }
    var dayOfMonth by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recurring Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item,
                    onValueChange = { item = it },
                    label = { Text("Item (e.g. Rent, Wifi)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownField("Category", Constants.CATEGORIES, category) { category = it }
                DropdownField("Payment Method", Constants.PAYMENT_METHODS, paymentMethod) { paymentMethod = it }
                DropdownField("Paid By", listOf(settings.personAName, settings.personBName), paidBy) { paidBy = it }
                OutlinedTextField(
                    value = dayOfMonth,
                    onValueChange = { dayOfMonth = it },
                    label = { Text("Day of month (1-28)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                val day = dayOfMonth.toIntOrNull()?.coerceIn(1, 28) ?: 1
                if (amt != null && amt > 0 && item.isNotBlank()) {
                    viewModel.addRecurring(
                        RecurringExpense(
                            item = item.trim(),
                            amount = amt,
                            category = category,
                            paymentMethod = paymentMethod,
                            paidBy = paidBy,
                            dayOfMonth = day
                        )
                    )
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
