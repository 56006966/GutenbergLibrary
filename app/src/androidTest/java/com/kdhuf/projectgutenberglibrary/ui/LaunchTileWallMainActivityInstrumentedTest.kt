package com.kdhuf.projectgutenberglibrary.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
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
                val window = LaunchTileWallLayoutLogic.windowForBounds(
                    width = tileWall.width,
                    height = tileWall.height
                )
                val expectedRows = LaunchTileWallLayoutLogic.segmentRows(window)
                val expectedCols = LaunchTileWallLayoutLogic.segmentCols(window)

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

    @Test
    fun startupOverlayStaysDismissedAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.launchEnterButton)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(R.id.nav_host_fragment_content_main)).check(matches(isDisplayed()))
            onView(withId(R.id.openDrawerButton)).check(matches(isDisplayed()))
            onView(withId(R.id.launchLoadingOverlay))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }
}
