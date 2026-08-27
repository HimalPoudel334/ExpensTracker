package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roomies.expensetracker.model.SplitMode
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
 * Lets the user choose how an expense is split:
 * - EQUAL: divided evenly among a checked subset of members. Whoever is
 *   checked when the expense is saved is who it's split among, permanently
 *   -- adding or removing a group member later never changes it (see
 *   resolveSplitAmong). Defaults to everyone currently in the group.
 * - MANUAL: an editable amount per member, unchanged from before.
 * - ON_ME: the whole amount stays with the payer; nothing owed by anyone else.
 *
 * State (mode, equalParticipants, splitValues) is hoisted to the caller so
 * Add and Edit screens can initialize it differently (Add: EQUAL/everyone
 * by default; Edit: pre-filled from the expense's existing splitAmong via
 * Expense.inferredSplitMode).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditor(
    members: List<Pair<String, String>>,
    totalAmount: Double,
    currencySymbol: String,
    mode: SplitMode,
    onModeChange: (SplitMode) -> Unit,
    equalParticipants: Set<String>,
    onEqualParticipantsChange: (Set<String>) -> Unit,
    splitValues: Map<String, String>,
    onSplitValuesChange: (Map<String, String>) -> Unit
) {
    var equalPickerExpanded by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Split", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == SplitMode.EQUAL,
                    onClick = { onModeChange(SplitMode.EQUAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Equal") }
                SegmentedButton(
                    selected = mode == SplitMode.MANUAL,
                    onClick = { onModeChange(SplitMode.MANUAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Manual") }
                SegmentedButton(
                    selected = mode == SplitMode.ON_ME,
                    onClick = { onModeChange(SplitMode.ON_ME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("On Me") }
            }
            Spacer(modifier = Modifier.height(4.dp))
            when (mode) {
                SplitMode.EQUAL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (equalParticipants.size == members.size) {
                                "Splitting among all ${members.size} members"
                            } else {
                                "Splitting among ${equalParticipants.size} of ${members.size} members"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (equalPickerExpanded) "Done" else "Change",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { equalPickerExpanded = !equalPickerExpanded }
                        )
                    }
                    if (equalPickerExpanded) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            members.forEach { (uid, name) ->
                                val checked = equalParticipants.contains(uid)
                                FilterChip(
                                    selected = checked,
                                    onClick = {
                                        onEqualParticipantsChange(
                                            if (checked) equalParticipants - uid else equalParticipants + uid
                                        )
                                    },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                    if (equalParticipants.isNotEmpty()) {
                        val share = totalAmount / equalParticipants.size
                        Text(
                            "$currencySymbol${"%.2f".format(share)} each \u00d7 ${equalParticipants.size} " +
                                    if (equalParticipants.size == 1) "person" else "people",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Select at least one person",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                SplitMode.MANUAL -> {
                    members.forEach { (uid, name) ->
                        OutlinedTextField(
                            value = splitValues[uid] ?: "",
                            onValueChange = { value -> onSplitValuesChange(splitValues + (uid to value)) },
                            label = { Text(name) },
                            prefix = { Text(currencySymbol) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val allocated = splitValues.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
                    val remaining = totalAmount - allocated
                    val isBalanced = abs(remaining) <= 0.01
                    Text(
                        if (isBalanced) {
                            "Allocated $currencySymbol${"%.2f".format(allocated)} of $currencySymbol${"%.2f".format(totalAmount)} \u2713"
                        } else {
                            "Allocated $currencySymbol${"%.2f".format(allocated)} of $currencySymbol${"%.2f".format(totalAmount)}" +
                                " ($currencySymbol${"%.2f".format(remaining)} left)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isBalanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                SplitMode.ON_ME -> {
                    Text(
                        "Full amount stays with the payer \u2014 not shared with the group",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * True once the current split mode's inputs are internally consistent and
 * safe to save: EQUAL needs at least one person checked, MANUAL needs every
 * member's amount to parse and sum to the expense total, ON_ME is always
 * valid. Used to gate the Save button.
 */
fun isSplitValid(
    mode: SplitMode,
    totalAmount: Double,
    equalParticipants: Set<String>,
    splitValues: Map<String, String>
): Boolean = when (mode) {
    SplitMode.ON_ME -> true
    SplitMode.EQUAL -> equalParticipants.isNotEmpty()
    SplitMode.MANUAL -> {
        if (splitValues.values.any { it.toDoubleOrNull() == null }) {
            false
        } else {
            val allocated = splitValues.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
            abs(totalAmount - allocated) <= 0.01
        }
    }
}

/**
 * Converts SplitEditor's state into the Map<String, Double> Expense.splitAmong
 * expects. Unlike the old "empty means equal split among current members"
 * behavior, EQUAL now always resolves to concrete per-member amounts here --
 * so the split is locked in at save time and stays correct even if the
 * group's membership changes later (see SplitMode.kt).
 */
fun resolveSplitAmong(
    mode: SplitMode,
    payerUid: String,
    totalAmount: Double,
    equalParticipants: Set<String>,
    splitValues: Map<String, String>
): Map<String, Double> = when (mode) {
    SplitMode.ON_ME -> mapOf(payerUid to totalAmount)
    SplitMode.MANUAL -> splitValues.mapNotNull { (uid, text) -> text.toDoubleOrNull()?.let { uid to it } }.toMap()
    SplitMode.EQUAL -> {
        if (equalParticipants.isEmpty()) {
            emptyMap()
        } else {
            val share = totalAmount / equalParticipants.size
            equalParticipants.associateWith { share }
        }
    }
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
