package com.phunkypixels.projectgutenberglibrary.data.catalog

import com.phunkypixels.projectgutenberglibrary.data.remote.BookDto
import com.phunkypixels.projectgutenberglibrary.data.remote.GutenbergResponse

interface CatalogDataSource {
    suspend fun getBooks(
        search: String? = null,
        topic: String? = null,
        sort: String? = null
    ): GutenbergResponse

    suspend fun getBooksPage(url: String): GutenbergResponse

    suspend fun getBookDetails(id: Int): BookDto
}
