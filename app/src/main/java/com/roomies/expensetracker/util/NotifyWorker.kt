package com.roomies.expensetracker.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.roomies.expensetracker.data.FirestoreRepository
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Runs [PushNotifier.notify] (token lookup + the call to the self-hosted
 * webhook) as WorkManager work instead of inside viewModelScope.
 *
 * Why this exists: viewModelScope is canceled the moment the ViewModel is
 * cleared, which happens as soon as the user backgrounds or swipes away the
 * app right after adding an expense/item -- the exact moment this call needs
 * to survive. WorkManager schedules this as independent, OS-managed work
 * that isn't tied to the Activity/ViewModel lifecycle, and retries with
 * backoff if the network blips (e.g. the notify server was briefly
 * unreachable), instead of silently dying with a SocketException.
 */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val otherUids = inputData.getStringArray(KEY_OTHER_UIDS)?.toList().orEmpty()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val channelId = inputData.getString(KEY_CHANNEL_ID) ?: return Result.failure()
        val data = inputData.getString(KEY_DATA)?.let { json ->
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.orEmpty()

        if (otherUids.isEmpty()) return Result.success()

        return try {
            val tokensByUid = FirestoreRepository.getFcmTokens(otherUids)
            PushNotifier.notify(tokensByUid, title, body, channelId, data)
            Result.success()
        } catch (e: Exception) {
            // Transient (network) failure -- let WorkManager retry with backoff
            // rather than dropping the push entirely.
            Result.retry()
        }
    }

    companion object {
        private const val KEY_OTHER_UIDS = "otherUids"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_CHANNEL_ID = "channelId"
        private const val KEY_DATA = "data"

        /** Enqueues the push as durable, retrying background work. */
        fun enqueue(
            context: Context,
            otherUids: List<String>,
            title: String,
            body: String,
            channelId: String,
            data: Map<String, String> = emptyMap()
        ) {
            if (otherUids.isEmpty()) return

            val input = workDataOf(
                KEY_OTHER_UIDS to otherUids.toTypedArray(),
                KEY_TITLE to title,
                KEY_BODY to body,
                KEY_CHANNEL_ID to channelId,
                KEY_DATA to JSONObject(data as Map<*, *>).toString()
            )

            val request = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setInputData(input)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
