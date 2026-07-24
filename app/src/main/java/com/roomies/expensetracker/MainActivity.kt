package com.roomies.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.roomies.expensetracker.data.AuthManager
import com.roomies.expensetracker.ui.navigation.Screen
import com.roomies.expensetracker.ui.navigation.bottomNavItems
import com.roomies.expensetracker.ui.screens.AddExpenseScreen
import com.roomies.expensetracker.ui.screens.ExpensesListScreen
import com.roomies.expensetracker.ui.screens.GroupsScreen
import com.roomies.expensetracker.ui.screens.LoginScreen
import com.roomies.expensetracker.ui.screens.RecurringScreen
import com.roomies.expensetracker.ui.screens.ReportsScreen
import com.roomies.expensetracker.ui.screens.SettingsScreen
import com.roomies.expensetracker.ui.screens.ShoppingListScreen
import com.roomies.expensetracker.ui.theme.ExpenseTrackerTheme
import com.roomies.expensetracker.util.NotificationHelper
import com.roomies.expensetracker.viewmodel.MainViewModel
import kotlin.getValue

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* silently accepted or denied — app works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            ExpenseTrackerTheme {
                AppRoot(viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val currentUser by AuthManager.currentUser.collectAsState()

    if (currentUser == null) {
        LoginScreen()
        return
    }

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
            composable(Screen.Shopping.route) { ShoppingListScreen(viewModel, navController) }
            composable(Screen.Reports.route) { ReportsScreen(viewModel) }
            composable(Screen.Recurring.route) { RecurringScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel, navController) }
            composable(Screen.Groups.route) { GroupsScreen(viewModel) }
        }
    }
}

