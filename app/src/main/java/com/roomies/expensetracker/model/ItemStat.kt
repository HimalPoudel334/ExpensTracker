package com.roomies.expensetracker.model

/**
 * Combined amount + frequency stats for one item name, used by the Reports
 * screen's sortable "Top Items" table (see MainViewModel.topItemStatsOf).
 */
data class ItemStat(
    val item: String,
    val amount: Double,
    val count: Int
)
