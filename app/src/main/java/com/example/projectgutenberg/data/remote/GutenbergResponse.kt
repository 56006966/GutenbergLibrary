package com.example.projectgutenberg.data.remote

data class GutenbergResponse(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<BookDto> = emptyList()
)