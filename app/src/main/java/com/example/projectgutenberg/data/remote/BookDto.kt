package com.example.projectgutenberg.data.remote

data class BookDto(
    val id: Int,
    val title: String,
    val authors: List<AuthorDto>,
    val subjects: List<String>?,
    val formats: Map<String, String>?,
    val download_count: Int
)
data class AuthorDto(
    val name: String
)