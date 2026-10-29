package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.example.oryn.reader.data.local.entity.AnnotationEntity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.net.Uri
import com.example.oryn.reader.data.importer.LinkSpan
import com.example.oryn.reader.data.importer.LinkTarget
import com.example.oryn.reader.data.importer.RichBook
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oryn.reader.data.importer.PdfPages
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import com.example.oryn.reader.data.local.preferences.ReaderScrollMode
import com.example.oryn.reader.data.local.preferences.ReaderTextAlign
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.yield
import kotlin.math.max
import kotlin.math.min

/* ───────────────────────── Pager commun (page qui se tourne / glissement) ───────────────────────── */

/**
 * Affiche [pageCount] pages : tournage de page réaliste en mode PAGES, glissement en mode SCROLL.
 * À placer dans un `key(...)` : quand la mise en page change, l'état repart de [initialPage].
 */
@OptIn(ExperimentalPageCurlApi::class)
@Composable
internal fun BookPager(
    pageCount: Int,
    initialPage: Int,
    curlMode: Boolean,
    backgroundColor: Color,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onTap: ((page: Int, offset: Offset) -> Boolean)? = null,
    currentPageRef: Array<() -> Int>? = null,
    lockPaging: Boolean = false,
    page: @Composable (Int) -> Unit
) {
    if (pageCount <= 0) return
    val start = initialPage.coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(initialPage = start, pageCount = { pageCount })
    val curlState = rememberPageCurlState(initialCurrent = start)
    // Page réellement affichée (le tournage de page garde les pages voisines superposées)
    SideEffect { currentPageRef?.set(0) { if (curlMode) curlState.current else pagerState.currentPage } }
    val latestChanged by rememberUpdatedState(onPageChanged)
    val latestTap by rememberUpdatedState(onCenterTap)
    val latestPageTap by rememberUpdatedState(onTap)

    // Garde la même page quand on bascule entre pages (curl) et défilement
    LaunchedEffect(curlMode) {
        if (curlMode) curlState.snapTo(pagerState.currentPage.coerceAtMost(pageCount - 1))
        else pagerState.scrollToPage(curlState.current.coerceAtMost(pageCount - 1))
    }

    // Progression : seulement quand l'utilisateur change de page (pas à l'ouverture)
    LaunchedEffect(pagerState, curlState, curlMode) {
        snapshotFlow { if (curlMode) curlState.current else pagerState.currentPage }
            .drop(1)
            .collect { latestChanged(it) }
    }

    if (curlMode) {
        val curlConfig = rememberPageCurlConfig(
            backPageColor = backgroundColor,
            backPageContentAlpha = 0.12f,
            shadowColor = Color.Black,
            shadowAlpha = 0.25f,
            onCustomTap = { size, offset ->
                if (latestPageTap?.invoke(curlState.current, offset) == true) {
                    true // un lien a été touché
                } else {
                    // tiers central : affiche / masque l'interface
                    val centre = offset.x > size.width * 0.33f && offset.x < size.width * 0.67f
                    if (centre) latestTap()
                    centre
                }
            }
        )
        // Sélection en cours : le tournage de page par glissement est suspendu (les poignées ont le geste pour elles)
        SideEffect {
            curlConfig.dragForwardEnabled = !lockPaging
            curlConfig.dragBackwardEnabled = !lockPaging
        }
        PageCurl(
            count = pageCount,
            state = curlState,
            modifier = Modifier.fillMaxSize(),
            config = curlConfig
        ) { index -> page(index) }
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !lockPaging
        ) { index -> page(index) }
    }
}

/* ───────────────────────── Texte (EPUB / TXT) : mise en page à la taille de l'écran ───────────────────────── */

private class PageLayout(val key: String, val starts: IntArray, val style: TextStyle)

/** Mémorise les mises en page déjà calculées (même livre, mêmes réglages, même écran). */
private object PaginationCache {
    private val cache = LruCache<String, IntArray>(12)
    fun get(key: String): IntArray? = cache.get(key)
    fun put(key: String, starts: IntArray) { cache.put(key, starts) }
}

