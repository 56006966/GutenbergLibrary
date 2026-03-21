package com.example.projectgutenberg.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequentlyDownloadedPagingTest {

    @Test
    fun `buildNextPopularPageUrl advances to page 5 after initial top 100`() {
        val nextUrl = FrequentlyDownloadedPaging.buildNextPopularPageUrl(100)

        assertEquals("https://gutendex.com/books?sort=popular&page=5", nextUrl)
    }

    @Test
    fun `buildNextPopularPageUrl advances to page 2 after first partial page`() {
        val nextUrl = FrequentlyDownloadedPaging.buildNextPopularPageUrl(25)

        assertEquals("https://gutendex.com/books?sort=popular&page=2", nextUrl)
    }

    @Test
    fun `shouldLoadMore is false while already loading`() {
        assertFalse(
            FrequentlyDownloadedPaging.shouldLoadMore(
                dy = 5,
                isLoadingMore = true,
                lastVisible = 18,
                itemCount = 20
            )
        )
    }

    @Test
    fun `shouldLoadMore is false when scrolling upward`() {
        assertFalse(
            FrequentlyDownloadedPaging.shouldLoadMore(
                dy = -2,
                isLoadingMore = false,
                lastVisible = 18,
                itemCount = 20
            )
        )
    }

    @Test
    fun `shouldLoadMore becomes true near end of list`() {
        assertTrue(
            FrequentlyDownloadedPaging.shouldLoadMore(
                dy = 8,
                isLoadingMore = false,
                lastVisible = 19,
                itemCount = 24
            )
        )
    }
}
