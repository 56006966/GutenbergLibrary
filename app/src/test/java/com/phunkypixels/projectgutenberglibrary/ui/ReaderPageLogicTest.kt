package com.phunkypixels.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageLogicTest {

    @Test
    fun `decorative initial block is merged into following paragraph`() {
        val blocks = listOf(
            """<p><img src="images/a.jpg" width="96" height="120" /></p>""",
            """<p>truth universally acknowledged.</p>"""
        )

        val merged = ReaderPageLogic.mergeDecorativeInitialBlocks(blocks)

        assertEquals(1, merged.size)
        assertEquals(
            """<p><img src="images/a.jpg" width="96" height="120" />truth universally acknowledged.</p>""",
            merged.first()
        )
    }

    @Test
    fun `decorative initial image alone does not count as meaningful page content`() {
        assertFalse(
            ReaderPageLogic.hasMeaningfulPageContent(
                """<p><img src="images/a.jpg" width="96" height="120" /></p>"""
            )
        )
    }

    @Test
    fun `illustration page still counts as meaningful page content`() {
        assertTrue(
            ReaderPageLogic.hasMeaningfulPageContent(
                """<figure><img src="images/plate_01.jpg" width="640" height="960" /></figure>"""
            )
        )
    }

    @Test
    fun `small drop cap image is treated as decorative`() {
        assertTrue(
            ReaderPageLogic.isDecorativeImageTag(
                """<img src="images/a.jpg" width="96" height="120" />"""
            )
        )
    }

    @Test
    fun `large illustration image is not treated as decorative`() {
        assertFalse(
            ReaderPageLogic.isDecorativeImageTag(
                """<img src="images/plate_01.jpg" width="640" height="960" />"""
            )
        )
    }

    @Test
    fun `page numbers are formatted as roman numerals`() {
        assertEquals("I", ReaderPageLogic.formatPageNumber(1))
        assertEquals("XII", ReaderPageLogic.formatPageNumber(12))
        assertEquals("MCCCXLII", ReaderPageLogic.formatPageNumber(1342))
    }

    @Test
    fun `page numbers outside roman range fall back to decimal`() {
        assertEquals("0", ReaderPageLogic.formatPageNumber(0))
        assertEquals("4000", ReaderPageLogic.formatPageNumber(4000))
    }

    @Test
    fun `decorative initial block is not merged without following content block`() {
        val blocks = listOf(
            """<p><img src="images/a.jpg" width="96" height="120" /></p>"""
        )

        val merged = ReaderPageLogic.mergeDecorativeInitialBlocks(blocks)

        assertEquals(blocks, merged)
    }

    @Test
    fun `page with only entities and whitespace has no meaningful content`() {
        assertFalse(
            ReaderPageLogic.hasMeaningfulPageContent(
                """<p>&nbsp;&nbsp;</p>"""
            )
        )
    }

    @Test
    fun `single tap in left zone goes to previous page`() {
        assertEquals(
            ReaderSingleTapAction.PREVIOUS_PAGE,
            ReaderPageLogic.resolveSingleTapAction(x = 40f, width = 300)
        )
    }

    @Test
    fun `single tap in center zone passes through to web content`() {
        assertEquals(
            ReaderSingleTapAction.PASS_THROUGH,
            ReaderPageLogic.resolveSingleTapAction(x = 150f, width = 300)
        )
    }

    @Test
    fun `single tap in right zone goes to next page`() {
        assertEquals(
            ReaderSingleTapAction.NEXT_PAGE,
            ReaderPageLogic.resolveSingleTapAction(x = 270f, width = 300)
        )
    }
}
