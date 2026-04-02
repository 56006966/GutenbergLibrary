package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookWebViewDecisionsTest {

    @Test
    fun `saved epub is considered openable when file exists reference is non null`() {
        assertTrue(shouldOpenSavedEpub(File("book.epub")))
    }

    @Test
    fun `saved epub is not openable when missing`() {
        assertFalse(shouldOpenSavedEpub(null))
    }
}
