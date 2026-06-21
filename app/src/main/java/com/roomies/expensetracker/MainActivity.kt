package com.roomies.expensetracker

import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.roomies.expensetracker.ui.navigation.Screen
import com.roomies.expensetracker.ui.navigation.bottomNavItems
import com.roomies.expensetracker.ui.screens.AddExpenseScreen
import com.roomies.expensetracker.ui.screens.ExpensesListScreen
import com.roomies.expensetracker.ui.screens.RecurringScreen
import com.roomies.expensetracker.ui.screens.ReportsScreen
import com.roomies.expensetracker.ui.screens.SettingsScreen
import com.roomies.expensetracker.ui.theme.ExpenseTrackerTheme
import com.roomies.expensetracker.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseTrackerTheme {
                AppRoot(viewModel)
            }
        }
    }
}

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Add.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Add.route) { AddExpenseScreen(viewModel) }
            composable(Screen.Expenses.route) { ExpensesListScreen(viewModel) }
            composable(Screen.Reports.route) { ReportsScreen(viewModel) }
            composable(Screen.Recurring.route) { RecurringScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}
