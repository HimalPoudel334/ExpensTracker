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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.roomies.expensetracker.data.AuthManager
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.canManagePayers
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
    val members by viewModel.members.collectAsState()
    val pendingItem by viewModel.pendingShoppingItem.collectAsState()
    val activeGroup by viewModel.activeGroup.collectAsState()
    val currentUid = AuthManager.currentUser.collectAsState().value?.uid.orEmpty()
    val canChoosePayer = activeGroup?.canManagePayers(currentUid) ?: false

    var amountFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var item by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Constants.CATEGORIES.first()) }
    var paymentMethod by remember { mutableStateOf(Constants.PAYMENT_METHODS.first()) }
    var paidBy by remember { mutableStateOf(currentUid) }
    var notes by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableLongStateOf(DateUtils.todayLocalNoon()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var useCustomSplit by remember { mutableStateOf(false) }
    var splitValues by remember { mutableStateOf(emptyMap<String, String>()) }
    val amountFocusRequester = remember { FocusRequester() }

    // Whoever is attributed as payer decides how THIS expense is split; a
    // creator/admin can additionally edit anyone's split (matching their
    // "Paid By" override privilege).
    val canEditSplit = canChoosePayer || paidBy == currentUid

    // Pre-fill from shopping list if navigated from "Add as Expense". Only a
    // privileged (creator/admin) user gets the item's original payer carried
    // over -- everyone else's expenses are always attributed to themselves.
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
            paidBy = if (canChoosePayer) it.addedBy.ifBlank { currentUid } else currentUid
            // Do NOT clear pendingItem here — we need the ID when saving
        }
    }

    // Non-privileged members are always locked to themselves as payer; a
    // creator/admin can pick freely (see MemberDropdownField vs the read-only
    // field below).
    LaunchedEffect(canChoosePayer, currentUid) {
        if (!canChoosePayer || paidBy.isBlank()) paidBy = currentUid
    }

    val itemSuggestions = remember(expenses, item) {
        val query = item.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            expenses.asSequence().map { it.item }
                .distinctBy { it.lowercase() }
                .filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
                .sortedBy { it.lowercase() }
                .take(6).toList()
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

        AutocompleteTextField(
            label = "Item / Description",
            value = item,
            suggestions = itemSuggestions,
            onValueChange = { item = it },
            onSuggestionSelected = { selected ->
                item = selected
                val lastPrice = expenses
                    .filter { it.item.equals(selected, ignoreCase = true) }
                    .maxByOrNull { it.dateMillis }
                    ?.amount
                if (lastPrice != null) {
                    val text = lastPrice.toString()
                    amountFieldValue = TextFieldValue(text, selection = TextRange(0, text.length))
                    amountFocusRequester.requestFocus()
                }
            }
        )

        OutlinedTextField(
            value = amountFieldValue,
            onValueChange = { amountFieldValue = it },
            label = { Text("Amount (${settings.currencySymbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(amountFocusRequester)
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

        if (canChoosePayer) {
            MemberDropdownField(
                label = "Paid By",
                members = members,
                selectedUid = paidBy,
                onSelected = { paidBy = it }
            )
        } else {
            OutlinedTextField(
                value = members.find { it.first == currentUid }?.second ?: "You",
                onValueChange = {},
                readOnly = true,
                label = { Text("Paid By") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (canEditSplit) {
            SplitEditor(
                members = members,
                totalAmount = amountFieldValue.text.toDoubleOrNull() ?: 0.0,
                currencySymbol = settings.currencySymbol,
                useCustom = useCustomSplit,
                onUseCustomChange = { useCustomSplit = it },
                splitValues = splitValues,
                onSplitValuesChange = { splitValues = it }
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        val amt = amountFieldValue.text.toDoubleOrNull()
        val splitOk = !canEditSplit || isSplitValid(useCustomSplit, amt ?: 0.0, splitValues)

        Button(
            onClick = {
                if (amt != null && amt > 0 && item.isNotBlank()) {
                    viewModel.addExpense(
                        Expense(
                            amount = amt,
                            item = item.trim(),
                            category = category,
                            paymentMethod = paymentMethod,
                            paidBy = if (canChoosePayer) paidBy else currentUid,
                            dateMillis = selectedDateMillis,
                            notes = notes.trim(),
                            splitAmong = if (canEditSplit) resolveSplitAmong(useCustomSplit, splitValues) else emptyMap()
                        )
                    )
                    // Now that expense is saved, remove the shopping item it came from
                    pendingItem?.let {
                        viewModel.deleteShoppingItem(it.id)
                        viewModel.clearPendingShoppingItem()
                    }
                    amountFieldValue = TextFieldValue("")
                    item = ""
                    notes = ""
                    selectedDateMillis = DateUtils.todayLocalNoon()
                    useCustomSplit = false
                    splitValues = emptyMap()
                    showConfirmation = true
                }
            },
            enabled = amt != null && amt > 0 && item.isNotBlank() && splitOk,
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
