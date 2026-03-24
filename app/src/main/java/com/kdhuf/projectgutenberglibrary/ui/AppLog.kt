package com.kdhuf.projectgutenberglibrary.ui

import android.util.Log

internal inline fun debugLog(tag: String, message: () -> String) {
    if (Log.isLoggable(tag, Log.DEBUG)) {
        Log.d(tag, message())
    }
}
