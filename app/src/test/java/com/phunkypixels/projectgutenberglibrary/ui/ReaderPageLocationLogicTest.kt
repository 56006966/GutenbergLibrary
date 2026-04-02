package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageLocationLogicTest {

    @Test
    fun `captureScrollRatio falls back when max scroll is unavailable`() {
        val ratio = ReaderPageLocationLogic.captureScrollRatio(
            scrollY = 120,
            maxScroll = 0,
            fallbackRatio = 0.4f
        )

        assertEquals(0.4f, ratio, 0.0001f)
    }

    @Test
    fun `captureScrollRatio normalizes current scroll position`() {
        val ratio = ReaderPageLocationLogic.captureScrollRatio(
            scrollY = 150,
            maxScroll = 300,
            fallbackRatio = 0f
        )

        assertEquals(0.5f, ratio, 0.0001f)
    }

    @Test
    fun `captureScrollRatio clamps overscrolled values`() {
        val ratio = ReaderPageLocationLogic.captureScrollRatio(
            scrollY = 400,
            maxScroll = 300,
            fallbackRatio = 0f
        )

        assertEquals(1.0f, ratio, 0.0001f)
    }

    @Test
    fun `targetScrollY clamps normalized ratio into range`() {
        val targetScroll = ReaderPageLocationLogic.targetScrollY(
            normalizedRatio = 1.5f,
            maxScroll = 240
        )

        assertEquals(240, targetScroll)
    }

    @Test
    fun `targetScrollY returns zero when content cannot scroll`() {
        val targetScroll = ReaderPageLocationLogic.targetScrollY(
            normalizedRatio = 0.6f,
            maxScroll = 0
        )

        assertEquals(0, targetScroll)
    }

    @Test
    fun `targetScrollY rounds fractional positions consistently`() {
        val targetScroll = ReaderPageLocationLogic.targetScrollY(
            normalizedRatio = 0.333f,
            maxScroll = 300
        )

        assertEquals(100, targetScroll)
    }
}
