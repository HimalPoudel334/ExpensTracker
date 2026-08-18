package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roomies.expensetracker.util.DateUtils
import dev.shivathapaa.nepalidatepickerkmp.NepaliDatePicker
import dev.shivathapaa.nepalidatepickerkmp.NepaliDatePickerDialog
import dev.shivathapaa.nepalidatepickerkmp.calendar_model.NepaliDatePickerDefaults
import dev.shivathapaa.nepalidatepickerkmp.rememberNepaliDatePickerState
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Like DropdownField, but the options are (uid, displayName) pairs — shows
 * names, emits the uid. Used for "Paid By"/"Added By" pickers now that those
 * fields store a real group member's uid instead of a free-text name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDropdownField(
    label: String,
    members: List<Pair<String, String>>,
    selectedUid: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = members.find { it.first == selectedUid }?.second ?: selectedUid
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            members.forEach { (uid, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(uid)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/**
 * Read-only text field that opens a Nepali (BS) date picker dialog when tapped.
 * `selectedMillis` is our local-noon AD storage convention; `onDateSelected`
 * receives the same convention back.
 */
@Composable
fun NepaliDatePickerField(
    label: String,
    selectedMillis: Long,
    modifier: Modifier = Modifier,
    onDateSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = DateUtils.formatNepali(selectedMillis),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
            }
        },
        modifier = modifier.fillMaxWidth()
    )

    if (showDialog) {
        val state = rememberNepaliDatePickerState(
            initialSelectedDate = DateUtils.toNepaliSimpleDate(selectedMillis)
        )
        NepaliDatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                NepaliDatePickerDefaults.DialogButton(
                    text = "OK",
                    onButtonClick = {
                        state.selectedEnglishDate?.let { english ->
                            onDateSelected(
                                DateUtils.fromEnglishCustomCalendar(
                                    english.year, english.month, english.dayOfMonth
                                )
                            )
                        }
                        showDialog = false
                    }
                )
            },
            dismissButton = {
                NepaliDatePickerDefaults.DialogButton(
                    text = "Cancel",
                    onButtonClick = { showDialog = false }
                )
            }
        ) {
            NepaliDatePicker(state = state)
        }
    }
}

/**
 * Lets the user optionally override an expense's equal split. When
 * `useCustom` is false, `splitAmong` stays empty and Expense.shareFor falls
 * back to an equal split (the pre-existing behavior) -- so nothing changes
 * for anyone who never touches this. When true, each member gets an
 * editable amount and the caller should block saving until they sum to the
 * expense total (see the "Allocated ... of ..." line, which turns to an
 * error color when it doesn't match).
 *
 * State (useCustom, splitValues) is hoisted to the caller so Add and Edit
 * screens can initialize it differently (Add: off by default; Edit: on and
 * pre-filled if the expense already has a custom splitAmong).
 */
@Composable
fun SplitEditor(
    members: List<Pair<String, String>>,
    totalAmount: Double,
    currencySymbol: String,
    useCustom: Boolean,
    onUseCustomChange: (Boolean) -> Unit,
    splitValues: Map<String, String>,
    onSplitValuesChange: (Map<String, String>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Split this expense manually", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = useCustom, onCheckedChange = onUseCustomChange)
        }

        if (useCustom) {
            Spacer(modifier = Modifier.height(4.dp))
            members.forEach { (uid, name) ->
                OutlinedTextField(
                    value = splitValues[uid] ?: "",
                    onValueChange = { value -> onSplitValuesChange(splitValues + (uid to value)) },
                    label = { Text(name) },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
            val allocated = splitValues.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
            val remaining = totalAmount - allocated
            val isBalanced = abs(remaining) <= 0.01
            Text(
                if (isBalanced) {
                    "Allocated $currencySymbol${"%.2f".format(allocated)} of $currencySymbol${"%.2f".format(totalAmount)} ✓"
                } else {
                    "Allocated $currencySymbol${"%.2f".format(allocated)} of $currencySymbol${"%.2f".format(totalAmount)}" +
                        " ($currencySymbol${"%.2f".format(remaining)} left)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isBalanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * True once a custom split (if enabled) is internally consistent: every
 * member has a valid number and they sum to the expense total. Used to gate
 * the Save button so an unbalanced manual split can't be submitted.
 */
fun isSplitValid(useCustom: Boolean, totalAmount: Double, splitValues: Map<String, String>): Boolean {
    if (!useCustom) return true
    if (splitValues.values.any { it.toDoubleOrNull() == null }) return false
    val allocated = splitValues.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    return abs(totalAmount - allocated) <= 0.01
}

/** Converts SplitEditor's text-field state into the Map<String, Double> Expense.splitAmong expects, or empty if not using a custom split. */
fun resolveSplitAmong(useCustom: Boolean, splitValues: Map<String, String>): Map<String, Double> {
    if (!useCustom) return emptyMap()
    return splitValues.mapNotNull { (uid, text) -> text.toDoubleOrNull()?.let { uid to it } }.toMap()
}

/**
 * Editable text field that shows matching previously-used values as you type.
 * Used for the Item field so "Jar Water" and "Water Jar" don't end up as two
 * different items in the reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteTextField(
    label: String,
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onSuggestionSelected(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}
