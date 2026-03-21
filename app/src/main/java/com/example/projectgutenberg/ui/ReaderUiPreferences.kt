package com.example.projectgutenberg.ui

import android.content.Context

class ReaderUiPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDarkModeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun getTextZoom(): Int = prefs.getInt(KEY_TEXT_ZOOM, DEFAULT_TEXT_ZOOM)

    fun setTextZoom(zoom: Int) {
        prefs.edit().putInt(KEY_TEXT_ZOOM, zoom).apply()
    }

    fun getLastPageIndex(bookId: Int): Int = prefs.getInt(lastPageKey(bookId), 0)

    fun setLastPageIndex(bookId: Int, pageIndex: Int) {
        prefs.edit().putInt(lastPageKey(bookId), pageIndex).apply()
    }

    fun getLastScrollRatio(bookId: Int): Float = prefs.getFloat(lastScrollKey(bookId), 0f)

    fun setLastScrollRatio(bookId: Int, ratio: Float) {
        prefs.edit().putFloat(lastScrollKey(bookId), ratio.coerceIn(0f, 1f)).apply()
    }

    fun getPreferredVoiceName(): String? = prefs.getString(KEY_TTS_VOICE_NAME, null)

    fun setPreferredVoiceName(voiceName: String?) {
        prefs.edit().putString(KEY_TTS_VOICE_NAME, voiceName).apply()
    }

    private companion object {
        const val PREFS_NAME = "reader_ui_prefs"
        const val KEY_DARK_MODE = "dark_mode_enabled"
        const val KEY_TEXT_ZOOM = "text_zoom"
        const val KEY_TTS_VOICE_NAME = "tts_voice_name"
        const val DEFAULT_TEXT_ZOOM = 115

        fun lastPageKey(bookId: Int): String = "last_page_$bookId"
        fun lastScrollKey(bookId: Int): String = "last_scroll_$bookId"
    }
}
