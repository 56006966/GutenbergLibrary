package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageHtmlBuilderTest {

    @Test
    fun `build injects light mode colors and page body`() {
        val html = ReaderPageHtmlBuilder.build(
            pageBody = "<p>Hello world</p>",
            inkModeEnabled = false
        )

        assertTrue(html.contains("#faf7f0"))
        assertTrue(html.contains("#111111"))
        assertTrue(html.contains("<body><p>Hello world</p></body>"))
    }

    @Test
    fun `build disables native text selection and preserves tts hooks`() {
        val html = ReaderPageHtmlBuilder.build(
            pageBody = "<p>Hello world</p>",
            inkModeEnabled = true
        )

        assertTrue(html.contains("user-select: none"))
        assertTrue(html.contains("-webkit-touch-callout: none"))
        assertTrue(html.contains("window.AndroidReader.onWordTapped"))
        assertTrue(html.contains("document.addEventListener('contextmenu'"))
        assertTrue(html.contains("window.readerTts.prepare()"))
    }
}
