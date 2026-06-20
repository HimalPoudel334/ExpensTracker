package com.roomies.expensetracker.model

data class Expense(
    var id: String = "",
    var amount: Double = 0.0,
    var item: String = "",
    var category: String = "",
    var paymentMethod: String = "",
    var paidBy: String = "",
    var dateMillis: Long = System.currentTimeMillis(),
    var notes: String = "",
    var recurringId: String = ""
)