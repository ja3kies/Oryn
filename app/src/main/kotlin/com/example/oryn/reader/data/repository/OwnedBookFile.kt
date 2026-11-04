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

/** Caller owns the library mutation lock; only files created by that operation are candidates. */
internal suspend fun discardUnreferencedOwnedFiles(
    created: List<File>, booksDirectory: File, isReferenced: suspend (File) -> Boolean
) {
    for (file in created) {
        if (!isReferenced(file)) ownedBookFile(file, booksDirectory)?.delete()
    }
}
