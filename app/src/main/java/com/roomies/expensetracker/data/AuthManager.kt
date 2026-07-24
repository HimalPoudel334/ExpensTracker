package com.roomies.expensetracker.data

import android.content.Context
import com.roomies.expensetracker.R
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID

/**
 * Handles real Google Sign-In via Credential Manager, replacing the previous
 * anonymous-only auth. Firebase's `default_web_client_id` string resource is
 * auto-generated from google-services.json by the google-services Gradle
 * plugin, so no client ID needs to be hardcoded here.
 */
object AuthManager {
    private val auth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { _currentUser.value = it.currentUser }
    }

    /**
     * Launches the Credential Manager bottom sheet, signs the resulting
     * Google ID token into Firebase, and returns the signed-in user.
     * filterByAuthorizedAccounts = false means this works for both
     * first-time sign-up and returning sign-in.
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .setNonce(generateNonce())
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: return Result.failure(IllegalStateException("Sign-in returned no user"))

            ensureUserDoc(user)
            Result.success(user)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    /** Creates users/{uid} on first sign-in; no-op if it already exists. */
    private suspend fun ensureUserDoc(user: FirebaseUser) {
        try {
            val userDoc = mapOf(
                "displayName" to (user.displayName ?: ""),
                "email" to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: "")
            )
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.uid)
                .set(userDoc, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            // Non-fatal: user doc can be retried/backfilled later; don't block sign-in on this.
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return UUID.nameUUIDFromBytes(bytes).toString()
    }
}
