package com.phunkypixels.projectgutenberglibrary.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

private const val TAG = "NetworkStatus"

fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (securityException: SecurityException) {
        Log.w(TAG, "Unable to read network state; treating device as offline", securityException)
        false
    }
}
