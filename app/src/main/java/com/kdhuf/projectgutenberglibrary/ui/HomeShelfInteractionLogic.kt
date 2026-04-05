package com.kdhuf.projectgutenberglibrary.ui

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
            titleLength >= 42 -> 80
            titleLength >= 30 -> 74
            titleLength >= 20 -> 68
            titleLength >= 12 -> 60
            else -> 54
        }
        val heightDp = when {
            titleLength >= 42 -> 182
            titleLength >= 30 -> 180
            titleLength >= 20 -> 178
            titleLength >= 12 -> 176
            else -> 172
        }
        return HomeShelfMetrics(
            widthDp = widthDp,
            heightDp = heightDp
        )
    }
}
