package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsRendererLogicTest {

    @Test
    fun `pageBodyToSpeakableText strips markup and normalizes whitespace`() {
        val speakableText = ReaderTtsRendererLogic.pageBodyToSpeakableText(
            "<p>Hello <strong>there</strong>&nbsp; friend</p>\n<div>General Kenobi</div>"
        )

        assertEquals("Hello there friend General Kenobi", speakableText)
    }

    @Test
    fun `buildPageState prefers pending word index when restoring selection`() {
        val pageState = ReaderTtsRendererLogic.buildPageState(
            pageBody = "<p>alpha beta gamma delta</p>",
            currentWordIndex = 1,
            pendingWordIndex = 3
        )

        assertEquals("alpha beta gamma delta", pageState.speakableContent)
        assertEquals(4, pageState.wordBoundaries.size)
        assertEquals(3, pageState.selectedWordIndex)
    }

    @Test
    fun `buildPageState clamps selected index to the final available word`() {
        val pageState = ReaderTtsRendererLogic.buildPageState(
            pageBody = "<p>Call me Ishmael</p>",
            currentWordIndex = 99,
            pendingWordIndex = 0
        )

        assertEquals(2, pageState.selectedWordIndex)
    }

    @Test
    fun `buildPageState leaves selection at zero when no words are present`() {
        val pageState = ReaderTtsRendererLogic.buildPageState(
            pageBody = "<div>&nbsp;</div>",
            currentWordIndex = 7,
            pendingWordIndex = 2
        )

        assertEquals("", pageState.speakableContent)
        assertEquals(emptyList<TtsWordBoundary>(), pageState.wordBoundaries)
        assertEquals(0, pageState.selectedWordIndex)
    }

    @Test
    fun `buildPageState keeps current selection when no pending word is supplied`() {
        val pageState = ReaderTtsRendererLogic.buildPageState(
            pageBody = "<p>one two three four</p>",
            currentWordIndex = 2,
            pendingWordIndex = 0
        )

        assertEquals(2, pageState.selectedWordIndex)
    }

    @Test
    fun `pageBodyToSpeakableText collapses entities and line breaks into readable spacing`() {
        val speakableText = ReaderTtsRendererLogic.pageBodyToSpeakableText(
            "<h1>Chapter&nbsp;1</h1>\n<p>Call<br/>me &amp; Ishmael</p>"
        )

        assertEquals("Chapter 1 Call me Ishmael", speakableText)
    }
}
