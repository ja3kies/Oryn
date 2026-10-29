package com.example.oryn.reader.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/* ───────────────────────── Modèle ───────────────────────── */

enum class ReaderFont(val label: String) {
    Serif("Serif"), SansSerif("Sans-serif"), OpenDyslexic("OpenDyslexic"), Device("Système")
}

enum class ReadingMode(val label: String) { Pages("Pages"), Scroll("Défilement") }

enum class ReaderTheme(val label: String) {
    Auto("Auto"), Light("Clair"), Sepia("Sépia"), Dark("Sombre"),
    Amoled("Nuit"), Mint("Menthe"), Lavender("Lavande"), Peach("Pêche")
}

@Immutable
data class ReaderPalette(val bg: Color, val fg: Color, val accent: Color)

data class ReaderSettings(
    val textSize: Float = 16f,
    val lineSpacing: Float = 1.6f,
    val font: ReaderFont = ReaderFont.OpenDyslexic,
    val mode: ReadingMode = ReadingMode.Pages,
    val margin: Float = 24f,
    val justify: Boolean = false,
    val theme: ReaderTheme = ReaderTheme.Auto
)

fun ReaderFont.family(): FontFamily = when (this) {
    ReaderFont.Serif -> FontFamily.Serif
    ReaderFont.SansSerif -> FontFamily.SansSerif
    ReaderFont.OpenDyslexic -> OpenDyslexicFamily
    ReaderFont.Device -> FontFamily.Default
}

// Les thèmes de lecture sont volontairement indépendants de la palette de l'app
fun ReaderTheme.palette(dark: Boolean): ReaderPalette = when (this) {
    ReaderTheme.Auto -> (if (dark) ReaderTheme.Dark else ReaderTheme.Light).palette(dark)
    ReaderTheme.Light -> ReaderPalette(Color(0xFFFFFFFF), Color(0xFF1B1B1F), Color(0xFF5B5BD6))
    ReaderTheme.Sepia -> ReaderPalette(Color(0xFFF4ECD8), Color(0xFF5B4636), Color(0xFFB5651D))
    ReaderTheme.Dark -> ReaderPalette(Color(0xFF1E1E24), Color(0xFFE6E1E9), Color(0xFFB8A9FF))
    ReaderTheme.Amoled -> ReaderPalette(Color(0xFF000000), Color(0xFFBDBDC6), Color(0xFF7FD6FF))
    ReaderTheme.Mint -> ReaderPalette(Color(0xFFE2F6EA), Color(0xFF1D3B2F), Color(0xFF1FA37A))
    ReaderTheme.Lavender -> ReaderPalette(Color(0xFFECE6FA), Color(0xFF2B2350), Color(0xFF8A5CF5))
    ReaderTheme.Peach -> ReaderPalette(Color(0xFFFFE9DD), Color(0xFF4A2B22), Color(0xFFFF7A4D))
}

@Composable
fun ReaderTheme.resolve(): ReaderPalette =
    palette(MaterialTheme.colorScheme.background.luminance() < 0.5f)

/* ───────────────────────── Écran ───────────────────────── */

@Composable
fun ReaderScreen(
    modifier: Modifier = Modifier,
    title: String = "Le Petit Prince",
    author: String = "Antoine de Saint-Exupéry",
    onBack: () -> Unit = {}
) {
    var settings by remember { mutableStateOf(ReaderSettings()) }
    var showSheet by remember { mutableStateOf(true) }
    val pal = settings.theme.resolve()

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(pal.bg, lerp(pal.bg, pal.accent, 0.12f))))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ReaderTopBar(title, author, pal.fg, onBack) { showSheet = !showSheet }
            ReaderText(settings, pal, Modifier.weight(1f))
        }
        if (showSheet) {
            ReaderSettingsPanel(
                settings = settings,
                onChange = { settings = it },
                onDismiss = { showSheet = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun ReaderTopBar(title: String, author: String, fg: Color, onBack: () -> Unit, onSettings: () -> Unit) {
    val box = fg.copy(alpha = 0.10f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(Icons.Rounded.ArrowBack, "Retour", 48.dp, container = box, tint = fg, onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
            Text(author, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.65f), maxLines = 1)
        }
        GlassIconButton(Icons.Rounded.BookmarkBorder, "Marque-page", 48.dp, container = box, tint = fg, onClick = {})
        Spacer(Modifier.width(12.dp))
        GlassIconButton(Icons.Rounded.Settings, "Réglages", 48.dp, container = box, tint = fg, onClick = onSettings)
    }
}

@Composable
private fun ReaderText(settings: ReaderSettings, pal: ReaderPalette, modifier: Modifier = Modifier) {
    val align = if (settings.justify) TextAlign.Justify else TextAlign.Start
    val size = settings.textSize.sp
    val lh = (settings.textSize * settings.lineSpacing).sp
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = settings.margin.dp, vertical = 16.dp)
    ) {
        Text(
            "Alors, le petit prince se cacha dans les roseaux et, pendant longtemps, il resta là à écouter. Il entendit la voix du serpent qui disait :",
            fontFamily = settings.font.family(), fontSize = size, lineHeight = lh,
            color = pal.fg, textAlign = align
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "« Tu es venu ici pour mourir. »",
            fontFamily = settings.font.family(), fontSize = size, lineHeight = lh,
            fontStyle = FontStyle.Italic, color = pal.fg, textAlign = align
        )
    }
}

