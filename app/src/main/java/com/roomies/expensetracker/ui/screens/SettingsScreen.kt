package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roomies.expensetracker.model.AppSettings
import com.roomies.expensetracker.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    var personA by remember(settings) { mutableStateOf(settings.personAName) }
    var personB by remember(settings) { mutableStateOf(settings.personBName) }
    var currency by remember(settings) { mutableStateOf(settings.currencySymbol) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = personA,
            onValueChange = { personA = it },
            label = { Text("Person A Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = personB,
            onValueChange = { personB = it },
            label = { Text("Person B Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it },
            label = { Text("Currency Symbol") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                viewModel.updateSettings(AppSettings(personA.trim(), personB.trim(), currency.trim()))
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
        Text(
            "Both phones must use the same Firebase project (same google-services.json) for data to sync between devices.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
