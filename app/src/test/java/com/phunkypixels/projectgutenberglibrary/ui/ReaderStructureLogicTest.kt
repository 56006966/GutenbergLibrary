package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderStructureLogicTest {

    @Test
    fun `buildStructure maps toc entries onto matching pages`() {
        val pages = listOf(
            "<h1>Preface</h1><p>Welcome.</p>",
            "<h1>Chapter I</h1><p>Call me Ishmael.</p>",
            "<h1>Chapter II</h1><p>The Carpet-Bag.</p>"
        )

        val structure = ReaderStructureLogic.buildStructure(
            pages = pages,
            tocHints = listOf(
                ReaderTocHint(title = "Preface", depth = 0),
                ReaderTocHint(title = "Chapter I", depth = 0),
                ReaderTocHint(title = "Chapter II", depth = 0)
            )
        )

        assertEquals(3, structure.tocEntries.size)
        assertEquals(0, structure.tocEntries[0].pageIndex)
        assertEquals(1, structure.tocEntries[1].pageIndex)
        assertEquals(2, structure.tocEntries[2].pageIndex)
        assertEquals(listOf("Preface", "Chapter I", "Chapter II"), structure.chapterTitlesByPage)
    }

    @Test
    fun `buildStructure carries chapter title forward until next toc entry`() {
        val pages = listOf(
            "<h1>Chapter I</h1><p>Start.</p>",
            "<p>Still chapter one.</p>",
            "<h1>Chapter II</h1><p>Next chapter.</p>"
        )

        val structure = ReaderStructureLogic.buildStructure(
            pages = pages,
            tocHints = listOf(
                ReaderTocHint(title = "Chapter I", depth = 0),
                ReaderTocHint(title = "Chapter II", depth = 0)
            )
        )

        assertEquals(listOf("Chapter I", "Chapter I", "Chapter II"), structure.chapterTitlesByPage)
    }

    @Test
    fun `buildStructure de duplicates repeated toc entries for same page`() {
        val structure = ReaderStructureLogic.buildStructure(
            pages = listOf("<h1>Chapter I</h1><p>Start.</p>"),
            tocHints = listOf(
                ReaderTocHint(title = "Chapter I", depth = 0),
                ReaderTocHint(title = "Chapter I", depth = 1)
            )
        )

        assertEquals(1, structure.tocEntries.size)
        assertEquals("Chapter I", structure.tocEntries.first().title)
    }

    @Test
    fun `extractPrintedPageLabel prefers explicit pagebreak marker`() {
        val label = ReaderStructureLogic.extractPrintedPageLabel(
            """<p><span class="pagenum">23</span> The story continues.</p>"""
        )

        assertEquals("23", label)
    }

    @Test
    fun `extractPrintedPageLabel falls back to bracketed page notation`() {
        val label = ReaderStructureLogic.extractPrintedPageLabel(
            """<p>[Page xiv]</p><p>Opening text.</p>"""
        )

        assertEquals("xiv", label)
    }

    @Test
    fun `extractPrintedPageLabel returns null when no printed page marker exists`() {
        val label = ReaderStructureLogic.extractPrintedPageLabel(
            """<p>No explicit printed page marker here.</p>"""
        )

        assertNull(label)
    }

    @Test
    fun `formatReaderPosition combines chapter printed page and logical page`() {
        val label = ReaderStructureLogic.formatReaderPosition(
            chapterTitle = "Chapter I",
            printedPageLabel = "23",
            logicalPageLabel = "5",
            logicalPageCountLabel = "42"
        )

        assertEquals("Chapter I - Printed page 23 - Page 5 of 42", label)
    }

    @Test
    fun `formatReaderPosition falls back to logical page only when chapter and printed page are absent`() {
        val label = ReaderStructureLogic.formatReaderPosition(
            chapterTitle = null,
            printedPageLabel = null,
            logicalPageLabel = "5",
            logicalPageCountLabel = "42"
        )

        assertEquals("Page 5 of 42", label)
    }
}
