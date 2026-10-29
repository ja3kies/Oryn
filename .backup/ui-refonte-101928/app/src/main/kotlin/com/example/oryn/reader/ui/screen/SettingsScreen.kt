package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.AppLanguage
import com.example.oryn.reader.i18n.Lang
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.components.OrynIcons
import com.example.oryn.reader.ui.components.inkSecondary
import androidx.compose.ui.graphics.Color
import com.example.oryn.reader.ui.components.solidSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.local.preferences.ReaderPreferencesRepository
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import com.example.oryn.reader.ui.components.CloverAccentOption
import com.example.oryn.reader.ui.components.navBarClearance
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.AppThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    currentAccent: AppAccentTheme,
    onAccentChange: (AppAccentTheme) -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readerPrefs = remember { ReaderPreferencesRepository(context) }
    val readerSettings by readerPrefs.readerSettingsFlow.collectAsState(initial = ReaderSettings())
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f

    Column(
        modifier = modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = navBarClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = tr("Réglages"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onBackground,
            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
        )

        // Thème : trois cartes-icônes
        SettingsCard(title = tr("Thème de l'application"), icon = OrynIcons.Palette) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    val icon = when (mode) {
                        AppThemeMode.SYSTEM -> OrynIcons.AutoTheme
                        AppThemeMode.LIGHT -> OrynIcons.Sun
                        AppThemeMode.DARK -> OrynIcons.Moon
                    }
                    ThemeModeChoice(
                        icon = icon,
                        label = mode.label,
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Accent : trèfles, 4 par ligne
        SettingsCard(title = tr("Couleur d'accentuation"), icon = OrynIcons.Sparkle) {
            AppAccentTheme.choices.chunked(5).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { accent ->
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
        }

        // Langue : suit le système, ou force le français / l'anglais
        SettingsCard(title = "Langue", icon = OrynIcons.Globe) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { language ->
                    LanguageChoice(
                        badge = when (language) {
                            AppLanguage.SYSTEM -> "Auto"
                            AppLanguage.FRENCH -> "FR"
                            AppLanguage.ENGLISH -> "EN"
                        },
                        label = when (language) {
                            AppLanguage.SYSTEM -> "Système"
                            AppLanguage.FRENCH -> "Français"
                            AppLanguage.ENGLISH -> "English"
                        },
                        selected = Lang.mode == language,
                        onClick = { Lang.set(context, language) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Lecture : barre d'état
        SettingsCard(title = tr("Lecture"), icon = OrynIcons.BookOpen) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(OrynIcons.EyeOff, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Masquer la barre d'état"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        tr("Pendant la lecture, pour un affichage plein écran."),
                        style = MaterialTheme.typography.bodySmall,
                        color = inkSecondary()
                    )
                }
                Switch(
                    checked = readerSettings.hideStatusBar,
                    onCheckedChange = { hidden ->
                        scope.launch { readerPrefs.updateSettings { it.copy(hideStatusBar = hidden) } }
                    }
                )
            }
        }

        SettingsCard(title = tr("Découvrir Oryn"), icon = OrynIcons.Sparkle) {
            TextButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(tr("Revoir la présentation"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().solidSurface(RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = inkSecondary()
                )
            }
            content()
        }
    }
}

@Composable
private fun ThemeModeChoice(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.22f) else colors.onSurface.copy(alpha = 0.07f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface
        )
    }
}

@Composable
private fun LanguageChoice(
    badge: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.22f) else colors.onSurface.copy(alpha = 0.07f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            badge,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) colors.primary else colors.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface
        )
    }
}
