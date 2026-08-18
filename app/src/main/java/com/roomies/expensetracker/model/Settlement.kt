package com.roomies.expensetracker.model

import com.google.firebase.firestore.Exclude

/**
 * A direct payment from one group member to another that pays down (some
 * or all of) their debt -- e.g. "Ram paid Sita Rs 500". This doesn't touch
 * any Expense; it's applied as an adjustment on top of expense-derived
 * balances (see MainViewModel.netBalances).
 */
data class Settlement(
    @get:Exclude var id: String = "",
    var fromUid: String = "",
    var toUid: String = "",
    var amount: Double = 0.0,
    var dateMillis: Long = System.currentTimeMillis(),
    var note: String = ""
)
