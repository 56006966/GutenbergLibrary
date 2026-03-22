package com.kdhuf.projectgutenberglibrary.ui

import android.webkit.WebView

internal data class ReaderTtsPageState(
    val speakableContent: String,
    val wordBoundaries: List<TtsWordBoundary>,
    val selectedWordIndex: Int
)

internal interface ReaderTtsRendererAdapter {
    fun buildPageState(
        pageBody: String,
        currentWordIndex: Int,
        pendingWordIndex: Int
    ): ReaderTtsPageState

    fun prepareForPage(
        wordBoundaries: List<TtsWordBoundary>,
        currentWordIndex: Int,
        shouldHighlightActiveWord: Boolean,
        onPrepared: (resolvedWordIndex: Int) -> Unit
    )

    fun highlightWord(wordIndex: Int, wordBoundaries: List<TtsWordBoundary>)

    fun clearActiveWord()

    fun resolveWordIndexAtFraction(
        xFraction: Float,
        yFraction: Float,
        onResolved: (Int?) -> Unit
    )
}

internal class WebViewReaderTtsRendererAdapter(
    private val webView: WebView
) : ReaderTtsRendererAdapter {

    override fun buildPageState(
        pageBody: String,
        currentWordIndex: Int,
        pendingWordIndex: Int
    ): ReaderTtsPageState {
        return ReaderTtsRendererLogic.buildPageState(
            pageBody = pageBody,
            currentWordIndex = currentWordIndex,
            pendingWordIndex = pendingWordIndex
        )
    }

    override fun prepareForPage(
        wordBoundaries: List<TtsWordBoundary>,
        currentWordIndex: Int,
        shouldHighlightActiveWord: Boolean,
        onPrepared: (resolvedWordIndex: Int) -> Unit
    ) {
        webView.evaluateJavascript("(window.readerTts && window.readerTts.prepare()) || 0;") { result ->
            val jsWordCount = result.trim('"').toIntOrNull() ?: 0
            val resolvedWordIndex = ReaderTtsLogic.clampWordIndex(currentWordIndex, jsWordCount)
            if (wordBoundaries.isNotEmpty() && shouldHighlightActiveWord) {
                highlightWord(resolvedWordIndex, wordBoundaries)
            } else {
                clearActiveWord()
            }
            onPrepared(resolvedWordIndex)
        }
    }

    override fun highlightWord(wordIndex: Int, wordBoundaries: List<TtsWordBoundary>) {
        if (wordBoundaries.isEmpty()) {
            clearActiveWord()
            return
        }
        val clampedWordIndex = ReaderTtsLogic.clampWordIndex(wordIndex, wordBoundaries.size)
        webView.evaluateJavascript(
            "window.readerTts && window.readerTts.setActiveWord($clampedWordIndex);",
            null
        )
    }

    override fun clearActiveWord() {
        webView.evaluateJavascript(
            "window.readerTts && window.readerTts.clearActiveWord();",
            null
        )
    }

    override fun resolveWordIndexAtFraction(
        xFraction: Float,
        yFraction: Float,
        onResolved: (Int?) -> Unit
    ) {
        val safeXFraction = xFraction.coerceIn(0f, 1f)
        val safeYFraction = yFraction.coerceIn(0f, 1f)
        webView.evaluateJavascript(
            "window.readerTts && window.readerTts.findWordIndexAtFraction($safeXFraction,$safeYFraction);"
        ) { result ->
            val resolvedIndex = result?.trim('"')?.toIntOrNull()?.takeIf { it >= 0 }
            onResolved(resolvedIndex)
        }
    }
}

internal object ReaderTtsRendererLogic {
    private val tagRegex = Regex("<[^>]+>")
    private val entityRegex = Regex("&[^;]+;")
    private val whitespaceRegex = Regex("\\s+")

    fun pageBodyToSpeakableText(pageBody: String): String {
        return tagRegex.replace(pageBody, " ")
            .replace(entityRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun buildPageState(
        pageBody: String,
        currentWordIndex: Int,
        pendingWordIndex: Int
    ): ReaderTtsPageState {
        val speakableContent = pageBodyToSpeakableText(pageBody)
        val wordBoundaries = ReaderTtsLogic.buildWordBoundaries(speakableContent)
        val selectedWordIndex = ReaderTtsLogic.clampWordIndex(
            wordIndex = if (pendingWordIndex > 0) pendingWordIndex else currentWordIndex,
            wordCount = wordBoundaries.size
        )
        return ReaderTtsPageState(
            speakableContent = speakableContent,
            wordBoundaries = wordBoundaries,
            selectedWordIndex = selectedWordIndex
        )
    }
}