/* ───────────────────────── Feuille de réglages ───────────────────────── */

@Composable
fun ReaderSettingsPanel(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val pa = primaryAccent()
    val sa = secondaryAccent()
    val ta = tertiaryAccent()

    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        color = cs.surface.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier
                .heightIn(max = 660.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(cs.primaryContainer.copy(alpha = 0.45f), Color.Transparent),
                        startY = 0f, endY = 600f
                    )
                )
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(cs.outline.copy(alpha = 0.5f))
                    .clickable(onClick = onDismiss)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Rounded.MenuBook, size = 56.dp,
                    brush = Brush.linearGradient(listOf(cs.primary, cs.tertiary)),
                    content = cs.onPrimary
                )
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text("Réglages de lecture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                    Text("Personnalise ton expérience de lecture", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Box {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHigh.copy(alpha = 0.7f))
                            .clickable { menu = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MoreHoriz, "Options", Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Réinitialiser") },
                            onClick = { onChange(ReaderSettings()); menu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Fermer") },
                            onClick = { menu = false; onDismiss() }
                        )
                    }
                }
            }

            GlassSegmented(
                items = listOf(0, 1),
                selected = tab,
                label = { if (it == 0) "Essentiels" else "Plus de réglages" },
                icon = { if (it == 0) Icons.Rounded.AutoAwesome else Icons.Rounded.Tune },
                filled = false,
                onSelect = { tab = it }
            )

            if (tab == 0) {
                /* ── Thèmes de lecture : cards colorées ── */
                GlassCard(tint = sa.base) {
                    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Palette, 44.dp, container = sa.container, content = sa.onContainer)
                            Column(Modifier.padding(start = 14.dp)) {
                                Text("Thème de lecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                Text(settings.theme.label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ReaderTheme.values().forEach { t ->
                                ThemeCard(t, t == settings.theme) { onChange(settings.copy(theme = t)) }
                            }
                        }
                    }
                }

                StepperCard(
                    icon = Icons.Rounded.FormatSize,
                    title = "Taille du texte",
                    valueLabel = "${settings.textSize.roundToInt()} pt",
                    value = settings.textSize,
                    range = 10f..32f,
                    step = 1f,
                    accent = pa,
                    onChange = { onChange(settings.copy(textSize = it)) }
                )
                StepperCard(
                    icon = Icons.Rounded.FormatLineSpacing,
                    title = "Interligne",
                    valueLabel = String.format(java.util.Locale.getDefault(), "%.1f ×", settings.lineSpacing),
                    value = settings.lineSpacing,
                    range = 1.0f..2.4f,
                    step = 0.1f,
                    accent = ta,
                    onChange = { onChange(settings.copy(lineSpacing = it)) }
                )

                /* ── Polices : cards « Aa » ── */
                GlassCard(tint = ta.base) {
                    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(ta.container.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aa", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ta.onContainer)
                            }
                            Column(Modifier.padding(start = 14.dp)) {
                                Text("Police de caractères", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                Text(settings.font.label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val accents = listOf(cs.primary, cs.secondary, cs.tertiary, lerp(cs.primary, cs.tertiary, 0.5f))
                            ReaderFont.values().forEachIndexed { i, f ->
                                FontCard(f, accents[i % accents.size], f == settings.font) {
                                    onChange(settings.copy(font = f))
                                }
                            }
                        }
                    }
                }

                GlassCard(tint = pa.base) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.MenuBook, 44.dp, container = pa.container, content = pa.onContainer)
                            Column(Modifier.padding(start = 14.dp)) {
                                Text("Mode de lecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                Text(settings.mode.label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        GlassSegmented(
                            items = ReadingMode.values().toList(),
                            selected = settings.mode,
                            label = { it.label },
                            icon = { if (it == ReadingMode.Pages) Icons.Rounded.MenuBook else Icons.Rounded.ViewList },
                            filled = true,
                            onSelect = { onChange(settings.copy(mode = it)) }
                        )
                    }
                }
                GlassCard(onClick = { tab = 1 }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
                        Text(
                            "Plus de réglages",
                            Modifier.weight(1f).padding(start = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurface
                        )
                        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
                    }
                }
            } else {
                StepperCard(
                    icon = Icons.Rounded.Tune,
                    title = "Marges",
                    valueLabel = "${settings.margin.roundToInt()} dp",
                    value = settings.margin,
                    range = 8f..48f,
                    step = 4f,
                    accent = sa,
                    onChange = { onChange(settings.copy(margin = it)) }
                )
                GlassCard(tint = pa.base) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.FormatLineSpacing, 44.dp, container = pa.container, content = pa.onContainer)
                        Text(
                            "Texte justifié",
                            Modifier.weight(1f).padding(horizontal = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface
                        )
                        Switch(checked = settings.justify, onCheckedChange = { onChange(settings.copy(justify = it)) })
                    }
                }
            }
        }
    }
}
