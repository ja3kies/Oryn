package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.components.inkSecondary
import androidx.compose.ui.graphics.Color
import com.example.oryn.reader.ui.components.GlassChip
import com.example.oryn.reader.ui.components.GlassIconButton
import com.example.oryn.reader.ui.components.OrynIcons
import com.example.oryn.reader.ui.components.solidSurface
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oryn.reader.data.remote.GutendexClient
import com.example.oryn.reader.data.remote.OnlineBook
import com.example.oryn.reader.rememberMainViewModel
import com.example.oryn.reader.ui.components.CoverCache
import com.example.oryn.reader.ui.components.navBarClearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class LibrarySource(val name: String, val description: String, val url: String)

/** Livres récents : accès ouvert, licences libres, ou prêt en ligne. */
private val recentSources = listOf(
    LibrarySource("DOAB", "Plus de 88 000 livres académiques en accès ouvert", "https://www.doabooks.org"),
    LibrarySource("OAPEN", "Livres scientifiques récents, PDF libres", "https://library.oapen.org"),
    LibrarySource("Des livres en communs", "Essais, BD et fictions sous licence libre (Framasoft)", "https://deslivresencommuns.org"),
    LibrarySource("Open Library", "Millions de titres ; seuls les livres du domaine public sont téléchargeables", "https://openlibrary.org")
)

/** Classiques du domaine public, mis à jour régulièrement. */
private val classicSources = listOf(
    LibrarySource("Standard Ebooks", "Classiques soigneusement relus et mis en forme", "https://standardebooks.org/ebooks"),
    LibrarySource("Gallica (BnF)", "La bibliothèque numérique de la BnF", "https://gallica.bnf.fr"),
    LibrarySource("Wikisource", "Textes du domaine public en français", "https://fr.wikisource.org"),
    LibrarySource("Bibebook", "Classiques français gratuits", "https://www.bibebook.com"),
    LibrarySource("Feedbooks", "Domaine public, EPUB prêts à lire", "https://www.feedbooks.com/publicdomain"),
    LibrarySource("Ebooks libres et gratuits", "Des milliers de classiques francophones", "https://www.ebooksgratuits.com"),
    LibrarySource("Bibliothèque électronique du Québec", "Littérature francophone du domaine public", "https://beq.ebooksgratuits.com"),
    LibrarySource("Internet Archive", "Livres, revues et documents numérisés", "https://archive.org"),
    LibrarySource("Project Gutenberg", "Le site officiel, plus de 70 000 titres", "https://www.gutenberg.org")
)

private val languages = listOf("fr" to "Français", "en" to "English", "es" to "Español", "" to "Toutes")

