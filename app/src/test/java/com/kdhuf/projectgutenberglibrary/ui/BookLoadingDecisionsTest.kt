package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookLoadingDecisionsTest {

    @Test
    fun `opens saved epub when present`() {
        assertTrue(shouldOpenSavedEpub(File("saved.epub")))
    }

    @Test
    fun `does not open saved epub when missing`() {
        assertFalse(shouldOpenSavedEpub(null))
    }

    @Test
    fun `retries saved epub only when it was not already attempted`() {
        val saved = File("saved.epub")

        assertTrue(shouldRetrySavedEpubFallback(saved, attemptedSavedEpub = false))
        assertFalse(shouldRetrySavedEpubFallback(saved, attemptedSavedEpub = true))
    }

    @Test
    fun `does not retry saved epub fallback when no saved file exists`() {
        assertFalse(shouldRetrySavedEpubFallback(null, attemptedSavedEpub = false))
    }

    @Test
    fun `does not retry saved epub fallback after saved file was already attempted`() {
        assertFalse(shouldRetrySavedEpubFallback(File("saved.epub"), attemptedSavedEpub = true))
    }
}
