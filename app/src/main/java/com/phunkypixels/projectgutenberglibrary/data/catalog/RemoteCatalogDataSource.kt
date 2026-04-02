package com.phunkypixels.projectgutenberglibrary.data.catalog

import com.phunkypixels.projectgutenberglibrary.data.remote.BookDto
import com.phunkypixels.projectgutenberglibrary.data.remote.CatalogBackendApi
import com.phunkypixels.projectgutenberglibrary.data.remote.GutenbergResponse

class RemoteCatalogDataSource(
    private val api: CatalogBackendApi
) : CatalogDataSource {
    override suspend fun getBooks(
        search: String?,
        topic: String?,
        sort: String?
    ): GutenbergResponse = api.getBooks(search, topic, sort)

    override suspend fun getBooksPage(url: String): GutenbergResponse = api.getBooksPage(url)

    override suspend fun getBookDetails(id: Int): BookDto = api.getBookDetails(id)
}
