package com.example.oryn.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.oryn.reader.data.local.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY startOffset")
    fun observe(bookId: Long): Flow<List<AnnotationEntity>>

    @Insert
    suspend fun insert(annotation: AnnotationEntity): Long

    @Update
    suspend fun update(annotation: AnnotationEntity)

    @Delete
    suspend fun delete(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)
}
