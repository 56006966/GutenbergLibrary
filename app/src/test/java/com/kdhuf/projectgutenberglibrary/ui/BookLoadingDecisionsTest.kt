package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookLoadingDecisionsTest {

    @Test
    fun `saved epub is always preferred when it exists`() {
        val savedEpub = File("book_1342_images.epub")

        assertTrue(shouldOpenSavedEpub(savedEpub))
    }

    @Test
    fun `missing saved epub falls back to remote source`() {
        assertFalse(shouldOpenSavedEpub(null))
    }

    @Test
    fun `remote cover is skipped when offline and no local cover exists`() {
        assertFalse(
            shouldLoadRemoteCover(
                localCover = null,
                primaryCover = "https://www.gutenberg.org/cover.jpg",
                networkAvailable = false
            )
        )
    }

    @Test
    fun `local cover is still allowed when offline`() {
        assertTrue(
            shouldLoadRemoteCover(
                localCover = "/data/user/0/com.kdhuf.projectgutenberglibrary/files/book_1_cover.jpg",
                primaryCover = "/data/user/0/com.kdhuf.projectgutenberglibrary/files/book_1_cover.jpg",
                networkAvailable = false
            )
        )
    }

    @Test
    fun `fallback remote cover can load when network is available`() {
        assertTrue(
            shouldLoadRemoteCover(
                localCover = null,
                primaryCover = null,
                networkAvailable = true
            )
        )
    }
}
