package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequentlyDownloadedPagingTest {

    @Test
    fun `loads more when user scrolls down into threshold window`() {
        assertTrue(
            FrequentlyDownloadedPaging.shouldLoadMore(
                dy = 12,
                isLoadingMore = false,
                lastVisible = 94,
                itemCount = 100
            )
        )
    }

    @Test
    fun `does not load more when scrolling upward or already loading`() {
        assertFalse(
            FrequentlyDownloadedPaging.shouldLoadMore(
                dy = -4,
                isLoadingMore = false,
                lastVisible = 99,
                itemCount = 100
            )
        )
        assertFalse(
            FrequentlyDownloadedPaging.shouldLoadMore(
                dy = 8,
                isLoadingMore = true,
                lastVisible = 99,
                itemCount = 100
            )
        )
    }

    @Test
    fun `does not load more before threshold or with empty content`() {
        assertFalse(
            FrequentlyDownloadedPaging.shouldLoadMore(
                dy = 8,
                isLoadingMore = false,
                lastVisible = 40,
                itemCount = 100
            )
        )
        assertFalse(
            FrequentlyDownloadedPaging.shouldLoadMore(
                dy = 8,
                isLoadingMore = false,
                lastVisible = 0,
                itemCount = 0
            )
        )
    }
}
