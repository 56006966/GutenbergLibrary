package com.example.projectgutenberg.data.remote

data class Book(
    val id: Int,
    val title: String,
    val subjects: List<String>,
    val authors: List<Person>,
    val summaries: List<String>,
    val translators: List<Person>,
    val bookshelves: List<String>,
    val languages: List<String>,
    val copyright: Boolean?,
    val media_type: String,
    val formats: Map<String, String>,
    val download_count: Int
)
