package com.example.oryn.reader.ui.components

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import com.example.oryn.reader.data.local.preferences.ReaderTextAlign
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.semantics.paneTitle
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
import androidx.compose.ui.platform.LocalDensity
import com.example.oryn.reader.ui.components.CloverGlyph
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
import com.example.oryn.reader.ui.theme.previewColor
import com.example.oryn.reader.ui.theme.pageColor
import com.example.oryn.reader.ui.theme.textColor

/** Fond partagé par la fiche de réglages et la barre du haut (légèrement transparent). */
val ReaderChromeColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface

enum class ReaderSheetFocus { ALL, TYPOGRAPHY, LAYOUT }

enum class ReaderSettingsTab(val label: String, val icon: ImageVector) {
    ESSENTIALS("Essentiels", OrynIcons.Font),
    APPEARANCE("Apparence", OrynIcons.Palette)
}

/**
 * Panneau de réglages de lecture, version compacte :
 * cartes côte à côte, steppers à icônes, puces de thème / police / accent sur une seule ligne.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
    initialFocus: ReaderSheetFocus = ReaderSheetFocus.ALL
) {
    // Primary text/icons must remain readable even when the user selects a low-contrast
    // accent (e.g. cherry rose). Use accent only on its matching primaryContainer surface.
    val panel = readerChromeScheme(currentAccent)
    MaterialTheme(colorScheme = panel, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val collapse: () -> Unit = { scope.launch { sheetState.hide(); onDismiss() }; Unit }
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = null,
            containerColor = Color.Transparent,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            ReaderControlsSheetContent(
                settings, onSettingsChange, currentChapterTitle, currentAccent, onAccentChange, collapse,
                onOpenToc, onOpenSearch, onOpenAnnotations, readingProgress, remainingLabel, onSeek,
                onPrevChapter, onNextChapter, pdfReflow, onTogglePdfReflow, modifier, initialFocus
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    modifier: Modifier = Modifier,
    initialFocus: ReaderSheetFocus = ReaderSheetFocus.ALL
) {
    val colors = MaterialTheme.colorScheme
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var tab by remember { mutableStateOf(ReaderSettingsTab.ESSENTIALS) }
    val motionEnabled = systemAnimationsEnabled(LocalContext.current)
    val closeFocus = remember { FocusRequester() }
    val typographyTarget = remember { BringIntoViewRequester() }
    val layoutTarget = remember { BringIntoViewRequester() }
    LaunchedEffect(initialFocus) {
        withFrameNanos { }
        closeFocus.requestFocus()
        when (initialFocus) {
            ReaderSheetFocus.TYPOGRAPHY -> typographyTarget.bringIntoView()
            ReaderSheetFocus.LAYOUT -> layoutTarget.bringIntoView()
            ReaderSheetFocus.ALL -> Unit
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { paneTitle = tr("Réglages de lecture") }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                    if (it.nativeKeyEvent.repeatCount == 0) onDismiss()
                    true
                } else false
            },
        shape = RoundedCornerShape(com.example.oryn.reader.ui.theme.OrynShape.sheet),
        color = ReaderChromeColor,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = minOf(640.dp, screenHeight * 0.82f))
                .animateContentSize(tween(if (motionEnabled) 160 else 0))
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Poignée : glisser vers le bas ou toucher pour fermer
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.6f)
                    .heightIn(min = 48.dp)
                    .focusRequester(closeFocus)
                    .semantics { contentDescription = tr("Réduire les commandes") }
                    .clickable(role = Role.Button, onClickLabel = tr("Réduire les commandes"), onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.5f))
                )
            }

            // The Reader Pill already exposes contents, annotations and reading
            // progress. Keep only unique actions accessible from the sheet.
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("Réglages de lecture"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                onOpenSearch?.let { search ->
                    IconToggle(OrynIcons.Search, tr("Recherche"), size = 48.dp, onClick = search)
                }
                onTogglePdfReflow?.let { toggle ->
                    IconToggle(
                        if (pdfReflow) OrynIcons.Pages else OrynIcons.Font,
                        if (pdfReflow) tr("Page") else tr("Texte"),
                        size = 48.dp,
                        onClick = toggle
                    )
                }
            }
            // Two labelled tabs are easier to discover than icon-only segmented options.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.surfaceContainerLow)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReaderSettingsTab.entries.forEach { section ->
                    val active = tab == section
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.primaryContainer else Color.Transparent)
                            .selectable(selected = active, role = Role.Tab, onClick = { tab = section })
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            section.icon, contentDescription = null,
                            tint = if (active) colors.onPrimaryContainer else colors.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tr(section.label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) colors.onPrimaryContainer else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (current) {
                        ReaderSettingsTab.ESSENTIALS -> EssentialsPage(settings, onSettingsChange, typographyTarget, layoutTarget)
                        ReaderSettingsTab.APPEARANCE -> AppearancePage(
                            settings, onSettingsChange, currentAccent, onAccentChange
                        )
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
            .size(48.dp)
            .clickable(role = Role.RadioButton, onClickLabel = trf("Thème %s", preset.label), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(preset.pageColor)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(OrynIcons.Check, null, tint = preset.textColor, modifier = Modifier.size(16.dp))
        }
    }
}

/* ───────────────────────── Pages ───────────────────────── */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.EssentialsPage(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    typographyTarget: BringIntoViewRequester,
    layoutTarget: BringIntoViewRequester
) {
    val columns = if (LocalConfiguration.current.screenWidthDp < 380) 1 else 2
    val size = settings.fontSizePt.coerceIn(10, 36)
    val spacing = settings.lineSpacing.coerceIn(1f, 2.5f)
    // Taille + interligne côte à côte
    FlowRow(Modifier.fillMaxWidth().bringIntoViewRequester(typographyTarget), maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    // One compact font selector avoids four wide cards while keeping every option.
    MiniCard {
        var fontsOpen by remember { mutableStateOf(false) }
        CardTitle(OrynIcons.Font, tr("Police"))
        Box {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = tr("Police")) { fontsOpen = true }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr(settings.fontFamily.label), style = MaterialTheme.typography.labelLarge)
                Icon(OrynIcons.Chevron, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = fontsOpen, onDismissRequest = { fontsOpen = false }) {
                ReaderFontFamily.entries.forEach { font ->
                    DropdownMenuItem(
                        text = { Text(tr(font.label)) },
                        onClick = {
                            fontsOpen = false
                            onSettingsChange { it.copy(fontFamily = font) }
                        },
                        trailingIcon = {
                            if (settings.fontFamily == font) {
                                Icon(OrynIcons.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    }

    // Keep alignment and margins horizontal at normal width; split into full
    // width cards when the device or accessibility font scale needs room.
    FlowRow(
        Modifier.fillMaxWidth().bringIntoViewRequester(layoutTarget),
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MiniCard(modifier = Modifier.weight(1f), fillWidth = false) {
            CardTitle(OrynIcons.AlignLeft, tr("Alignement"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconToggle(OrynIcons.AlignLeft, tr("Aligné à gauche"), settings.textAlign == ReaderTextAlign.START) {
                    onSettingsChange { it.copy(textAlign = ReaderTextAlign.START) }
                }
                IconToggle(OrynIcons.AlignCenter, tr("Centré"), settings.textAlign == ReaderTextAlign.CENTER) {
                    onSettingsChange { it.copy(textAlign = ReaderTextAlign.CENTER) }
                }
                IconToggle(OrynIcons.AlignJustify, tr("Justifié"), settings.textAlign == ReaderTextAlign.JUSTIFY) {
                    onSettingsChange { it.copy(textAlign = ReaderTextAlign.JUSTIFY) }
                }
            }
        }
        MiniCard(modifier = Modifier.weight(1f), fillWidth = false) {
            CardTitle(OrynIcons.MarginNormal, tr("Marges"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    Triple(ReaderMarginSize.NARROW, OrynIcons.MarginNarrow, tr("Marges étroites")),
                    Triple(ReaderMarginSize.NORMAL, OrynIcons.MarginNormal, tr("Marges normales")),
                    Triple(ReaderMarginSize.WIDE, OrynIcons.MarginWide, tr("Marges larges"))
                ).forEach { (margin, icon, description) ->
                    IconToggle(icon, description, settings.marginSize == margin) {
                        onSettingsChange { it.copy(marginSize = margin) }
                    }
                }
            }
        }
    }
    MiniCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val scrolling = settings.scrollMode == ReaderScrollMode.SCROLL
            Icon(if (scrolling) OrynIcons.ScrollVertical else OrynIcons.Pages,
                contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f)) {
                Text(tr("Mode de lecture"), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (scrolling) tr("Défilement") else tr("Pages"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = scrolling,
                onCheckedChange = { enabled ->
                    onSettingsChange { it.copy(scrollMode = if (enabled) ReaderScrollMode.SCROLL else ReaderScrollMode.PAGES) }
                },
                modifier = Modifier.semantics { contentDescription = tr("Mode de lecture") }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.AppearancePage(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    currentAccent: AppAccentTheme,
    onAccentChange: (AppAccentTheme) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val narrowChoices = LocalConfiguration.current.screenWidthDp < 380 ||
        LocalDensity.current.fontScale >= 1.3f

    // Accent comes first. Preset IDs remain stable; only presentation order
    // changes, with OLED deliberately last.
    MiniCard {
        CardTitle(OrynIcons.Sparkle, tr("Couleur d'accentuation"))
        Row(Modifier.fillMaxWidth()) {
            AppAccentTheme.choices.forEach { accent ->
                CloverAccentOption(
                    accent = accent,
                    selected = currentAccent == accent,
                    dark = dark,
                    onClick = { onAccentChange(accent) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    MiniCard {
        CardTitle(OrynIcons.Palette, tr("Thème de lecture"))
        FlowRow(
            Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (narrowChoices) 2 else 3,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                ReaderThemePreset.LIGHT,
                ReaderThemePreset.BEIGE,
                ReaderThemePreset.MIDNIGHT,
                ReaderThemePreset.NUIT,
                ReaderThemePreset.OLED
            ).forEach { preset ->
                ThemeChip(
                    preset = preset,
                    selected = settings.themePreset == preset,
                    onClick = { onSettingsChange { it.copy(themePreset = preset) } },
                    modifier = Modifier.weight(1f)
                )
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
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.65f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(maxOf(size, 48.dp))
            .clip(shape)
            .background(if (selected) colors.primaryContainer else container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (selected) this.selected = true },
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
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
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
            fontSize = 11.sp,
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
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(preset.pageColor)
                .border(
                    BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) colors.onSurface else colors.outlineVariant
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", color = preset.textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(
            tr(preset.label),
            fontSize = 11.sp,
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
            .heightIn(min = 48.dp)
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
