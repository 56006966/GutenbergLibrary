package com.example.projectgutenberg.data.remote
data class GutenbergResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<BookDto>
)
