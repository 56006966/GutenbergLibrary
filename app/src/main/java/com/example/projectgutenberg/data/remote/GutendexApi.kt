package com.example.projectgutenberg.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface GutenbergApi {

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

    @GET
    suspend fun getBookText(@Url textUrl: String): ResponseBody

    companion object {
        private const val BASE_URL = "https://gutendex.com/"

        fun create(): GutenbergApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GutenbergApi::class.java)
        }
    }
}
