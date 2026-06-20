package com.roomies.expensetracker.model

data class RecurringExpense(
    var id: String = "",
    var item: String = "",
    var category: String = "",
    var amount: Double = 0.0,
    var paymentMethod: String = "",
    var paidBy: String = "",
    var dayOfMonth: Int = 1,
    var active: Boolean = true
)