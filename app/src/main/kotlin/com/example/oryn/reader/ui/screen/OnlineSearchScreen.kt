package com.example.oryn.reader.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.remote.AnnasArchiveSource
import com.example.oryn.reader.data.remote.AnnasBook
import com.example.oryn.reader.ui.components.OrynIcons
import com.example.oryn.reader.ui.components.inkSecondary
import kotlinx.coroutines.launch
import java.io.File

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Success(val books: List<AnnasBook>) : SearchState
    data class Error(val message: String) : SearchState
}

@Composable
internal fun OnlineSearchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val downloadStates = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    fun launchSearch() {
        if (query.isBlank()) return
        state = SearchState.Loading
        scope.launch {
            try {
                val results = AnnasArchiveSource.search(query)
                state = if (results.isEmpty()) {
                    SearchState.Error("Aucun résultat. Le site est peut-être bloqué ou la recherche vide.")
                } else SearchState.Success(results)
            } catch (e: Exception) {
                state = SearchState.Error("Erreur réseau : ${e.message ?: "inconnue"}")
            }
        }
    }

    fun downloadBook(book: AnnasBook) {
        if (downloadStates[book.md5] == true) return
        downloadStates[book.md5] = true
        scope.launch {
            try {
                val links = AnnasArchiveSource.getDownloadLinks(book.md5)
                if (links.isEmpty()) {
                    Toast.makeText(context, "Aucun lien de téléchargement disponible.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val extension = book.format.lowercase().filter { it.isLetterOrDigit() }.take(5)
                    .takeIf { it in setOf("epub", "pdf") } ?: "epub"
                val file = File.createTempFile("oryn-archive-", ".$extension", context.cacheDir)
                var ok = false
                for (url in links) {
                    ok = AnnasArchiveSource.downloadFile(url, file)
                    if (ok) break
                }
                if (!ok) file.delete()
                if (ok) {
                    Toast.makeText(context, "Téléchargé : ${file.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Échec du téléchargement (liens expirés).", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur : ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                downloadStates[book.md5] = false
            }
        }
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(OrynIcons.ArrowLeft, "Retour", tint = colors.onBackground, modifier = Modifier.size(24.dp))
            }
            Text(
                "Catalogue en ligne",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Rechercher un livre (Anna's Archive)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(28.dp)
            )
            TextButton(onClick = { launchSearch() }, enabled = query.isNotBlank()) {
                Text("Chercher", fontWeight = FontWeight.Bold)
            }
        }

        when (val current = state) {
            is SearchState.Idle -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tape une recherche pour interroger Anna's Archive.", style = MaterialTheme.typography.bodyLarge, color = inkSecondary())
                }
            }
            is SearchState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }
            is SearchState.Error -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(current.message, color = colors.error, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { launchSearch() }) { Text("Réessayer", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            is SearchState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(current.books, key = { it.md5 }) { book ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { downloadBook(book) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = inkSecondary(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (downloadStates[book.md5] == true) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
                            } else {
                                Box(
                                    Modifier.clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(book.format, style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
