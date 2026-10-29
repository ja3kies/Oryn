package com.example.oryn.reader

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.ui.components.FloatingActionNavBar
import com.example.oryn.reader.ui.components.AmbientBackdrop
import com.example.oryn.reader.ui.components.systemAnimationsEnabled
import com.example.oryn.reader.ui.screen.BookmarksScreen
import com.example.oryn.reader.ui.screen.ExploreScreen
import com.example.oryn.reader.ui.screen.HomeScreen
import com.example.oryn.reader.ui.screen.ReaderScreen
import com.example.oryn.reader.ui.screen.SettingsScreen
import com.example.oryn.reader.ui.theme.OrynTheme
import com.example.oryn.reader.ui.theme.AppThemeMode

private val routeOrder = listOf("home", "explore", "bookmarks", "settings")

private val importMimeTypes = arrayOf(
    "application/epub+zip",
    "application/zip",
    "application/pdf",
    "text/plain",
    "application/octet-stream"
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val accentTheme by viewModel.accentTheme.collectAsState()
            val systemDarkMode = isSystemInDarkTheme()
            val motionEnabled = systemAnimationsEnabled(this@MainActivity)
            val darkMode = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDarkMode
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            var route by rememberSaveable { mutableStateOf("home") }
            var selectedBook by remember { mutableStateOf<BookEntity?>(null) }

            val booksState by viewModel.books.collectAsState()
            val books = booksState.orEmpty()
            val favorites by viewModel.favoriteBooks.collectAsState()

            val importLauncher = rememberLauncherForActivityResult(
                com.example.oryn.reader.data.importer.OpenBooksContract()
            ) { uris -> viewModel.importBooks(uris) }

            val importFavoriteLauncher = rememberLauncherForActivityResult(
                com.example.oryn.reader.data.importer.OpenBooksContract()
            ) { uris -> viewModel.importBooks(uris, favorite = true) }

            OrynTheme(
                darkTheme = darkMode,
                accentTheme = accentTheme
            ) {
                BackHandler(enabled = selectedBook != null) {
                    selectedBook = null
                }

                BackHandler(enabled = selectedBook == null && route != "home") {
                    route = "home"
                }

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (selectedBook != null) {
                        ReaderScreen(
                            book = selectedBook!!,
                            currentAccent = accentTheme,
                            onAccentChange = viewModel::setAccentTheme,
                            onBack = { selectedBook = null },
                            onUpdateProgress = viewModel::updateBookProgress
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Keep the backdrop and screen content in one source-ready subtree.
                            // A future backdrop effect can capture this container once, while the
                            // navigation bar remains a separate overlay drawn above it.
                            Box(modifier = Modifier.fillMaxSize()) {
                                AmbientBackdrop(modifier = Modifier.fillMaxSize())
                                val viewModel = rememberMainViewModel()

                    AnimatedContent(
                                    targetState = route,
                                    transitionSpec = {
                                        if (!motionEnabled) {
                                            EnterTransition.None togetherWith ExitTransition.None
                                        } else {
                                            val direction =
                                                if (routeOrder.indexOf(targetState) > routeOrder.indexOf(initialState)) 1 else -1
                                            (slideInHorizontally(animationSpec = tween(260)) { width -> direction * width / 7 } +
                                                fadeIn(animationSpec = tween(220)))
                                                .togetherWith(
                                                    slideOutHorizontally(animationSpec = tween(220)) { width -> -direction * width / 7 } +
                                                        fadeOut(animationSpec = tween(120))
                                                )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "screenTransition"
                                ) { target ->
                                    when (target) {
                                        "home" -> HomeScreen(
                                            books = books,
                                            isLoading = booksState == null,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onBookClick = { selectedBook = it },
                                            onImportClick = { importLauncher.launch(importMimeTypes) },
                                            onDeleteBooks = { books -> viewModel.deleteBooks(books) }
                                        )
                                        "explore" -> ExploreScreen(
                                            onImportClick = { importLauncher.launch(importMimeTypes) }
                                        )
                                        "bookmarks" -> BookmarksScreen(
                                            favorites = favorites,
                                            isLoading = booksState == null,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onBookClick = { selectedBook = it },
                                            onImportClick = { importFavoriteLauncher.launch(importMimeTypes) }
                                        )
                                        "settings" -> SettingsScreen(
                                            themeMode = themeMode,
                                            onThemeModeChange = viewModel::setThemeMode,
                                            currentAccent = accentTheme,
                                            onAccentChange = viewModel::setAccentTheme
                                        )
                                    }
                                }
                            }

                            FloatingActionNavBar(
                                selectedRoute = route,
                                onRouteSelected = { route = it },
                                onAddClick = {
                                    if (route == "bookmarks") importFavoriteLauncher.launch(importMimeTypes)
                                    else importLauncher.launch(importMimeTypes)
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uris = linkedSetOf<Uri>()

        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(uris::add)

            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let(uris::add)
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i).uri?.let(uris::add)
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list?.forEach(uris::add)
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i).uri?.let(uris::add)
                    }
                }
            }
        }

        if (uris.isNotEmpty()) viewModel.importSharedBooks(uris.toList())
    }

}
