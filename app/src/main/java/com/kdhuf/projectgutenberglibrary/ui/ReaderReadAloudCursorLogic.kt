package com.kdhuf.projectgutenberglibrary.ui

internal data class CursorPosition(
    val left: Float,
    val top: Float
)

internal object ReaderReadAloudCursorLogic {
    fun shouldShowCursor(
        isWebViewVisible: Boolean,
        wordCount: Int,
        ttsUnavailable: Boolean
    ): Boolean {
        return isWebViewVisible && wordCount > 0 && !ttsUnavailable
    }

    fun clampCursorPosition(
        rawLeft: Float,
        rawTop: Float,
        surfaceWidth: Int,
        surfaceHeight: Int,
        handleWidth: Int,
        handleHeight: Int
    ): CursorPosition {
        val maxLeft = (surfaceWidth - handleWidth).coerceAtLeast(0).toFloat()
        val maxTop = (surfaceHeight - handleHeight).coerceAtLeast(0).toFloat()
        return CursorPosition(
            left = rawLeft.coerceIn(0f, maxLeft),
            top = rawTop.coerceIn(0f, maxTop)
        )
    }

    fun centerFractions(
        left: Float,
        top: Float,
        handleWidth: Int,
        handleHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int
    ): Pair<Float, Float> {
        val safeSurfaceWidth = surfaceWidth.coerceAtLeast(1).toFloat()
        val safeSurfaceHeight = surfaceHeight.coerceAtLeast(1).toFloat()
        val centerXFraction = ((left + handleWidth / 2f) / safeSurfaceWidth).coerceIn(0f, 1f)
        val centerYFraction = ((top + handleHeight / 2f) / safeSurfaceHeight).coerceIn(0f, 1f)
        return centerXFraction to centerYFraction
    }

    fun edgeSnappedPosition(
        surfaceWidth: Int,
        surfaceHeight: Int,
        handleWidth: Int,
        handleHeight: Int,
        marginPx: Float
    ): CursorPosition {
        val left = (surfaceWidth - handleWidth - marginPx).coerceAtLeast(0f)
        val top = ((surfaceHeight - handleHeight) / 2f).coerceAtLeast(0f)
        return CursorPosition(left = left, top = top)
    }

    fun canCollapseChrome(isReadingAloud: Boolean): Boolean = !isReadingAloud
}
