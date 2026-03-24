package com.kdhuf.projectgutenberglibrary.ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.kdhuf.projectgutenberglibrary.R

internal object ExternalLinkPolicy {
    fun openWithNotice(
        context: Context,
        url: String
    ): Boolean {
        val noticeRes = if (url.contains("gutenberg.org", ignoreCase = true)) {
            R.string.external_link_gutenberg_notice
        } else {
            R.string.external_link_generic_notice
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.external_link_title)
            .setMessage(noticeRes)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.external_link_continue) { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
            .show()

        return true
    }
}
