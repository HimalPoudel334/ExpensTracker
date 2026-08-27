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
    var recurringId: String = "",
    // uid -> that member's share of `amount` for this expense. Empty means
    // "not customized" -- split equally among the group's CURRENT members
    // instead (the pre-existing behavior). Non-empty entries should sum to
    // `amount`; members omitted from the map owe nothing for this expense.
    var splitAmong: Map<String, Double> = emptyMap()
)

/**
 * This expense's share for one member, whether it uses a custom split or
 * falls back to an equal split across the group's current members.
 */
fun Expense.shareFor(uid: String, groupMemberUids: List<String>): Double = when {
    splitAmong.isNotEmpty() -> splitAmong[uid] ?: 0.0
    groupMemberUids.isNotEmpty() -> amount / groupMemberUids.size
    else -> 0.0
}

/**
 * True for a "this is on me" expense: nobody but the payer has a nonzero
 * share, so nothing is actually owed to the group for it. Ignores
 * zero-value entries -- a manual split where everyone else was typed as 0
 * counts as personal too, not just the explicit "On Me" mode.
 */
val Expense.isPersonalOnly: Boolean
    get() {
        val nonZeroKeys = splitAmong.filterValues { it > 0.01 }.keys
        return nonZeroKeys.isNotEmpty() && nonZeroKeys.singleOrNull() == paidBy
    }