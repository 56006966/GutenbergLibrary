package com.phunkypixels.projectgutenberglibrary.ui

internal data class ReaderTtsSessionState(
    val isReadingAloud: Boolean = false,
    val isPaused: Boolean = false,
    val currentWordIndex: Int = 0,
    val pendingWordIndex: Int = 0,
    val utteranceStartWordIndex: Int = 0,
    val shouldResumeAfterPageLoad: Boolean = false
)

internal enum class ReaderTtsToggleAction {
    START,
    PAUSE,
    WAIT_FOR_ENGINE,
    UNAVAILABLE
}

internal data class ReaderTtsToggleResult(
    val state: ReaderTtsSessionState,
    val action: ReaderTtsToggleAction
)

internal enum class ReaderTtsPageTransitionAction {
    NONE,
    STOP_ENGINE_AND_RESUME_AFTER_LOAD
}

internal data class ReaderTtsPageTransitionResult(
    val state: ReaderTtsSessionState,
    val action: ReaderTtsPageTransitionAction
)

internal enum class ReaderTtsCompletionAction {
    ADVANCE_TO_NEXT_PAGE,
    STOP_READING
}

internal data class ReaderTtsCompletionResult(
    val state: ReaderTtsSessionState,
    val action: ReaderTtsCompletionAction,
    val targetPageIndex: Int
)

internal object ReaderTtsSessionLogic {
    private fun clampedWordIndex(wordIndex: Int, wordCount: Int): Int {
        return ReaderTtsLogic.clampWordIndex(wordIndex, wordCount)
    }

    fun togglePlayback(
        state: ReaderTtsSessionState,
        engineReady: Boolean,
        engineUnavailable: Boolean
    ): ReaderTtsToggleResult {
        return when {
            !engineReady && !engineUnavailable -> ReaderTtsToggleResult(
                state = state,
                action = ReaderTtsToggleAction.WAIT_FOR_ENGINE
            )
            engineUnavailable -> ReaderTtsToggleResult(
                state = state.copy(isReadingAloud = false, isPaused = false),
                action = ReaderTtsToggleAction.UNAVAILABLE
            )
            state.isReadingAloud -> ReaderTtsToggleResult(
                state = state.copy(isReadingAloud = false, isPaused = true),
                action = ReaderTtsToggleAction.PAUSE
            )
            else -> ReaderTtsToggleResult(
                state = state.copy(isReadingAloud = true, isPaused = false),
                action = ReaderTtsToggleAction.START
            )
        }
    }

    fun onPageTransitionStarted(state: ReaderTtsSessionState): ReaderTtsPageTransitionResult {
        return if (state.isReadingAloud && !state.isPaused) {
            ReaderTtsPageTransitionResult(
                state = state.copy(shouldResumeAfterPageLoad = true),
                action = ReaderTtsPageTransitionAction.STOP_ENGINE_AND_RESUME_AFTER_LOAD
            )
        } else {
            ReaderTtsPageTransitionResult(
                state = state,
                action = ReaderTtsPageTransitionAction.NONE
            )
        }
    }

    fun onNewPageRendered(
        state: ReaderTtsSessionState,
        selectedWordIndex: Int
    ): ReaderTtsSessionState {
        return state.copy(
            currentWordIndex = selectedWordIndex,
            pendingWordIndex = 0
        )
    }

    fun onUtteranceAboutToSpeak(
        state: ReaderTtsSessionState,
        wordCount: Int
    ): ReaderTtsSessionState {
        val clampedIndex = clampedWordIndex(state.currentWordIndex, wordCount)
        return state.copy(
            currentWordIndex = clampedIndex,
            utteranceStartWordIndex = clampedIndex
        )
    }

    fun onRangeStart(
        state: ReaderTtsSessionState,
        boundaries: List<TtsWordBoundary>,
        utteranceStart: Int,
        utteranceEnd: Int
    ): ReaderTtsSessionState {
        if (!state.isReadingAloud || boundaries.isEmpty()) return state
        val resolvedWordIndex = ReaderTtsLogic.resolveWordIndexForRange(
            boundaries = boundaries,
            startWordIndex = state.utteranceStartWordIndex,
            utteranceStart = utteranceStart,
            utteranceEnd = utteranceEnd
        )
        return state.copy(currentWordIndex = resolvedWordIndex)
    }

    fun onUtteranceComplete(
        state: ReaderTtsSessionState,
        currentPageIndex: Int,
        lastPageIndex: Int
    ): ReaderTtsCompletionResult {
        if (!state.isReadingAloud) {
            return ReaderTtsCompletionResult(
                state = state,
                action = ReaderTtsCompletionAction.STOP_READING,
                targetPageIndex = currentPageIndex
            )
        }
        val continuation = ReaderTtsLogic.nextContinuationStep(
            currentPageIndex = currentPageIndex,
            lastPageIndex = lastPageIndex
        )
        return when (continuation.action) {
            TtsContinuationAction.ADVANCE_TO_NEXT_PAGE -> ReaderTtsCompletionResult(
                state = state.copy(
                    currentWordIndex = 0,
                    pendingWordIndex = 0,
                    shouldResumeAfterPageLoad = true
                ),
                action = ReaderTtsCompletionAction.ADVANCE_TO_NEXT_PAGE,
                targetPageIndex = continuation.targetPageIndex
            )
            TtsContinuationAction.STOP_READING -> ReaderTtsCompletionResult(
                state = state.copy(
                    isReadingAloud = false,
                    isPaused = false
                ),
                action = ReaderTtsCompletionAction.STOP_READING,
                targetPageIndex = continuation.targetPageIndex
            )
        }
    }

    fun onWordTapped(
        state: ReaderTtsSessionState,
        tappedWordIndex: Int,
        wordCount: Int
    ): ReaderTtsSessionState {
        val clampedIndex = clampedWordIndex(tappedWordIndex, wordCount)
        return state.copy(
            currentWordIndex = clampedIndex,
            pendingWordIndex = 0,
            isPaused = if (state.isReadingAloud) state.isPaused else true
        )
    }

    fun onStop(state: ReaderTtsSessionState): ReaderTtsSessionState {
        return if (state.isReadingAloud) {
            state.copy(isReadingAloud = false, isPaused = true)
        } else {
            state
        }
    }

    fun resetForPageChange(state: ReaderTtsSessionState): ReaderTtsSessionState {
        return state.copy(currentWordIndex = 0)
    }

    fun consumeResumeAfterPageLoad(state: ReaderTtsSessionState): ReaderTtsSessionState {
        return state.copy(shouldResumeAfterPageLoad = false)
    }

    fun restorePendingWordIndex(
        state: ReaderTtsSessionState,
        pendingWordIndex: Int
    ): ReaderTtsSessionState {
        return state.copy(pendingWordIndex = pendingWordIndex)
    }

    fun reset(state: ReaderTtsSessionState): ReaderTtsSessionState = ReaderTtsSessionState(
        pendingWordIndex = state.pendingWordIndex
    )
}
