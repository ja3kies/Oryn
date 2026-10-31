package com.example.oryn.reader.ui.components

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.example.oryn.reader.data.local.preferences.ReaderTextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oryn.reader.data.local.preferences.ReaderFontFamily
import com.example.oryn.reader.data.local.preferences.ReaderMarginSize
import com.example.oryn.reader.data.local.preferences.ReaderScrollMode
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import com.example.oryn.reader.data.local.preferences.ReaderThemePreset
import com.example.oryn.reader.ui.screen.ReaderOpenDyslexicFont
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.orynColorScheme
import com.example.oryn.reader.ui.theme.previewColor
import com.example.oryn.reader.ui.theme.pageColor
import com.example.oryn.reader.ui.theme.textColor

/** Surface du panneau de commandes de lecture, liée au thème de l’application. */
val ReaderChromeColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

enum class ReaderSettingsTab(val label: String, val icon: ImageVector) {
    ESSENTIALS("Essentiels", OrynIcons.Font),
    APPEARANCE("Apparence", OrynIcons.Palette)
}

/**
 * Panneau de réglages de lecture, version compacte :
 * cartes côte à côte, steppers à icônes, puces de thème / police / accent sur une seule ligne.
 */
@Composable
fun ReaderControlsSheet(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    currentChapterTitle: String,
    currentAccent: AppAccentTheme = AppAccentTheme.ORANGE,
    onAccentChange: (AppAccentTheme) -> Unit = {},
    onDismiss: () -> Unit = {},
    onOpenToc: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenAnnotations: (() -> Unit)? = null,
    readingProgress: Float = 0f,
    remainingLabel: String? = null,
    onSeek: ((Float) -> Unit)? = null,
    onPrevChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
    pdfReflow: Boolean = false,
    onTogglePdfReflow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Le panneau suit le thème de l'application au lieu d'imposer une surface sombre.
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val panel = remember(currentAccent, appIsDark) {
        val accent = if (appIsDark) currentAccent.accentDark else currentAccent.accentLight
        orynColorScheme(appIsDark, currentAccent).copy(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.55f) Color(0xFF0F3A3A) else Color.White
        )
    }
    MaterialTheme(colorScheme = panel, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        ReaderControlsSheetContent(
            settings, onSettingsChange, currentChapterTitle, currentAccent, onAccentChange, onDismiss,
            onOpenToc, onOpenSearch, onOpenAnnotations, readingProgress, remainingLabel, onSeek,
            onPrevChapter, onNextChapter, pdfReflow, onTogglePdfReflow, modifier
        )
    }
}

