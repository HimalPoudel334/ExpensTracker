package com.roomies.expensetracker.util

import android.util.Log
import com.roomies.expensetracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Reachability of the notify server (see /server, same one PushNotifier posts to). */
enum class ServerHealth { UNKNOWN, CHECKING, HEALTHY, UNREACHABLE, NOT_CONFIGURED }

/**
 * Pings the notify server's `/health` endpoint. Mirrors PushNotifier's plain
 * HttpURLConnection approach rather than pulling in Retrofit for one call.
 * Admin-only signal on Settings -- the app works fine without a notify
 * server configured, this just tells an admin whether push is currently up.
 */
object ServerHealthChecker {

    suspend fun check(): ServerHealth {
        val serverUrl = BuildConfig.NOTIFY_SERVER_URL
        if (serverUrl.isBlank()) return ServerHealth.NOT_CONFIGURED

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${serverUrl.trimEnd('/')}/health")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val responseCode = conn.responseCode
                val body = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText().orEmpty()
                }
                conn.disconnect()

                if (responseCode in 200..299 && JSONObject(body).optString("status") == "ok") {
                    ServerHealth.HEALTHY
                } else {
                    ServerHealth.UNREACHABLE
                }
            } catch (e: Exception) {
                Log.w("ServerHealthChecker", "health check failed", e)
                ServerHealth.UNREACHABLE
            }
        }
    }
}
