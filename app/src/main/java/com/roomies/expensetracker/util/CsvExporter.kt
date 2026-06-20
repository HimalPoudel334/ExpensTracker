package com.roomies.expensetracker.util

import android.content.Context
import com.roomies.expensetracker.model.Expense
import java.io.File
import java.io.FileWriter

object CsvExporter {
    fun export(context: Context, expenses: List<Expense>, label: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val safeLabel = label.replace(" ", "_")
        val file = File(dir, "expenses_$safeLabel.csv")
        FileWriter(file).use { writer ->
            writer.append("Date,Item,Category,Payment Method,Paid By,Amount,Notes\n")
            expenses.sortedBy { it.dateMillis }.forEach { e ->
                writer.append(formatDate(e) + "," +
                        quote(e.item) + "," +
                        e.category + "," +
                        e.paymentMethod + "," +
                        e.paidBy + "," +
                        e.amount.toString() + "," +
                        quote(e.notes) + "\n")
            }
        }
        return file
    }

    private fun formatDate(e: Expense) = DateUtils.formatDate(e.dateMillis)

    private fun quote(value: String): String {
        val cleaned = value.replace("\"", "'")
        return "\"$cleaned\""
    }
}