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
import com.example.oryn.reader.data.repository.LibraryMutationCoordinator
import com.example.oryn.reader.data.repository.discardUnreferencedOwnedFiles
import com.example.oryn.reader.data.repository.ownedBookFile
import com.example.oryn.reader.data.repository.BookRepository
import com.example.oryn.reader.ui.components.CoverCache
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.AppThemeMode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

/** Import state is only successful once the book is committed to Room. */
enum class WebImportPhase {
    DOWNLOADING, IMPORTING, SUCCEEDED, FAILED;
    val inProgress: Boolean get() = this == DOWNLOADING || this == IMPORTING
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val MAX_SHARED_IMPORT_BYTES = 100L * 1024 * 1024
    }

    private val repository = BookRepository(
        OrynDatabase.getDatabase(application).bookDao()
    )
    private val libraryMutations = LibraryMutationCoordinator()

    private val metadataReader = BookMetadataReader(application)
    private val coverStore = CoverStore(application)
    private val prefsRepository = UserPreferencesRepository(application)
    
    fun deleteBooks(books: List<BookEntity>) {
        if (books.isEmpty()) return
        launchLibraryMutation {
            val db = OrynDatabase.getDatabase(getApplication<Application>())
            db.bookDao().deleteBooksWithAnnotations(books.map { it.id }.distinct())
            books.forEach { deleteOwnedBookFile(it) }
        }
    }


    private suspend fun deleteOwnedBookFile(book: BookEntity) {
        if (!book.filePath.startsWith("file:") || repository.exists(book.filePath)) return
        val file = Uri.parse(book.filePath).path?.let(::File) ?: return
        ownedBookFile(file, File(getApplication<Application>().filesDir, "books"))?.delete()
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

    private val _webImportPhases = MutableStateFlow<Map<String, WebImportPhase>>(emptyMap())
    val webImportPhases: StateFlow<Map<String, WebImportPhase>> = _webImportPhases.asStateFlow()

    init {
        viewModelScope.launch { repository.deleteSamples() }
        launchLibraryMutation { backfillCovers() }
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
                        // A different book can still reference this shared cover.
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

        launchLibraryMutation {
            val copied = mutableListOf<Uri>()
            val created = mutableListOf<File>()
            var failed = 0
            val directory = File(app.filesDir, "books/shared").apply { mkdirs() }

            try {
                for (uri in uris.distinct()) {
                    currentCoroutineContext().ensureActive()
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
                                    currentCoroutineContext().ensureActive()
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
                        } else if (temp.renameTo(destination)) {
                            created += destination
                        } else if (destination.createNewFile()) {
                            // Only write a file atomically reserved by this import; never replace an existing file.
                            created += destination
                            temp.inputStream().use { input ->
                                destination.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            temp.delete()
                        }

                        copied += Uri.fromFile(destination)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed++
                    } finally {
                        temp?.takeIf { it.exists() }?.delete()
                    }
                }

                if (copied.isNotEmpty()) importBooksInternal(copied, favorite = false)
                if (failed > 0) toast(trf("Partage : %s fichier(s) inaccessible(s)", failed))
            } finally {
                // This block already owns the mutation mutex: do not acquire it again during cleanup.
                withContext(NonCancellable) {
                    discardUnreferencedOwnedFiles(created, File(app.filesDir, "books")) {
                        repository.exists(Uri.fromFile(it).toString())
                    }
                }
            }
        }
    }

    fun importBooks(uris: List<Uri>, favorite: Boolean = false) {
        if (uris.isEmpty()) return
        launchLibraryMutation { importBooksInternal(uris, favorite) }
    }

    /** Called while holding the library mutation lock, including after shared-file publication. */
    private suspend fun importBooksInternal(uris: List<Uri>, favorite: Boolean) {
        val app = getApplication<Application>()
        var added = 0
        var duplicates = 0
        var unsupported = 0
        var failed = 0

        for (uri in uris) {
            currentCoroutineContext().ensureActive()
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
                val inserted = repository.addBook(
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
                if (inserted) added++ else duplicates++
            } catch (_: SecurityException) {
                failed++
            } catch (cancelled: CancellationException) {
                throw cancelled
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

    /** Télécharge un EPUB du domaine public et l'ajoute à la bibliothèque. */
    fun addOnlineBook(book: OnlineBook) {
        if (book.id in _downloadingIds.value) return
        _downloadingIds.update { it + book.id }
        val app = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            val directory = File(app.filesDir, "books").apply { mkdirs() }
            val file = File(directory, "gutenberg-${book.id}.epub")
            var publishedFile = false
            var staged: File? = null
            try {
                if (repository.exists(Uri.fromFile(file).toString())) {
                    toast(tr("Déjà dans ta bibliothèque"))
                    return@launch
                }
                val incoming = File.createTempFile("gutenberg-${book.id}-", ".tmp", directory)
                staged = incoming
                libraryMutations.stageThenMutate(
                    stage = {
                        GutendexClient.download(book.epubUrl, incoming)
                        // The staging file is private to this transfer and has no Room references.
                        metadataReader.extractEpubCover(Uri.fromFile(incoming))
                            ?: book.coverUrl?.let { runCatching { GutendexClient.fetchBytes(it) }.getOrNull() }
                    },
                    publish = { coverBytes ->
                        val uri = Uri.fromFile(file)
                        // Recheck after the transfer: another import may have published this book.
                        if (repository.exists(uri.toString())) {
                            toast(tr("Déjà dans ta bibliothèque"))
                        } else {
                            if (file.exists() || !incoming.renameTo(file)) throw java.io.IOException("Book publication failed")
                            publishedFile = true
                            if (metadataReader.read(uri)?.format != "EPUB") throw java.io.IOException("Invalid EPUB download")
                            val hash = computeContentHash(uri)
                            val existingCover = hash?.let(coverStore::findByHash)
                            val cover = if (existingCover == null) coverBytes?.let { coverStore.saveBytes(it, hash) } else null
                            repository.addBook(
                                BookEntity(
                                    title = book.title, author = book.author, filePath = uri.toString(), format = "EPUB",
                                    coverPath = existingCover ?: cover ?: "", contentHash = hash
                                )
                            )
                            toast(trf("« %s » ajouté", book.title))
                        }
                    }
                )
            } catch (cancelled: CancellationException) {
                if (publishedFile) deleteUnreferencedDownload(file)
                throw cancelled
            } catch (_: Exception) {
                if (publishedFile) deleteUnreferencedDownload(file)
                toast(tr("Téléchargement impossible. Vérifie ta connexion."))
            } finally {
                staged?.delete()
                _downloadingIds.update { it - book.id }
            }
        }
    }

    /** Fichier trouvé dans le navigateur intégré (EPUB, PDF, TXT) : téléchargé puis ajouté. */
    fun addFromWeb(url: String, fileName: String, userAgent: String?, cookie: String?, referer: String?) {
        if (url in _webDownloads.value) return
        _webDownloads.update { it + url }
        _webImportPhases.update { it + (url to WebImportPhase.DOWNLOADING) }
        val app = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            var staged: File? = null
            var target: File? = null
            try {
                val dir = File(app.filesDir, "books").apply { mkdirs() }
                val safeName = fileName.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).ifBlank { "livre.epub" }
                val incoming = File.createTempFile("incoming-web-", ".tmp", dir)
                staged = incoming
                val headers = buildMap {
                    userAgent?.let { put("User-Agent", it) }
                    cookie?.let { put("Cookie", it) }
                    referer?.let { put("Referer", it) }
                }
                libraryMutations.stageThenMutate(
                    stage = { GutendexClient.downloadFromWeb(url, incoming, headers) },
                    publish = {
                        _webImportPhases.update { it + (url to WebImportPhase.IMPORTING) }
                        val file = uniqueFile(dir, safeName)
                        if (!incoming.renameTo(file)) throw java.io.IOException("Book publication failed")
                        target = file
                        val uri = Uri.fromFile(file)
                        val meta = metadataReader.read(uri)
                        if (meta == null) {
                            file.delete()
                            _webImportPhases.update { it + (url to WebImportPhase.FAILED) }
                            toast(tr("Format non pris en charge (EPUB, PDF ou TXT)."))
                        } else {
                            val hash = computeContentHash(uri)
                            val existingCover = hash?.let(coverStore::findByHash)
                            val embeddedCover = if (existingCover == null)
                                meta.coverBytes?.let { coverStore.saveBytes(it, hash) } else null
                            repository.addBook(
                                BookEntity(
                                    title = meta.title, author = meta.author, filePath = uri.toString(), format = meta.format,
                                    coverPath = existingCover ?: embeddedCover ?: if (meta.format == "EPUB") "" else null,
                                    contentHash = hash
                                )
                            )
                            _webImportPhases.update { it + (url to WebImportPhase.SUCCEEDED) }
                            toast(trf("« %s » ajouté à ta bibliothèque", meta.title))
                        }
                    }
                )
            } catch (cancelled: CancellationException) {
                target?.let { deleteUnreferencedDownload(it) }
                _webImportPhases.update { it + (url to WebImportPhase.FAILED) }
                throw cancelled
            } catch (_: Exception) {
                target?.let { deleteUnreferencedDownload(it) }
                _webImportPhases.update { it + (url to WebImportPhase.FAILED) }
                toast(tr("Téléchargement impossible. Le site demande peut-être une connexion."))
            } finally {
                staged?.delete()
                _webDownloads.update { it - url }
            }
        }
    }

    /** Cancellation can arrive after Room committed: never remove a file that a row now references. */
    private suspend fun deleteUnreferencedDownload(file: File) = withContext(NonCancellable) {
        libraryMutations.mutate {
            if (!repository.exists(Uri.fromFile(file).toString())) {
                ownedBookFile(file, File(getApplication<Application>().filesDir, "books"))?.delete()
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
        launchLibraryMutation {
            val hash = book.contentHash ?: computeContentHash(Uri.parse(book.filePath))
            val oldPath = book.coverPath
            // Invalide le cache AVANT d'écraser : même chemin, bitmap potentiellement stale
            if (!oldPath.isNullOrBlank()) CoverCache.invalidate(oldPath)
            val path = coverStore.saveUri(imageUri, hash)
            if (path == null) {
                toast(tr("Image illisible"))
                return@launchLibraryMutation
            }
            // Invalide aussi le nouveau chemin (hash-based), au cas où il existe déjà
            CoverCache.invalidate(path)
            repository.updateCoverAndHash(book.id, path, hash)
            if (!oldPath.isNullOrBlank() && oldPath != path && !repository.coverIsReferenced(oldPath)) {
                coverStore.delete(oldPath)
            }
            toast(tr("Couverture enregistrée"))
        }
    }

    /** Titre et auteur corrigés par l'utilisateur (le fichier EPUB n'est pas touché). */
    fun updateBookInfo(book: BookEntity, title: String, author: String) {
        val cleanTitle = title.trim().ifBlank { book.title }
        val cleanAuthor = author.trim().ifBlank { "Auteur inconnu" }
        launchLibraryMutation { repository.updateInfo(book.id, cleanTitle, cleanAuthor) }
    }

    fun removeCover(book: BookEntity) {
        launchLibraryMutation {
            repository.updateCover(book.id, "")
            book.coverPath?.takeIf { it.isNotBlank() && !repository.coverIsReferenced(it) }
                ?.let(coverStore::delete)
        }
    }

    fun updateBookProgress(book: BookEntity, progress: Float) {
        viewModelScope.launch {
            repository.updateProgress(book, progress.coerceIn(0f, 1f), System.currentTimeMillis())
        }
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch { repository.toggleFavorite(book.id) }
    }

    fun deleteBook(book: BookEntity) {
        launchLibraryMutation {
            val db = OrynDatabase.getDatabase(getApplication<Application>())
            db.bookDao().deleteBooksWithAnnotations(listOf(book.id))
            // La couverture est conservée pour une éventuelle réimportation.
            deleteOwnedBookFile(book)
        }
    }

    /** Serializes filesystem publication/reclamation with the Room references to those files. */
    private fun launchLibraryMutation(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        libraryMutations.mutate(block)
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
