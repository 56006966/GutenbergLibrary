package com.kdhuf.projectgutenberglibrary.data.remote

import com.kdhuf.projectgutenberglibrary.BuildConfig
import java.net.URI

object GutenbergMirror {
    private const val DEFAULT_MAIN_HOST = "www.gutenberg.org"
    private const val LEGACY_MAIN_HOST = "gutenberg.org"
    private const val DEFAULT_MAIN_BASE = "https://www.gutenberg.org/"
    private val MIRROR_HOST_MARKERS = listOf(".pglaf.org", ".gutenberg.org")
    private val fallbackBases = listOf(
        "https://gutenberg.pglaf.org/",
        "https://aleph.pglaf.org/"
    )

    private val baseUrl: String
        get() = BuildConfig.GUTENBERG_MIRROR_BASE_URL.ensureTrailingSlash()

    fun coverUrl(bookId: Int): String = "${DEFAULT_MAIN_BASE}cache/epub/$bookId/pg$bookId.cover.medium.jpg"

    fun imagesEpubUrl(bookId: Int): String = "${DEFAULT_MAIN_BASE}ebooks/$bookId.epub3.images"

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
                buildList {
                    translatedMirrorCandidates(parsed).forEach(::add)
                    literalRewriteCandidates(parsed).forEach { candidate ->
                        if (candidate !in this) add(candidate)
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

    private fun translatedMirrorCandidates(parsed: URI): List<String> {
        val path = parsed.rawPath?.removePrefix("/") ?: return emptyList()
        val candidates = mutableListOf<String>()

        Regex("""cache/epub/(\d+)/(.*)""").matchEntire(path)?.let { match ->
            val bookId = match.groupValues[1]
            val fileName = match.groupValues[2]
            candidates += buildUrl(baseUrl, "$bookId/$fileName", parsed)
            return candidates
        }

        Regex("""ebooks/(\d+)\.epub3\.images""").matchEntire(path)?.let { match ->
            val bookId = match.groupValues[1]
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-images-3.epub", parsed)
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-images.epub", parsed)
            return candidates
        }

        Regex("""ebooks/(\d+)\.epub\.images""").matchEntire(path)?.let { match ->
            val bookId = match.groupValues[1]
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-images.epub", parsed)
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-images-3.epub", parsed)
            return candidates
        }

        Regex("""ebooks/(\d+)\.epub3\.noimages""").matchEntire(path)?.let { match ->
            val bookId = match.groupValues[1]
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-noimages-3.epub", parsed)
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-noimages.epub", parsed)
            return candidates
        }

        Regex("""ebooks/(\d+)\.epub\.noimages""").matchEntire(path)?.let { match ->
            val bookId = match.groupValues[1]
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-noimages.epub", parsed)
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-noimages-3.epub", parsed)
            return candidates
        }

        Regex("""ebooks/(\d+)\.html\.images""").matchEntire(path)?.let { match ->
            val bookId = match.groupValues[1]
            candidates += buildUrl(baseUrl, "$bookId/pg$bookId-images.html", parsed)
            return candidates
        }

        return candidates
    }

    private fun literalRewriteCandidates(parsed: URI): List<String> {
        val path = parsed.rawPath?.removePrefix("/") ?: return emptyList()
        return allBaseUrls().map { candidateBase ->
            buildUrl(candidateBase, path, parsed)
        }
    }

    private fun buildUrl(base: String, path: String, parsed: URI): String {
        return buildString {
            append(base.ensureTrailingSlash())
            append(path.removePrefix("/"))
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

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}
