package com.onyx.android.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NoteNamingRule(
    val storageValue: String,
    val label: String,
) {
    UNTITLED_COUNTER(storageValue = "untitled_counter", label = "Untitled 1, 2, 3"),
    DATE_TIME(storageValue = "date_time", label = "Date and time"),
    BLANK(storageValue = "blank", label = "Blank title"),
    ;

    companion object {
        fun fromStorageValue(value: String?): NoteNamingRule =
            entries.firstOrNull { rule -> rule.storageValue == value } ?: UNTITLED_COUNTER
    }
}

class NoteLaunchPreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isResumeLastPageEnabled(): Boolean = prefs.getBoolean(KEY_RESUME_LAST_PAGE, DEFAULT_RESUME_LAST_PAGE)

    fun setResumeLastPageEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME_LAST_PAGE, enabled).apply()
    }

    fun getNoteNamingRule(): NoteNamingRule =
        NoteNamingRule.fromStorageValue(prefs.getString(KEY_NOTE_NAMING_RULE, DEFAULT_NOTE_NAMING_RULE))

    fun setNoteNamingRule(rule: NoteNamingRule) {
        prefs.edit().putString(KEY_NOTE_NAMING_RULE, rule.storageValue).apply()
    }

    fun buildDateTimeTitle(timestampMs: Long): String = DATE_TIME_FORMATTER.format(Date(timestampMs))

    companion object {
        private const val PREFS_NAME = "onyx_note_launch_preferences"
        private const val KEY_RESUME_LAST_PAGE = "resume_last_page"
        private const val KEY_NOTE_NAMING_RULE = "note_naming_rule"

        private const val DEFAULT_NOTE_NAMING_RULE = "untitled_counter"
        private const val DEFAULT_RESUME_LAST_PAGE = true

        private val DATE_TIME_FORMATTER =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.US,
            )
    }
}
