package com.tapflow.app

import android.content.Context

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var totalTaps: Int
        get() = prefs.getInt(KEY_TOTAL, 0)
        set(value) { prefs.edit().putInt(KEY_TOTAL, value).apply() }

    var bestSession: Int
        get() = prefs.getInt(KEY_BEST, 0)
        set(value) { prefs.edit().putInt(KEY_BEST, value).apply() }

    var sessionCount: Int
        get() = prefs.getInt(KEY_SESSIONS, 0)
        set(value) { prefs.edit().putInt(KEY_SESSIONS, value).apply() }

    fun addSession(count: Int, durationSeconds: Long, date: String) {
        val entry = "$count$SEP$durationSeconds$SEP$date"
        val existing = prefs.getString(KEY_HISTORY, "") ?: ""
        val updated = if (existing.isEmpty()) entry else "$entry\n$existing"
        prefs.edit().putString(KEY_HISTORY, updated.lines().take(50).joinToString("\n")).apply()
    }

    fun getHistory(): List<TapSession> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.split(SEP)
            if (parts.size == 3) TapSession(
                tapCount = parts[0].toIntOrNull() ?: return@mapNotNull null,
                durationSeconds = parts[1].toLongOrNull() ?: return@mapNotNull null,
                date = parts[2]
            ) else null
        }
    }

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS_NAME = "tapflow_prefs"
        private const val KEY_TOTAL = "total_taps"
        private const val KEY_BEST = "best_session"
        private const val KEY_SESSIONS = "session_count"
        private const val KEY_HISTORY = "history"
        private const val SEP = "|"
    }
}