@Composable
private fun ReaderControlsSheetContent(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    currentChapterTitle: String,
    currentAccent: AppAccentTheme = AppAccentTheme.ORANGE,
    onAccentChange: (AppAccentTheme) -> Unit = {},
    onDismiss: () -> Unit = {},
    onOpenToc: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenAnnotations: (() -> Unit)? = null,
    readingProgress: Float = 0f,
    remainingLabel: String? = null,
    onSeek: ((Float) -> Unit)? = null,
    onPrevChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
    /** PDF seulement : bascule entre les pages telles quelles et le texte remis en forme. */
    pdfReflow: Boolean = false,
    onTogglePdfReflow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissDistance = with(LocalDensity.current) { 64.dp.toPx() }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var tab by remember { mutableStateOf(ReaderSettingsTab.ESSENTIALS) }
    val motionEnabled = systemAnimationsEnabled(LocalContext.current)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .graphicsLayer { translationY = dragOffset.value },
        shape = RoundedCornerShape(34.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = minOf(520.dp, screenHeight * 0.7f))
                .animateContentSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Poignée : glisser vers le bas ou toucher pour fermer
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.6f)
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                scope.launch {
                                    dragOffset.snapTo((dragOffset.value + delta).coerceIn(-dismissDistance, dismissDistance * 2f))
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (dragOffset.value > dismissDistance) {
                                        if (expanded) {
                                            expanded = false
                                            dragOffset.animateTo(
                                                0f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.8f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        } else {
                                            onDismiss()
                                            delay(400)
                                            dragOffset.snapTo(0f)
                                        }
                                    } else {
                                        if (dragOffset.value < 0f) expanded = true
                                        dragOffset.animateTo(0f)
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { dragOffset.animateTo(0f) } }
                        )
                    }
                    .clickable(role = Role.Button, onClickLabel = if (expanded) tr("Réduire les réglages") else tr("Tous les réglages")) { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(colors.outline)
                )
            }

            // Outils rapides : sommaire, recherche, annotations
            if (onOpenToc != null || onOpenSearch != null || onOpenAnnotations != null || onTogglePdfReflow != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    onOpenToc?.let { ToolButton(OrynIcons.Toc, tr("Sommaire"), it, Modifier.weight(1f)) }
                    onOpenSearch?.let { ToolButton(OrynIcons.Search, tr("Recherche"), it, Modifier.weight(1f)) }
                    onOpenAnnotations?.let { ToolButton(OrynIcons.Highlighter, tr("Annotations"), it, Modifier.weight(1f)) }
                    onTogglePdfReflow?.let { ToolButton(if (pdfReflow) OrynIcons.Pages else OrynIcons.Font, if (pdfReflow) tr("Page") else tr("Texte"), it, Modifier.weight(1f)) }
                }
            }

            // Progression : glisser pour aller à un endroit du livre (+ chapitre précédent / suivant)
            if (onSeek != null) {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val shown = (dragging ?: readingProgress).coerceIn(0f, 1f)
                MiniCard {
                    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconToggle(OrynIcons.ArrowLeft, tr("Chapitre précédent"), enabled = onPrevChapter != null, size = 32.dp) {
                                onPrevChapter?.invoke()
                            }
                            Slider(
                                value = shown,
                                onValueChange = { dragging = it },
                                onValueChangeFinished = { dragging?.let(onSeek); dragging = null },
                                modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.graphicsLayer { rotationZ = 180f }) {
                                IconToggle(OrynIcons.ArrowLeft, tr("Chapitre suivant"), enabled = onNextChapter != null, size = 32.dp) {
                                    onNextChapter?.invoke()
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${(shown * 100).toInt()} %",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                remainingLabel ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Text(
                            currentChapterTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
            }

            // Réglages rapides : taille + thème
            if (!expanded) {
                val size = settings.fontSizePt.coerceIn(10, 36)
                MiniCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconToggle(OrynIcons.Minus, tr("Diminuer la taille"), enabled = size > 10, container = colors.surfaceContainer) {
                            onSettingsChange { it.copy(fontSizePt = (it.fontSizePt - 1).coerceAtLeast(10)) }
                        }
                        Text("$size pt", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                        IconToggle(OrynIcons.Plus, tr("Augmenter la taille"), enabled = size < 36, container = colors.surfaceContainer) {
                            onSettingsChange { it.copy(fontSizePt = (it.fontSizePt + 1).coerceAtMost(36)) }
                        }
                        Spacer(Modifier.weight(1f))
                        ReaderThemePreset.entries.forEach { preset ->
                            ThemeDot(preset, settings.themePreset == preset) {
                                onSettingsChange { it.copy(themePreset = preset) }
                            }
                        }
                    }
                }
                Text(
                    tr("Tous les réglages"),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("Réglages de lecture"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                    }
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(colors.surfaceContainerLow)
                            .padding(2.dp)
                    ) {
                        ReaderSettingsTab.entries.forEach { t ->
                            IconToggle(
                                icon = t.icon,
                                description = t.label,
                                selected = tab == t,
                                shape = CircleShape,
                                onClick = { tab = t }
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        if (motionEnabled) fadeIn(tween(140)) togetherWith fadeOut(tween(90))
                        else EnterTransition.None togetherWith ExitTransition.None
                    },
                    label = "readerSettingsTab"
                ) { current ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (current) {
                            ReaderSettingsTab.ESSENTIALS -> EssentialsPage(settings, onSettingsChange)
                            ReaderSettingsTab.APPEARANCE -> AppearancePage(
                                settings, onSettingsChange, currentAccent, onAccentChange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerHigh
    ) {
        Column(
            Modifier.heightIn(min = 56.dp).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun ThemeDot(preset: ReaderThemePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(preset.pageColor)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, CircleShape)
            .semantics { contentDescription = trf("Thème %s", preset.name) }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(OrynIcons.Check, null, tint = preset.textColor, modifier = Modifier.size(14.dp))
    }
}

/* ───────────────────────── Pages ───────────────────────── */

@Composable
private fun ColumnScope.EssentialsPage(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit
) {
    val size = settings.fontSizePt.coerceIn(10, 36)
    val spacing = settings.lineSpacing.coerceIn(1f, 2.5f)

    // Taille + interligne côte à côte
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperCard(
            icon = OrynIcons.TextSize,
            title = tr("Taille"),
            value = "$size pt",
            canDecrease = size > 10,
            canIncrease = size < 36,
            onDecrease = { onSettingsChange { it.copy(fontSizePt = (it.fontSizePt - 1).coerceAtLeast(10)) } },
            onIncrease = { onSettingsChange { it.copy(fontSizePt = (it.fontSizePt + 1).coerceAtMost(36)) } },
            modifier = Modifier.weight(1f)
        )
        StepperCard(
            icon = OrynIcons.LineSpacing,
            title = tr("Interligne"),
            value = "×%.1f".format(spacing),
            canDecrease = spacing > 1.05f,
            canIncrease = spacing < 2.45f,
            onDecrease = {
                onSettingsChange { it.copy(lineSpacing = (((it.lineSpacing - 0.1f) * 10).toInt() / 10f).coerceAtLeast(1f)) }
            },
            onIncrease = {
                onSettingsChange { it.copy(lineSpacing = (((it.lineSpacing + 0.1f) * 10).toInt() / 10f).coerceAtMost(2.5f)) }
            },
            modifier = Modifier.weight(1f)
        )
    }

    // Police : 4 puces sur une ligne
    MiniCard {
        CardTitle(OrynIcons.Font, tr("Police"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderFontFamily.entries.forEach { font ->
                FontChip(
                    font = font,
                    selected = settings.fontFamily == font,
                    onClick = { onSettingsChange { it.copy(fontFamily = font) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Alignement | carte « Marges + Défilement » coupée en deux moitiés côte à côte
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniCard(fillWidth = false) {
            CardTitle(OrynIcons.AlignLeft, tr("Texte"))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconToggle(OrynIcons.AlignLeft, tr("Aligné à gauche"), settings.textAlign == ReaderTextAlign.START, size = 28.dp) {
                    onSettingsChange { it.copy(textAlign = ReaderTextAlign.START) }
                }
                IconToggle(OrynIcons.AlignCenter, tr("Centré"), settings.textAlign == ReaderTextAlign.CENTER, size = 28.dp) {
                    onSettingsChange { it.copy(textAlign = ReaderTextAlign.CENTER) }
                }
                IconToggle(OrynIcons.AlignJustify, tr("Justifié"), settings.textAlign == ReaderTextAlign.JUSTIFY, size = 28.dp) {
                    onSettingsChange { it.copy(textAlign = ReaderTextAlign.JUSTIFY) }
                }
            }
        }
        MiniCard(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Moitié gauche : marges
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardTitle(OrynIcons.MarginNormal, tr("Marges"))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            Triple(ReaderMarginSize.NARROW, OrynIcons.MarginNarrow, tr("Marges étroites")),
                            Triple(ReaderMarginSize.NORMAL, OrynIcons.MarginNormal, tr("Marges normales")),
                            Triple(ReaderMarginSize.WIDE, OrynIcons.MarginWide, tr("Marges larges"))
                        ).forEach { (margin, icon, description) ->
                            IconToggle(icon, description, settings.marginSize == margin, size = 28.dp) {
                                onSettingsChange { it.copy(marginSize = margin) }
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                // Moitié droite : défilement
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardTitle(OrynIcons.ScrollVertical, tr("Défilement"))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconToggle(OrynIcons.Pages, tr("Mode pages"), settings.scrollMode == ReaderScrollMode.PAGES, size = 28.dp) {
                            onSettingsChange { it.copy(scrollMode = ReaderScrollMode.PAGES) }
                        }
                        IconToggle(OrynIcons.ScrollVertical, tr("Mode défilement"), settings.scrollMode == ReaderScrollMode.SCROLL, size = 28.dp) {
                            onSettingsChange { it.copy(scrollMode = ReaderScrollMode.SCROLL) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AppearancePage(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    currentAccent: AppAccentTheme,
    onAccentChange: (AppAccentTheme) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f

    // Thèmes de lecture : 4 mini-cartes sur une ligne
    MiniCard {
        CardTitle(OrynIcons.Palette, tr("Thème de lecture"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderThemePreset.entries.forEach { preset ->
                ThemeChip(
                    preset = preset,
                    selected = settings.themePreset == preset,
                    onClick = { onSettingsChange { it.copy(themePreset = preset) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Accent : pastilles de couleur sur une seule ligne
    MiniCard {
        CardTitle(OrynIcons.Sparkle, tr("Accent"))
        Row(Modifier.fillMaxWidth()) {
            AppAccentTheme.choices.forEach { accent ->
                AccentDot(
                    accent = accent,
                    selected = currentAccent == accent,
                    dark = dark,
                    onClick = { onAccentChange(accent) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Luminosité : icône, curseur, bouton auto
    MiniCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                OrynIcons.Sun,
                contentDescription = tr("Luminosité"),
                tint = colors.primary,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp)
            )
            Slider(
                value = settings.brightness.coerceIn(0.01f, 1f),
                onValueChange = { v -> onSettingsChange { it.copy(brightness = v, autoBrightness = false) } },
                valueRange = 0.01f..1f,
                enabled = !settings.autoBrightness,
                modifier = Modifier.weight(1f)
            )
            IconToggle(OrynIcons.SunAuto, tr("Luminosité automatique"), settings.autoBrightness) {
                onSettingsChange { it.copy(autoBrightness = !it.autoBrightness) }
            }
        }
    }
}

/* ───────────────────────── Briques ───────────────────────── */

@Composable
private fun MiniCard(
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun CardTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
        Text(
            title,
            maxLines = 1,
            fontSize = 11.sp,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Bouton d'icône carré : fond « primaryContainer » quand il est actif. */
@Composable
private fun IconToggle(
    icon: ImageVector,
    description: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    container: Color = Color.Transparent,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    size: Dp = 36.dp,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(if (size < 34.dp) 18.dp else 20.dp))
    }
}

@Composable
private fun StepperCard(
    icon: ImageVector,
    title: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    MiniCard(modifier) {
        CardTitle(icon, title)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconToggle(OrynIcons.Minus, tr("Diminuer"), enabled = canDecrease, container = colors.surfaceContainer, onClick = onDecrease)
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            IconToggle(OrynIcons.Plus, tr("Augmenter"), enabled = canIncrease, container = colors.surfaceContainer, onClick = onIncrease)
        }
    }
}

@Composable
private fun FontChip(
    font: ReaderFontFamily,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val family = when (font) {
        ReaderFontFamily.SERIF -> FontFamily.Serif
        ReaderFontFamily.SANS_SERIF -> FontFamily.SansSerif
        ReaderFontFamily.DYSLEXIC -> ReaderOpenDyslexicFont ?: FontFamily.Monospace
        ReaderFontFamily.SYSTEM -> FontFamily.Default
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "Aa",
            fontSize = 17.sp,
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface
        )
        Text(
            font.label,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeChip(
    preset: ReaderThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(preset.pageColor)
                .border(
                    BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) colors.primary else colors.outlineVariant
                    ),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", color = preset.textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(
            preset.label,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) colors.primary else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun AccentDot(
    accent: AppAccentTheme,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dotColor = accent.previewColor(dark)
    val ink = if (dotColor.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = modifier
            .height(34.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = tr(accent.label) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(dotColor)
                .then(
                    if (selected) Modifier.border(2.dp, colors.onSurface, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                selected -> Icon(OrynIcons.Check, null, tint = ink, modifier = Modifier.size(15.dp))
                accent == AppAccentTheme.DYNAMIC -> Icon(OrynIcons.Sparkle, null, tint = ink, modifier = Modifier.size(14.dp))
            }
        }
    }
}
