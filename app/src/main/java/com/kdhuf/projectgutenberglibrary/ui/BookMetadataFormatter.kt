package com.kdhuf.projectgutenberglibrary.ui

object BookMetadataFormatter {

    fun normalizeTitle(rawTitle: String): String {
        val trimmed = rawTitle.trim()
        if (trimmed.isBlank()) return "Untitled"

        return trimmed
            .replace(Regex("""\s*:\s*\$[a-z].*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+/\s*$"""), "")
            .trim()
    }
}
