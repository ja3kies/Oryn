package com.example.oryn.reader.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYSLEXIC_FONT = booleanPreferencesKey("dyslexic_font")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            ?: if (prefs[PreferencesKeys.DARK_MODE] == true) AppThemeMode.DARK else AppThemeMode.SYSTEM
    }

    val dyslexicFontFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DYSLEXIC_FONT] ?: false
    }

    val accentThemeFlow: Flow<AppAccentTheme> = context.dataStore.data.map { prefs ->
        val name = prefs[PreferencesKeys.ACCENT_THEME] ?: AppAccentTheme.ORANGE.name
        runCatching { AppAccentTheme.valueOf(name) }.getOrDefault(AppAccentTheme.ORANGE)
    }

    val onboardingCompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DARK_MODE] = enabled
            prefs[PreferencesKeys.THEME_MODE] = if (enabled) AppThemeMode.DARK.name else AppThemeMode.LIGHT.name
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = mode.name
            if (mode != AppThemeMode.SYSTEM) prefs[PreferencesKeys.DARK_MODE] = mode == AppThemeMode.DARK
        }
    }

    suspend fun setDyslexicFont(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DYSLEXIC_FONT] = enabled
        }
    }

    suspend fun setAccentTheme(theme: AppAccentTheme) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACCENT_THEME] = theme.name
        }
    }
}
