package com.kdhuf.projectgutenberglibrary.ui

data class LaunchTileWallWindow(
    val visibleRows: Int,
    val visibleCols: Int
)

object LaunchTileWallLayoutLogic {

    fun windowForBounds(width: Int, height: Int): LaunchTileWallWindow {
        return if (width > height) {
            LaunchTileWallWindow(visibleRows = 2, visibleCols = 4)
        } else {
            LaunchTileWallWindow(visibleRows = 3, visibleCols = 3)
        }
    }

    fun segmentRows(window: LaunchTileWallWindow): Int {
        return (window.visibleRows * 4).coerceIn(8, 16)
    }

    fun segmentCols(window: LaunchTileWallWindow): Int {
        return (window.visibleCols + 2).coerceIn(5, 8)
    }

    fun maxTileSizePx(window: LaunchTileWallWindow, density: Float): Int {
        val maxTileSizeDp = if (window.visibleRows == 2 && window.visibleCols == 4) 360 else 340
        return (maxTileSizeDp * density).toInt()
    }
}
