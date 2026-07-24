package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roomies.expensetracker.data.AuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ExpensTracker",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            "Sign in to sync your expenses with your group.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val result = AuthManager.signInWithGoogle(context)
                        isLoading = false
                        result.onFailure { e ->
                            errorMessage = e.message ?: "Sign-in failed. Please try again."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with Google")
            }
        }

        errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
