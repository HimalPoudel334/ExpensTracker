package com.roomies.expensetracker.model

data class ShoppingItem(
    var id: String = "",
    var name: String = "",
    var quantity: String = "",
    var addedBy: String = "",
    var addedByMe: Boolean = false,
    var note: String = "",
    var addedAtMillis: Long = System.currentTimeMillis()
)
