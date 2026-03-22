package com.kdhuf.projectgutenberglibrary.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kdhuf.projectgutenberglibrary.MainActivity
import com.kdhuf.projectgutenberglibrary.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTileWallMainActivityInstrumentedTest {

    @Test
    fun loadingWallUsesAspectRatioAwareGrid() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val tileWall = activity.findViewById<LaunchTileWallView>(R.id.launchTileWall)
                val (segmentRows, segmentCols) = tileWall.debugGridSize()
                val expectedRows = if (tileWall.width > tileWall.height) 8 else 12
                val expectedCols = if (tileWall.width > tileWall.height) 6 else 5

                assertTrue(tileWall.width > 0)
                assertTrue(tileWall.height > 0)
                assertEquals(expectedRows, segmentRows)
                assertEquals(expectedCols, segmentCols)
            }
        }
    }

    @Test
    fun loadingWallStartsAnimatingWhenOverlayBuilds() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val tileWall = activity.findViewById<LaunchTileWallView>(R.id.launchTileWall)
                assertTrue(tileWall.debugSegmentHeightPx() > 0f)
                assertTrue(tileWall.debugIsAnimating())
            }
        }
    }
}
