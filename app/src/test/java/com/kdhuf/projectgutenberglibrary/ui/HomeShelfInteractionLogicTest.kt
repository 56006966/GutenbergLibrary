package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeShelfInteractionLogicTest {

    @Test
    fun `first tap starts opening animation for tapped book`() {
        val result = HomeShelfInteractionLogic.onBookTapped(
            state = HomeShelfInteractionState(),
            bookId = 17
        )

        assertEquals(HomeShelfTapAction.START_OPENING_ANIMATION, result.action)
        assertNull(result.previousExpandedBookId)
        assertEquals(17, result.state.expandedBookId)
        assertEquals(17, result.state.openingBookId)
    }

    @Test
    fun `tapping different book starts opening animation for new book`() {
        val result = HomeShelfInteractionLogic.onBookTapped(
            state = HomeShelfInteractionState(expandedBookId = 17),
            bookId = 23
        )

        assertEquals(HomeShelfTapAction.START_OPENING_ANIMATION, result.action)
        assertEquals(17, result.previousExpandedBookId)
        assertEquals(23, result.state.expandedBookId)
        assertEquals(23, result.state.openingBookId)
    }

    @Test
    fun `tap on already expanded book keeps opening that book`() {
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
    fun `opening animation finished keeps no expanded book when none was selected`() {
        val nextState = HomeShelfInteractionLogic.onOpeningAnimationFinished(
            HomeShelfInteractionState(expandedBookId = null, openingBookId = 17)
        )

        assertNull(nextState.expandedBookId)
        assertNull(nextState.openingBookId)
    }

    @Test
    fun `metrics grow for longer titles without extreme jumps`() {
        val shortTitle = HomeShelfInteractionLogic.metricsForTitle("Emma")
        val mediumTitle = HomeShelfInteractionLogic.metricsForTitle("The Time Machine")
        val longTitle = HomeShelfInteractionLogic.metricsForTitle("The Interesting Narrative of the Life of Olaudah Equiano")

        assertEquals(HomeShelfMetrics(widthDp = 54, heightDp = 172), shortTitle)
        assertEquals(HomeShelfMetrics(widthDp = 60, heightDp = 176), mediumTitle)
        assertEquals(HomeShelfMetrics(widthDp = 80, heightDp = 182), longTitle)
    }

    @Test
    fun `blank title falls back to base shelf metrics`() {
        val metrics = HomeShelfInteractionLogic.metricsForTitle("   ")

        assertEquals(HomeShelfMetrics(widthDp = 54, heightDp = 172), metrics)
    }

    @Test
    fun `metrics step up at configured title length boundaries`() {
        val elevenChars = HomeShelfInteractionLogic.metricsForTitle("12345678901")
        val twelveChars = HomeShelfInteractionLogic.metricsForTitle("123456789012")
        val nineteenChars = HomeShelfInteractionLogic.metricsForTitle("1234567890123456789")
        val twentyChars = HomeShelfInteractionLogic.metricsForTitle("12345678901234567890")
        val twentyNineChars = HomeShelfInteractionLogic.metricsForTitle("12345678901234567890123456789")
        val thirtyChars = HomeShelfInteractionLogic.metricsForTitle("123456789012345678901234567890")
        val fortyOneChars = HomeShelfInteractionLogic.metricsForTitle("12345678901234567890123456789012345678901")
        val fortyTwoChars = HomeShelfInteractionLogic.metricsForTitle("123456789012345678901234567890123456789012")

        assertEquals(HomeShelfMetrics(widthDp = 54, heightDp = 172), elevenChars)
        assertEquals(HomeShelfMetrics(widthDp = 60, heightDp = 176), twelveChars)
        assertEquals(HomeShelfMetrics(widthDp = 60, heightDp = 176), nineteenChars)
        assertEquals(HomeShelfMetrics(widthDp = 68, heightDp = 178), twentyChars)
        assertEquals(HomeShelfMetrics(widthDp = 68, heightDp = 178), twentyNineChars)
        assertEquals(HomeShelfMetrics(widthDp = 74, heightDp = 180), thirtyChars)
        assertEquals(HomeShelfMetrics(widthDp = 74, heightDp = 180), fortyOneChars)
        assertEquals(HomeShelfMetrics(widthDp = 80, heightDp = 182), fortyTwoChars)
    }
}
