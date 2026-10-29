package com.example.oryn.reader.data.repository

import com.example.oryn.reader.data.local.dao.BookDao
import com.example.oryn.reader.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

/** Point d'entrée unique vers Room : l'UI et le ViewModel ne voient jamais le DAO. */
class BookRepository(private val dao: BookDao) {

    val allBooks: Flow<List<BookEntity>> = dao.getAllBooks()
    val favoriteBooks: Flow<List<BookEntity>> = dao.getFavoriteBooks()

    suspend fun addBook(book: BookEntity) = dao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = dao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) = dao.deleteBook(book)

    suspend fun setFavorite(book: BookEntity, favorite: Boolean) =
        dao.updateFavorite(book.id, favorite)

    suspend fun updateProgress(book: BookEntity, progress: Float, timestamp: Long) =
        dao.updateProgress(book.id, progress, timestamp)

    suspend fun updateCover(bookId: Long, path: String?) = dao.updateCover(bookId, path)

    suspend fun updateInfo(bookId: Long, title: String, author: String) =
        dao.updateInfo(bookId, title, author)

    suspend fun exists(path: String): Boolean = dao.countByPath(path) > 0

    suspend fun deleteSamples() = dao.deleteSamples()
}
