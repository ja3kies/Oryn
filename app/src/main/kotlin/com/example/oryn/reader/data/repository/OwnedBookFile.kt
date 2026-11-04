package com.example.oryn.reader.data.repository

import java.io.File

/** Only imported/downloaded private book files may be deleted; never follow a path outside books. */
internal fun ownedBookFile(file: File, booksDirectory: File): File? = runCatching {
    val root = booksDirectory.canonicalFile
    val candidate = file.canonicalFile
    candidate.takeIf {
        it.isFile && (it.parentFile == root || it.parentFile == File(root, "shared"))
    }
}.getOrNull()
