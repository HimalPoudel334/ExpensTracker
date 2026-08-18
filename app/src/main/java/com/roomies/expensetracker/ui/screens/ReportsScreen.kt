package com.roomies.expensetracker.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.roomies.expensetracker.data.AuthManager
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.SuggestedSettlement
import com.roomies.expensetracker.model.canManagePayers
import com.roomies.expensetracker.util.CsvExporter
import com.roomies.expensetracker.util.DateUtils
import com.roomies.expensetracker.viewmodel.MainViewModel

@Composable
fun ReportsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val memberNames by viewModel.memberNames.collectAsState()
    val members by viewModel.members.collectAsState()
    // collecting this keeps the screen recomposing whenever expenses change
    val expenseUpdates by viewModel.expenses.collectAsState()
    val netBalances by viewModel.netBalances.collectAsState()
    val suggestedSettlements by viewModel.simplifiedSettlements.collectAsState()
    val settlementsList by viewModel.settlements.collectAsState()
    val activeGroup by viewModel.activeGroup.collectAsState()
    val currentUid = AuthManager.currentUser.collectAsState().value?.uid.orEmpty()
    val canManage = activeGroup?.canManagePayers(currentUid) ?: false

    var showRecordDialog by remember { mutableStateOf(false) }
    var prefillSettlement by remember { mutableStateOf<SuggestedSettlement?>(null) }

    var refMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val total = viewModel.totalForMonth(refMillis)
    val byPerson = viewModel.byPersonForMonth(refMillis)
    val byCategory = viewModel.byCategoryForMonth(refMillis)
    val byPayment = viewModel.byPaymentMethodForMonth(refMillis)
    val topByAmount = viewModel.topItemsByAmount(refMillis)
    val topByFrequency = viewModel.topItemsByFrequency(refMillis)
    val balances = viewModel.balancesForMonth(refMillis)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { refMillis = DateUtils.addBsMonths(refMillis, -1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(DateUtils.formatNepaliMonthYear(refMillis), style = MaterialTheme.typography.titleLarge)
                Text(DateUtils.formatMonthYear(refMillis), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refMillis = DateUtils.addBsMonths(refMillis, 1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
            }
        }

        StatCard("Total Monthly Expenses", "${settings.currencySymbol} ${"%.2f".format(total)}")

        Text("Expenses by Person", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (byPerson.isEmpty()) Text("No data") else byPerson.forEach { (uid, amt) ->
            ReportRow(memberNames[uid] ?: uid, "${settings.currencySymbol} ${"%.2f".format(amt)}")
        }

        Text("Expenses by Category", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (byPerson.isEmpty()) Text("No data") else byCategory.forEach { (category, amt) ->
            ReportRow(category, "${settings.currencySymbol} ${"%.2f".format(amt)}")
        }

        Text("Expenses by Payment Method", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (byPayment.isEmpty()) Text("No data") else byPayment.forEach { (method, amt) ->
            ReportRow(method, "${settings.currencySymbol} ${"%.2f".format(amt)}")
        }

        Text("Most Expended Items (by amount)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (topByAmount.isEmpty()) Text("No data") else topByAmount.forEach { (item, amt) ->
            ReportRow(item, "${settings.currencySymbol} ${"%.2f".format(amt)}")
        }

        Text("Most Frequently Bought Items", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (topByFrequency.isEmpty()) Text("No data") else topByFrequency.forEach { (item, count) ->
            ReportRow(item, "$count time(s)")
        }

        HorizontalDivider()
        Text(
            "Balances (equal split among current group members)",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
        )
        if (balances.isEmpty()) {
            Text("No group members to split between yet")
        } else {
            balances.entries.sortedByDescending { it.value }.forEach { (uid, balance) ->
                val name = memberNames[uid] ?: uid
                val label = when {
                    balance > 0.5 -> "is owed ${settings.currencySymbol} ${"%.2f".format(balance)}"
                    balance < -0.5 -> "owes ${settings.currencySymbol} ${"%.2f".format(-balance)}"
                    else -> "settled up"
                }
                ReportRow(name, label)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { exportAndShareCsv(context, viewModel.expensesForMonth(refMillis), refMillis, memberNames) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Month as CSV")
        }

        HorizontalDivider()
        Text(
            "Overall Balance (all-time)",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
        )
        if (netBalances.isEmpty()) {
            Text("No group members to split between yet")
        } else {
            netBalances.entries.sortedByDescending { it.value }.forEach { (uid, balance) ->
                val name = memberNames[uid] ?: uid
                val label = when {
                    balance > 0.5 -> "is owed ${settings.currencySymbol} ${"%.2f".format(balance)}"
                    balance < -0.5 -> "owes ${settings.currencySymbol} ${"%.2f".format(-balance)}"
                    else -> "settled up"
                }
                ReportRow(name, label)
            }
        }

        if (suggestedSettlements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Suggested Settlements", style = MaterialTheme.typography.titleSmall)
            suggestedSettlements.forEach { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${memberNames[suggestion.fromUid] ?: suggestion.fromUid} → " +
                            "${memberNames[suggestion.toUid] ?: suggestion.toUid}: " +
                            "${settings.currencySymbol} ${"%.2f".format(suggestion.amount)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { prefillSettlement = suggestion }) {
                        Text("Settle")
                    }
                }
            }
        }

        if (settlementsList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Recent Settlements", style = MaterialTheme.typography.titleSmall)
            settlementsList.take(10).forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${memberNames[s.fromUid] ?: s.fromUid} paid ${memberNames[s.toUid] ?: s.toUid} " +
                                "${settings.currencySymbol} ${"%.2f".format(s.amount)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            DateUtils.formatDate(s.dateMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canManage) {
                        IconButton(onClick = { viewModel.deleteSettlement(s.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete settlement",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = { showRecordDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Payment, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Record a Payment")
        }
    }

    if (showRecordDialog) {
        RecordSettlementDialog(
            members = members,
            currentUid = currentUid,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showRecordDialog = false },
            onRecord = { fromUid, toUid, amount, note ->
                viewModel.recordSettlement(fromUid, toUid, amount, note)
                showRecordDialog = false
            }
        )
    }

    prefillSettlement?.let { suggestion ->
        RecordSettlementDialog(
            members = members,
            currentUid = currentUid,
            currencySymbol = settings.currencySymbol,
            initialFromUid = suggestion.fromUid,
            initialToUid = suggestion.toUid,
            initialAmount = suggestion.amount,
            onDismiss = { prefillSettlement = null },
            onRecord = { fromUid, toUid, amount, note ->
                viewModel.recordSettlement(fromUid, toUid, amount, note)
                prefillSettlement = null
            }
        )
    }
}

@Composable
fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Records a direct payment between two members. Pre-fills From/To/Amount
 * when opened from a suggested settlement's "Settle" button; opens blank
 * (defaulting From to the signed-in user) from "Record a Payment".
 */
@Composable
fun RecordSettlementDialog(
    members: List<Pair<String, String>>,
    currentUid: String,
    currencySymbol: String,
    initialFromUid: String = currentUid,
    initialToUid: String = "",
    initialAmount: Double? = null,
    onDismiss: () -> Unit,
    onRecord: (fromUid: String, toUid: String, amount: Double, note: String) -> Unit
) {
    var fromUid by remember { mutableStateOf(initialFromUid) }
    var toUid by remember { mutableStateOf(initialToUid.ifBlank { members.firstOrNull { it.first != fromUid }?.first ?: "" }) }
    var amount by remember { mutableStateOf(initialAmount?.let { "%.2f".format(it) } ?: "") }
    var note by remember { mutableStateOf("") }

    val amt = amount.toDoubleOrNull()
    val isValid = amt != null && amt > 0 && fromUid.isNotBlank() && toUid.isNotBlank() && fromUid != toUid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record a Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberDropdownField("From (who paid)", members, fromUid) { fromUid = it }
                MemberDropdownField("To (who received)", members, toUid) { toUid = it }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (fromUid == toUid && fromUid.isNotBlank()) {
                    Text(
                        "From and To must be different people",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRecord(fromUid, toUid, amt ?: 0.0, note) },
                enabled = isValid
            ) { Text("Record") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun exportAndShareCsv(context: Context, expenses: List<Expense>, refMillis: Long, memberNames: Map<String, String>) {
    val file = CsvExporter.export(context, expenses, DateUtils.formatMonthYear(refMillis), memberNames)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share CSV report"))
}
