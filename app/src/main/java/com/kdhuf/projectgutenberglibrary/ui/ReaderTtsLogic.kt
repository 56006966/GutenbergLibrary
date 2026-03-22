package com.kdhuf.projectgutenberglibrary.ui

internal data class TtsWordBoundary(
    val index: Int,
    val start: Int,
    val endExclusive: Int
)

internal enum class TtsContinuationAction {
    ADVANCE_TO_NEXT_PAGE,
    STOP_READING
}

internal data class TtsContinuationStep(
    val action: TtsContinuationAction,
    val targetPageIndex: Int
)

internal data class TtsUtteranceChunk(
    val text: String,
    val startWordIndex: Int,
    val endWordIndex: Int
)

internal object ReaderTtsLogic {
    private val wordRegex = Regex("\\S+")

    fun buildWordBoundaries(text: String): List<TtsWordBoundary> {
        return wordRegex.findAll(text).mapIndexed { index, matchResult ->
            TtsWordBoundary(
                index = index,
                start = matchResult.range.first,
                endExclusive = matchResult.range.last + 1
            )
        }.toList()
    }

    fun clampWordIndex(wordIndex: Int, wordCount: Int): Int {
        if (wordCount <= 0) return 0
        return wordIndex.coerceIn(0, wordCount - 1)
    }

    fun speakableSuffix(text: String, boundaries: List<TtsWordBoundary>, startWordIndex: Int): String {
        if (boundaries.isEmpty()) return text.trim()
        val clampedIndex = clampWordIndex(startWordIndex, boundaries.size)
        return text.substring(boundaries[clampedIndex].start).trimStart()
    }

    fun utteranceChunk(
        text: String,
        boundaries: List<TtsWordBoundary>,
        startWordIndex: Int,
        maxCharacters: Int
    ): TtsUtteranceChunk {
        if (boundaries.isEmpty()) {
            return TtsUtteranceChunk(
                text = text.trim(),
                startWordIndex = 0,
                endWordIndex = 0
            )
        }

        val safeMaxCharacters = maxCharacters.coerceAtLeast(1)
        val clampedStartWordIndex = clampWordIndex(startWordIndex, boundaries.size)
        val startChar = boundaries[clampedStartWordIndex].start
        val maxEndExclusive = startChar + safeMaxCharacters

        var endWordIndex = clampedStartWordIndex
        while (endWordIndex < boundaries.lastIndex) {
            val nextBoundary = boundaries[endWordIndex + 1]
            if (nextBoundary.start >= maxEndExclusive) break
            endWordIndex += 1
        }

        val chunkText = text.substring(
            startIndex = startChar,
            endIndex = boundaries[endWordIndex].endExclusive
        ).trim()

        return TtsUtteranceChunk(
            text = chunkText,
            startWordIndex = clampedStartWordIndex,
            endWordIndex = endWordIndex
        )
    }

    fun resolveWordIndexForRange(
        boundaries: List<TtsWordBoundary>,
        startWordIndex: Int,
        utteranceStart: Int,
        utteranceEnd: Int
    ): Int {
        if (boundaries.isEmpty()) return 0

        val clampedStartWordIndex = clampWordIndex(startWordIndex, boundaries.size)
        val absoluteStart = boundaries[clampedStartWordIndex].start + utteranceStart.coerceAtLeast(0)
        val absoluteEndExclusive = (boundaries[clampedStartWordIndex].start + utteranceEnd.coerceAtLeast(utteranceStart))
            .coerceAtLeast(absoluteStart + 1)

        val directMatch = boundaries.firstOrNull { boundary ->
            absoluteStart in boundary.start until boundary.endExclusive
        }
        if (directMatch != null) {
            return directMatch.index
        }

        val overlapMatch = boundaries.firstOrNull { boundary ->
            boundary.start < absoluteEndExclusive && boundary.endExclusive > absoluteStart
        }
        if (overlapMatch != null) {
            return overlapMatch.index
        }

        return boundaries.lastOrNull { boundary -> boundary.start <= absoluteStart }?.index
            ?: boundaries.last().index
    }

    fun nextContinuationStep(
        currentPageIndex: Int,
        lastPageIndex: Int
    ): TtsContinuationStep {
        return if (currentPageIndex < lastPageIndex) {
            TtsContinuationStep(
                action = TtsContinuationAction.ADVANCE_TO_NEXT_PAGE,
                targetPageIndex = currentPageIndex + 1
            )
        } else {
            TtsContinuationStep(
                action = TtsContinuationAction.STOP_READING,
                targetPageIndex = currentPageIndex
            )
        }
    }
}
