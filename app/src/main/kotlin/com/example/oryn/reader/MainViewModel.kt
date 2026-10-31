package com.example.oryn.reader

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.oryn.reader.data.importer.BookMetadataReader
import com.example.oryn.reader.data.importer.CoverStore
import com.example.oryn.reader.data.local.OrynDatabase
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.data.local.preferences.UserPreferencesRepository
import com.example.oryn.reader.data.remote.GutendexClient
import com.example.oryn.reader.data.remote.OnlineBook
import com.example.oryn.reader.data.repository.BookRepository
import com.example.oryn.reader.ui.components.CoverCache
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val MAX_SHARED_IMPORT_BYTES = 100L * 1024 * 1024
    }

    private val repository = BookRepository(
        OrynDatabase.getDatabase(application).bookDao()
    )
    private val metadataReader = BookMetadataReader(application)
    private val coverStore = CoverStore(application)
    private val prefsRepository = UserPreferencesRepository(application)
    
    fun deleteBooks(books: List<BookEntity>) {
        if (books.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val db = OrynDatabase.getDatabase(getApplication<Application>())
            db.bookDao().deleteBooksWithAnnotations(books.map { it.id }.distinct())
            val booksDir = File(getApplication<Application>().filesDir, "books")
            books.forEach { book ->
                if (book.filePath.startsWith("file:")) {
                    val file = Uri.parse(book.filePath).path?.let(::File)
                    if (file != null && file.parentFile == booksDir) file.delete()
                }
            }
        }
    }


    val books: StateFlow<List<BookEntity>?> = repository.allBooks
        .map { it as List<BookEntity>? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favoriteBooks: StateFlow<List<BookEntity>> = repository.favoriteBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<AppThemeMode> = prefsRepository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.SYSTEM)

    val dyslexicFont: StateFlow<Boolean> = prefsRepository.dyslexicFontFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val accentTheme: StateFlow<AppAccentTheme> = prefsRepository.accentThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppAccentTheme.ORANGE)

    val onboardingComplete: StateFlow<Boolean?> = prefsRepository.onboardingCompleteFlow
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _downloadingIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Identifiants Gutenberg en cours de téléchargement (pour l'écran Explorer). */
    val downloadingIds: StateFlow<Set<Long>> = _downloadingIds.asStateFlow()

    private val _webDownloads = MutableStateFlow<Set<String>>(emptySet())
    /** Adresses de fichiers en cours de téléchargement depuis le navigateur intégré. */
    val webDownloads: StateFlow<Set<String>> = _webDownloads.asStateFlow()

    init {
        viewModelScope.launch { repository.deleteSamples() }
        viewModelScope.launch(Dispatchers.IO) { backfillCovers() }
    }

    /**
     * Calcule le hash de contenu des livres existants et migre leurs
     * couvertures vers le schéma `cover_<hash>.jpg` (bibliothèque Oryn).
     * Ne s'exécute qu'une fois par livre.
     */
    private suspend fun backfillCovers() {
        val pending = repository.allBooks.first().filter {
            it.contentHash == null || (it.format == "EPUB" && it.coverPath == null)
        }
        for (book in pending) {
            val uri = Uri.parse(book.filePath)
            val hash = book.contentHash ?: computeContentHash(uri)
            var newCoverPath = book.coverPath

            if (newCoverPath == null && book.format == "EPUB") {
                val existing = hash?.let(coverStore::findByHash)
                newCoverPath = existing ?: runCatching {
                    metadataReader.extractEpubCover(uri)?.let { coverStore.saveBytes(it, hash) }
                }.getOrNull() ?: ""
            }

            if (hash != null && !newCoverPath.isNullOrBlank()) {
                // Si le chemin actuel n'est pas hash-nommé, migre-le.
                if (coverStore.findByHash(hash) == null) {
                    val bytes = runCatching { File(newCoverPath).readBytes() }.getOrNull()
                    val migrated = bytes?.let { coverStore.saveBytes(it, hash) }
                    if (migrated != null) {
                        coverStore.delete(newCoverPath)
                        newCoverPath = migrated
                    }
                }
            }

            if (newCoverPath != book.coverPath || hash != book.contentHash) {
                repository.updateCoverAndHash(book.id, newCoverPath, hash)
            }
        }
    }

    /**
     * SHA-256 des 64 premiers Ko du fichier pointé par [uri]. Utilisé comme
     * identifiant stable d'un livre : si l'utilisateur supprime puis réimporte
     * le même fichier, on retrouve sa couverture Oryn sans toucher à l'EPUB.
     */
    private fun computeContentHash(uri: Uri): String? = runCatching {
        val buffer = ByteArray(64 * 1024)
        var total = 0
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            while (total < buffer.size) {
                val n = input.read(buffer, total, buffer.size - total)
                if (n < 0) break
                total += n
            }
        } ?: return@runCatching null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(buffer, 0, total)
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setDarkMode(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefsRepository.setThemeMode(mode) }
    }

    fun setDyslexicFont(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setDyslexicFont(enabled) }
    }

    fun setAccentTheme(theme: AppAccentTheme) {
        viewModelScope.launch { prefsRepository.setAccentTheme(theme) }
    }

    fun setOnboardingComplete(complete: Boolean) {
        viewModelScope.launch { prefsRepository.setOnboardingComplete(complete) }
    }

    /** Copie les documents partagés dans le stockage privé avant import. */
    fun importSharedBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            val copied = mutableListOf<Uri>()
            var failed = 0
            val directory = File(app.filesDir, "books/shared").apply { mkdirs() }

            for (uri in uris.distinct()) {
                var temp: File? = null
                try {
                    val name = runCatching {
                        app.contentResolver.query(
                            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                        ?: "livre"

                    val safeName = name.substringAfterLast('/').substringAfterLast('\\')
                        .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
                        .trim().take(100).ifBlank { "livre" }

                    val digest = MessageDigest.getInstance("SHA-256")
                    temp = File.createTempFile("incoming-", ".tmp", directory)

                    app.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_SHARED_IMPORT_BYTES) {
                                    throw java.io.IOException(tr("Fichier trop volumineux"))
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: throw java.io.IOException(tr("URI inaccessible"))

                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    val destination = File(directory, "$hash-$safeName")

                    if (destination.exists()) {
                        temp.delete()
                    } else if (!temp.renameTo(destination)) {
                        temp.copyTo(destination, overwrite = true)
                        temp.delete()
                    }

                    copied += Uri.fromFile(destination)
                } catch (_: Exception) {
                    failed++
                } finally {
                    temp?.takeIf { it.exists() }?.delete()
                }
            }

            if (copied.isNotEmpty()) importBooks(copied)
            if (failed > 0) toast(trf("Partage : %s fichier(s) inaccessible(s)", failed))
        }
    }

    fun importBooks(uris: List<Uri>, favorite: Boolean = false) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            var added = 0
            var duplicates = 0
            var unsupported = 0
            var failed = 0

            for (uri in uris) {
                try {
                    val path = uri.toString()
                    if (repository.exists(path)) {
                        duplicates++
                        continue
                    }

                    val meta = metadataReader.read(uri)
                    if (meta == null) {
                        unsupported++
                        continue
                    }

                    // Les URI file:// partagées sont déjà copiées dans le stockage privé.
                    if (uri.scheme == "content") {
                        runCatching {
                            app.contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }.recoverCatching {
                            app.contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    }
                    val hash = computeContentHash(uri)
                    val existingCover = hash?.let(coverStore::findByHash)
                    val embeddedCover = if (existingCover == null)
                        meta.coverBytes?.let { coverStore.saveBytes(it, hash) } else null
                    repository.addBook(
                        BookEntity(
                            title = meta.title,
                            author = meta.author,
                            filePath = path,
                            format = meta.format,
                            isFavorite = favorite,
                            coverPath = existingCover ?: embeddedCover ?: if (meta.format == "EPUB") "" else null,
                            contentHash = hash
                        )
                    )
                    added++
                } catch (_: SecurityException) {
                    failed++
                } catch (_: Exception) {
                    failed++
                }
            }

            val summary = buildList {
                if (added > 0) add(trf("%s importé(s)", added))
                if (duplicates > 0) add(trf("%s déjà présent(s)", duplicates))
                if (unsupported > 0) add(trf("%s format(s) non pris en charge", unsupported))
                if (failed > 0) add(trf("%s échec(s) d’accès ou d’import", failed))
            }.joinToString(" · ").ifBlank { tr("Aucun livre importé.") }
            toast(trf("Importation : %s", summary))
        }
    }

    /** Télécharge un EPUB du domaine public et l'ajoute à la bibliothèque. */
    fun addOnlineBook(book: OnlineBook) {
        if (book.id in _downloadingIds.value) return
        _downloadingIds.update { it + book.id }
        val app = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            val file = File(File(app.filesDir, "books"), "gutenberg-${book.id}.epub")
            try {
                val uri = Uri.fromFile(file)
                if (repository.exists(uri.toString())) {
                    toast(tr("Déjà dans ta bibliothèque"))
                    return@launch
                }
                GutendexClient.download(book.epubUrl, file)

                val hash = computeContentHash(uri)
                val existingCover = hash?.let(coverStore::findByHash)
                val coverBytes = if (existingCover == null) {
                    metadataReader.extractEpubCover(uri)
                        ?: book.coverUrl?.let { runCatching { GutendexClient.fetchBytes(it) }.getOrNull() }
                } else null
                val cover = coverBytes?.let { coverStore.saveBytes(it, hash) }

                repository.addBook(
                    BookEntity(
                        title = book.title,
                        author = book.author,
                        filePath = uri.toString(),
                        format = "EPUB",
                        coverPath = existingCover ?: cover ?: "",
                        contentHash = hash
                    )
                )
                toast(trf("« %s » ajouté", book.title))
            } catch (_: Exception) {
                file.delete()
                toast(tr("Téléchargement impossible. Vérifie ta connexion."))
            } finally {
                _downloadingIds.update { it - book.id }
            }
        }
    }

    /** Fichier trouvé dans le navigateur intégré (EPUB, PDF, TXT) : téléchargé puis ajouté. */
    fun addFromWeb(url: String, fileName: String, userAgent: String?, cookie: String?, referer: String?) {
        if (url in _webDownloads.value) return
        _webDownloads.update { it + url }
        val app = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            var target: File? = null
            try {
                val dir = File(app.filesDir, "books").apply { mkdirs() }
                val safeName = fileName.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).ifBlank { "livre.epub" }
                target = uniqueFile(dir, safeName)
                val headers = buildMap {
                    userAgent?.let { put("User-Agent", it) }
                    cookie?.let { put("Cookie", it) }
                    referer?.let { put("Referer", it) }
                }
                GutendexClient.downloadFromWeb(url, target, headers)

                val uri = Uri.fromFile(target)
                val meta = metadataReader.read(uri)
                if (meta == null) {
                    target.delete()
                    toast(tr("Format non pris en charge (EPUB, PDF ou TXT)."))
                    return@launch
                }
                val hash = computeContentHash(uri)
                val existingCover = hash?.let(coverStore::findByHash)
                val embeddedCover = if (existingCover == null)
                    meta.coverBytes?.let { coverStore.saveBytes(it, hash) } else null
                repository.addBook(
                    BookEntity(
                        title = meta.title,
                        author = meta.author,
                        filePath = uri.toString(),
                        format = meta.format,
                        coverPath = existingCover ?: embeddedCover ?: if (meta.format == "EPUB") "" else null,
                        contentHash = hash
                    )
                )
                toast(trf("« %s » ajouté à ta bibliothèque", meta.title))
            } catch (_: Exception) {
                target?.delete()
                toast(tr("Téléchargement impossible. Le site demande peut-être une connexion."))
            } finally {
                _webDownloads.update { it - url }
            }
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        while (file.exists()) {
            file = File(dir, if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext")
            index++
        }
        return file
    }

    /**
     * Couverture choisie par l'utilisateur. Enregistrée dans le stockage privé
     * d'Oryn sous `cover_<hash>.jpg` : elle survit à la suppression du livre
     * et sera automatiquement réappliquée si le même fichier est réimporté.
     * Aucune écriture dans le fichier EPUB source.
     */
    fun setCover(book: BookEntity, imageUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val hash = book.contentHash ?: computeContentHash(Uri.parse(book.filePath))
            val oldPath = book.coverPath
            // Invalide le cache AVANT d'écraser : même chemin, bitmap potentiellement stale
            if (!oldPath.isNullOrBlank()) CoverCache.invalidate(oldPath)
            val path = coverStore.saveUri(imageUri, hash)
            if (path == null) {
                toast(tr("Image illisible"))
                return@launch
            }
            // Invalide aussi le nouveau chemin (hash-based), au cas où il existe déjà
            CoverCache.invalidate(path)
            repository.updateCoverAndHash(book.id, path, hash)
            if (!oldPath.isNullOrBlank() && oldPath != path) {
                coverStore.delete(oldPath)
            }
            toast(tr("Couverture enregistrée"))
        }
    }

    /** Titre et auteur corrigés par l'utilisateur (le fichier EPUB n'est pas touché). */
    fun updateBookInfo(book: BookEntity, title: String, author: String) {
        val cleanTitle = title.trim().ifBlank { book.title }
        val cleanAuthor = author.trim().ifBlank { "Auteur inconnu" }
        viewModelScope.launch(Dispatchers.IO) { repository.updateInfo(book.id, cleanTitle, cleanAuthor) }
    }

    fun removeCover(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCover(book.id, "")
            coverStore.delete(book.coverPath)
        }
    }

    fun updateBookProgress(book: BookEntity, progress: Float) {
        viewModelScope.launch {
            repository.updateProgress(book, progress.coerceIn(0f, 1f), System.currentTimeMillis())
        }
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch { repository.setFavorite(book, !book.isFavorite) }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBook(book)
            OrynDatabase.getDatabase(getApplication()).annotationDao().deleteForBook(book.id)
            OrynDatabase.getDatabase(getApplication()).annotationDao().deleteForBook(-book.id)
            // La couverture est conservée sur disque : elle sera réutilisée si
            // l'utilisateur réimporte le même fichier (bibliothèque de covers Oryn).
            // Fichier téléchargé par l'app : on le supprime aussi (jamais un fichier de l'utilisateur)
            if (book.filePath.startsWith("file:")) {
                val file = Uri.parse(book.filePath).path?.let(::File)
                val booksDir = File(getApplication<Application>().filesDir, "books")
                if (file != null && file.parentFile == booksDir) file.delete()
            }
        }
    }

    private suspend fun toast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Récupère le MainViewModel de l'activité depuis n'importe quel écran, sans changer MainActivity. */
@Composable
fun rememberMainViewModel(): MainViewModel {
    val activity = LocalContext.current.findComponentActivity()
    return remember(activity) { ViewModelProvider(activity)[MainViewModel::class.java] }
}

private fun Context.findComponentActivity(): ComponentActivity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    error("Aucune ComponentActivity dans le contexte")

    
    }
