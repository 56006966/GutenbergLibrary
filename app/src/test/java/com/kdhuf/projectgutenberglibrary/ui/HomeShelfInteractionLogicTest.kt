package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeShelfInteractionLogicTest {

    @Test
    fun `first tap reveals cover for tapped book`() {
        val result = HomeShelfInteractionLogic.onBookTapped(
            state = HomeShelfInteractionState(),
            bookId = 17
        )

        assertEquals(HomeShelfTapAction.REVEAL_COVER, result.action)
        assertNull(result.previousExpandedBookId)
        assertEquals(17, result.state.expandedBookId)
        assertNull(result.state.openingBookId)
    }

    @Test
    fun `tapping different book moves expanded state to new book`() {
        val result = HomeShelfInteractionLogic.onBookTapped(
            state = HomeShelfInteractionState(expandedBookId = 17),
            bookId = 23
        )

        assertEquals(HomeShelfTapAction.REVEAL_COVER, result.action)
        assertEquals(17, result.previousExpandedBookId)
        assertEquals(23, result.state.expandedBookId)
        assertNull(result.state.openingBookId)
    }

    @Test
    fun `second tap on expanded book starts opening animation`() {
        val result = HomeShelfInteractionLogic.onBookTapped(
            state = HomeShelfInteractionState(expandedBookId = 17),
            bookId = 17
        )

        assertEquals(HomeShelfTapAction.START_OPENING_ANIMATION, result.action)
        assertEquals(17, result.previousExpandedBookId)
        assertEquals(17, result.state.expandedBookId)
        assertEquals(17, result.state.openingBookId)
    }

    @Test
    fun `opening animation finished clears opening flag`() {
        val nextState = HomeShelfInteractionLogic.onOpeningAnimationFinished(
            HomeShelfInteractionState(expandedBookId = 17, openingBookId = 17)
        )

        assertEquals(17, nextState.expandedBookId)
        assertNull(nextState.openingBookId)
    }

    @Test
    fun `metrics grow for longer titles without extreme jumps`() {
        val shortTitle = HomeShelfInteractionLogic.metricsForTitle("Emma")
        val mediumTitle = HomeShelfInteractionLogic.metricsForTitle("The Time Machine")
        val longTitle = HomeShelfInteractionLogic.metricsForTitle("The Interesting Narrative of the Life of Olaudah Equiano")

        assertEquals(HomeShelfMetrics(widthDp = 58, heightDp = 174), shortTitle)
        assertEquals(HomeShelfMetrics(widthDp = 64, heightDp = 178), mediumTitle)
        assertEquals(HomeShelfMetrics(widthDp = 88, heightDp = 186), longTitle)
    }
}
