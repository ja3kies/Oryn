package com.example.oryn.reader.data.local.dao

import androidx.room.*
import com.example.oryn.reader.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadTimestamp DESC")
    fun getFavoriteBooks(): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books WHERE filePath = :path")
    suspend fun countByPath(path: String): Int

    @Query("DELETE FROM books WHERE filePath LIKE '/sample/%'")
    suspend fun deleteSamples()

    // Mises à jour ciblées : une copie périmée du livre n'écrase plus la couverture ou les favoris.
    @Query("UPDATE books SET progress = :progress, lastReadTimestamp = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, timestamp: Long)

    @Query("UPDATE books SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE books SET coverPath = :path WHERE id = :id")
    suspend fun updateCover(id: Long, path: String?)

    @Query("UPDATE books SET coverPath = :coverPath, contentHash = :hash WHERE id = :id")
    suspend fun updateCoverAndHash(id: Long, coverPath: String?, hash: String?)

    @Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
    suspend fun updateInfo(id: Long, title: String, author: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
