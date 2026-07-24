package com.roomies.expensetracker.data

import android.content.Context

/**
 * Remembers which group this device last showed, per signed-in user.
 * This is a per-device UI preference, not shared data, so it lives in
 * SharedPreferences rather than Firestore.
 */
object GroupPreference {
    private const val PREFS_NAME = "group_prefs"
    private fun key(uid: String) = "active_group_id_$uid"

    fun getSelectedGroupId(context: Context, uid: String): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key(uid), null)

    fun setSelectedGroupId(context: Context, uid: String, groupId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(key(uid), groupId).apply()
    }

    fun clearSelectedGroupId(context: Context, uid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(key(uid)).apply()
    }
}
