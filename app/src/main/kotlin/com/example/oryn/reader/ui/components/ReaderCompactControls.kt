package com.example.oryn.reader.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.luminance
import com.example.oryn.reader.data.local.preferences.ReaderScrollMode
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.OrynShape
import com.example.oryn.reader.ui.theme.orynColorScheme
import kotlin.math.roundToInt

/** Stable gutters: toggling chrome must never change the pagination viewport. */
data class ReaderChromeMetrics(val top: Dp, val progress: Dp) {
    val bottom: Dp get() = 64.dp
}
fun readerChromeMetrics(fontScale: Float, chapterDetail: Boolean = false) = ReaderChromeMetrics(
    maxOf(56.dp, ((if (chapterDetail) 36 else 20) * fontScale + 8).dp), maxOf(36.dp, (22 * fontScale + 8).dp)
)

/** A bottom-most input sibling catches gutters without intercepting document gestures or buttons. */
@Composable
fun ReaderSurfaceTapBackdrop(onTap: () -> Unit, modifier: Modifier = Modifier) {
    val latestTap by rememberUpdatedState(onTap)
    Box(modifier.pointerInput(Unit) { detectTapGestures { latestTap() } })
}

@Composable
fun ReaderControlsBackHandler(state: ReaderControlsState, enabled: Boolean = true, onEvent: (ReaderControlsEvent) -> Unit, onExit: () -> Unit) {
    BackHandler(enabled) {
        if (state.backLeavesReader) onExit() else onEvent(ReaderControlsEvent.BACK)
    }
}

/** Enter/Space activate the document only while it owns focus, never while a button does. */
@Composable
fun Modifier.readerControlsKeyModifier(state: ReaderControlsState, enabled: Boolean, onEvent: (ReaderControlsEvent) -> Unit): Modifier {
    var documentFocused by remember { mutableStateOf(false) }
    return this.onPreviewKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) false
        else when {
            event.key == Key.Escape && state != ReaderControlsState.HIDDEN -> {
                if (event.nativeKeyEvent.repeatCount == 0) onEvent(ReaderControlsEvent.BACK)
                true
            }
            event.key == Key.F10 || (documentFocused && event.key in listOf(Key.Enter, Key.Spacebar)) -> {
                if (event.nativeKeyEvent.repeatCount == 0) onEvent(ReaderControlsEvent.SURFACE_TAP)
                true
            }
            else -> false
        }
    }.onFocusChanged { documentFocused = it.isFocused }.focusable()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderCompactControls(
    state: ReaderControlsState,
    metrics: ReaderChromeMetrics,
    chapterTitle: String,
    chapterRemaining: String?,
    bookProgress: Float,
    chapterProgress: Float?,
    bookmarked: Boolean,
    currentAccent: AppAccentTheme,
    scrollMode: ReaderScrollMode = ReaderScrollMode.PAGES,
    onToggleScrollMode: () -> Unit = {},
    onBack: () -> Unit,
    onTimer: () -> Unit,
    onBookmark: () -> Unit,
    onContents: (() -> Unit)?,
    onAnnotations: (() -> Unit)?,
    onExpand: (ReaderSheetFocus) -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = systemAnimationsEnabled(LocalContext.current)
    val chromeDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // No outgoing compact panel underneath the modal sheet, including during its animation.
    if (state == ReaderControlsState.EXPANDED) return
    AnimatedVisibility(
        visible = state == ReaderControlsState.COMPACT,
        enter = if (motion) fadeIn(tween(180)) +
            slideInVertically(initialOffsetY = { it / 5 }, animationSpec = tween(210)) +
            scaleIn(initialScale = 0.97f, animationSpec = tween(210)) else EnterTransition.None,
        exit = if (motion) fadeOut(tween(90)) else ExitTransition.None,
        modifier = modifier
    ) {
        MaterialTheme(colorScheme = orynColorScheme(chromeDark, currentAccent)) {
            val colors = MaterialTheme.colorScheme
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp)
                        .widthIn(max = 600.dp).fillMaxWidth().height(metrics.top)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .testTag("ReaderCompactTopBar").semantics { paneTitle = tr("Commandes de lecture") },
                    shape = RoundedCornerShape(OrynShape.card), color = colors.surface
                ) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChromeAction(OrynIcons.ArrowLeft, tr("Retour"), onBack)
                        Column(
                            Modifier.weight(1f).padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = onContents != null, onClickLabel = tr("Sommaire")) {
                                    onContents?.invoke()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(chapterTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            chapterRemaining?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        ChromeAction(OrynIcons.Clock, tr("Chronomètre de lecture"), onTimer)
                        IconToggleButton(checked = bookmarked, onCheckedChange = { onBookmark() }, modifier = Modifier.size(48.dp)) {
                            Icon(if (bookmarked) OrynIcons.BookmarkFilled else OrynIcons.Bookmark,
                                contentDescription = tr(if (bookmarked) "Retirer le marque-page du livre" else "Marquer le livre"), modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)
                        .widthIn(max = 600.dp).fillMaxWidth().testTag("ReaderCompactBottomControls"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.height(56.dp).pointerInput(Unit) { detectTapGestures { } },
                        shape = CircleShape, color = colors.surface
                    ) {
                        // Five existing actions fit into the compact pill without a hidden scroll gesture.
                        Row(
                            Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ChromeAction(OrynIcons.Toc, tr("Sommaire"), { onContents?.invoke() }, enabled = onContents != null)
                            ChromeAction(OrynIcons.Highlighter, tr("Annotations"), { onAnnotations?.invoke() }, enabled = onAnnotations != null)
                            ChromeAction(
                                if (scrollMode == ReaderScrollMode.SCROLL) OrynIcons.ScrollVertical else OrynIcons.Pages,
                                if (scrollMode == ReaderScrollMode.SCROLL) tr("Passer en mode pages") else tr("Passer en mode défilement"),
                                onToggleScrollMode
                            )
                            ChromeAction(OrynIcons.TextSize, tr("Typographie"), { onExpand(ReaderSheetFocus.TYPOGRAPHY) })
                            ChromeAction(OrynIcons.Menu, tr("Tous les réglages"), { onExpand(ReaderSheetFocus.ALL) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromeAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}
