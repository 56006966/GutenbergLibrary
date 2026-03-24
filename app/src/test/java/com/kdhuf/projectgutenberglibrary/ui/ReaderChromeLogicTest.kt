package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChromeLogicTest {

    @Test
    fun `transition is skipped when chrome is already in requested state`() {
        val transition = ReaderChromeLogic.transition(
            currentVisible = true,
            targetVisible = true,
            topHeight = 80,
            bottomHeight = 64,
            animate = true
        )

        assertNull(transition)
    }

    @Test
    fun `transition hides chrome by moving it off screen`() {
        val transition = ReaderChromeLogic.transition(
            currentVisible = true,
            targetVisible = false,
            topHeight = 72,
            bottomHeight = 56,
            animate = true
        )

        requireNotNull(transition)
        assertEquals(-72f, transition.topTranslationY, 0.0001f)
        assertEquals(56f, transition.bottomTranslationY, 0.0001f)
        assertEquals(0f, transition.alpha, 0.0001f)
        assertTrue(transition.animate)
    }

    @Test
    fun `transition shows chrome by resetting translations`() {
        val transition = ReaderChromeLogic.transition(
            currentVisible = false,
            targetVisible = true,
            topHeight = 72,
            bottomHeight = 56,
            animate = false
        )

        requireNotNull(transition)
        assertEquals(0f, transition.topTranslationY, 0.0001f)
        assertEquals(0f, transition.bottomTranslationY, 0.0001f)
        assertEquals(1f, transition.alpha, 0.0001f)
        assertEquals(false, transition.animate)
    }
}
