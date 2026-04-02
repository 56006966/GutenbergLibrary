package com.phunkypixels.projectgutenberglibrary.ui

data class LaunchTileWallWindow(
    val visibleRows: Int,
    val visibleCols: Int
)

object LaunchTileWallLayoutLogic {

    fun windowForBounds(width: Int, height: Int): LaunchTileWallWindow {
        return if (width > height) {
            LaunchTileWallWindow(visibleRows = 2, visibleCols = 6)
        } else {
            LaunchTileWallWindow(visibleRows = 3, visibleCols = 4)
        }
    }

    fun segmentRows(window: LaunchTileWallWindow): Int {
        return (window.visibleRows * 3).coerceIn(6, 10)
    }

    fun segmentCols(window: LaunchTileWallWindow): Int {
        return (window.visibleCols + 1).coerceIn(4, 7)
    }

    fun maxTileSizePx(window: LaunchTileWallWindow, density: Float): Int {
        val maxTileSizeDp = if (window.visibleRows == 2 && window.visibleCols == 6) 176 else 210
        return (maxTileSizeDp * density).toInt()
    }
}