/**
 * Coupe [text] en pages qui tiennent exactement dans [heightPx]. Chaque page se termine à la
 * dernière ligne ENTIÈRE visible : aucun mot n'est coupé, le suivant passe sur la page d'après.
 *
 * Le texte est mesuré par gros blocs (≈ 8 pages d'un coup) sur un thread d'arrière-plan : les pages
 * sont ensuite découpées avec les hauteurs de lignes, sans re-mesurer chaque page.
 * Renvoie le décalage (en caractères) du début de chaque page.
 */
private suspend fun paginateText(
    rich: AnnotatedString,
    style: TextStyle,
    fontFamilyResolver: FontFamily.Resolver,
    density: Density,
    layoutDirection: LayoutDirection,
    widthPx: Int,
    heightPx: Int,
    onProgress: (Float) -> Unit
): IntArray = withContext(Dispatchers.Default) {
    val text = rich.text
    // Mesureur propre à ce thread : pas de partage avec l'interface
    val measurer = TextMeasurer(fontFamilyResolver, density, layoutDirection, 4)
    val starts = ArrayList<Int>(text.length / 1_000 + 8)
    var pos = 0
    var chunkSize = 24_000

    while (pos < text.length) {
        ensureActive()
        // pas de lignes vides ni d'espaces en haut d'une page
        while (pos < text.length && (text[pos] == '\n' || text[pos] == ' ')) pos++
        if (pos >= text.length) break

        var chunkEnd = min(text.length, pos + chunkSize)
        if (chunkEnd < text.length) {
            val newline = text.lastIndexOf('\n', chunkEnd)
            if (newline > pos) chunkEnd = newline + 1 // le bloc s'arrête en fin de paragraphe
        }
        val isLastChunk = chunkEnd >= text.length
        val chunk = text.substring(pos, chunkEnd)
        val layout = measurer.measure(
            text = rich.subSequence(pos, chunkEnd),
            style = style,
            constraints = Constraints(maxWidth = widthPx)
        )

        val lineCount = layout.lineCount
        var line = 0
        var pagesFound = 0
        var stoppedOnIncompletePage = false

        while (line < lineCount) {
            // lignes vides en haut de page : ignorées
            while (line < lineCount &&
                chunk.substring(layout.getLineStart(line), layout.getLineEnd(line)).isBlank()
            ) line++
            if (line >= lineCount) break

            val top = layout.getLineTop(line)
            var last = line
            while (last + 1 < lineCount && layout.getLineBottom(last + 1) - top <= heightPx) last++

            // La dernière page d'un bloc peut continuer dans le bloc suivant : on la refait avec lui
            if (last == lineCount - 1 && !isLastChunk) {
                stoppedOnIncompletePage = true
                break
            }
            starts += pos + layout.getLineStart(line)
            pagesFound++
            line = last + 1
        }

        when {
            isLastChunk -> break
            stoppedOnIncompletePage && pagesFound == 0 -> chunkSize *= 2 // bloc plus petit qu'une page
            stoppedOnIncompletePage -> pos += layout.getLineStart(line)
            else -> pos = chunkEnd
        }
        onProgress(pos.toFloat() / text.length)
        yield()
    }
    starts.toIntArray()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReflowReader(
    bookId: Long,
    book: RichBook,
    linkColor: Color,
    initialProgress: Float,
    settings: ReaderSettings,
    fontFamily: FontFamily,
    foregroundColor: Color,
    backgroundColor: Color,
    horizontalPadding: Dp,
    onProgress: (Float) -> Unit,
    onCenterTap: () -> Unit,
    annotations: List<AnnotationEntity> = emptyList(),
    onAddAnnotation: (AnnotationEntity) -> Unit = {},
    onUpdateAnnotation: (AnnotationEntity) -> Unit = {},
    onDeleteAnnotation: (AnnotationEntity) -> Unit = {},
    jumpRequest: Pair<Int, Int>? = null,
    onJumpHandled: () -> Unit = {},
    onDefine: ((String) -> Unit)? = null,
    onTranslate: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val fontResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val bars = WindowInsets.systemBarsIgnoringVisibility
    val curlMode = settings.scrollMode == ReaderScrollMode.PAGES
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val text = book.text

    // Texte stylé (couleur des liens selon le thème), préparé hors de l'interface
    val rich by produceState<AnnotatedString?>(null, book, linkColor) {
        value = withContext(Dispatchers.Default) { book.toAnnotatedString(linkColor) }
    }
    val sortedLinks = remember(book) { book.links.sortedBy { it.start } }

    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var jumpNonce by remember { mutableIntStateOf(0) }
    val backStack = remember(bookId) { mutableStateListOf<Int>() }
    val pageHit = remember { HashMap<Int, (Offset) -> Boolean>() }
    val pageSelect = remember { HashMap<Int, (Offset) -> Unit>() }
    val currentPageRef = remember { arrayOf<() -> Int>({ 0 }) }
    val latestAnnotations by rememberUpdatedState(annotations)
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    // Sélection en cours : [début, fin[ en caractères ; annotation touchée : son identifiant
    var selection by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var activeId by remember { mutableStateOf<Long?>(null) }
    var noteSelection by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var noteAnnotation by remember { mutableStateOf<AnnotationEntity?>(null) }
    val handleColor = MaterialTheme.colorScheme.primary
    var barAnchor by remember { mutableStateOf<Rect?>(null) }
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val activeColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { containerCoords = it }) {
        // Marges verticales STABLES (barres système comptées même masquées) : la mise en page
        // ne change pas quand la barre d'état apparaît ou disparaît.
        val topPad = with(density) { bars.getTop(density).toDp() } + 12.dp
        val bottomPad = with(density) { bars.getBottom(density).toDp() } + 8.dp
        val footerHeight = 28.dp
        val textHeight = maxHeight - topPad - bottomPad - footerHeight
        val widthPx = with(density) { (maxWidth - horizontalPadding * 2).roundToPx() }
        val heightPx = with(density) { textHeight.roundToPx() }
        val padLeftPx = with(density) { horizontalPadding.toPx() }
        val padTopPx = with(density) { topPad.toPx() }

        val measureStyle = remember(settings.fontFamily, settings.fontSizePt, settings.lineSpacing, settings.textAlign, fontFamily) {
            bodyLarge.copy(
                fontFamily = fontFamily,
                fontSize = settings.fontSizePt.sp,
                lineHeight = (settings.fontSizePt * settings.lineSpacing).sp,
                textAlign = when (settings.textAlign) {
                    ReaderTextAlign.START -> TextAlign.Start
                    ReaderTextAlign.CENTER -> TextAlign.Center
                    ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                },
                // Pas de césure : un mot n'est jamais coupé en deux. Coupes simples = plus rapide.
                lineBreak = LineBreak.Simple,
                hyphens = Hyphens.None
            )
        }
        val layoutKey = "$bookId|$widthPx|$heightPx|${settings.fontFamily}|${settings.fontSizePt}|${settings.lineSpacing}|${settings.textAlign}"

        var layout by remember { mutableStateOf<PageLayout?>(null) }
        var progress by remember { mutableFloatStateOf(0f) }
        var computing by remember { mutableStateOf(true) }
        // Position de lecture (début de la page courante), conservée quand la mise en page change
        val anchor = remember(bookId) {
            intArrayOf(((initialProgress * text.length).toInt() - 1).coerceIn(0, max(0, text.length - 1)))
        }

        val richReady = rich != null
        LaunchedEffect(layoutKey, richReady) {
            val styled = rich ?: return@LaunchedEffect
            computing = true
            if (layout != null) kotlinx.coroutines.delay(200) // attend la fin des clics sur +/−
            val cached = PaginationCache.get(layoutKey)
            val starts = cached ?: paginateText(styled, measureStyle, fontResolver, density, layoutDirection, widthPx, heightPx) { progress = it }
                .also { PaginationCache.put(layoutKey, it) }
            layout = PageLayout(layoutKey, starts, measureStyle)
            computing = false
        }

        // Saut vers un passage du livre (lien interne) ; le point de départ est mémorisé pour « Retour »
        val jumpTo: (Int, Boolean) -> Unit = { offset, push ->
            if (push) backStack.add(anchor[0])
            anchor[0] = offset.coerceIn(0, max(0, text.length - 1))
            jumpNonce++
            onProgress(offset.toFloat() / max(1, text.length))
        }
        LaunchedEffect(jumpRequest, layout != null) {
            val target = jumpRequest
            if (target != null && layout != null) {
                activeId = null
                jumpTo(target.first, true)
                // résultat de recherche : le passage trouvé est sélectionné
                selection = if (target.second > 0) {
                    target.first to min(text.length, target.first + target.second)
                } else null
                onJumpHandled()
            }
        }
        val activate: (LinkSpan) -> Unit = { link ->
            when (val target = link.target) {
                is LinkTarget.External -> pendingUrl = target.url
                is LinkTarget.Internal -> {
                    // Lien très court (« 1 », « [12] », « * ») : c'est une note, on l'affiche sans quitter la page
                    val note = if (link.end - link.start <= 5) footnotePreview(text, target.offset) else null
                    if (note != null) preview = note to target.offset else jumpTo(target.offset, true)
                }
            }
        }

        val current = layout
        val styled = rich
        if (current == null || styled == null) {
            // Première ouverture : page vide et un simple filet de progression, rien de plus
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),
                color = foregroundColor.copy(alpha = 0.35f),
                trackColor = Color.Transparent
            )
        } else {
            val starts = current.starts
            key(current.key, jumpNonce) {
                val index = starts.binarySearch(anchor[0]).let { if (it >= 0) it else -it - 2 }
                BookPager(
                    pageCount = starts.size,
                    initialPage = index.coerceIn(0, max(0, starts.size - 1)),
                    curlMode = curlMode,
                    backgroundColor = backgroundColor,
                    onPageChanged = { page ->
                        selection = null
                        activeId = null
                        anchor[0] = starts[page]
                        val endOffset = if (page + 1 < starts.size) starts[page + 1] else text.length
                        onProgress(endOffset.toFloat() / text.length)
                    },
                    onCenterTap = onCenterTap,
                    onTap = { page, offset -> pageHit[page]?.invoke(offset) == true },
                    currentPageRef = currentPageRef,
                    lockPaging = selection != null
                ) { page ->
                    val from = starts[page]
                    val to = if (page + 1 < starts.size) starts[page + 1] else text.length
                    var end = to
                    while (end > from && text[end - 1].isWhitespace()) end--
                    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                    var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    var gestureCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                    // Point tactile (repère de la zone de geste) -> repère exact du texte
                    val toText: (Offset) -> Offset = { o ->
                        val t = textCoords
                        val g = gestureCoords
                        if (t != null && g != null && t.isAttached && g.isAttached) t.localPositionOf(g, o)
                        else Offset(o.x - padLeftPx, o.y - padTopPx)
                    }

                    // Caractère touché (coordonnées relatives à la page entière), ou null si on touche à côté du texte
                    val charAt: (Offset) -> Int? = at@{ o ->
                        val lr = textLayout ?: return@at null
                        if (end <= from) return@at null
                        val local = hitChar(lr, toText(o), end - from, slop = 10f) ?: return@at null
                        from + local
                    }

                    // Un toucher : efface la sélection, ouvre un lien, active une annotation… sinon affiche/masque l'interface
                    val tapAt: (Offset) -> Boolean = at@{ o ->
                        if (selection != null) {
                            selection = null
                            return@at true
                        }
                        val absolute = charAt(o)
                        if (absolute != null) {
                            val link = sortedLinks.firstOrNull { absolute >= it.start && absolute < it.end }
                            if (link != null) {
                                activate(link)
                                return@at true
                            }
                            val note = latestAnnotations.firstOrNull { absolute >= it.startOffset && absolute < it.endOffset }
                            if (note != null) {
                                activeId = note.id
                                return@at true
                            }
                        }
                        if (activeId != null) {
                            activeId = null
                            return@at true
                        }
                        false
                    }

                    // Appui long : sélectionne le mot touché ; sur une sélection existante : la phrase, puis le paragraphe
                    val startSelection: (Offset) -> Unit = start@{ o ->
                        val lr = textLayout ?: return@start
                        if (end <= from) return@start
                        var local = hitChar(lr, toText(o), end - from) ?: return@start
                        // doigt sur un espace ou une ponctuation collée à un mot : on prend le mot voisin
                        if (!text[from + local].isLetterOrDigit()) {
                            if (local > 0 && text[from + local - 1].isLetterOrDigit()) local--
                            else if (local + 1 < end - from && text[from + local + 1].isLetterOrDigit()) local++
                            else return@start
                        }
                        val word = lr.getWordBoundary(local)
                        val a = from + word.start
                        val b = (from + word.end).coerceAtMost(end)
                        if (b <= a || !text.substring(a, b).any { it.isLetterOrDigit() }) return@start
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        activeId = null
                        val cur = selection
                        selection = if (cur != null && a >= cur.first && b <= cur.second) {
                            val sentence = sentenceBounds(text, a, from, end)
                            if (sentence != cur && sentence.second > sentence.first) sentence
                            else paragraphBounds(text, a, from, end)
                        } else a to b
                    }

                    SideEffect {
                        pageHit[page] = tapAt
                        pageSelect[page] = startSelection
                    }
                    // En mode « page qui se tourne », les pages voisines sont superposées à la page affichée :
                    // l'appui long est toujours confié à la page réellement visible.
                    val longPressAt: (Offset) -> Unit = { o ->
                        val visible = currentPageRef[0]()
                        val target = pageSelect[visible]
                        if (target != null) target(o) else startSelection(o)
                    }

                    // Texte de la page (les surlignages sont dessinés par-dessus : affichage immédiat)
                    val pageText = remember(current.key, linkColor, from, end) { styled.subSequence(from, end) }

                    // Position de la barre d'actions : juste au-dessus du passage sélectionné
                    LaunchedEffect(selection, activeId, textLayout, textCoords, containerCoords, annotations) {
                        val lr = textLayout ?: return@LaunchedEffect
                        val range = selection
                            ?: latestAnnotations.firstOrNull { it.id == activeId }?.let { it.startOffset to it.endOffset }
                            ?: return@LaunchedEffect
                        if (end > from && range.first >= from && range.first < end) {
                            val s0 = (range.first - from).coerceIn(0, end - from)
                            val e0 = (min(range.second, end) - from).coerceIn(s0, end - from)
                            if (e0 > s0) {
                                val box = lr.getPathForRange(s0, e0).getBounds()
                                val t = textCoords
                                val c = containerCoords
                                barAnchor = if (t != null && c != null && t.isAttached && c.isAttached) {
                                    val tl = c.localPositionOf(t, Offset(box.left, box.top))
                                    val br = c.localPositionOf(t, Offset(box.right, box.bottom))
                                    Rect(tl, br)
                                } else Rect(box.left + padLeftPx, box.top + padTopPx, box.right + padLeftPx, box.bottom + padTopPx)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor)
                            .onGloballyPositioned { gestureCoords = it }
                            .then(
                                if (curlMode) {
                                    // Le tournage de page gère les touchers ; ici on ne réagit qu'à l'appui long
                                    Modifier.pointerInput(page) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val press = awaitLongPressOrCancellation(down.id)
                                            if (press != null) {
                                                longPressAt(press.position)
                                                // on avale la fin du geste pour ne pas tourner la page
                                                do {
                                                    val event = awaitPointerEvent()
                                                    event.changes.forEach { it.consume() }
                                                } while (event.changes.any { it.pressed })
                                            }
                                        }
                                    }
                                } else {
                                    Modifier.pointerInput(page) {
                                        detectTapGestures(
                                            onLongPress = { o -> longPressAt(o) },
                                            onTap = { o -> if (!tapAt(o)) onCenterTap() }
                                        )
                                    }
                                }
                            )
                            .padding(start = horizontalPadding, end = horizontalPadding, top = topPad, bottom = bottomPad)
                    ) {
                        Column {
                            Text(
                                text = pageText,
                                style = current.style.copy(color = foregroundColor),
                                onTextLayout = { textLayout = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(textHeight)
                                    .onGloballyPositioned { textCoords = it }
                                    .drawWithContent {
                                        val lr = textLayout
                                        val len = end - from
                                        if (lr != null && len > 0) {
                                            fun local(v: Int) = (v - from).coerceIn(0, len)
                                            for (a in latestAnnotations) {
                                                val s0 = local(a.startOffset)
                                                val e0 = local(a.endOffset)
                                                if (e0 <= s0) continue
                                                if (a.kind == "HIGHLIGHT") {
                                                    drawPath(
                                                        lr.getPathForRange(s0, e0),
                                                        HighlightColors[a.color.coerceIn(0, 3)].copy(alpha = 0.45f)
                                                    )
                                                }
                                                if (a.id == activeId) {
                                                    drawPath(lr.getPathForRange(s0, e0), activeColor, style = Stroke(2.dp.toPx()))
                                                }
                                            }
                                            selection?.let { sel ->
                                                val s0 = local(sel.first)
                                                val e0 = local(sel.second)
                                                if (e0 > s0) drawPath(lr.getPathForRange(s0, e0), Color(0xFFD99A32).copy(alpha = 0.35f))
                                            }
                                        }
                                        drawContent()
                                        if (lr != null && len > 0) {
                                            for (a in latestAnnotations) {
                                                if (a.kind != "UNDERLINE") continue
                                                val s0 = (a.startOffset - from).coerceIn(0, len)
                                                val e0 = (a.endOffset - from).coerceIn(0, len)
                                                if (e0 > s0) drawRangeUnderline(lr, s0, e0, foregroundColor.copy(alpha = 0.9f))
                                            }
                                        }
                                    }
                            )
                            Box(Modifier.fillMaxWidth().height(footerHeight), contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    text = "${page + 1} / ${starts.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = foregroundColor.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Poignées pour étendre la sélection
                        val sel = selection
                        val lr = textLayout
                        if (sel != null && lr != null && end > from && sel.first >= from && sel.first < end) {
                            val last = end - from - 1
                            val startBox = lr.getBoundingBox((sel.first - from).coerceIn(0, last))
                            val endBox = lr.getBoundingBox((sel.second - 1 - from).coerceIn(0, last))
                            SelectionHandle(
                                anchor = Offset(startBox.left, startBox.bottom),
                                color = handleColor,
                                onDrag = { p ->
                                    val cur = selection
                                    if (cur != null) {
                                        // la poignée saute de mot en mot
                                        val wb = lr.getWordBoundary(lr.getOffsetForPosition(p).coerceIn(0, last))
                                        var o = from + wb.start
                                        while (o < cur.second - 1 && text[o].isWhitespace()) o++
                                        selection = o.coerceIn(from, cur.second - 1) to cur.second
                                    }
                                },
                                onDone = {}
                            )
                            SelectionHandle(
                                anchor = Offset(endBox.right, endBox.bottom),
                                color = handleColor,
                                onDrag = { p ->
                                    val cur = selection
                                    if (cur != null) {
                                        val wb = lr.getWordBoundary(lr.getOffsetForPosition(p).coerceIn(0, last))
                                        var o = (from + wb.end).coerceAtMost(end)
                                        while (o > cur.first + 1 && text[o - 1].isWhitespace()) o--
                                        selection = cur.first to o.coerceIn(cur.first + 1, end)
                                    }
                                },
                                onDone = {}
                            )
                        }
                    }
                }
            }
            if (computing) {
                LinearProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),
                    color = foregroundColor.copy(alpha = 0.35f),
                    trackColor = Color.Transparent
                )
            }
        }

        // Barre d'actions : sélection en cours ou annotation touchée
        var barSize by remember { mutableStateOf(IntSize.Zero) }
        LaunchedEffect(selection == null && activeId == null) {
            if (selection == null && activeId == null) barAnchor = null
        }
        run {
            val sel = selection
            val active = latestAnnotations.firstOrNull { it.id == activeId }
            if (sel != null || active != null) {
                val rangeText = sel?.let { text.substring(it.first, it.second) } ?: active?.quote.orEmpty()
                fun create(kind: String, color: Int, note: String?) {
                    val range = sel ?: return
                    onAddAnnotation(
                        AnnotationEntity(
                            bookId = bookId,
                            startOffset = range.first,
                            endOffset = range.second,
                            kind = kind,
                            color = color,
                            note = note,
                            quote = text.substring(range.first, range.second).trim().take(400)
                        )
                    )
                    selection = null
                }
                SelectionBar(
                    existing = active,
                    onColor = { c ->
                        if (sel != null) create("HIGHLIGHT", c, null)
                        else if (active != null) onUpdateAnnotation(active.copy(kind = "HIGHLIGHT", color = c))
                    },
                    onUnderline = {
                        if (sel != null) create("UNDERLINE", 0, null)
                        else if (active != null) onUpdateAnnotation(active.copy(kind = "UNDERLINE"))
                    },
                    onNote = {
                        if (sel != null) noteSelection = sel else noteAnnotation = active
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString(rangeText.trim()))
                        Toast.makeText(context, tr("Copié"), Toast.LENGTH_SHORT).show()
                        selection = null
                        activeId = null
                    },
                    onDelete = active?.let { a -> { onDeleteAnnotation(a); activeId = null } },
                    onExtend = sel?.let { cur ->
                        {
                            val st = layout?.starts
                            if (st != null && st.isNotEmpty()) {
                                val pi = st.binarySearch(cur.first).let { if (it >= 0) it else -it - 2 }.coerceIn(0, st.size - 1)
                                val lo = st[pi]
                                var hi = if (pi + 1 < st.size) st[pi + 1] else text.length
                                while (hi > lo && text[hi - 1].isWhitespace()) hi--
                                if (hi > lo) selection = extendSelection(text, cur, lo, hi)
                            }
                        }
                    },
                    onDefine = onDefine?.let { f -> { f(rangeText.trim()) } },
                    onTranslate = onTranslate?.let { f -> { f(rangeText.trim()) } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onSizeChanged { barSize = it }
                        .offset {
                            val anchorRect = barAnchor
                            if (anchorRect == null || barSize.width == 0) {
                                IntOffset(-10_000, -10_000) // hors écran jusqu'à la mesure
                            } else {
                                val margin = 8.dp.roundToPx()
                                val gap = 14.dp.roundToPx()
                                val x = (anchorRect.center.x - barSize.width / 2f).roundToInt()
                                    .coerceIn(margin, (constraints.maxWidth - barSize.width - margin).coerceAtLeast(margin))
                                val above = anchorRect.top - barSize.height - gap
                                // pas de place au-dessus : sous le passage, sous les poignées
                                val y = if (above >= padTopPx) above else anchorRect.bottom + gap + 36.dp.roundToPx()
                                IntOffset(x, y.roundToInt())
                            }
                        }
                )
            }
        }

        noteSelection?.let { range ->
            NoteDialog(
                initial = "",
                onSave = { note ->
                    onAddAnnotation(
                        AnnotationEntity(
                            bookId = bookId,
                            startOffset = range.first,
                            endOffset = range.second,
                            kind = "HIGHLIGHT",
                            color = 0,
                            note = note.ifBlank { null },
                            quote = text.substring(range.first, range.second).trim().take(400)
                        )
                    )
                    noteSelection = null
                    selection = null
                },
                onDismiss = { noteSelection = null }
            )
        }
        noteAnnotation?.let { target ->
            NoteDialog(
                initial = target.note.orEmpty(),
                onSave = { note ->
                    onUpdateAnnotation(target.copy(note = note.ifBlank { null }))
                    noteAnnotation = null
                },
                onDismiss = { noteAnnotation = null }
            )
        }

        // Retour à l'endroit d'où l'on est parti (après un saut par lien)
        if (backStack.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPad + 2.dp)
                    .clip(CircleShape)
                    .background(foregroundColor.copy(alpha = 0.92f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("↩  Retour à ma lecture"),
                    color = backgroundColor,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable {
                            val target = backStack.removeAt(backStack.lastIndex)
                            jumpTo(target, false)
                        }
                        .padding(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp)
                )
                Text(
                    "✕",
                    color = backgroundColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { backStack.clear() }
                        .padding(start = 6.dp, end = 14.dp, top = 9.dp, bottom = 9.dp)
                )
            }
        }
        pendingUrl?.let { url ->
            AlertDialog(
                onDismissRequest = { pendingUrl = null },
                title = { Text(tr("Ouvrir ce lien ?")) },
                text = { Text(url, maxLines = 4, overflow = TextOverflow.Ellipsis) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingUrl = null
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text(tr("Ouvrir")) }
                },
                dismissButton = { TextButton(onClick = { pendingUrl = null }) { Text(tr("Annuler")) } }
            )
        }

        preview?.let { (note, target) ->
            AlertDialog(
                onDismissRequest = { preview = null },
                title = { Text(tr("Note")) },
                text = { Text(note, maxLines = 14, overflow = TextOverflow.Ellipsis) },
                confirmButton = {
                    TextButton(onClick = {
                        preview = null
                        jumpTo(target, true)
                    }) { Text(tr("Aller au passage")) }
                },
                dismissButton = { TextButton(onClick = { preview = null }) { Text(tr("Fermer")) } }
            )
        }
    }
}

