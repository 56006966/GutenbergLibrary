package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GutenbergCanonicalLinksTest {

    @Test
    fun `bookUrl uses canonical ebooks path`() {
        assertEquals(
            "https://www.gutenberg.org/ebooks/1342",
            GutenbergCanonicalLinks.bookUrl(1342)
        )
    }

    @Test
    fun `authorUrl uses canonical author path with underscores`() {
        assertEquals(
            "https://www.gutenberg.org/author/Mark_Twain",
            GutenbergCanonicalLinks.authorUrl("Mark Twain")
        )
    }

    @Test
    fun `authorUrl preserves disambiguation text`() {
        assertEquals(
            "https://www.gutenberg.org/author/Henry_James_(1843-1916)",
            GutenbergCanonicalLinks.authorUrl("Henry James (1843-1916)")
        )
    }
}
