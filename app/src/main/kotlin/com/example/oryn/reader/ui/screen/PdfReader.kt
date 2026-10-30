package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTransformGestures
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.example.oryn.reader.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.importer.PDF_STRIDE
import com.example.oryn.reader.data.importer.PdfPages
import com.example.oryn.reader.data.importer.PdfTextPage
import com.example.oryn.reader.data.importer.PdfWord
import com.example.oryn.reader.data.local.entity.AnnotationEntity
import com.example.oryn.reader.ui.components.HugeIcons
import com.example.oryn.reader.data.local.preferences.ReaderScrollMode
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** « Étendre » sur un PDF : mot -> phrase -> page entière. */
internal fun extendWordSelection(words: List<PdfWord>, sel: Pair<Int, Int>): Pair<Int, Int> {
    if (words.isEmpty()) return sel
    fun ends(w: String) = w.endsWith(".") || w.endsWith("!") || w.endsWith("?") || w.endsWith("…")
    var s = sel.first.coerceIn(0, words.size - 1)
    while (s > 0 && !ends(words[s - 1].text)) s--
    var e = (sel.second - 1).coerceIn(0, words.size - 1)
    while (e < words.size - 1 && !ends(words[e].text)) e++
    val sentence = s to (e + 1)
    if (sentence.first <= sel.first && sentence.second >= sel.second && sentence != sel) return sentence
    return 0 to words.size
}

