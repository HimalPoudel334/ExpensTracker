package com.roomies.expensetracker.util

import android.util.Log
import com.roomies.expensetracker.BuildConfig
import com.roomies.expensetracker.data.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls the small self-hosted webhook (see /server in the repo) that relays a
 * push through Firebase Cloud Messaging, so group members get a system
 * notification even when ExpenseTracker isn't running on their phone.
 *
 * This is fire-and-forget from the caller's point of view: failures are
 * logged and swallowed, never surfaced to the user. The existing in-app
 * live-listener notification (NotificationHelper, in MainViewModel) already
 * covers the "app is open" case on other members' devices independently of
 * whether this succeeds -- this is purely the "app is closed" fallback.
 *
 * If NOTIFY_SERVER_URL / NOTIFY_SHARED_SECRET aren't configured in
 * local.properties, this silently does nothing rather than failing loudly --
 * the app is fully usable without a notify server, just without background
 * push.
 */
object PushNotifier {

    /**
     * @param tokensByUid uid -> that user's FCM tokens. Kept per-uid (rather
     *   than a flat token list) purely so a failed send can be traced back to
     *   whose Firestore doc a stale token should be removed from.
     */
    suspend fun notify(
        tokensByUid: Map<String, List<String>>,
        title: String,
        body: String,
        channelId: String,
        data: Map<String, String> = emptyMap()
    ) {
        val serverUrl = BuildConfig.NOTIFY_SERVER_URL
        val secret = BuildConfig.NOTIFY_SHARED_SECRET
        if (serverUrl.isBlank() || secret.isBlank()) return

        val tokenToUid = tokensByUid.flatMap { (uid, tokens) -> tokens.map { it to uid } }.toMap()
        val allTokens = tokenToUid.keys.toList()
        if (allTokens.isEmpty()) return

        withContext(Dispatchers.IO) {
            try {
                val url = URL("${serverUrl.trimEnd('/')}/notify")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $secret")
                }

                val dataJson = JSONObject().apply {
                    data.forEach { (k, v) -> put(k, v) }
                }
                val payload = JSONObject().apply {
                    put("tokens", JSONArray(allTokens))
                    put("title", title)
                    put("body", body)
                    put("channelId", channelId)
                    put("data", dataJson)
                }

                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.readText().orEmpty()
                conn.disconnect()

                if (responseCode !in 200..299) {
                    Log.w("PushNotifier", "notify failed: HTTP $responseCode $responseText")
                    return@withContext
                }

                cleanUpStaleTokens(responseText, tokenToUid)
            } catch (e: Exception) {
                Log.w("PushNotifier", "notify request failed", e)
            }
        }
    }

    /** Removes tokens FCM reports as unregistered, so a user's token list doesn't grow stale forever. */
    private fun cleanUpStaleTokens(responseText: String, tokenToUid: Map<String, String>) {
        val results = try {
            JSONObject(responseText).optJSONArray("results")
        } catch (e: Exception) {
            null
        } ?: return

        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optBoolean("success", true)) continue
            val badToken = r.optString("token")
            val error = r.optString("error")
            val looksStale = error.contains("UNREGISTERED", ignoreCase = true) ||
                error.contains("NOT_FOUND", ignoreCase = true) ||
                error.contains("404")
            if (looksStale) {
                tokenToUid[badToken]?.let { uid -> FirestoreRepository.removeFcmToken(uid, badToken) }
            }
        }
    }
}
