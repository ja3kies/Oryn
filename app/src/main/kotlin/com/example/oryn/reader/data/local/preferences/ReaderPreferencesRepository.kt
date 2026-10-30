package com.example.oryn.reader.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader_settings")

enum class ReaderThemePreset(val label: String) {
    LIGHT("Clair"),
    BEIGE("Beige"),
    MIDNIGHT("Prune"),
    OLED("Noir OLED"),
    NUIT("Nuit Oryn")
}

enum class ReaderFontFamily(val label: String) {
    SYSTEM("Par défaut"),
    SERIF("Sérif"),
    SANS_SERIF("Sans-serif"),
    DYSLEXIC("OpenDyslexic")
}

enum class ReaderMarginSize(val label: String) {
    NARROW("Étroites"),
    NORMAL("Normales"),
    WIDE("Larges")
}

enum class ReaderScrollMode(val label: String) {
    SCROLL("Scroll"),
    PAGES("Pages")
}

enum class ReaderTextAlign(val label: String) {
    START("À gauche"),
    CENTER("Centré"),
    JUSTIFY("Justifié")
}

data class ReaderSettings(
    val themePreset: ReaderThemePreset = ReaderThemePreset.LIGHT,
    val brightness: Float = 0.8f,
    val autoBrightness: Boolean = true,
    val lineSpacing: Float = 1.4f,
    val fontSizePt: Int = 18,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    val marginSize: ReaderMarginSize = ReaderMarginSize.NORMAL,
    val scrollMode: ReaderScrollMode = ReaderScrollMode.PAGES,
    val textAlign: ReaderTextAlign = ReaderTextAlign.START,
    val hideStatusBar: Boolean = false,
    val showReadingTimeLeft: Boolean = false,
    /** PDF : lire le texte remis en forme (plutôt que les pages telles quelles). */
    val pdfReflow: Boolean = false
)

class ReaderPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val AUTO_BRIGHTNESS = booleanPreferencesKey("auto_brightness")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val FONT_SIZE_PT = intPreferencesKey("font_size_pt")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val MARGIN_SIZE = stringPreferencesKey("margin_size")
        val SCROLL_MODE = stringPreferencesKey("scroll_mode")
        val ALIGN_LEFT = booleanPreferencesKey("align_left")
        val TEXT_ALIGN = stringPreferencesKey("text_align")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val SHOW_READING_TIME = booleanPreferencesKey("show_reading_time")
        val PDF_REFLOW = booleanPreferencesKey("pdf_reflow")
    }

    /** Relit le nouvel alignement, ou convertit l'ancien booléen « aligné à gauche ». */
    private fun readTextAlign(prefs: Preferences): ReaderTextAlign =
        prefs[Keys.TEXT_ALIGN]?.let { runCatching { ReaderTextAlign.valueOf(it) }.getOrNull() }
            ?: if (prefs[Keys.ALIGN_LEFT] == false) ReaderTextAlign.JUSTIFY else ReaderTextAlign.START

    val readerSettingsFlow: Flow<ReaderSettings> = context.readerDataStore.data.map { prefs ->
        ReaderSettings(
            themePreset = runCatching { ReaderThemePreset.valueOf(prefs[Keys.THEME_PRESET] ?: "") }.getOrDefault(ReaderThemePreset.LIGHT),
            brightness = prefs[Keys.BRIGHTNESS] ?: 0.8f,
            autoBrightness = prefs[Keys.AUTO_BRIGHTNESS] ?: true,
            lineSpacing = prefs[Keys.LINE_SPACING] ?: 1.4f,
            fontSizePt = prefs[Keys.FONT_SIZE_PT] ?: 18,
            fontFamily = runCatching { ReaderFontFamily.valueOf(prefs[Keys.FONT_FAMILY] ?: "") }.getOrDefault(ReaderFontFamily.SYSTEM),
            marginSize = runCatching { ReaderMarginSize.valueOf(prefs[Keys.MARGIN_SIZE] ?: "") }.getOrDefault(ReaderMarginSize.NORMAL),
            scrollMode = runCatching { ReaderScrollMode.valueOf(prefs[Keys.SCROLL_MODE] ?: "") }.getOrDefault(ReaderScrollMode.PAGES),
            textAlign = readTextAlign(prefs),
            hideStatusBar = prefs[Keys.HIDE_STATUS_BAR] ?: false,
            showReadingTimeLeft = prefs[Keys.SHOW_READING_TIME] ?: false,
            pdfReflow = prefs[Keys.PDF_REFLOW] ?: false
        )
    }

    suspend fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerDataStore.edit { prefs ->
            val current = ReaderSettings(
                themePreset = runCatching { ReaderThemePreset.valueOf(prefs[Keys.THEME_PRESET] ?: "") }.getOrDefault(ReaderThemePreset.LIGHT),
                brightness = prefs[Keys.BRIGHTNESS] ?: 0.8f,
                autoBrightness = prefs[Keys.AUTO_BRIGHTNESS] ?: true,
                lineSpacing = prefs[Keys.LINE_SPACING] ?: 1.4f,
                fontSizePt = prefs[Keys.FONT_SIZE_PT] ?: 18,
                fontFamily = runCatching { ReaderFontFamily.valueOf(prefs[Keys.FONT_FAMILY] ?: "") }.getOrDefault(ReaderFontFamily.SYSTEM),
                marginSize = runCatching { ReaderMarginSize.valueOf(prefs[Keys.MARGIN_SIZE] ?: "") }.getOrDefault(ReaderMarginSize.NORMAL),
                scrollMode = runCatching { ReaderScrollMode.valueOf(prefs[Keys.SCROLL_MODE] ?: "") }.getOrDefault(ReaderScrollMode.PAGES),
                textAlign = readTextAlign(prefs),
                hideStatusBar = prefs[Keys.HIDE_STATUS_BAR] ?: false,
                showReadingTimeLeft = prefs[Keys.SHOW_READING_TIME] ?: false,
            pdfReflow = prefs[Keys.PDF_REFLOW] ?: false
            )
            val updated = transform(current)
            prefs[Keys.THEME_PRESET] = updated.themePreset.name
            prefs[Keys.BRIGHTNESS] = updated.brightness
            prefs[Keys.AUTO_BRIGHTNESS] = updated.autoBrightness
            prefs[Keys.LINE_SPACING] = updated.lineSpacing
            prefs[Keys.FONT_SIZE_PT] = updated.fontSizePt
            prefs[Keys.FONT_FAMILY] = updated.fontFamily.name
            prefs[Keys.MARGIN_SIZE] = updated.marginSize.name
            prefs[Keys.SCROLL_MODE] = updated.scrollMode.name
            prefs[Keys.TEXT_ALIGN] = updated.textAlign.name
            prefs[Keys.HIDE_STATUS_BAR] = updated.hideStatusBar
            prefs[Keys.SHOW_READING_TIME] = updated.showReadingTimeLeft
            prefs[Keys.PDF_REFLOW] = updated.pdfReflow
        }
    }
}