/* ───────────────────────── PDF : une image par page ───────────────────────── */

private fun DrawScope.drawRangeUnderline(lr: TextLayoutResult, a: Int, b: Int, color: Color) {
    val firstLine = lr.getLineForOffset(a)
    val lastLine = lr.getLineForOffset((b - 1).coerceAtLeast(a))
    for (line in firstLine..lastLine) {
        val x1 = if (line == firstLine) lr.getHorizontalPosition(a, true) else lr.getLineLeft(line)
        val x2 = if (line == lastLine) lr.getHorizontalPosition(b, true) else lr.getLineRight(line)
        val y = lr.getLineBaseline(line) + 3.dp.toPx()
        if (x2 > x1) drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = 1.6.dp.toPx())
    }
}


/**
 * Caractère visé par un toucher, tolérant : on retient d'abord la LIGNE touchée (interligne et fin de ligne inclus),
 * puis le caractère le plus proche dans cette ligne. null si le doigt est trop loin du texte.
 */
internal fun hitChar(lr: TextLayoutResult, p: Offset, count: Int, slop: Float = 28f): Int? {
    if (count <= 0 || lr.lineCount == 0) return null
    if (p.y < -slop || p.y > lr.getLineBottom(lr.lineCount - 1) + slop) return null
    val line = lr.getLineForVerticalPosition(p.y.coerceAtLeast(0f))
    val left = lr.getLineLeft(line)
    val right = lr.getLineRight(line)
    if (p.x < left - slop || p.x > right + slop) return null
    val start = lr.getLineStart(line)
    val lastInLine = max(start, lr.getLineEnd(line, visibleEnd = true) - 1)
    val x = p.x.coerceIn(left, max(left, right - 0.5f))
    val midY = (lr.getLineTop(line) + lr.getLineBottom(line)) / 2f
    var off = lr.getOffsetForPosition(Offset(x, midY)).coerceIn(start, lastInLine)
    // la position retournée est la frontière la plus proche : si le doigt est à gauche du caractère, c'est le précédent
    if (off > start && x < lr.getBoundingBox(off).left) off--
    return off.coerceIn(0, count - 1)
}

