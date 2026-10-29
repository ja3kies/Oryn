package com.example.oryn.reader.data.remote

data class AnnasBook(
    val md5: String,
    val title: String,
    val author: String,
    val format: String,
    val coverUrl: String? = null
)
