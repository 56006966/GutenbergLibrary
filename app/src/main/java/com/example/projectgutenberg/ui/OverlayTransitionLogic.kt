package com.example.projectgutenberg.ui

internal data class OverlayAnimationSpec(
    val fadeInDuration: Long,
    val holdDuration: Long,
    val fadeOutDuration: Long
)

internal object OverlayTransitionLogic {
    val startupSpec = OverlayAnimationSpec(
        fadeInDuration = 0L,
        holdDuration = 700L,
        fadeOutDuration = 320L
    )

    val navigationSpec = OverlayAnimationSpec(
        fadeInDuration = 120L,
        holdDuration = 220L,
        fadeOutDuration = 260L
    )

    fun shouldShowTransition(
        startupOverlayShown: Boolean,
        previousDestinationId: Int?,
        destinationId: Int,
        isOverlayRunning: Boolean
    ): Boolean {
        return startupOverlayShown &&
            previousDestinationId != null &&
            previousDestinationId != destinationId &&
            !isOverlayRunning
    }
}
