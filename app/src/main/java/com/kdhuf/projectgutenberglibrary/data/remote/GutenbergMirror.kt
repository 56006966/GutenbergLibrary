package com.kdhuf.projectgutenberglibrary.data.remote

import com.kdhuf.projectgutenberglibrary.BuildConfig
import java.net.URI

object GutenbergMirror {
    private const val DEFAULT_MAIN_HOST = "www.gutenberg.org"
    private const val LEGACY_MAIN_HOST = "gutenberg.org"
    private val MIRROR_HOST_MARKERS = listOf(".pglaf.org", ".gutenberg.org")
    private val fallbackBases = listOf(
        "https://gutenberg.pglaf.org/",
        "https://aleph.pglaf.org/"
    )

    private val baseUrl: String
        get() = BuildConfig.GUTENBERG_MIRROR_BASE_URL.ensureTrailingSlash()

    fun coverUrl(bookId: Int): String = "${baseUrl}cache/epub/$bookId/pg$bookId.cover.medium.jpg"

    fun imagesEpubUrl(bookId: Int): String = "${baseUrl}cache/epub/$bookId/pg$bookId-images.epub"

    fun resolve(url: String?): String? {
        return resolveCandidates(url).firstOrNull()
    }

    fun resolveCandidates(url: String?): List<String> {
        val raw = url?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val parsed = URI(raw)
            val host = parsed.host?.lowercase().orEmpty()
            if (!shouldRewriteHost(host)) {
                listOf(raw.replace("http://", "https://"))
            } else {
                val path = parsed.rawPath?.removePrefix("/") ?: ""
                allBaseUrls().map { candidateBase ->
                    buildString {
                        append(candidateBase)
                        append(path)
                        parsed.rawQuery?.let {
                            append('?')
                            append(it)
                        }
                        parsed.rawFragment?.let {
                            append('#')
                            append(it)
                        }
                    }
                }
            }
        }.getOrElse { listOf(raw.replace("http://", "https://")) }
    }

    private fun shouldRewriteHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (host == DEFAULT_MAIN_HOST || host == LEGACY_MAIN_HOST) return true
        return MIRROR_HOST_MARKERS.any { host.endsWith(it) }
    }

    private fun allBaseUrls(): List<String> {
        return buildList {
            add(baseUrl)
            fallbackBases.forEach { candidate ->
                val normalized = candidate.ensureTrailingSlash()
                if (normalized != baseUrl) {
                    add(normalized)
                }
            }
        }
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}