private val gutenbergFile = Regex("gutenberg-(\\d+)\\.epub$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val viewModel = rememberMainViewModel()
    val library by viewModel.books.collectAsState()
    val downloading by viewModel.downloadingIds.collectAsState()
    val colors = MaterialTheme.colorScheme

    var query by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("fr") }
    var results by remember { mutableStateOf<List<OnlineBook>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openSource by remember { mutableStateOf<LibrarySource?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    // Livres Gutenberg déjà présents dans la bibliothèque
    val addedIds = remember(library) {
        library.orEmpty().mapNotNull {
            gutenbergFile.find(it.filePath)?.groupValues?.get(1)?.toLongOrNull()
        }.toSet()
    }

    // Recherche avec délai : on attend que l'utilisateur arrête de taper
    LaunchedEffect(query, language, retry, tab) {
        if (tab != 1) return@LaunchedEffect
        if (query.isNotBlank()) delay(450)
        loading = true
        error = null
        errorDetail = null
        try {
            val result = GutendexClient.search(query, language.ifBlank { null }, 1)
            results = result.books
            page = 1
            hasMore = result.hasMore
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            results = emptyList()
            hasMore = false
            error = tr("Impossible de joindre la bibliothèque. Vérifie ta connexion.")
            errorDetail = "${e.javaClass.simpleName}: ${e.message.orEmpty().take(120)}"
        } finally {
            loading = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = navBarClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("Explorer"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                GlassIconButton(OrynIcons.Plus, tr("Importer depuis l'appareil"), onClick = onImportClick)
            }
        }

        // Deux espaces : catalogues (navigateur intégré) / recherche de classiques
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(tr("Catalogues"), tab == 0, { tab = 0 })
                GlassChip(tr("Rechercher un classique"), tab == 1, { tab = 1 })
            }
        }

        if (tab == 0) {
            item { SectionTitle(tr("Récents · accès ouvert et licences libres")) }
            item { SourceRow(recentSources) { openSource = it } }
            item {
                Text(
                    tr("Les romans récents du commerce sont protégés (DRM) : ils ne peuvent pas être ajoutés à l'app."),
                    style = MaterialTheme.typography.bodySmall,
                    color = inkSecondary()
                )
            }
            item { SectionTitle(tr("Classiques · domaine public")) }
            item { SourceRow(classicSources) { openSource = it } }
        }

        if (tab == 1) {
            // Carte de recherche
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().solidSurface(RoundedCornerShape(26.dp)),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(OrynIcons.Compass, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(
                                    tr("Classiques libres de droits"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    tr("Project Gutenberg · domaine public"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = inkSecondary()
                                )
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            placeholder = { Text(tr("Titre ou auteur…")) },
                            leadingIcon = { Icon(OrynIcons.Search, null) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(OrynIcons.Close, contentDescription = tr("Effacer"))
                                        }
                                    }
                                    TextButton(onClick = { focusManager.clearFocus() }) {
                                        Text(tr("Chercher"))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            languages.forEach { (code, label) ->
                                GlassChip(label, language == code, { language = code })
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    if (query.isBlank()) tr("Les plus populaires") else tr("Résultats"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (error != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(error.orEmpty(), color = inkSecondary())
                        errorDetail?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = inkSecondary())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { retry++ }) { Text(tr("Réessayer")) }
                            OutlinedButton(onClick = {
                                openSource = LibrarySource(
                                    "Project Gutenberg", "",
                                    "https://www.gutenberg.org/ebooks/search/?sort_order=downloads"
                                )
                            }) { Text(tr("Parcourir Gutenberg")) }
                        }
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    Text(
                        tr("Aucun livre trouvé."),
                        color = inkSecondary(),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(results, key = { it.id }) { book ->
                    OnlineBookRow(
                        book = book,
                        added = book.id in addedIds,
                        downloading = book.id in downloading,
                        onDownload = { viewModel.addOnlineBook(book) }
                    )
                }
                if (hasMore) {
                    item {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    loadingMore = true
                                    try {
                                        val next = GutendexClient.search(query, language.ifBlank { null }, page + 1)
                                        results = results + next.books.filter { n -> results.none { it.id == n.id } }
                                        page += 1
                                        hasMore = next.hasMore
                                    } catch (_: Exception) {
                                        error = tr("Impossible de charger la suite.")
                                    } finally {
                                        loadingMore = false
                                    }
                                }
                            },
                            enabled = !loadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (loadingMore) tr("Chargement…") else tr("Voir plus")) }
                    }
                }
            }
        }
    }

    openSource?.let { source ->
        WebLibraryDialog(title = source.name, startUrl = source.url, onClose = { openSource = null })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceRow(sources: List<LibrarySource>, onOpen: (LibrarySource) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        sources.forEach { source ->
            Surface(
                onClick = { onOpen(source) },
                modifier = Modifier.width(188.dp).solidSurface(RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            source.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            OrynIcons.External,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        source.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = inkSecondary(),
                        minLines = 3,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineBookRow(
    book: OnlineBook,
    added: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().solidSurface(RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RemoteCover(
                url = book.coverUrl,
                modifier = Modifier
                    .width(48.dp)
                    .height(68.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = inkSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.language.isNotBlank()) {
                    Text(
                        book.language.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }
            when {
                added -> Icon(
                    OrynIcons.Check,
                    contentDescription = tr("Déjà dans ta bibliothèque"),
                    tint = colors.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                downloading -> CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(24.dp),
                    strokeWidth = 2.5.dp
                )
                else -> FilledTonalIconButton(onClick = onDownload) {
                    Icon(OrynIcons.Download, contentDescription = tr("Ajouter à ma bibliothèque"))
                }
            }
        }
    }
}

@Composable
private fun RemoteCover(url: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val bitmap by produceState<Bitmap?>(initialValue = url?.let { CoverCache.get(it) }, url) {
        if (url != null && value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { CoverCache.decodeBytes(GutendexClient.fetchBytes(url)) }.getOrNull()
            }?.also { CoverCache.put(url, it) }
        }
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = colors.primaryContainer) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    OrynIcons.BookOpen,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
