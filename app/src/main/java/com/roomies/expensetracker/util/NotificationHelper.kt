package com.roomies.expensetracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.graphics.Color

object NotificationHelper {
    private const val SHOPPING_CHANNEL_ID = "shopping_list"
    private const val SHOPPING_CHANNEL_NAME = "Shopping List"
    private const val EXPENSE_CHANNEL_ID = "expense_updates"
    private const val EXPENSE_CHANNEL_NAME = "Expense Updates"
    private var notifId = 1000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    SHOPPING_CHANNEL_ID, SHOPPING_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for new shopping list items"
                    enableLights(true)
                    lightColor = Color.GREEN
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    EXPENSE_CHANNEL_ID, EXPENSE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when a group member adds an expense"
                    enableLights(true)
                    lightColor = Color.BLUE
                }
            )
        }
    }

    fun notifyNewShoppingItem(context: Context, itemName: String, addedBy: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, SHOPPING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Shopping Item")
            .setContentText("$addedBy added \"$itemName\" to the list")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(notifId++, notification)
    }

    fun notifyNewExpense(context: Context, itemName: String, amountText: String, paidBy: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, EXPENSE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Expense")
            .setContentText("$paidBy added \"$itemName\" \u2014 $amountText")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(notifId++, notification)
    }
}
