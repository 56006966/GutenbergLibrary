package com.example.projectgutenberg.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

// Top-level API interface
interface GutenbergApi {

    @GET("books")
    suspend fun getBooks(@Query("topic") topic: String): GutenbergResponse

    @GET("books/{id}")
    suspend fun getBookDetails(@Path("id") id: Int): BookDto

    @GET
    suspend fun getBookText(@Url textUrl: String): String
}
