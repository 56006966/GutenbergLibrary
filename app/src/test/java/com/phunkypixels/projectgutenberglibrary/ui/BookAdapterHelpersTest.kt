package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookAdapterHelpersTest {

    @Test
    fun `remote cover is skipped when offline and no local cover exists`() {
        assertFalse(
            shouldLoadRemoteCover(
                localCover = null,
                primaryCover = "https://example.test/cover.jpg",
                networkAvailable = false
            )
        )
    }

    @Test
    fun `remote cover still loads when local cover exists`() {
        assertTrue(
            shouldLoadRemoteCover(
                localCover = "/covers/local.jpg",
                primaryCover = "https://example.test/cover.jpg",
                networkAvailable = false
            )
        )
    }

    @Test
    fun `blank primary cover falls back to network availability`() {
        assertTrue(
            shouldLoadRemoteCover(
                localCover = null,
                primaryCover = null,
                networkAvailable = true
            )
        )
        assertFalse(
            shouldLoadRemoteCover(
                localCover = null,
                primaryCover = "   ",
                networkAvailable = false
            )
        )
    }
}
