package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.data.importer.PdfReflowBuilder
import com.example.oryn.reader.data.importer.PdfReflowBook
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.ui.theme.linkColor
import android.app.Activity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.oryn.reader.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.oryn.reader.R
import com.example.oryn.reader.data.importer.BookLoadResult
import com.example.oryn.reader.data.importer.BookTextLoader
import com.example.oryn.reader.data.importer.RichBook
import com.example.oryn.reader.data.importer.PDF_STRIDE
import com.example.oryn.reader.data.importer.TocEntry
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.data.local.preferences.ReaderFontFamily
import com.example.oryn.reader.data.local.preferences.ReaderMarginSize
import com.example.oryn.reader.data.local.preferences.ReaderPreferencesRepository
import com.example.oryn.reader.data.local.preferences.ReaderScrollMode
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import com.example.oryn.reader.data.local.preferences.ReaderTextAlign
import com.example.oryn.reader.data.local.OrynDatabase
import com.example.oryn.reader.data.local.entity.AnnotationEntity
import com.example.oryn.reader.ui.components.OrynIcons
import com.example.oryn.reader.ui.components.ReaderChromeColor
import com.example.oryn.reader.ui.components.ReaderControlsSheet
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.pageColor
import com.example.oryn.reader.ui.theme.textColor
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

