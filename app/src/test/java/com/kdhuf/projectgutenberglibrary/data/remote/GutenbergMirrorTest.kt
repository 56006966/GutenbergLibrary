package com.kdhuf.projectgutenberglibrary.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GutenbergMirrorTest {

    @Test
    fun `rewrite candidates prefer configured mirror for gutenberg hosts`() {
        val candidates = GutenbergMirror.resolveCandidates("https://www.gutenberg.org/cache/epub/84/pg84-images.epub")

        assertEquals("https://books.phunkypixels.com/cache/epub/84/pg84-images.epub", candidates.first())
        assertTrue(candidates.contains("https://gutenberg.pglaf.org/cache/epub/84/pg84-images.epub"))
    }

    @Test
    fun `non gutenberg hosts are normalized to https without mirror rewrite`() {
        val candidates = GutenbergMirror.resolveCandidates("http://example.test/books/file.epub")

        assertEquals(listOf("https://example.test/books/file.epub"), candidates)
    }

    @Test
    fun `blank urls resolve to no candidates`() {
        assertTrue(GutenbergMirror.resolveCandidates(" ").isEmpty())
    }
}
