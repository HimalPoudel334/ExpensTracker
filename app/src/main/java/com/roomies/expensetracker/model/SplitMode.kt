package com.roomies.expensetracker.model

import kotlin.math.abs

/**
 * How an expense's amount is divided among group members. Whichever mode
 * produces splitAmong, it's resolved to concrete amounts and locked in at
 * save time (see resolveSplitAmong in Common.kt) -- so an EQUAL split
 * always reflects who was actually chosen to share it, and never silently
 * changes later just because the group's membership changed.
 */
enum class SplitMode {
    /** Divided evenly among a chosen set of members (see SplitEditor's checklist). */
    EQUAL,
    /** Manually entered per-member amounts. */
    MANUAL,
    /** Kept entirely by the payer -- not shared with the group at all. */
    ON_ME
}

/**
 * Best-guess mode for pre-filling the split editor when opening an existing
 * expense to edit. Purely a UI convenience -- doesn't affect balances.
 */
fun Expense.inferredSplitMode(): SplitMode = when {
    splitAmong.isEmpty() -> SplitMode.EQUAL
    isPersonalOnly -> SplitMode.ON_ME
    else -> {
        val nonZero = splitAmong.filterValues { it > 0.01 }
        val looksEven = nonZero.isNotEmpty() &&
            nonZero.values.all { abs(it - amount / nonZero.size) < 0.01 }
        if (looksEven) SplitMode.EQUAL else SplitMode.MANUAL
    }
}
