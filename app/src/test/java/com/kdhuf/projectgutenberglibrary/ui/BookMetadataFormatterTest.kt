package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BookMetadataFormatterTest {

    @Test
    fun `normalizeTitle strips marc subtitle fragments`() {
        val normalized = BookMetadataFormatter.normalizeTitle("Main title :\$b Subtitle fragment")

        assertEquals("Main title", normalized)
    }

    @Test
    fun `normalizeTitle strips marc subtitle fragments with spaces`() {
        val normalized = BookMetadataFormatter.normalizeTitle("Peter Pan : \$b [Peter and Wendy]")

        assertEquals("Peter Pan", normalized)
    }

    @Test
    fun `normalizeTitle keeps regular punctuation`() {
        val normalized = BookMetadataFormatter.normalizeTitle("Poems, with parentheses (revised edition)")

        assertEquals("Poems, with parentheses (revised edition)", normalized)
    }

    @Test
    fun `normalizeTitle falls back for blank strings`() {
        assertEquals("Untitled", BookMetadataFormatter.normalizeTitle("   "))
    }
}
