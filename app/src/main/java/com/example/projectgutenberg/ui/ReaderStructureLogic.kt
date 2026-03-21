package com.example.projectgutenberg.ui

internal data class ReaderTocEntry(
    val title: String,
    val depth: Int,
    val pageIndex: Int
)

internal data class ReaderTocHint(
    val title: String,
    val depth: Int
)

internal data class ReaderStructure(
    val tocEntries: List<ReaderTocEntry>,
    val chapterTitlesByPage: List<String?>,
    val printedPageLabelsByPage: List<String?>
)

internal object ReaderStructureLogic {
    private val tagRegex = Regex("<[^>]+>")
    private val entityRegex = Regex("&[^;]+;")
    private val whitespaceRegex = Regex("\\s+")
    private val bracketedPageRegex = Regex("""\[\s*(?:page|pg\.?)\s+([ivxlcdm0-9]+)\s*\]""", RegexOption.IGNORE_CASE)
    private val pageMarkerTagRegex = Regex(
        """<(span|a|div)\b[^>]*(?:class|id|epub:type|role|title)\s*=\s*["'][^"']*(?:pagebreak|page-break|pagenum|page-num|page-number|pagenumber|pb)[^"']*["'][^>]*>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun buildStructure(
        pages: List<String>,
        tocHints: List<ReaderTocHint>
    ): ReaderStructure {
        val pageTexts = pages.map(::normalizeText)
        val tocEntries = tocHints.mapNotNull { hint ->
            findMatchingPageIndex(hint.title, pageTexts)?.let { pageIndex ->
                ReaderTocEntry(
                    title = hint.title,
                    depth = hint.depth,
                    pageIndex = pageIndex
                )
            }
        }
            .distinctBy { it.title.lowercase() to it.pageIndex }
            .sortedWith(compareBy<ReaderTocEntry> { it.pageIndex }.thenBy { it.depth })

        val chapterTitles = MutableList<String?>(pages.size) { null }
        if (tocEntries.isNotEmpty()) {
            tocEntries.forEachIndexed { index, entry ->
                val nextPageIndexExclusive = tocEntries.getOrNull(index + 1)?.pageIndex ?: pages.size
                for (pageIndex in entry.pageIndex until nextPageIndexExclusive.coerceAtMost(pages.size)) {
                    if (pageIndex in chapterTitles.indices) {
                        chapterTitles[pageIndex] = entry.title
                    }
                }
            }
        }

        val printedPageLabels = pages.map(::extractPrintedPageLabel)

        return ReaderStructure(
            tocEntries = tocEntries,
            chapterTitlesByPage = chapterTitles,
            printedPageLabelsByPage = printedPageLabels
        )
    }

    fun extractPrintedPageLabel(pageBody: String): String? {
        val explicitMarker = pageMarkerTagRegex.find(pageBody)?.groupValues?.getOrNull(2)
            ?.let(::normalizeText)
            ?.takeIf { it.isNotBlank() }
        if (!explicitMarker.isNullOrBlank()) {
            return explicitMarker
        }

        return bracketedPageRegex.find(pageBody)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun formatReaderPosition(
        chapterTitle: String?,
        printedPageLabel: String?,
        logicalPageLabel: String,
        logicalPageCountLabel: String
    ): String {
        return listOfNotNull(
            chapterTitle?.takeIf { it.isNotBlank() },
            printedPageLabel?.takeIf { it.isNotBlank() }?.let { "Printed page $it" },
            "Page $logicalPageLabel of $logicalPageCountLabel"
        ).joinToString(" • ")
    }

    private fun findMatchingPageIndex(title: String, normalizedPages: List<String>): Int? {
        val normalizedTitle = normalizeText(title)
        if (normalizedTitle.isBlank()) return null

        val exactIndex = normalizedPages.indexOfFirst { page ->
            page.startsWith(normalizedTitle) || page.contains(normalizedTitle)
        }
        if (exactIndex >= 0) return exactIndex

        val compactTitle = normalizedTitle.replace(whitespaceRegex, "")
        return normalizedPages.indexOfFirst { page ->
            page.replace(whitespaceRegex, "").contains(compactTitle)
        }.takeIf { it >= 0 }
    }

    private fun normalizeText(value: String): String {
        return tagRegex.replace(value, " ")
            .replace(entityRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
            .lowercase()
    }
}
