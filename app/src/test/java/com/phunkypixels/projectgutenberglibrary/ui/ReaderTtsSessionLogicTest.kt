package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsSessionLogicTest {

    @Test
    fun `togglePlayback waits when engine is still initializing`() {
        val result = ReaderTtsSessionLogic.togglePlayback(
            state = ReaderTtsSessionState(),
            engineReady = false,
            engineUnavailable = false
        )

        assertEquals(ReaderTtsToggleAction.WAIT_FOR_ENGINE, result.action)
        assertFalse(result.state.isReadingAloud)
        assertFalse(result.state.isPaused)
    }

    @Test
    fun `togglePlayback reports unavailable when engine cannot be used`() {
        val result = ReaderTtsSessionLogic.togglePlayback(
            state = ReaderTtsSessionState(isReadingAloud = true, isPaused = false),
            engineReady = false,
            engineUnavailable = true
        )

        assertEquals(ReaderTtsToggleAction.UNAVAILABLE, result.action)
        assertFalse(result.state.isReadingAloud)
        assertFalse(result.state.isPaused)
    }

    @Test
    fun `togglePlayback starts reading when engine is ready`() {
        val result = ReaderTtsSessionLogic.togglePlayback(
            state = ReaderTtsSessionState(),
            engineReady = true,
            engineUnavailable = false
        )

        assertEquals(ReaderTtsToggleAction.START, result.action)
        assertTrue(result.state.isReadingAloud)
        assertFalse(result.state.isPaused)
    }

    @Test
    fun `togglePlayback pauses active reading session`() {
        val result = ReaderTtsSessionLogic.togglePlayback(
            state = ReaderTtsSessionState(isReadingAloud = true, isPaused = false),
            engineReady = true,
            engineUnavailable = false
        )

        assertEquals(ReaderTtsToggleAction.PAUSE, result.action)
        assertFalse(result.state.isReadingAloud)
        assertTrue(result.state.isPaused)
    }

    @Test
    fun `page transition requests engine stop only while actively reading`() {
        val result = ReaderTtsSessionLogic.onPageTransitionStarted(
            ReaderTtsSessionState(isReadingAloud = true, isPaused = false)
        )

        assertEquals(ReaderTtsPageTransitionAction.STOP_ENGINE_AND_RESUME_AFTER_LOAD, result.action)
        assertTrue(result.state.shouldResumeAfterPageLoad)
    }

    @Test
    fun `page transition does nothing when reading is already paused`() {
        val result = ReaderTtsSessionLogic.onPageTransitionStarted(
            ReaderTtsSessionState(isReadingAloud = true, isPaused = true)
        )

        assertEquals(ReaderTtsPageTransitionAction.NONE, result.action)
        assertFalse(result.state.shouldResumeAfterPageLoad)
    }

    @Test
    fun `onNewPageRendered clears pending selection and keeps resolved word index`() {
        val result = ReaderTtsSessionLogic.onNewPageRendered(
            state = ReaderTtsSessionState(currentWordIndex = 1, pendingWordIndex = 4),
            selectedWordIndex = 2
        )

        assertEquals(2, result.currentWordIndex)
        assertEquals(0, result.pendingWordIndex)
    }

    @Test
    fun `onUtteranceAboutToSpeak clamps current word and stores utterance start`() {
        val result = ReaderTtsSessionLogic.onUtteranceAboutToSpeak(
            state = ReaderTtsSessionState(currentWordIndex = 99),
            wordCount = 3
        )

        assertEquals(2, result.currentWordIndex)
        assertEquals(2, result.utteranceStartWordIndex)
    }

    @Test
    fun `onRangeStart resolves current word from utterance offsets`() {
        val boundaries = ReaderTtsLogic.buildWordBoundaries("alpha beta gamma")
        val result = ReaderTtsSessionLogic.onRangeStart(
            state = ReaderTtsSessionState(
                isReadingAloud = true,
                utteranceStartWordIndex = 0
            ),
            boundaries = boundaries,
            utteranceStart = 6,
            utteranceEnd = 10
        )

        assertEquals(1, result.currentWordIndex)
    }

    @Test
    fun `onUtteranceComplete advances to the next page when available`() {
        val result = ReaderTtsSessionLogic.onUtteranceComplete(
            state = ReaderTtsSessionState(isReadingAloud = true),
            currentPageIndex = 1,
            lastPageIndex = 3
        )

        assertEquals(ReaderTtsCompletionAction.ADVANCE_TO_NEXT_PAGE, result.action)
        assertEquals(2, result.targetPageIndex)
        assertTrue(result.state.shouldResumeAfterPageLoad)
        assertEquals(0, result.state.currentWordIndex)
    }

    @Test
    fun `onUtteranceComplete stops reading on the last page`() {
        val result = ReaderTtsSessionLogic.onUtteranceComplete(
            state = ReaderTtsSessionState(isReadingAloud = true),
            currentPageIndex = 3,
            lastPageIndex = 3
        )

        assertEquals(ReaderTtsCompletionAction.STOP_READING, result.action)
        assertFalse(result.state.isReadingAloud)
        assertFalse(result.state.isPaused)
    }

    @Test
    fun `onWordTapped clamps selection and marks paused when idle`() {
        val result = ReaderTtsSessionLogic.onWordTapped(
            state = ReaderTtsSessionState(isReadingAloud = false, isPaused = false),
            tappedWordIndex = 99,
            wordCount = 3
        )

        assertEquals(2, result.currentWordIndex)
        assertTrue(result.isPaused)
    }

    @Test
    fun `onWordTapped keeps active playback running from the tapped word`() {
        val result = ReaderTtsSessionLogic.onWordTapped(
            state = ReaderTtsSessionState(
                isReadingAloud = true,
                isPaused = false,
                currentWordIndex = 1,
                pendingWordIndex = 6
            ),
            tappedWordIndex = 4,
            wordCount = 8
        )

        assertTrue(result.isReadingAloud)
        assertFalse(result.isPaused)
        assertEquals(4, result.currentWordIndex)
        assertEquals(0, result.pendingWordIndex)
    }

    @Test
    fun `onWordTapped preserves paused state when playback was already paused`() {
        val result = ReaderTtsSessionLogic.onWordTapped(
            state = ReaderTtsSessionState(
                isReadingAloud = true,
                isPaused = true,
                currentWordIndex = 2
            ),
            tappedWordIndex = 1,
            wordCount = 5
        )

        assertTrue(result.isReadingAloud)
        assertTrue(result.isPaused)
        assertEquals(1, result.currentWordIndex)
    }

    @Test
    fun `onRangeStart ignores utterance offsets when playback is not active`() {
        val boundaries = ReaderTtsLogic.buildWordBoundaries("alpha beta gamma")
        val result = ReaderTtsSessionLogic.onRangeStart(
            state = ReaderTtsSessionState(
                isReadingAloud = false,
                currentWordIndex = 2,
                utteranceStartWordIndex = 1
            ),
            boundaries = boundaries,
            utteranceStart = 0,
            utteranceEnd = 5
        )

        assertEquals(2, result.currentWordIndex)
    }

    @Test
    fun `restorePendingWordIndex stores requested pending selection`() {
        val result = ReaderTtsSessionLogic.restorePendingWordIndex(
            state = ReaderTtsSessionState(currentWordIndex = 3),
            pendingWordIndex = 7
        )

        assertEquals(3, result.currentWordIndex)
        assertEquals(7, result.pendingWordIndex)
    }

    @Test
    fun `consumeResumeAfterPageLoad clears pending resume flag`() {
        val result = ReaderTtsSessionLogic.consumeResumeAfterPageLoad(
            ReaderTtsSessionState(shouldResumeAfterPageLoad = true)
        )

        assertFalse(result.shouldResumeAfterPageLoad)
    }

    @Test
    fun `resetForPageChange clears current word without losing reading state`() {
        val result = ReaderTtsSessionLogic.resetForPageChange(
            ReaderTtsSessionState(isReadingAloud = true, isPaused = false, currentWordIndex = 5)
        )

        assertTrue(result.isReadingAloud)
        assertFalse(result.isPaused)
        assertEquals(0, result.currentWordIndex)
    }

    @Test
    fun `reset keeps pending selection for the next render pass`() {
        val result = ReaderTtsSessionLogic.reset(
            ReaderTtsSessionState(
                isReadingAloud = true,
                isPaused = false,
                currentWordIndex = 5,
                pendingWordIndex = 9,
                utteranceStartWordIndex = 4,
                shouldResumeAfterPageLoad = true
            )
        )

        assertFalse(result.isReadingAloud)
        assertFalse(result.isPaused)
        assertEquals(0, result.currentWordIndex)
        assertEquals(9, result.pendingWordIndex)
        assertEquals(0, result.utteranceStartWordIndex)
        assertFalse(result.shouldResumeAfterPageLoad)
    }
}
