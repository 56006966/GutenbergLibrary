package com.example.projectgutenberg.ui

import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.projectgutenberg.MainActivity
import com.example.projectgutenberg.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrequentlyDownloadedNavigationInstrumentedTest {

    @Test
    fun frequentlyDownloadedShowsDrawerButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
                navHost.navController.navigate(R.id.nav_frequently_downloaded_screen)
            }

            onView(withId(R.id.openDrawerButton)).check(matches(isDisplayed()))
            onView(withId(R.id.frequentlyDownloadedRecyclerView)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun frequentlyDownloadedDestinationSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
                navHost.navController.navigate(R.id.nav_frequently_downloaded_screen)
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
                assertEquals(
                    R.id.nav_frequently_downloaded_screen,
                    navHost.navController.currentDestination?.id
                )
            }

            onView(withId(R.id.openDrawerButton)).check(matches(isDisplayed()))
        }
    }
}
