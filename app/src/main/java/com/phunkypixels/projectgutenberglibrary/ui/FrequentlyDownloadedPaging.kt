package com.phunkypixels.projectgutenberglibrary.ui

internal object FrequentlyDownloadedPaging {
    const val InitialTopDownloadLimit = 100
    const val PagingThreshold = 6

    fun shouldLoadMore(dy: Int, isLoadingMore: Boolean, lastVisible: Int, itemCount: Int): Boolean {
        if (dy <= 0 || isLoadingMore || itemCount <= 0) return false
        return lastVisible >= itemCount - PagingThreshold
    }
}
