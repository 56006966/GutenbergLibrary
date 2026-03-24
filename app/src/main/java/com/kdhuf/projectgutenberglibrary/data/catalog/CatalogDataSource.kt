package com.kdhuf.projectgutenberglibrary.data.catalog

import com.kdhuf.projectgutenberglibrary.data.remote.BookDto
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergResponse

interface CatalogDataSource {
    suspend fun getBooks(
        search: String? = null,
        topic: String? = null,
        sort: String? = null
    ): GutenbergResponse

    suspend fun getBooksPage(url: String): GutenbergResponse

    suspend fun getBookDetails(id: Int): BookDto
}