val ReaderOpenDyslexicFont: FontFamily? = runCatching {
    FontFamily(Font(R.font.opendyslexic_regular, FontWeight.Normal))
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: BookEntity,
    currentAccent: AppAccentTheme,
    onAccentChange: (AppAccentTheme) -> Unit,
    onBack: () -> Unit,
    onUpdateProgress: (BookEntity, Float) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val preferencesRepo = remember { ReaderPreferencesRepository(context) }

    // null tant que les réglages ne sont pas lus : évite le flash du thème par défaut
    val loadedSettings by preferencesRepo.readerSettingsFlow.collectAsState(initial = null)
    val ready = loadedSettings != null
    val settings = loadedSettings ?: ReaderSettings()

    // Progression « vivante » : mise à jour immédiate (la fiche de lecture ne dépend plus de la base de données)
    var liveProgress by remember(book.id) { mutableStateOf(book.progress) }

    var loadResult by remember { mutableStateOf<BookLoadResult?>(null) }
    var uiVisible by remember { mutableStateOf(false) }
    var annotationsOpen by remember { mutableStateOf(false) }
    var jumpRequest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tocOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val annotationDao = remember { OrynDatabase.getDatabase(context).annotationDao() }

    // Autorisation expirée (« Permission Denial ») : l'utilisateur re-sélectionne le fichier, on la rétablit
    var reloadKey by remember { mutableStateOf(0) }
    var pathOverride by remember(book.id) { mutableStateOf<String?>(null) }
    val regrant = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.recoverCatching {
                context.contentResolver.takePersistableUriPermission(picked, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val newPath = picked.toString()
            if (newPath != book.filePath) {
                pathOverride = newPath
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { OrynDatabase.getDatabase(context).bookDao().updateBook(book.copy(filePath = newPath)) }
                }
            }
            loadResult = null
            reloadKey++
        }
    }

    LaunchedEffect(book.filePath, reloadKey) {
        loadResult = BookTextLoader(context).loadBook(pathOverride ?: book.filePath, book.format)
    }

    // Marque le livre comme « lu récemment » sans toucher à la progression
    LaunchedEffect(book.id) { onUpdateProgress(book, book.progress) }

    // Ferme le PDF en quittant le lecteur
    DisposableEffect(book.filePath) {
        onDispose { (loadResult as? BookLoadResult.Success)?.pdf?.close() }
    }

    // Luminosité manuelle, rétablie en quittant le lecteur
    LaunchedEffect(settings.brightness, settings.autoBrightness, ready) {
        val window = activity?.window ?: return@LaunchedEffect
        val params = window.attributes
        params.screenBrightness = if (settings.autoBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            settings.brightness.coerceIn(0.01f, 1f)
        }
        window.attributes = params
    }

    LaunchedEffect(settings.hideStatusBar, uiVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        if (settings.hideStatusBar && !uiVisible) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    // L'écran reste allumé pendant la lecture
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(activity, view) {
        onDispose {
            activity?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    val readerRich: RichBook? = (loadResult as? BookLoadResult.Success)?.rich
    val readerPdf = (loadResult as? BookLoadResult.Success)?.pdf
    val pdfToc by produceState(emptyList<TocEntry>(), readerPdf) { value = readerPdf?.text?.outline().orEmpty() }
    val pdfPageCount = readerPdf?.pageCount ?: 0
    val pdfCurrentPage = if (pdfPageCount > 0) (Math.round(liveProgress * pdfPageCount) - 1).coerceIn(0, pdfPageCount - 1) else 0

    // Mode « texte » d'un PDF : le texte est remis en forme (titres, paragraphes) puis lu comme un livre numérique
    var reflowProgress by remember { mutableStateOf(0f) }
    var reflowFailed by remember { mutableStateOf(false) }
    var switchPage by remember { mutableStateOf(-1) }
    val wantReflow = settings.pdfReflow && readerPdf != null
    val reflowBook by produceState<PdfReflowBook?>(null, readerPdf, wantReflow) {
        value = null
        reflowFailed = false
        reflowProgress = 0f
        if (wantReflow) {
            value = try {
                PdfReflowBuilder.build(
                    context, android.net.Uri.parse(pathOverride ?: book.filePath), readerPdf.text, readerPdf.pageCount
                ) { reflowProgress = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                reflowFailed = true
                null
            }
        }
    }
    val useReflow = wantReflow && reflowBook?.scanned == false
    val reflowBuilding = wantReflow && reflowBook == null && !reflowFailed
    val reflowUnavailable = wantReflow && !useReflow && !reflowBuilding
    val effectiveRich: RichBook? = readerRich ?: if (useReflow) reflowBook?.rich else null
    // Les annotations du mode texte (positions en caractères) sont rangées à part de celles des pages PDF
    val annotBookId = if (useReflow) -book.id else book.id
    val annotationsFlow = remember(annotBookId) { annotationDao.observe(annotBookId) }
    val annotations by annotationsFlow.collectAsState(initial = emptyList())
    LaunchedEffect(reflowUnavailable) {
        if (reflowUnavailable) {
            android.widget.Toast.makeText(
                context, tr("Ce PDF ne contient pas de texte exploitable : affichage en pages."), android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    val backgroundColor = if (ready) settings.themePreset.pageColor else MaterialTheme.colorScheme.background
    val foregroundColor = if (ready) settings.themePreset.textColor else MaterialTheme.colorScheme.onBackground

    val fontFamily = when (settings.fontFamily) {
        ReaderFontFamily.SYSTEM -> FontFamily.Default
        ReaderFontFamily.SERIF -> FontFamily.Serif
        ReaderFontFamily.SANS_SERIF -> FontFamily.SansSerif
        ReaderFontFamily.DYSLEXIC -> ReaderOpenDyslexicFont ?: FontFamily.Default
    }

    val horizontalPadding = when (settings.marginSize) {
        ReaderMarginSize.NARROW -> 16.dp
        ReaderMarginSize.NORMAL -> 36.dp
        ReaderMarginSize.WIDE -> 56.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (ready) {
            when (val result = loadResult) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = foregroundColor)
                }

                is BookLoadResult.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val denied = result.message.contains("Permission", ignoreCase = true) ||
                        result.message.contains("ACTION_OPEN_DOCUMENT")
                    androidx.compose.foundation.layout.Column(
                        Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (denied) tr("Android n'autorise plus l'accès à ce fichier (autorisation expirée ou jamais conservée).")
                            else result.message,
                            color = foregroundColor,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        if (denied) {
                            Text(
                                tr("Re-sélectionne le même fichier pour rétablir l'accès : ta progression et tes annotations sont conservées."),
                                color = foregroundColor.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            androidx.compose.material3.Button(onClick = { regrant.launch(arrayOf("*/*")) }) {
                                Text(tr("Réautoriser le fichier"))
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = onBack) { Text(tr("Retour")) }
                    }
                }

                is BookLoadResult.Success -> {
                    val pdf = result.pdf
                    val toggleUi = { uiVisible = !uiVisible }
                    val saveProgress: (Float) -> Unit = { fraction ->
                        liveProgress = fraction.coerceIn(0f, 1f)
                        onUpdateProgress(book, liveProgress)
                    }

                    val htmlPage = result.html
                    if (htmlPage != null) {
                        val linkColor = settings.themePreset.linkColor
                        HtmlReader(
                            html = htmlPage,
                            initialProgress = book.progress,
                            pageColor = backgroundColor,
                            textColor = foregroundColor,
                            linkColor = linkColor,
                            themeName = settings.themePreset.name,
                            readerSettings = settings,
                            onProgress = saveProgress,
                            onCenterTap = toggleUi
                        )
                    } else if (pdf != null && reflowBuilding) {
                        PdfReflowBuilding(
                            progress = reflowProgress,
                            color = foregroundColor,
                            onCancel = { scope.launch { preferencesRepo.updateSettings { it.copy(pdfReflow = false) } } }
                        )
                    } else if (pdf != null && !useReflow) {
                        PdfReader(
                            pdf = pdf,
                            bookId = book.id,
                            initialProgress = liveProgress,
                            settings = settings,
                            foregroundColor = foregroundColor,
                            backgroundColor = backgroundColor,
                            horizontalPadding = horizontalPadding,
                            onProgress = saveProgress,
                            onCenterTap = toggleUi,
                            annotations = annotations,
                            onAddAnnotation = { a -> scope.launch { annotationDao.insert(a) } },
                            onUpdateAnnotation = { a -> scope.launch { annotationDao.update(a) } },
                            onDeleteAnnotation = { a -> scope.launch { annotationDao.delete(a) } },
                            jumpRequest = jumpRequest,
                            onJumpHandled = { jumpRequest = null }
                        )
                    } else {
                        // Le texte est remis en page à la taille réelle de l'écran : plus de bas de page coupé
                        val rich = remember(result, reflowBook, useReflow) {
                            (if (useReflow) reflowBook?.rich else null)
                                ?: result.rich ?: RichBook(result.pages.joinToString("\n\n"), emptyList(), emptyList())
                        }
                        // Couleur des liens adaptée au fond de chaque thème de lecture
                        val linkColor = settings.themePreset.linkColor
                        ReflowReader(
                            bookId = annotBookId,
                            book = rich,
                            linkColor = linkColor,
                            initialProgress = if (useReflow) {
                                if (switchPage >= 0) reflowBook?.fractionOfPage(switchPage) ?: liveProgress else liveProgress
                            } else book.progress,
                            settings = settings,
                            fontFamily = fontFamily,
                            foregroundColor = foregroundColor,
                            backgroundColor = backgroundColor,
                            horizontalPadding = horizontalPadding,
                            onProgress = saveProgress,
                            onCenterTap = toggleUi,
                            annotations = annotations,
                            onAddAnnotation = { a -> scope.launch { annotationDao.insert(a) } },
                            onUpdateAnnotation = { a -> scope.launch { annotationDao.update(a) } },
                            onDeleteAnnotation = { a -> scope.launch { annotationDao.delete(a) } },
                            jumpRequest = jumpRequest,
                            onJumpHandled = { jumpRequest = null }
                        )
                    }
                }
            }
        }

        // Barre du haut
        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            // Même fond (légèrement transparent) que la fiche de réglages
            val chromeText = Color(0xFFF2F2EF)
            Surface(
                color = ReaderChromeColor,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                shadowElevation = 8.dp
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(book.title, color = chromeText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                book.author,
                                color = Color(0xFFB6B6B0),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Retour"), tint = chromeText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }

        if (tocOpen && (effectiveRich != null || readerPdf != null)) {
            TocDialog(
                entries = effectiveRich?.toc ?: pdfToc,
                currentOffset = if (effectiveRich != null) (liveProgress * effectiveRich.text.length).toInt() else pdfCurrentPage * PDF_STRIDE,
                onJump = { offset ->
                    tocOpen = false
                    uiVisible = false
                    jumpRequest = offset to 0
                },
                onDismiss = { tocOpen = false }
            )
        }
        if (searchOpen && effectiveRich != null) {
            SearchDialog(
                text = effectiveRich.text,
                onJump = { offset, length ->
                    searchOpen = false
                    uiVisible = false
                    jumpRequest = offset to length
                },
                onDismiss = { searchOpen = false }
            )
        }
        if (annotationsOpen) {
            AnnotationsDialog(
                title = book.title,
                author = book.author,
                annotations = annotations,
                onJump = { offset ->
                    annotationsOpen = false
                    uiVisible = false
                    jumpRequest = offset to 0
                },
                onDelete = { a -> scope.launch { annotationDao.delete(a) } },
                onDismiss = { annotationsOpen = false }
            )
        }

        // Panneau de réglages flottant
        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            val rb = effectiveRich
            val toc = rb?.toc ?: pdfToc
            val isPdf = readerPdf != null && !useReflow
            val position = if (rb != null) (liveProgress * rb.text.length).toInt() else pdfCurrentPage * PDF_STRIDE
            val chapterIndex = toc.indexOfLast { it.offset <= position }
            val chapterTitle = toc.getOrNull(chapterIndex)?.title?.takeIf { it.isNotBlank() } ?: book.title
            // Estimation : ~1 000 caractères par minute de lecture (EPUB / TXT) ; page courante pour un PDF
            val remaining = when {
                rb != null -> (((1f - liveProgress) * rb.text.length) / 1000f).toInt().let { m ->
                    when {
                        m < 1 -> tr("moins d'une minute restante")
                        m < 60 -> trf("≈ %s min restantes", m)
                        else -> trf("≈ %s h %s restantes", m / 60, "%02d".format(m % 60))
                    }
                }
                isPdf -> trf("page %s / %s", pdfCurrentPage + 1, pdfPageCount)
                else -> null
            }
            fun jump(offset: Int) {
                val o: Int
                if (rb != null) {
                    val len = rb.text.length
                    o = offset.coerceIn(0, (len - 1).coerceAtLeast(0))
                    liveProgress = o.toFloat() / len
                } else if (isPdf) {
                    o = offset
                    liveProgress = ((o / PDF_STRIDE + 1f) / pdfPageCount).coerceIn(0f, 1f)
                } else return
                uiVisible = false
                jumpRequest = o to 0
            }
            ReaderControlsSheet(
                settings = settings,
                onSettingsChange = { updater ->
                    scope.launch { preferencesRepo.updateSettings(updater) }
                },
                currentChapterTitle = chapterTitle,
                pdfReflow = settings.pdfReflow,
                onTogglePdfReflow = if (readerPdf != null) {
                    {
                        val b = reflowBook
                        if (useReflow && b != null) {
                            // texte → pages : on retombe sur la page qui contient la position courante
                            val page = b.pageAt((liveProgress * b.rich.text.length).toInt())
                            liveProgress = ((page + 1f) / pdfPageCount).coerceIn(0f, 1f)
                            switchPage = -1
                        } else {
                            switchPage = pdfCurrentPage
                        }
                        scope.launch { preferencesRepo.updateSettings { it.copy(pdfReflow = !it.pdfReflow) } }
                    }
                } else null,
                currentAccent = currentAccent,
                onAccentChange = onAccentChange,
                onDismiss = { uiVisible = false },
                onOpenToc = toc.takeIf { it.isNotEmpty() }?.let { { uiVisible = false; tocOpen = true } },
                onOpenSearch = rb?.let { { uiVisible = false; searchOpen = true } },
                onOpenAnnotations = if (rb != null || isPdf) { { uiVisible = false; annotationsOpen = true } } else null,
                readingProgress = liveProgress,
                remainingLabel = remaining,
                onSeek = when {
                    rb != null -> ({ f: Float -> jump((f * rb.text.length).toInt()) })
                    isPdf -> ({ f: Float -> jump((f * pdfPageCount).toInt().coerceIn(0, pdfPageCount - 1) * PDF_STRIDE) })
                    else -> null
                },
                onPrevChapter = toc.takeIf { chapterIndex >= 0 }?.let {
                    {
                        // 1er toucher : début du chapitre courant ; si on y est déjà, chapitre précédent
                        val cur = toc[chapterIndex].offset
                        val target = if (position - cur > 200 || chapterIndex == 0) cur else toc[chapterIndex - 1].offset
                        jump(target)
                    }
                },
                onNextChapter = toc.takeIf { chapterIndex + 1 < it.size }?.let { { jump(toc[chapterIndex + 1].offset) } }
            )
        }
    }
}
