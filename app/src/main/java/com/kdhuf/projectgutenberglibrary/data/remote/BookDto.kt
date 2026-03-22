package com.kdhuf.projectgutenberglibrary.data.remote

data class BookDto(
    val id: Int,
    val title: String,
    val authors: List<AuthorDto> = emptyList(),
    val subjects: List<String>? = null,
    val languages: List<String>? = null,
    val formats: Map<String, String>? = null,
    val download_count: Int = 0
)

data class AuthorDto(
    val name: String
)
