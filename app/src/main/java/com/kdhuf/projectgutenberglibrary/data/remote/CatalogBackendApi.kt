package com.kdhuf.projectgutenberglibrary.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface CatalogBackendApi {

    @GET("books")
    suspend fun getBooks(
        @Query("search") search: String? = null,
        @Query("topic") topic: String? = null,
        @Query("sort") sort: String? = null
    ): GutenbergResponse

    @GET
    suspend fun getBooksPage(@Url url: String): GutenbergResponse

    @GET("books/{id}")
    suspend fun getBookDetails(@Path("id") id: Int): BookDto
}
