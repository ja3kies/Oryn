package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.data.importer.PdfReflowBuilder
import com.example.oryn.reader.data.importer.PdfReflowBook
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.ui.theme.linkColor
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import com.example.oryn.reader.ui.components.ReaderControlsState
import com.example.oryn.reader.ui.components.ReaderControlsEvent
import com.example.oryn.reader.ui.components.ReaderControlsBackHandler
import com.example.oryn.reader.ui.components.ReaderCompactControls
import com.example.oryn.reader.ui.components.ReaderSurfaceTapBackdrop
import com.example.oryn.reader.ui.components.ReaderSheetFocus
import com.example.oryn.reader.ui.components.readerChromeMetrics
import com.example.oryn.reader.ui.components.readerChapterProgress
import com.example.oryn.reader.ui.components.transition
import com.example.oryn.reader.ui.components.readerControlsKeyModifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import com.example.oryn.reader.data.local.OrynDatabase
import com.example.oryn.reader.ui.components.ReaderControlsSheet
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.pageColor
import com.example.oryn.reader.ui.theme.textColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

val ReaderOpenDyslexicFont: FontFamily? = runCatching {
    FontFamily(Font(R.font.opendyslexic_regular, FontWeight.Normal))
}.getOrNull()

private enum class ReaderTool { CONTENTS, SEARCH, ANNOTATIONS, TIMER }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    book: BookEntity,
    currentAccent: AppAccentTheme,
    onAccentChange: (AppAccentTheme) -> Unit,
    onBack: () -> Unit,
    onUpdateProgress: (BookEntity, Float) -> Unit,
    onToggleBookmark: (BookEntity) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val preferencesRepo = remember { ReaderPreferencesRepository(context) }

    // null tant que les réglages ne sont pas lus : évite le flash du thème par défaut
    val loadedSettings by preferencesRepo.readerSettingsFlow.collectAsState(initial = null)
    val ready = loadedSettings != null
    // Apply each reader control to Compose immediately. DataStore remains the
    // source of truth once it acknowledges the latest value.
    var optimisticSettings by remember(book.id) { mutableStateOf<ReaderSettings?>(null) }
    val settings = optimisticSettings ?: loadedSettings ?: ReaderSettings()
    LaunchedEffect(loadedSettings, optimisticSettings) {
        if (optimisticSettings != null && loadedSettings == optimisticSettings) {
            optimisticSettings = null
        }
    }
    fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val updated = transform(settings)
        optimisticSettings = updated
        scope.launch { preferencesRepo.updateSettings { updated } }
    }

    // Progression « vivante » : mise à jour immédiate (la fiche de lecture ne dépend plus de la base de données)
    var liveProgress by remember(book.id) { mutableStateOf(book.progress) }

    var loadResult by remember(book.id) { mutableStateOf<BookLoadResult?>(null) }
    var controlsState by rememberSaveable(book.id) { mutableStateOf(ReaderControlsState.HIDDEN) }
    var sheetFocus by rememberSaveable(book.id) { mutableStateOf(ReaderSheetFocus.ALL) }
    var activeTool by rememberSaveable(book.id) { mutableStateOf<ReaderTool?>(null) }
    val controlsEvent: (ReaderControlsEvent) -> Unit = { controlsState = controlsState.transition(it) }
    val timer = rememberSaveable(book.id, saver = ReaderSessionTimer.Saver) { ReaderSessionTimer() }
    TrackReaderSession(timer)
    fun openTool(tool: ReaderTool) { controlsState = ReaderControlsState.HIDDEN; activeTool = tool }
    fun closeTool(revealControls: Boolean = true) {
        activeTool = null
        if (revealControls) controlsEvent(ReaderControlsEvent.SHOW_COMPACT)
    }
    var jumpRequest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Fiche « Définir / Traduire » ouverte depuis le menu de sélection
    var lookup by remember { mutableStateOf<LookupRequest?>(null) }
    fun showLookup(request: LookupRequest) {
        controlsEvent(ReaderControlsEvent.HIDE)
        activeTool = null
        lookup = request
    }
    ReaderControlsBackHandler(controlsState, activeTool == null && lookup == null, controlsEvent, onBack)
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
                    runCatching { OrynDatabase.getDatabase(context).bookDao().updateFilePath(book.id, newPath) }
                }
            }
            loadResult = null
            reloadKey++
        }
    }

    LaunchedEffect(book.id, book.filePath, reloadKey) {
        loadResult = null
        val loaded = BookTextLoader(context).loadBook(pathOverride ?: book.filePath, book.format)
        loadResult = loaded
        try {
            // This effect owns exactly the document it acquired, including on reload.
            awaitCancellation()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                (loaded as? BookLoadResult.Success)?.pdf?.close()
            }
        }
    }

    // Marque le livre comme « lu récemment » sans toucher à la progression
    LaunchedEffect(book.id) { onUpdateProgress(book, book.progress) }

    // Reader brightness follows Android system settings. Keep legacy DataStore values
    // readable for backwards compatibility, but never apply a stale window override.

    LaunchedEffect(settings.hideStatusBar, controlsState) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        if (settings.hideStatusBar && controlsState == ReaderControlsState.HIDDEN) {
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
    val pdfCurrentPage = pdfPageAtProgress(liveProgress, pdfPageCount)

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
    val effectiveRich: RichBook? = remember(loadResult, reflowBook, useReflow) {
        readerRich ?: if (useReflow) reflowBook?.rich else (loadResult as? BookLoadResult.Success)
            ?.takeIf { it.pdf == null && it.html == null }
            ?.let { RichBook(it.pages.joinToString("\n\n"), emptyList(), emptyList()) }
    }
    // Les annotations du mode texte (positions en caractères) sont rangées à part de celles des pages PDF
    val annotBookId = if (useReflow) -book.id else book.id
    var chapterPagesAfter by remember(annotBookId, effectiveRich) { mutableStateOf<Int?>(null) }
    var visiblePosition by remember(annotBookId, effectiveRich) { mutableStateOf<Int?>(null) }
    val annotationsFlow = remember(annotBookId) { annotationDao.observe(annotBookId) }
    val annotations by annotationsFlow.collectAsState(initial = emptyList())
    LaunchedEffect(reflowUnavailable) {
        if (reflowUnavailable) {
            android.widget.Toast.makeText(
                context, tr("Mode texte indisponible : affichage en pages."), android.widget.Toast.LENGTH_LONG
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

    val chromeToc = effectiveRich?.toc ?: pdfToc
    val chromeIsPdf = readerPdf != null && !useReflow
    val chromePosition = visiblePosition ?: if (effectiveRich != null) (liveProgress * effectiveRich.text.length).toInt() else pdfCurrentPage * PDF_STRIDE
    val documentLength = effectiveRich?.text?.length ?: if (chromeIsPdf) (pdfPageCount.toLong() * PDF_STRIDE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
    val chapter = remember(chromeToc, chromePosition, documentLength, chromeIsPdf, visiblePosition) {
        if (visiblePosition == null) null else readerChapterProgress(chromeToc.map { it.offset to it.title }, chromePosition, documentLength, if (chromeIsPdf) PDF_STRIDE else null)
    }
    val chromeTitle = chapter?.title?.takeIf { it.isNotBlank() } ?: book.title
    val chapterRemaining = (if (chromeIsPdf) chapter?.remainingPages else chapterPagesAfter)?.let { trf("%s pages après cette page", it) }
    val density = LocalDensity.current
    val systemInsets = WindowInsets.systemBarsIgnoringVisibility
    val chromeInsets = systemInsets.union(WindowInsets.displayCutout)
    val cutoutTop = with(density) { (chromeInsets.getTop(this) - systemInsets.getTop(this)).coerceAtLeast(0).toDp() }
    val cutoutBottom = with(density) { (chromeInsets.getBottom(this) - systemInsets.getBottom(this)).coerceAtLeast(0).toDp() }
    val chromeMetrics = readerChromeMetrics(density.fontScale, chromeToc.isNotEmpty())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .readerControlsKeyModifier(controlsState, activeTool == null && lookup == null, controlsEvent)
    ) {
        ReaderSurfaceTapBackdrop({ controlsEvent(ReaderControlsEvent.SURFACE_TAP) }, Modifier.matchParentSize())
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
                    val toggleUi = { controlsEvent(ReaderControlsEvent.SURFACE_TAP) }
                    val saveProgress: (Float) -> Unit = { fraction ->
                        liveProgress = fraction.coerceIn(0f, 1f)
                        onUpdateProgress(book, liveProgress)
                    }

                    // Fixed, font-scale-aware gutters keep text clear without repaginating on a tap.
                    Box(Modifier.fillMaxSize()
                        .windowInsetsPadding(chromeInsets.only(WindowInsetsSides.Horizontal))
                        .padding(top = chromeMetrics.top + 16.dp + cutoutTop, bottom = chromeMetrics.bottom + 16.dp + cutoutBottom)
                        .semantics {
                            onClick(label = tr(if (controlsState == ReaderControlsState.HIDDEN) "Afficher les commandes de lecture" else "Masquer les commandes de lecture")) { controlsEvent(ReaderControlsEvent.SURFACE_TAP); true }
                        }) {
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
                            onVisiblePosition = { visiblePosition = it },
                            annotations = annotations,
                            onAddAnnotation = { a -> scope.launch { annotationDao.insert(a) } },
                            onUpdateAnnotation = { a -> scope.launch { annotationDao.update(a) } },
                            onDeleteAnnotation = { a -> scope.launch { annotationDao.delete(a) } },
                            jumpRequest = jumpRequest,
                            onJumpHandled = { jumpRequest = null },
                            onDefine = { showLookup(LookupRequest(LookupMode.DEFINE, it)) },
                            onTranslate = { showLookup(LookupRequest(LookupMode.TRANSLATE, it)) }
                        )
                    } else {
                        // Le texte est remis en page à la taille réelle de l'écran : plus de bas de page coupé
                        val rich = remember(result, reflowBook, useReflow) {
                            (if (useReflow) reflowBook?.rich else null)
                                ?: effectiveRich ?: result.rich ?: RichBook(result.pages.joinToString("\n\n"), emptyList(), emptyList())
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
                            onVisiblePosition = { visiblePosition = it },
                            onChapterPagesAfter = { chapterPagesAfter = it },
                            annotations = annotations,
                            onAddAnnotation = { a -> scope.launch { annotationDao.insert(a) } },
                            onUpdateAnnotation = { a -> scope.launch { annotationDao.update(a) } },
                            onDeleteAnnotation = { a -> scope.launch { annotationDao.delete(a) } },
                            jumpRequest = jumpRequest,
                            onJumpHandled = { jumpRequest = null },
                            onDefine = { showLookup(LookupRequest(LookupMode.DEFINE, it)) },
                            onTranslate = { showLookup(LookupRequest(LookupMode.TRANSLATE, it)) }
                        )
                    }
                    }
                }
            }
        }

        lookup?.let { request -> LookupSheet(request) { lookup = null } }

        if (activeTool == null && lookup == null) ReaderCompactControls(
            state = controlsState, metrics = chromeMetrics, chapterTitle = chromeTitle,
            chapterRemaining = chapterRemaining, bookProgress = liveProgress, chapterProgress = chapter?.fraction,
            bookmarked = book.isFavorite, currentAccent = currentAccent, onBack = onBack,
            onTimer = { openTool(ReaderTool.TIMER) }, onBookmark = { onToggleBookmark(book) },
            onContents = chromeToc.takeIf { it.isNotEmpty() }?.let { { openTool(ReaderTool.CONTENTS) } },
            onAnnotations = if (effectiveRich != null || chromeIsPdf) ({ openTool(ReaderTool.ANNOTATIONS) }) else null,
            onExpand = { focus -> sheetFocus = focus; controlsEvent(ReaderControlsEvent.EXPAND) },
            modifier = Modifier.fillMaxSize()
        )
        if (activeTool == ReaderTool.TIMER) ReaderTimerDialog(timer) { closeTool() }

        if (activeTool == ReaderTool.CONTENTS && (effectiveRich != null || readerPdf != null)) {
            TocDialog(
                entries = effectiveRich?.toc ?: pdfToc,
                currentOffset = if (effectiveRich != null) (liveProgress * effectiveRich.text.length).toInt() else pdfCurrentPage * PDF_STRIDE,
                onJump = { offset ->
                    closeTool(false)
                    controlsEvent(ReaderControlsEvent.HIDE)
                    jumpRequest = offset to 0
                },
                onDismiss = { closeTool() }
            )
        }
        if (activeTool == ReaderTool.SEARCH && effectiveRich != null) {
            SearchDialog(
                text = effectiveRich.text,
                currentOffset = (liveProgress * effectiveRich.text.length).toInt(),
                onJump = { offset, length ->
                    // Keep the search dialog open for successive previous/next jumps.
                    jumpRequest = offset to length
                },
                onDismiss = { closeTool() }
            )
        }
        if (activeTool == ReaderTool.ANNOTATIONS) {
            AnnotationsDialog(
                title = book.title,
                author = book.author,
                annotations = annotations,
                onJump = { offset ->
                    closeTool(false)
                    controlsEvent(ReaderControlsEvent.HIDE)
                    jumpRequest = offset to 0
                },
                onDelete = { a -> scope.launch { annotationDao.delete(a) } },
                onDismiss = { closeTool() }
            )
        }

        if (controlsState == ReaderControlsState.EXPANDED) {
            val rb = effectiveRich
            val toc = rb?.toc ?: pdfToc
            val isPdf = readerPdf != null && !useReflow
            val position = chromePosition
            val chapterIndex = toc.indexOfLast { it.offset <= position }
            val chapterTitle = chromeTitle
            val remaining = chapterRemaining ?: if (isPdf) trf("page %s / %s", pdfCurrentPage + 1, pdfPageCount) else null
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
                controlsEvent(ReaderControlsEvent.HIDE)
                jumpRequest = o to 0
            }
            ReaderControlsSheet(
                settings = settings,
                initialFocus = sheetFocus,
                onSettingsChange = ::updateReaderSettings,
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
                        updateReaderSettings { it.copy(pdfReflow = !it.pdfReflow) }
                    }
                } else null,
                currentAccent = currentAccent,
                onAccentChange = onAccentChange,
                onDismiss = { controlsEvent(ReaderControlsEvent.DISMISS_SHEET) },
                onOpenToc = toc.takeIf { it.isNotEmpty() }?.let { { openTool(ReaderTool.CONTENTS) } },
                onOpenSearch = rb?.let { { openTool(ReaderTool.SEARCH) } },
                onOpenAnnotations = if (rb != null || isPdf) { { openTool(ReaderTool.ANNOTATIONS) } } else null,
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
