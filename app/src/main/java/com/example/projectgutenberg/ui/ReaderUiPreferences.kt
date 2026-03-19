package com.example.projectgutenberg.ui

import android.content.Context

class ReaderUiPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDarkModeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "reader_ui_prefs"
        const val KEY_DARK_MODE = "dark_mode_enabled"
    }
}
