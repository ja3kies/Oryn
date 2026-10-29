package com.example.oryn.reader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val filePath: String,
    val format: String, // EPUB, PDF, TXT
    val progress: Float = 0f,
    val isFavorite: Boolean = false,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    /** null = pas encore cherchée ; "" = aucune couverture ; sinon chemin d'une image locale. */
    val coverPath: String? = null
)

