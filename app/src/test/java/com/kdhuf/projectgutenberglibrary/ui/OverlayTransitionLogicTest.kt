package com.kdhuf.projectgutenberglibrary.ui

import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTransitionLogicTest {

    @Test
    fun `startup spec matches current animation timing`() {
        assertEquals(0L, OverlayTransitionLogic.startupSpec.fadeInDuration)
        assertEquals(700L, OverlayTransitionLogic.startupSpec.holdDuration)
        assertEquals(320L, OverlayTransitionLogic.startupSpec.fadeOutDuration)
    }

    @Test
    fun `navigation spec matches current animation timing`() {
        assertEquals(120L, OverlayTransitionLogic.navigationSpec.fadeInDuration)
        assertEquals(220L, OverlayTransitionLogic.navigationSpec.holdDuration)
        assertEquals(260L, OverlayTransitionLogic.navigationSpec.fadeOutDuration)
    }

    @Test
    fun `shouldShowTransition is false before startup overlay completes`() {
        assertFalse(
            OverlayTransitionLogic.shouldShowTransition(
                startupOverlayShown = false,
                previousDestinationId = 1,
                destinationId = 2,
                isOverlayRunning = false
            )
        )
    }

    @Test
    fun `shouldShowTransition is false with same destination`() {
        assertFalse(
            OverlayTransitionLogic.shouldShowTransition(
                startupOverlayShown = true,
                previousDestinationId = 5,
                destinationId = 5,
                isOverlayRunning = false
            )
        )
    }

    @Test
    fun `shouldShowTransition is true for completed startup and destination change`() {
        assertTrue(
            OverlayTransitionLogic.shouldShowTransition(
                startupOverlayShown = true,
                previousDestinationId = 3,
                destinationId = 4,
                isOverlayRunning = false
            )
        )
    }
}
