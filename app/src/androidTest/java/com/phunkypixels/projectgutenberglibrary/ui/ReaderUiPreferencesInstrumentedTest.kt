package com.phunkypixels.projectgutenberglibrary.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderUiPreferencesInstrumentedTest {

    private lateinit var context: Context
    private lateinit var preferences: ReaderUiPreferences

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        preferences = ReaderUiPreferences(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun persistsThemeAndTextZoom() {
        preferences.setDarkModeEnabled(true)
        preferences.setTextZoom(145)

        assertTrue(preferences.isDarkModeEnabled())
        assertEquals(145, preferences.getTextZoom())
    }

    @Test
    fun persistsPageAndScrollPositionPerBook() {
        preferences.setLastPageIndex(bookId = 84, pageIndex = 12)
        preferences.setLastScrollRatio(bookId = 84, ratio = 0.42f)
        preferences.setLastPageIndex(bookId = 1342, pageIndex = 3)
        preferences.setLastScrollRatio(bookId = 1342, ratio = 0.91f)

        assertEquals(12, preferences.getLastPageIndex(84))
        assertEquals(0.42f, preferences.getLastScrollRatio(84), 0.0001f)
        assertEquals(3, preferences.getLastPageIndex(1342))
        assertEquals(0.91f, preferences.getLastScrollRatio(1342), 0.0001f)
    }

    @Test
    fun clampsScrollRatioIntoValidRange() {
        preferences.setLastScrollRatio(bookId = 84, ratio = 3.5f)
        assertEquals(1f, preferences.getLastScrollRatio(84), 0.0001f)

        preferences.setLastScrollRatio(bookId = 84, ratio = -0.5f)
        assertEquals(0f, preferences.getLastScrollRatio(84), 0.0001f)
    }

    @Test
    fun persistsPreferredVoiceName() {
        assertFalse(preferences.getPreferredVoiceName() != null)

        preferences.setPreferredVoiceName("en-us-x-sfg#female_1-local")
        assertEquals("en-us-x-sfg#female_1-local", preferences.getPreferredVoiceName())

        preferences.setPreferredVoiceName(null)
        assertEquals(null, preferences.getPreferredVoiceName())
    }

    private companion object {
        const val PREFS_NAME = "reader_ui_prefs"
    }
}
