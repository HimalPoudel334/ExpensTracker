package com.roomies.expensetracker.model

/**
 * A suggested payment that would reduce a group's outstanding debts,
 * produced by simplifying everyone's net balance into the smallest
 * practical set of transactions (see MainViewModel.simplifiedSettlements).
 * Not persisted on its own -- confirming one via "Settle Up" records it as
 * a real Settlement.
 */
data class SuggestedSettlement(
    val fromUid: String,
    val toUid: String,
    val amount: Double
)
