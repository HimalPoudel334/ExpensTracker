package com.roomies.expensetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Add : Screen("add", "Add", Icons.Filled.Add)
    object Expenses : Screen("expenses", "Expenses", Icons.AutoMirrored.Filled.List)
    object Shopping : Screen("shopping", "Shopping", Icons.Filled.ShoppingCart)
    object Reports : Screen("reports", "Reports", Icons.Filled.Assessment)
    object Recurring : Screen("recurring", "Recurring", Icons.Filled.Repeat)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Add,
    Screen.Expenses,
    Screen.Shopping,
    Screen.Reports,
    Screen.Recurring,
    Screen.Settings
)
