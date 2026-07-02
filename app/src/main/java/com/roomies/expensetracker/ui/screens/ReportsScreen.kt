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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.util.CsvExporter
import com.roomies.expensetracker.util.DateUtils
import com.roomies.expensetracker.util.DeviceConfig
import com.roomies.expensetracker.viewmodel.MainViewModel

@Composable
fun ReportsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    // collecting this keeps the screen recomposing whenever expenses change
    val expenseUpdates by viewModel.expenses.collectAsState()

    var refMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val total = viewModel.totalForMonth(refMillis)
    val byPerson = viewModel.byPersonForMonth(refMillis)
    val byCategory = viewModel.byCategoryForMonth(refMillis)
    val byPayment = viewModel.byPaymentMethodForMonth(refMillis)
    val topByAmount = viewModel.topItemsByAmount(refMillis)
    val topByFrequency = viewModel.topItemsByFrequency(refMillis)
    val (aPaid, bPaid, settlementMsg) = viewModel.settlementForMonth(refMillis)

    val isMyDevice = DeviceConfig.isMyDevice(context)

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
        if (byPerson.isEmpty()) Text("No data") else byPerson.forEach { (person, amt) ->
            ReportRow(person, "${settings.currencySymbol} ${"%.2f".format(amt)}")
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
        if (isMyDevice) {
            Text(
                "Settlement (assumes 50/50 split)",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
            )
        }
        ReportRow(settings.personAName, "${settings.currencySymbol} ${"%.2f".format(aPaid)} paid")
        ReportRow(settings.personBName, "${settings.currencySymbol} ${"%.2f".format(bPaid)} paid")
        if (isMyDevice) {
            Text(
                settlementMsg,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { exportAndShareCsv(context, viewModel.expensesForMonth(refMillis), refMillis) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Month as CSV")
        }
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

private fun exportAndShareCsv(context: Context, expenses: List<Expense>, refMillis: Long) {
    val file = CsvExporter.export(context, expenses, DateUtils.formatMonthYear(refMillis))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share CSV report"))
}
