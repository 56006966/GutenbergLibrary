package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadFailureUxContractTest {

    @Test
    fun `reader failure state exposes retry action`() {
        val layout = projectFile("app/src/main/res/layout/fragment_book_webview.xml").readText()

        assertTrue(layout.contains("android:id=\"@+id/retryDownloadButton\""))
        assertTrue(layout.contains("android:text=\"@string/retry_download\""))
    }

    @Test
    fun `download failure strings are user friendly`() {
        val strings = projectFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(strings.contains("<string name=\"book_failed_status\">Could not open this title.</string>"))
        assertTrue(strings.contains("<string name=\"failed_load_book\">Failed to load book: %1\$s</string>"))
        assertTrue(strings.contains("<string name=\"retry_download\">Retry download</string>"))
    }

    @Test
    fun `download flow communicates retry and local save progress`() {
        val strings = projectFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(strings.contains("<string name=\"book_downloading_status\">Downloading EPUB and saving it to My Library...</string>"))
        assertTrue(strings.contains("<string name=\"book_retry_download_status\">Trying another download source...</string>"))
    }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot

        val fromAppModule = File("..", path)
        check(fromAppModule.exists()) { "Could not find $path from ${File(".").absolutePath}" }
        return fromAppModule
    }
}
