package com.kdhuf.projectgutenberglibrary.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Int): BookEntity?

    @Query("SELECT * FROM books ORDER BY downloads DESC")
    fun getPopularBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY id DESC")
    fun getNewestReleases(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE status = :status ORDER BY title ASC")
    fun getBooksByStatus(status: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE status IN (:statuses) ORDER BY title ASC")
    fun getBooksByStatuses(statuses: List<String>): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY title ASC")
    fun sortByTitle(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY downloads DESC")
    fun sortByDownloads(): Flow<List<BookEntity>>

    @Query("UPDATE books SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)

    @Query("UPDATE books SET lastPageIndex = :pageIndex WHERE id = :id")
    suspend fun updateLastPageIndex(id: Int, pageIndex: Int)

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFavorite: Boolean)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Int)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)
    @Query("SELECT * FROM books")
    fun getAllBooksFlow(): Flow<List<BookEntity>>
}
