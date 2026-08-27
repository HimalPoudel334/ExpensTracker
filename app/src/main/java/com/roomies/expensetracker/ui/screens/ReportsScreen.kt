package com.roomies.expensetracker.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.roomies.expensetracker.data.AuthManager
import com.roomies.expensetracker.model.BalanceDetail
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.ItemStat
import com.roomies.expensetracker.model.SuggestedSettlement
import com.roomies.expensetracker.model.canManagePayers
import com.roomies.expensetracker.util.CsvExporter
import com.roomies.expensetracker.util.DateUtils
import com.roomies.expensetracker.viewmodel.MainViewModel

private enum class ReportFilterMode { THIS_MONTH, CUSTOM_RANGE }

/** Palette cycled through for category/person/payment-method breakdown bars. */
private val ReportPalette = listOf(
    Color(0xFF2E7D32), // primary green
    Color(0xFFFFA000), // amber
    Color(0xFF1976D2), // blue
    Color(0xFF8E24AA), // purple
    Color(0xFFD81B60), // pink
    Color(0xFF00897B), // teal
    Color(0xFF6D4C41), // brown
    Color(0xFF546E7A), // blue grey
)

@OptIn(ExperimentalMaterial3Api::class)
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

    var filterMode by remember { mutableStateOf(ReportFilterMode.THIS_MONTH) }
    var refMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var rangeStart by remember { mutableLongStateOf(DateUtils.bsMonthStart(System.currentTimeMillis())) }
    var rangeEnd by remember { mutableLongStateOf(DateUtils.bsMonthEnd(System.currentTimeMillis())) }

    val periodExpenses = when (filterMode) {
        ReportFilterMode.THIS_MONTH -> viewModel.expensesForMonth(refMillis)
        ReportFilterMode.CUSTOM_RANGE -> viewModel.expensesInRange(
            minOf(rangeStart, rangeEnd), maxOf(rangeStart, rangeEnd)
        )
    }
    val periodLabel = when (filterMode) {
        ReportFilterMode.THIS_MONTH -> DateUtils.formatNepaliMonthYear(refMillis)
        ReportFilterMode.CUSTOM_RANGE -> "${DateUtils.formatNepali(rangeStart)} \u2013 ${DateUtils.formatNepali(rangeEnd)}"
    }
    val exportLabel = when (filterMode) {
        ReportFilterMode.THIS_MONTH -> DateUtils.formatMonthYear(refMillis)
        ReportFilterMode.CUSTOM_RANGE -> "${DateUtils.formatDate(rangeStart)}_to_${DateUtils.formatDate(rangeEnd)}"
    }

    val total = viewModel.totalOf(periodExpenses)
    val byPerson = viewModel.byPersonOf(periodExpenses).mapKeys { memberNames[it.key] ?: it.key }
    val byPersonShared = viewModel.byPersonSharedOf(periodExpenses).mapKeys { memberNames[it.key] ?: it.key }
    val byCategory = viewModel.byCategoryOf(periodExpenses)
    val byPayment = viewModel.byPaymentMethodOf(periodExpenses)
    val itemStats = viewModel.topItemStatsOf(periodExpenses)
    val periodBalances = viewModel.balancesOf(periodExpenses)
    val periodBalanceDetails = viewModel.balanceDetailsOf(periodExpenses)
    val memberCount = activeGroup?.members?.size ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Reports", style = MaterialTheme.typography.headlineSmall)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = filterMode == ReportFilterMode.THIS_MONTH,
                onClick = { filterMode = ReportFilterMode.THIS_MONTH },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("This Month") }
            SegmentedButton(
                selected = filterMode == ReportFilterMode.CUSTOM_RANGE,
                onClick = {
                    // Seed the range with the currently viewed BS month the first
                    // time the user switches into custom range mode.
                    if (filterMode != ReportFilterMode.CUSTOM_RANGE) {
                        rangeStart = DateUtils.bsMonthStart(refMillis)
                        rangeEnd = DateUtils.bsMonthEnd(refMillis)
                    }
                    filterMode = ReportFilterMode.CUSTOM_RANGE
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Custom Range") }
        }

        if (filterMode == ReportFilterMode.THIS_MONTH) {
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
                    Text(
                        DateUtils.formatMonthYear(refMillis), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { refMillis = DateUtils.addBsMonths(refMillis, 1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NepaliDatePickerField(
                    label = "From",
                    selectedMillis = rangeStart,
                    modifier = Modifier.weight(1f),
                    onDateSelected = { rangeStart = it }
                )
                NepaliDatePickerField(
                    label = "To",
                    selectedMillis = rangeEnd,
                    modifier = Modifier.weight(1f),
                    onDateSelected = { rangeEnd = it }
                )
            }
        }

        ReportHeroCard(periodLabel = periodLabel, total = total, currencySymbol = settings.currencySymbol)

        if (periodExpenses.isEmpty()) {
            EmptyReportState()
        } else {
            ReportSection(title = "By Category", icon = Icons.Filled.Category) {
                BreakdownBarList(byCategory, total, settings.currencySymbol)
            }

            ReportSection(title = "By Person", icon = Icons.Filled.Person) {
                BreakdownBarList(byPerson, total, settings.currencySymbol)
            }

            ReportSection(title = "Shared Expenses by Person", icon = Icons.Filled.Person) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Excludes \u201cthis is on me\u201d expenses that were never split with the group",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BreakdownBarList(byPersonShared, byPersonShared.values.sum(), settings.currencySymbol)
                }
            }

            ReportSection(title = "By Payment Method", icon = Icons.Filled.Payments) {
                BreakdownBarList(byPayment, total, settings.currencySymbol)
            }

            ReportSection(title = "Top Items", icon = Icons.Filled.Star) {
                SortableItemsTable(stats = itemStats, currencySymbol = settings.currencySymbol)
            }

            ReportSection(title = "Balances This Period", icon = Icons.Filled.AccountBalanceWallet) {
                if (periodBalances.isEmpty()) {
                    Text(
                        "No group members to split between yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Each expense is split equally among all $memberCount current " +
                                "members by default (a custom split is used instead wherever one was set).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        periodBalances.entries.sortedByDescending { it.value }.forEach { (uid, balance) ->
                            BalanceRow(
                                name = memberNames[uid] ?: uid,
                                balance = balance,
                                currencySymbol = settings.currencySymbol,
                                detail = periodBalanceDetails[uid]
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { exportAndShareCsv(context, periodExpenses, exportLabel, memberNames) },
            modifier = Modifier.fillMaxWidth(),
            enabled = periodExpenses.isNotEmpty()
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export as CSV")
        }

        ReportSection(title = "Settle Up (all-time)", icon = Icons.Filled.SwapHoriz) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (netBalances.isEmpty()) {
                        Text(
                            "No group members to split between yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        netBalances.entries.sortedByDescending { it.value }.forEach { (uid, balance) ->
                            BalanceRow(memberNames[uid] ?: uid, balance, settings.currencySymbol)
                        }
                    }
                }

                if (suggestedSettlements.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Suggested Settlements", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        suggestedSettlements.forEach { suggestion ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${memberNames[suggestion.fromUid] ?: suggestion.fromUid} \u2192 " +
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
                }

                if (settlementsList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Recent Settlements", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                        DateUtils.formatNepali(s.dateMillis),
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
                }

                OutlinedButton(
                    onClick = { showRecordDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Payment, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record a Payment")
                }
            }
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
private fun ReportHeroCard(periodLabel: String, total: Double, currencySymbol: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                periodLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$currencySymbol ${"%.2f".format(total)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Total spent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun ReportSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun BreakdownBarList(data: Map<String, Double>, total: Double, currencySymbol: String) {
    if (data.isEmpty()) {
        Text("No data", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val sorted = data.entries.sortedByDescending { it.value }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sorted.forEachIndexed { index, (label, amount) ->
            val fraction = if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f
            val color = ReportPalette[index % ReportPalette.size]
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$currencySymbol ${"%.2f".format(amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

private enum class ItemSortColumn { ITEM, AMOUNT, COUNT }

/**
 * Merges the old "Top Items by Amount" / "Top Items by Frequency" lists into
 * one table. Tap a header to sort by that column, like clicking a column
 * header in a web datatable; tap the same header again to flip direction.
 */
@Composable
private fun SortableItemsTable(stats: List<ItemStat>, currencySymbol: String) {
    var sortColumn by remember { mutableStateOf(ItemSortColumn.AMOUNT) }
    var ascending by remember { mutableStateOf(false) }

    fun toggleSort(column: ItemSortColumn) {
        if (sortColumn == column) ascending = !ascending else {
            sortColumn = column
            ascending = false
        }
    }

    val sorted = remember(stats, sortColumn, ascending) {
        val base = when (sortColumn) {
            ItemSortColumn.ITEM -> stats.sortedBy { it.item.lowercase() }
            ItemSortColumn.AMOUNT -> stats.sortedBy { it.amount }
            ItemSortColumn.COUNT -> stats.sortedBy { it.count }
        }
        if (ascending) base else base.reversed()
    }

    if (stats.isEmpty()) {
        Text("No data", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SortableHeaderCell(
                label = "Item",
                modifier = Modifier.weight(1f),
                active = sortColumn == ItemSortColumn.ITEM,
                ascending = ascending,
                onClick = { toggleSort(ItemSortColumn.ITEM) }
            )
            SortableHeaderCell(
                label = "Amount",
                modifier = Modifier.width(96.dp),
                active = sortColumn == ItemSortColumn.AMOUNT,
                ascending = ascending,
                onClick = { toggleSort(ItemSortColumn.AMOUNT) }
            )
            SortableHeaderCell(
                label = "Times",
                modifier = Modifier.width(56.dp),
                active = sortColumn == ItemSortColumn.COUNT,
                ascending = ascending,
                onClick = { toggleSort(ItemSortColumn.COUNT) }
            )
        }
        HorizontalDivider()
        sorted.forEach { stat ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stat.item, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "$currencySymbol ${"%.2f".format(stat.amount)}",
                    modifier = Modifier.width(96.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("${stat.count}", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SortableHeaderCell(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean,
    ascending: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
        if (active) {
            Icon(
                if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (ascending) "Sorted ascending" else "Sorted descending",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(14.dp).height(14.dp)
            )
        }
    }
}

@Composable
private fun BalanceRow(
    name: String,
    balance: Double,
    currencySymbol: String,
    detail: BalanceDetail? = null
) {
    val (label, containerColor, contentColor) = when {
        balance > 0.5 -> Triple(
            "is owed $currencySymbol ${"%.2f".format(balance)}",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        balance < -0.5 -> Triple(
            "owes $currencySymbol ${"%.2f".format(-balance)}",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            "settled up",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = contentColor, fontWeight = FontWeight.Medium)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            }
            if (detail != null) {
                Text(
                    "paid $currencySymbol ${"%.2f".format(detail.paid)} \u2212 " +
                        "share $currencySymbol ${"%.2f".format(detail.share)} = " +
                        "$currencySymbol ${"%.2f".format(detail.net)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun EmptyReportState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(40.dp)
            )
            Text(
                "No expenses for this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

private fun exportAndShareCsv(context: Context, expenses: List<Expense>, label: String, memberNames: Map<String, String>) {
    val file = CsvExporter.export(context, expenses, label, memberNames)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share CSV report"))
}
