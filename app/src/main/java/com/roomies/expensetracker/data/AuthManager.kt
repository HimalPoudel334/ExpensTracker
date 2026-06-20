package com.roomies.expensetracker.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Signs the device in anonymously to Firebase. This lets us write Firestore
 * security rules that require `request.auth != null`, which keeps random
 * internet bots out even though we don't have a real login screen.
 */
object AuthManager {
    private val auth = FirebaseAuth.getInstance()

    suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }
}