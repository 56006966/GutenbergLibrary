package com.kdhuf.projectgutenberglibrary.data.remote

data class Book(
    val id: Int,
    val title: String,
    val authors: List<Person>,
    val languages: List<String>,
    val formats: Map<String, String>,
    val download_count: Int
)
