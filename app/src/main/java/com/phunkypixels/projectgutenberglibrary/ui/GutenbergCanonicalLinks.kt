package com.phunkypixels.projectgutenberglibrary.ui

import android.content.Context

internal object GutenbergCanonicalLinks {
    fun bookUrl(bookId: Int): String = "https://www.gutenberg.org/ebooks/$bookId"

    fun authorUrl(authorName: String): String {
        val slug = authorName
            .trim()
            .replace(Regex("\\s+"), "_")
        return "https://www.gutenberg.org/author/$slug"
    }

    fun openBook(context: Context, bookId: Int): Boolean {
        return ExternalLinkPolicy.openWithNotice(context, bookUrl(bookId))
    }
}
