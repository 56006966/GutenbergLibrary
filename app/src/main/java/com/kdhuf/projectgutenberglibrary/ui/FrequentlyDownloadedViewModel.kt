package com.kdhuf.projectgutenberglibrary.ui

import androidx.lifecycle.ViewModel
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity

class FrequentlyDownloadedViewModel : ViewModel() {
    val loadedBooks = mutableListOf<BookEntity>()
    var nextPopularPageUrl: String? = null
    var isInitialLoadComplete: Boolean = false
    var isLoadingMore: Boolean = false
    var scrollPosition: Int = 0
    var scrollOffset: Int = 0
}
