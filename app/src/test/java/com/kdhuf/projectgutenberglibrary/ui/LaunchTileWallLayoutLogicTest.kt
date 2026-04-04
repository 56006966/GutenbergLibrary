package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchTileWallLayoutLogicTest {

    @Test
    fun `uses portrait loading window for tall bounds`() {
        val window = LaunchTileWallLayoutLogic.windowForBounds(width = 1080, height = 2400)

        assertEquals(3, window.visibleRows)
        assertEquals(4, window.visibleCols)
        assertEquals(9, LaunchTileWallLayoutLogic.segmentRows(window))
        assertEquals(5, LaunchTileWallLayoutLogic.segmentCols(window))
        assertEquals(210, LaunchTileWallLayoutLogic.maxTileSizePx(window, density = 1f))
    }

    @Test
    fun `uses landscape loading window for wide bounds`() {
        val window = LaunchTileWallLayoutLogic.windowForBounds(width = 2400, height = 1080)

        assertEquals(2, window.visibleRows)
        assertEquals(6, window.visibleCols)
        assertEquals(6, LaunchTileWallLayoutLogic.segmentRows(window))
        assertEquals(7, LaunchTileWallLayoutLogic.segmentCols(window))
        assertEquals(176, LaunchTileWallLayoutLogic.maxTileSizePx(window, density = 1f))
    }
}
