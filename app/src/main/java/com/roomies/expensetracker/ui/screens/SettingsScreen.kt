package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.roomies.expensetracker.data.AuthManager
import com.roomies.expensetracker.model.AppSettings
import com.roomies.expensetracker.model.canManagePayers
import com.roomies.expensetracker.ui.navigation.Screen
import com.roomies.expensetracker.util.ServerHealth
import com.roomies.expensetracker.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, navController: NavHostController) {
    val currentUser by AuthManager.currentUser.collectAsState()
    val activeGroup by viewModel.activeGroup.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val serverHealth by viewModel.serverHealth.collectAsState()
    val isAdmin = activeGroup?.canManagePayers(currentUser?.uid.orEmpty()) == true
    var currency by remember(settings) { mutableStateOf(settings.currencySymbol) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it },
            label = { Text("Currency Symbol") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                viewModel.updateSettings(AppSettings(currency.trim()))
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Settings")
        }

        if (saved) {
            Text("Saved \u2713", color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider()
        Text("Group", style = MaterialTheme.typography.titleMedium)
        Text(
            activeGroup?.name ?: "No group selected",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(
            onClick = { navController.navigate(Screen.Groups.route) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Groups")
        }

        HorizontalDivider()
        Text("Recurring Expenses", style = MaterialTheme.typography.titleMedium)
        Text(
            "Set up expenses that repeat automatically, like rent or subscriptions.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(
            onClick = { navController.navigate(Screen.Recurring.route) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Recurring Expenses")
        }

        HorizontalDivider()
        Text("Account", style = MaterialTheme.typography.titleMedium)
        Text(
            currentUser?.email ?: currentUser?.displayName ?: "Signed in",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(
            onClick = { AuthManager.signOut() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }

        if (isAdmin) {
            HorizontalDivider()
            Text("Notify Server", style = MaterialTheme.typography.titleMedium)
            ServerHealthRow(
                health = serverHealth,
                onRefresh = { viewModel.checkServerHealth() }
            )
        }
    }
}

/** Admin-only reachability indicator for the notify server. Tap to re-check. */
@Composable
private fun ServerHealthRow(health: ServerHealth, onRefresh: () -> Unit) {
    val (color, label) = when (health) {
        ServerHealth.HEALTHY -> Color(0xFF2ECC71) to "Online"
        ServerHealth.UNREACHABLE -> Color(0xFFE74C3C) to "Unreachable"
        ServerHealth.CHECKING -> Color(0xFFF1C40F) to "Checking\u2026"
        ServerHealth.NOT_CONFIGURED -> Color.Gray to "Not configured"
        ServerHealth.UNKNOWN -> Color.Gray to "Unknown"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRefresh)
            .padding(vertical = 4.dp)
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
