package com.example.oryn.reader.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.oryn.reader.data.local.preferences.ReaderThemePreset

val ReaderThemePreset.pageColor: Color
    get() = when (this) {
        ReaderThemePreset.LIGHT -> Color(0xFFF8F8F5)
        ReaderThemePreset.BEIGE -> Color(0xFFF4ECD8)
        ReaderThemePreset.MIDNIGHT -> Color(0xFF1C1C1B)
        ReaderThemePreset.OLED -> Color.Black
    }

val ReaderThemePreset.textColor: Color
    get() = when (this) {
        ReaderThemePreset.LIGHT -> Color(0xFF1E1E1E)
        ReaderThemePreset.BEIGE -> Color(0xFF3C3024)
        ReaderThemePreset.MIDNIGHT -> Color(0xFFE9E9E4)
        ReaderThemePreset.OLED -> Color(0xFFF0F0F0)
    }

val ReaderThemePreset.linkColor: Color
    get() = when (this) {
        ReaderThemePreset.LIGHT, ReaderThemePreset.BEIGE -> Color(0xFF3F5A00)
        ReaderThemePreset.MIDNIGHT, ReaderThemePreset.OLED -> Color(0xFFCBF53C)
    }
