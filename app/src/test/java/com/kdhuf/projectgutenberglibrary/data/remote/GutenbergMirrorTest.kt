package com.kdhuf.projectgutenberglibrary.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GutenbergMirrorTest {

    @Test
    fun `rewrite candidates prefer configured mirror for gutenberg hosts`() {
        val candidates = GutenbergMirror.resolveCandidates("https://www.gutenberg.org/cache/epub/84/pg84-images.epub")

        assertEquals("https://books.phunkypixels.com/84/pg84-images.epub", candidates.first())
        assertTrue(candidates.contains("https://gutenberg.pglaf.org/cache/epub/84/pg84-images.epub"))
    }

    @Test
    fun `epub3 images rewrite maps to generated mirror layout first`() {
        val candidates = GutenbergMirror.resolveCandidates("https://www.gutenberg.org/ebooks/84.epub3.images")

        assertEquals("https://books.phunkypixels.com/84/pg84-images-3.epub", candidates.first())
        assertTrue(candidates.contains("https://books.phunkypixels.com/84/pg84-images.epub"))
        assertTrue(candidates.contains("https://gutenberg.pglaf.org/ebooks/84.epub3.images"))
    }

    @Test
    fun `cover fallback uses mirror-native cover url`() {
        val coverUrl = GutenbergMirror.coverUrl(84)
        val candidates = GutenbergMirror.resolveCandidates(coverUrl)

        assertEquals("https://books.phunkypixels.com/84/pg84.cover.medium.jpg", coverUrl)
        assertEquals("https://books.phunkypixels.com/84/pg84.cover.medium.jpg", candidates.first())
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
