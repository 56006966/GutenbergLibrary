package com.kdhuf.projectgutenberglibrary.ui

import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergMirror

object LaunchTileWallCuratedBooks {

    private data class LaunchCoverSeed(
        val id: Int,
        val title: String,
        val author: String
    )

    private val curatedSeeds = listOf(
        LaunchCoverSeed(10007, "Carmilla", "J. Sheridan Le Fanu"),
        LaunchCoverSeed(105, "Persuasion", "Jane Austen"),
        LaunchCoverSeed(2701, "Moby Dick; Or, The Whale", "Herman Melville"),
        LaunchCoverSeed(108, "The Return of Sherlock Holmes", "Arthur Conan Doyle"),
        LaunchCoverSeed(768, "Wuthering Heights", "Emily Bronte"),
        LaunchCoverSeed(11, "Alice's Adventures in Wonderland", "Lewis Carroll"),
        LaunchCoverSeed(106, "Jungle Tales of Tarzan", "Edgar Rice Burroughs"),
        LaunchCoverSeed(12, "Through the Looking-Glass", "Lewis Carroll"),
        LaunchCoverSeed(28, "The Fables of Aesop", "Aesop"),
        LaunchCoverSeed(102, "The Tragedy of Pudd'nhead Wilson", "Mark Twain"),
        LaunchCoverSeed(84, "Frankenstein; Or, The Modern Prometheus", "Mary Wollstonecraft Shelley"),
        LaunchCoverSeed(1342, "Pride and Prejudice", "Jane Austen"),
        LaunchCoverSeed(64317, "The Great Gatsby", "F. Scott Fitzgerald"),
        LaunchCoverSeed(174, "The Picture of Dorian Gray", "Oscar Wilde"),
        LaunchCoverSeed(1513, "Romeo and Juliet", "William Shakespeare"),
        LaunchCoverSeed(43, "The Strange Case of Dr. Jekyll and Mr. Hyde", "Robert Louis Stevenson"),
        LaunchCoverSeed(37106, "Little Women; Or, Meg, Jo, Beth, and Amy", "Louisa May Alcott"),
        LaunchCoverSeed(2554, "Crime and Punishment", "Fyodor Dostoyevsky"),
        LaunchCoverSeed(1260, "Jane Eyre: An Autobiography", "Charlotte Bronte"),
        LaunchCoverSeed(345, "Dracula", "Bram Stoker"),
        LaunchCoverSeed(1184, "The Count of Monte Cristo", "Alexandre Dumas"),
        LaunchCoverSeed(844, "The Importance of Being Earnest", "Oscar Wilde"),
        LaunchCoverSeed(16389, "The Enchanted April", "Elizabeth von Arnim"),
        LaunchCoverSeed(103, "Around the World in Eighty Days", "Jules Verne")
    )

    fun books(): List<BookEntity> = curatedSeeds.map { seed ->
        BookEntity(
            id = seed.id,
            title = seed.title,
            author = seed.author,
            genre = "Classic literature",
            downloads = 0,
            coverUrl = GutenbergMirror.coverUrl(seed.id),
            coverPath = null,
            text = null,
            epubPath = null,
            lastPageIndex = 0,
            downloadedAt = 0L,
            isFavorite = false,
            status = BookEntity.STATUS_TO_READ
        )
    }
}
