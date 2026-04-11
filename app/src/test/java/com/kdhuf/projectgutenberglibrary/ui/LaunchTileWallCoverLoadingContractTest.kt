package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LaunchTileWallCoverLoadingContractTest {

    @Test
    fun `launch wall loads book covers instead of icon-only placeholders`() {
        val source = launchTileWallSource()

        assertTrue(source.contains("preferredLaunchWallCover(book)"))
        assertTrue(source.contains("image.load(url)"))
        assertTrue(source.contains("fallbackUrl"))
    }

    @Test
    fun `launch wall staggers cover loading to protect startup`() {
        val source = launchTileWallSource()

        assertTrue(source.contains("loadTileCoverDeferred("))
        assertTrue(source.contains("image.postDelayed("))
        assertTrue(source.contains("tileBindSequence++ % 18"))
    }

    private fun launchTileWallSource(): String {
        return projectFile("app/src/main/java/com/kdhuf/projectgutenberglibrary/ui/LaunchTileWallView.kt")
            .readText()
    }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot

        val fromAppModule = File("..", path)
        check(fromAppModule.exists()) { "Could not find $path from ${File(".").absolutePath}" }
        return fromAppModule
    }
}
