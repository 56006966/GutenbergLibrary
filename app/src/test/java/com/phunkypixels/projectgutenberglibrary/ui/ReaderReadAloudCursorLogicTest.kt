package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReadAloudCursorLogicTest {

    @Test
    fun `shouldShowCursor only returns true when page has words and tts is available`() {
        assertTrue(
            ReaderReadAloudCursorLogic.shouldShowCursor(
                isWebViewVisible = true,
                wordCount = 12,
                ttsUnavailable = false
            )
        )
        assertFalse(
            ReaderReadAloudCursorLogic.shouldShowCursor(
                isWebViewVisible = false,
                wordCount = 12,
                ttsUnavailable = false
            )
        )
        assertFalse(
            ReaderReadAloudCursorLogic.shouldShowCursor(
                isWebViewVisible = true,
                wordCount = 0,
                ttsUnavailable = false
            )
        )
        assertFalse(
            ReaderReadAloudCursorLogic.shouldShowCursor(
                isWebViewVisible = true,
                wordCount = 12,
                ttsUnavailable = true
            )
        )
    }

    @Test
    fun `clampCursorPosition keeps handle inside page surface`() {
        val position = ReaderReadAloudCursorLogic.clampCursorPosition(
            rawLeft = 500f,
            rawTop = -20f,
            surfaceWidth = 320,
            surfaceHeight = 640,
            handleWidth = 44,
            handleHeight = 44
        )

        assertEquals(276f, position.left, 0.0001f)
        assertEquals(0f, position.top, 0.0001f)
    }

    @Test
    fun `centerFractions returns normalized handle center position`() {
        val (xFraction, yFraction) = ReaderReadAloudCursorLogic.centerFractions(
            left = 78f,
            top = 156f,
            handleWidth = 44,
            handleHeight = 44,
            surfaceWidth = 320,
            surfaceHeight = 640
        )

        assertEquals(0.3125f, xFraction, 0.0001f)
        assertEquals(0.278125f, yFraction, 0.0001f)
    }

    @Test
    fun `edgeSnappedPosition anchors cursor to the right edge and vertical center`() {
        val position = ReaderReadAloudCursorLogic.edgeSnappedPosition(
            surfaceWidth = 320,
            surfaceHeight = 640,
            handleWidth = 44,
            handleHeight = 44,
            marginPx = 12f
        )

        assertEquals(264f, position.left, 0.0001f)
        assertEquals(298f, position.top, 0.0001f)
    }

    @Test
    fun `canCollapseChrome prevents collapse while reading aloud`() {
        assertFalse(ReaderReadAloudCursorLogic.canCollapseChrome(isReadingAloud = true))
        assertTrue(ReaderReadAloudCursorLogic.canCollapseChrome(isReadingAloud = false))
    }
}
