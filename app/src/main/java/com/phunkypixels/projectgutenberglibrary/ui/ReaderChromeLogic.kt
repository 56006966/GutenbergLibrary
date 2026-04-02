package com.phunkypixels.projectgutenberglibrary.ui

internal data class ReaderChromeTransition(
    val topTranslationY: Float,
    val bottomTranslationY: Float,
    val alpha: Float,
    val animate: Boolean
)

internal object ReaderChromeLogic {
    fun transition(
        currentVisible: Boolean,
        targetVisible: Boolean,
        topHeight: Int,
        bottomHeight: Int,
        animate: Boolean
    ): ReaderChromeTransition? {
        if (currentVisible == targetVisible) return null

        return ReaderChromeTransition(
            topTranslationY = if (targetVisible) 0f else -topHeight.coerceAtLeast(0).toFloat(),
            bottomTranslationY = if (targetVisible) 0f else bottomHeight.coerceAtLeast(0).toFloat(),
            alpha = if (targetVisible) 1f else 0f,
            animate = animate
        )
    }
}