private fun isSentenceEnd(c: Char) = c == '.' || c == '!' || c == '?' || c == '…' || c == '\n'

/** Phrase contenant [at], bornée à [lo, hi). */
internal fun sentenceBounds(text: CharSequence, at: Int, lo: Int, hi: Int): Pair<Int, Int> {
    var s = at
    while (s > lo && !isSentenceEnd(text[s - 1])) s--
    var e = at
    while (e < hi && !isSentenceEnd(text[e])) e++
    if (e < hi && text[e] != '\n') e++
    while (s < e && text[s].isWhitespace()) s++
    while (e > s && text[e - 1].isWhitespace()) e--
    return s to e
}

/** Paragraphe contenant [at], borné à [lo, hi). */
internal fun paragraphBounds(text: CharSequence, at: Int, lo: Int, hi: Int): Pair<Int, Int> {
    var s = at
    while (s > lo && text[s - 1] != '\n') s--
    var e = at
    while (e < hi && text[e] != '\n') e++
    while (s < e && text[s].isWhitespace()) s++
    while (e > s && text[e - 1].isWhitespace()) e--
    return s to e
}


/** « Étendre » : mot -> phrase -> paragraphe -> page entière, toujours dans [lo, hi). */
internal fun extendSelection(text: CharSequence, sel: Pair<Int, Int>, lo: Int, hi: Int): Pair<Int, Int> {
    val last = (sel.second - 1).coerceIn(sel.first, hi - 1)
    val sentence = sentenceBounds(text, sel.first, lo, hi).first to sentenceBounds(text, last, lo, hi).second
    if (sentence.first <= sel.first && sentence.second >= sel.second && sentence != sel) return sentence
    val paragraph = paragraphBounds(text, sel.first, lo, hi).first to paragraphBounds(text, last, lo, hi).second
    if (paragraph.first <= sel.first && paragraph.second >= sel.second && paragraph != sel) return paragraph
    return lo to hi
}