/**
 * Lecteur PDF : page rendue en image + couche texte invisible (PdfBox) pour sélectionner des mots,
 * surligner / souligner / annoter, copier, et suivre les liens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfReader(
    pdf: PdfPages,
    bookId: Long,
    initialProgress: Float,
    settings: ReaderSettings,
    foregroundColor: Color,
    backgroundColor: Color,
    horizontalPadding: Dp,
    onProgress: (Float) -> Unit,
    onCenterTap: () -> Unit,
    annotations: List<AnnotationEntity>,
    onAddAnnotation: (AnnotationEntity) -> Unit,
    onUpdateAnnotation: (AnnotationEntity) -> Unit,
    onDeleteAnnotation: (AnnotationEntity) -> Unit,
    jumpRequest: Pair<Int, Int>?,
    onJumpHandled: () -> Unit,
    onDefine: ((String) -> Unit)? = null,
    onTranslate: ((String) -> Unit)? = null
) {
    val total = pdf.pageCount
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val bars = WindowInsets.systemBarsIgnoringVisibility
    val density = LocalDensity.current
    val topPad = with(density) { bars.getTop(density).toDp() } + 12.dp
    val bottomPad = with(density) { bars.getBottom(density).toDp() } + 8.dp
    val curlMode = settings.scrollMode == ReaderScrollMode.PAGES
    val handleColor = MaterialTheme.colorScheme.primary
    val activeColor = MaterialTheme.colorScheme.primary

    var startPage by remember(pdf) {
        mutableIntStateOf(
            if (initialProgress > 0f && total > 0) ((initialProgress * total).toInt() - 1).coerceIn(0, total - 1) else 0
        )
    }
    var showIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(startPage) {
        showIndicator = true
        kotlinx.coroutines.delay(2000)
        showIndicator = false
    }
    var jumpNonce by remember { mutableIntStateOf(0) }
    var selPage by remember { mutableIntStateOf(-1) }
    var selection by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selQuote by remember { mutableStateOf("") }
    var activeId by remember { mutableStateOf<Long?>(null) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var noteRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var noteAnnotation by remember { mutableStateOf<AnnotationEntity?>(null) }
    var barAnchor by remember { mutableStateOf<Rect?>(null) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    var containerRoot by remember { mutableStateOf(Offset.Zero) }

    val currentPageRef = remember { arrayOf<() -> Int>({ 0 }) }
    val pageSelect = remember { HashMap<Int, (Offset) -> Unit>() }
    val pageHit = remember { HashMap<Int, (Offset) -> Boolean>() }
    val pageOrigin = remember { HashMap<Int, Offset>() }
    val pageWords = remember { HashMap<Int, List<PdfWord>>() }
    val latestAnnotations by rememberUpdatedState(annotations)

    // Saut (sommaire, annotation, curseur de progression) : la valeur est « page × PDF_STRIDE »
    LaunchedEffect(jumpRequest) {
        val request = jumpRequest ?: return@LaunchedEffect
        startPage = (request.first / PDF_STRIDE).coerceIn(0, max(0, total - 1))
        selection = null
        activeId = null
        jumpNonce++
        onProgress((startPage + 1f) / total)
        onJumpHandled()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerRoot = it.positionInRoot() }
    ) {
        val maxWidthPx = constraints.maxWidth
        key(pdf, jumpNonce) {
            BookPager(
                pageCount = total,
                initialPage = startPage,
                curlMode = curlMode,
                backgroundColor = backgroundColor,
                onPageChanged = { page ->
                    selection = null
                    activeId = null
                    startPage = page
                    onProgress((page + 1f) / total)
                },
                onCenterTap = onCenterTap,
                onTap = { page, offset ->
                    val origin = pageOrigin[page]
                    origin != null && pageHit[page]?.invoke(offset - origin) == true
                },
                currentPageRef = currentPageRef,
                lockPaging = selection != null
            ) { page ->
                val bitmap by produceState<Bitmap?>(null, page, pdf) { value = pdf.render(page) }
                val textPage by produceState(PdfTextPage.EMPTY, page, pdf) { value = pdf.text.page(page) }
                LaunchedEffect(page) {
                    pdf.render(page + 1)
                    pdf.render(page - 1)
                }
                val words = textPage.words
                var boxSize by remember { mutableStateOf(IntSize.Zero) }
                var boxCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
                var pageScale by remember(page) { mutableStateOf(1f) }
                var pageOffset by remember(page) { mutableStateOf(Offset.Zero) }
                val w = max(1f, boxSize.width.toFloat())
                val h = max(1f, boxSize.height.toFloat())

                fun rectOf(i: Int): Rect {
                    val word = words[i]
                    return Rect(word.l * w, word.t * h, word.r * w, word.b * h)
                }

                // Mot sous le doigt (marge de tolérance [slop] en pixels)
                val wordAt: (Offset, Float) -> Int = { p, slop ->
                    var best = -1
                    var bestD = Float.MAX_VALUE
                    for (i in words.indices) {
                        val r = rectOf(i).inflate(slop)
                        if (r.contains(p)) {
                            val c = r.center
                            val d = (c.x - p.x) * (c.x - p.x) + (c.y - p.y) * (c.y - p.y)
                            if (d < bestD) { best = i; bestD = d }
                        }
                    }
                    best
                }
                // Mot le plus proche (glissement d'une poignée)
                val nearestWord: (Offset) -> Int = { p ->
                    var best = -1
                    var bestD = Float.MAX_VALUE
                    for (i in words.indices) {
                        val r = rectOf(i)
                        val dx = max(max(r.left - p.x, 0f), p.x - r.right)
                        val dy = max(max(r.top - p.y, 0f), p.y - r.bottom)
                        val d = dx * dx + dy * dy * 4f // la ligne compte plus que la colonne
                        if (d < bestD) { best = i; bestD = d }
                    }
                    best
                }

                val tapAt: (Offset) -> Boolean = at@{ p ->
                    if (selection != null) {
                        selection = null
                        return@at true
                    }
                    val nx = p.x / w
                    val ny = p.y / h
                    val link = textPage.links.firstOrNull { nx >= it.l && nx <= it.r && ny >= it.t && ny <= it.b }
                    if (link != null) {
                        val url = link.uri
                        if (url != null) {
                            pendingUrl = url
                        } else if (link.page in 0 until total) {
                            startPage = link.page
                            activeId = null
                            jumpNonce++
                            onProgress((link.page + 1f) / total)
                        }
                        return@at true
                    }
                    val wi = wordAt(p, 18f)
                    if (wi >= 0) {
                        val base = page * PDF_STRIDE
                        val note = latestAnnotations.firstOrNull {
                            it.startOffset / PDF_STRIDE == page && wi >= it.startOffset - base && wi < it.endOffset - base
                        }
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

                val startSelect: (Offset) -> Unit = start@{ p ->
                    val wi = wordAt(p, 14f)
                    if (wi < 0) return@start
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    activeId = null
                    val cur = selection
                    if (cur != null && selPage == page && wi >= cur.first && wi < cur.second) {
                        selection = extendWordSelection(words, cur) // appui long répété : phrase, puis page
                    } else {
                        selPage = page
                        selection = wi to (wi + 1)
                    }
                }

                SideEffect {
                    pageHit[page] = tapAt
                    pageSelect[page] = startSelect
                    pageWords[page] = words
                }
                // Les pages voisines sont superposées en mode « page qui se tourne » : l'appui long va à la page visible
                val longPressAt: (Offset) -> Unit = { p ->
                    val target = pageSelect[currentPageRef[0]()]
                    if (target != null) target(p) else startSelect(p)
                }

                // Texte de la sélection + position de la barre d'actions (en pixels, repère du lecteur)
                LaunchedEffect(selection, selPage, activeId, words, boxCoords, boxSize, annotations) {
                    val coords = boxCoords ?: return@LaunchedEffect
                    if (words.isEmpty() || !coords.isAttached) return@LaunchedEffect
                    val base = page * PDF_STRIDE
                    val range: Pair<Int, Int>? = if (selection != null && selPage == page) selection
                    else latestAnnotations.firstOrNull { it.id == activeId && it.startOffset / PDF_STRIDE == page }
                        ?.let { (it.startOffset - base) to (it.endOffset - base) }
                    if (range == null) return@LaunchedEffect
                    val a = range.first.coerceIn(0, words.size - 1)
                    val b = (range.second - 1).coerceIn(a, words.size - 1)
                    if (selection != null && selPage == page) {
                        selQuote = words.subList(a, b + 1).joinToString(" ") { it.text }
                    }
                    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = 0f; var bt = 0f
                    for (i in a..b) {
                        val rc = rectOf(i)
                        l = min(l, rc.left); t = min(t, rc.top); r = max(r, rc.right); bt = max(bt, rc.bottom)
                    }
                    val tl = coords.localToRoot(Offset(l, t)) - containerRoot
                    val br = coords.localToRoot(Offset(r, bt)) - containerRoot
                    barAnchor = Rect(tl, br)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .then(
                            if (curlMode) Modifier else Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCenterTap
                            )
                        )
                        .padding(start = horizontalPadding, end = horizontalPadding, top = topPad, bottom = bottomPad),
                    contentAlignment = Alignment.Center
                ) {
                    val current = bitmap
                    if (current == null) {
                        CircularProgressIndicator(color = foregroundColor)
                    } else {
                        Box(
                            Modifier
                                .aspectRatio(current.width.toFloat() / current.height.toFloat())
                                .graphicsLayer(
                                    scaleX = pageScale,
                                    scaleY = pageScale,
                                    translationX = pageOffset.x,
                                    translationY = pageOffset.y
                                )
                                .onSizeChanged { boxSize = it }
                                .onGloballyPositioned {
                                    boxCoords = it
                                    pageOrigin[page] = it.positionInRoot() - containerRoot
                                }
                                .pointerInput(Unit) {
                                    if (!curlMode) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newScale = (pageScale * zoom).coerceIn(1f, 5f)
                                            val maxX = ((boxSize.width * (newScale - 1)) / 2).coerceAtLeast(0f)
                                            val maxY = ((boxSize.height * (newScale - 1)) / 2).coerceAtLeast(0f)
                                            pageScale = newScale
                                            pageOffset = Offset(
                                                (pageOffset.x + pan.x).coerceIn(-maxX, maxX),
                                                (pageOffset.y + pan.y).coerceIn(-maxY, maxY)
                                            )
                                        }
                                    }
                                }
                                .pointerInput(page, curlMode) {
                                    if (curlMode) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val press = awaitLongPressOrCancellation(down.id)
                                            if (press != null) {
                                                longPressAt((press.position - pageOffset) / pageScale)
                                                do {
                                                    val event = awaitPointerEvent()
                                                    event.changes.forEach { it.consume() }
                                                } while (event.changes.any { it.pressed })
                                            }
                                        }
                                    } else {
                                        detectTapGestures(
                                            onDoubleTap = { pos ->
                                                if (pageScale > 1f) {
                                                    pageScale = 1f
                                                    pageOffset = Offset.Zero
                                                } else {
                                                    pageScale = 2.5f
                                                    val cx = boxSize.width / 2f
                                                    val cy = boxSize.height / 2f
                                                    val maxX = ((boxSize.width * (pageScale - 1)) / 2).coerceAtLeast(0f)
                                                    val maxY = ((boxSize.height * (pageScale - 1)) / 2).coerceAtLeast(0f)
                                                    pageOffset = Offset(
                                                        ((cx - pos.x) * (pageScale - 1)).coerceIn(-maxX, maxX),
                                                        ((cy - pos.y) * (pageScale - 1)).coerceIn(-maxY, maxY)
                                                    )
                                                }
                                            },
                                            onLongPress = { pos -> longPressAt((pos - pageOffset) / pageScale) },
                                            onTap = { pos -> if (pageHit[page]?.invoke((pos - pageOffset) / pageScale) != true) onCenterTap() }
                                        )
                                    }
                                }
                        ) {
                            Image(
                                bitmap = current.asImageBitmap(),
                                contentDescription = trf("Page %s", page + 1),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                            Canvas(Modifier.fillMaxSize()) {
                                val base = page * PDF_STRIDE
                                fun span(a: Int, b: Int, draw: (Rect) -> Unit) {
                                    val lo = a.coerceIn(0, words.size)
                                    val hi = b.coerceIn(lo, words.size)
                                    for (i in lo until hi) {
                                        var rc = rectOf(i)
                                        // comble l'espace entre deux mots d'une même ligne
                                        if (i + 1 < hi) {
                                            val next = words[i + 1]
                                            val cur = words[i]
                                            if (next.l > cur.r && abs(next.b - cur.b) < 0.008f) {
                                                rc = Rect(rc.left, rc.top, next.l * size.width, rc.bottom)
                                            }
                                        }
                                        draw(rc)
                                    }
                                }
                                if (words.isNotEmpty()) {
                                    for (a in latestAnnotations) {
                                        if (a.startOffset / PDF_STRIDE != page) continue
                                        val lo = a.startOffset - base
                                        val hi = a.endOffset - base
                                        if (a.kind == "HIGHLIGHT") {
                                            val c = HighlightColors[a.color.coerceIn(0, 3)].copy(alpha = 0.45f)
                                            span(lo, hi) { rc -> drawRect(c, rc.topLeft, rc.size) }
                                        } else {
                                            span(lo, hi) { rc ->
                                                drawLine(
                                                    Color(0xFF222222),
                                                    Offset(rc.left, rc.bottom),
                                                    Offset(rc.right, rc.bottom),
                                                    strokeWidth = 1.6.dp.toPx()
                                                )
                                            }
                                        }
                                        if (a.id == activeId) {
                                            span(lo, hi) { rc ->
                                                drawRect(
                                                    activeColor, rc.topLeft, rc.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
                                                )
                                            }
                                        }
                                        // Pastille de note : petit disque après le dernier mot
                                        if (a.note != null) {
                                            val r = 3.5.dp.toPx()
                                            span(lo, hi) { rc ->
                                                val cx = rc.right + r + 2.dp.toPx()
                                                val cy = rc.top + r
                                                drawCircle(activeColor, radius = r, center = Offset(cx, cy))
                                                drawCircle(Color.White, radius = r * 0.45f, center = Offset(cx, cy))
                                            }
                                        }
                                    }
                                    val sel = selection
                                    if (sel != null && selPage == page) {
                                        span(sel.first, sel.second) { rc ->
                                            drawRect(Color(0xFFD99A32).copy(alpha = 0.35f), rc.topLeft, rc.size)
                                        }
                                    }
                                }
                            }
                            // Icône « note » dans la marge droite de la page, alignée sur la ligne du dernier mot de l'annotation
                            latestAnnotations.forEach { a ->
                                if (a.note == null) return@forEach
                                if (a.startOffset / PDF_STRIDE != page) return@forEach
                                val lastIdx = (a.endOffset - 1 - page * PDF_STRIDE).coerceIn(0, words.size - 1)
                                if (lastIdx !in words.indices) return@forEach
                                Icon(
                                    imageVector = HugeIcons.MessageSquareText,
                                    contentDescription = tr("Note"),
                                    tint = activeColor,
                                    modifier = Modifier
                                        .offset {
                                            val r = rectOf(lastIdx)
                                            val iconPx = 22.dp.toPx()
                                            IntOffset(
                                                (boxSize.width.toFloat() - iconPx - 2.dp.toPx()).roundToInt(),
                                                ((r.top + r.bottom) / 2f - iconPx / 2).roundToInt()
                                            )
                                        }
                                        .size(22.dp)
                                        .clickable { activeId = a.id }
                                )
                            }

                            // Poignées : étendre ou réduire la sélection mot par mot
                            val sel = selection
                            if (sel != null && selPage == page && words.isNotEmpty()) {
                                val first = rectOf(sel.first.coerceIn(0, words.size - 1))
                                val last = rectOf((sel.second - 1).coerceIn(0, words.size - 1))
                                SelectionHandle(
                                    anchor = Offset(first.left, first.bottom),
                                    color = handleColor,
                                    onDrag = { p ->
                                        val cur = selection
                                        val i = nearestWord(p)
                                        if (cur != null && i >= 0) selection = min(i, cur.second - 1) to cur.second
                                    },
                                    onDone = {}
                                )
                                SelectionHandle(
                                    anchor = Offset(last.right, last.bottom),
                                    color = handleColor,
                                    onDrag = { p ->
                                        val cur = selection
                                        val i = nearestWord(p)
                                        if (cur != null && i >= 0) selection = cur.first to max(i + 1, cur.first + 1)
                                    },
                                    onDone = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showIndicator,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("${startPage + 1} / $total", color = Color.White, fontSize = 12.sp)
            }
        }

        // Barre d'actions : sélection en cours ou annotation touchée
        LaunchedEffect(selection == null && activeId == null) {
            if (selection == null && activeId == null) barAnchor = null
        }
        val sel = selection
        val active = latestAnnotations.firstOrNull { it.id == activeId }
        if (sel != null || active != null) {
            val rangeText = if (sel != null) selQuote else active?.quote.orEmpty()
            fun create(kind: String, color: Int, note: String?) {
                val range = sel ?: return
                onAddAnnotation(
                    AnnotationEntity(
                        bookId = bookId,
                        startOffset = selPage * PDF_STRIDE + range.first,
                        endOffset = selPage * PDF_STRIDE + range.second,
                        kind = kind,
                        color = color,
                        note = note,
                        quote = selQuote.trim().take(400)
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
                    if (sel != null) noteRange = sel else noteAnnotation = active
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(rangeText.trim()))
                    Toast.makeText(context, tr("Copié"), Toast.LENGTH_SHORT).show()
                    selection = null
                    activeId = null
                },
                onDelete = active?.let { a -> { onDeleteAnnotation(a); activeId = null } },
                onDefine = onDefine?.let { f -> { f(rangeText.trim()) } },
                onTranslate = onTranslate?.let { f -> { f(rangeText.trim()) } },
                onExtend = sel?.let { cur ->
                    {
                        val list = pageWords[selPage].orEmpty()
                        if (list.isNotEmpty()) selection = extendWordSelection(list, cur)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { barSize = it }
                    .offset {
                        val anchorRect = barAnchor
                        if (anchorRect == null || barSize.width == 0) {
                            IntOffset(-10_000, -10_000)
                        } else {
                            val margin = 8.dp.roundToPx()
                            val gap = 14.dp.roundToPx()
                            val x = (anchorRect.center.x - barSize.width / 2f).roundToInt()
                                .coerceIn(margin, (maxWidthPx - barSize.width - margin).coerceAtLeast(margin))
                            val above = anchorRect.top - barSize.height - gap
                            val y = if (above >= topPad.roundToPx()) above else anchorRect.bottom + gap + 36.dp.roundToPx()
                            IntOffset(x, y.roundToInt())
                        }
                    }
            )
        }

        noteRange?.let { range ->
            NoteDialog(
                initial = "",
                onSave = { note ->
                    onAddAnnotation(
                        AnnotationEntity(
                            bookId = bookId,
                            startOffset = selPage * PDF_STRIDE + range.first,
                            endOffset = selPage * PDF_STRIDE + range.second,
                            kind = "HIGHLIGHT",
                            color = 0,
                            note = note.ifBlank { null },
                            quote = selQuote.trim().take(400)
                        )
                    )
                    noteRange = null
                    selection = null
                },
                onDismiss = { noteRange = null }
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
    }
}
