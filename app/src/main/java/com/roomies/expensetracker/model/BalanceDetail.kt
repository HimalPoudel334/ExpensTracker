package com.roomies.expensetracker.model

/**
 * The paid vs. share breakdown behind one member's net balance, so the
 * Reports screen can show group members how a balance was actually
 * calculated instead of just the final number (see
 * MainViewModel.balanceDetailsOf).
 */
data class BalanceDetail(
    val paid: Double,
    val share: Double,
    val net: Double
)
