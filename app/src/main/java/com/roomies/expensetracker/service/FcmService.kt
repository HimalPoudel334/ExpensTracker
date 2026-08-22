package com.roomies.expensetracker.service

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.roomies.expensetracker.data.AuthManager

/**
 * Registers this device for push notifications sent via PushNotifier/the
 * self-hosted notify server (see /server), so group members get a system
 * notification even when ExpenseTracker isn't running.
 *
 * Deliberately does NOT override onMessageReceived. Our pushes are sent with
 * a "notification" block (see PushNotifier/the server's /notify), and for
 * that kind of FCM message, Android only invokes onMessageReceived while the
 * app is in the foreground -- and in that case, the Firestore live-listener
 * already running in MainViewModel independently shows the same
 * notification via NotificationHelper. Overriding this here too would just
 * duplicate it. When the app is backgrounded or killed, the OS shows the
 * notification itself directly from the system tray, no code of ours runs
 * at all -- which is the entire point of this class existing.
 */
class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = AuthManager.currentUser.value?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
    }
}
