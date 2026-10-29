package com.example.oryn.reader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Surlignage ou soulignage d'un passage, repéré par sa position dans le texte du livre. */
@Entity(tableName = "annotations", indices = [Index("bookId")])
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val startOffset: Int,
    val endOffset: Int,
    /** "HIGHLIGHT" ou "UNDERLINE" */
    val kind: String,
    /** Indice de couleur (surlignage) : 0 jaune, 1 vert, 2 bleu, 3 rose */
    val color: Int,
    val note: String?,
    /** Texte du passage, conservé pour la liste et l'export. */
    val quote: String,
    val createdAt: Long = System.currentTimeMillis()
)
