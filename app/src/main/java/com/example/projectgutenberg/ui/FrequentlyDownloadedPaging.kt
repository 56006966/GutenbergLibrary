package com.example.projectgutenberg.ui

internal object FrequentlyDownloadedPaging {
    const val InitialTopDownloadLimit = 100
    const val PagingThreshold = 6
    const val EstimatedPageSize = 25

    fun shouldLoadMore(dy: Int, isLoadingMore: Boolean, lastVisible: Int, itemCount: Int): Boolean {
        if (dy <= 0 || isLoadingMore || itemCount <= 0) return false
        return lastVisible >= itemCount - PagingThreshold
    }

    fun buildNextPopularPageUrl(initialCount: Int): String {
        val nextPage = (initialCount / EstimatedPageSize) + 1
        return "https://gutendex.com/books?sort=popular&page=$nextPage"
    }
}
