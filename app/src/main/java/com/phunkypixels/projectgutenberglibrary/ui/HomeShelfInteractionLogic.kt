package com.phunkypixels.projectgutenberglibrary.ui

internal data class HomeShelfMetrics(
    val widthDp: Int,
    val heightDp: Int
)

internal data class HomeShelfInteractionState(
    val expandedBookId: Int? = null,
    val openingBookId: Int? = null
)

internal enum class HomeShelfTapAction {
    START_OPENING_ANIMATION
}

internal data class HomeShelfTapResult(
    val action: HomeShelfTapAction,
    val previousExpandedBookId: Int?,
    val state: HomeShelfInteractionState
)

internal object HomeShelfInteractionLogic {

    fun onBookTapped(
        state: HomeShelfInteractionState,
        bookId: Int
    ): HomeShelfTapResult {
        return HomeShelfTapResult(
            action = HomeShelfTapAction.START_OPENING_ANIMATION,
            previousExpandedBookId = state.expandedBookId,
            state = HomeShelfInteractionState(
                expandedBookId = bookId,
                openingBookId = bookId
            )
        )
    }

    fun onOpeningAnimationFinished(state: HomeShelfInteractionState): HomeShelfInteractionState {
        return state.copy(openingBookId = null)
    }

    fun metricsForTitle(bookTitle: String): HomeShelfMetrics {
        val compactTitle = bookTitle.trim().ifBlank { "Book" }
        val titleLength = compactTitle.length
        val widthDp = when {
            titleLength >= 42 -> 88
            titleLength >= 30 -> 80
            titleLength >= 20 -> 72
            titleLength >= 12 -> 64
            else -> 58
        }
        val heightDp = when {
            titleLength >= 42 -> 186
            titleLength >= 30 -> 184
            titleLength >= 20 -> 180
            titleLength >= 12 -> 178
            else -> 174
        }
        return HomeShelfMetrics(
            widthDp = widthDp,
            heightDp = heightDp
        )
    }
}
