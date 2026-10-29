package com.example.oryn.reader.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.oryn.reader.data.local.preferences.ReaderThemePreset

val ReaderThemePreset.pageColor: Color
    get() = when (this) {
        ReaderThemePreset.LIGHT -> Color(0xFFFBF9F5)
        ReaderThemePreset.BEIGE -> Color(0xFFF4ECD8)
        ReaderThemePreset.MIDNIGHT -> Color(0xFF30231D)
        ReaderThemePreset.OLED -> Color.Black
    }

val ReaderThemePreset.textColor: Color
    get() = when (this) {
        ReaderThemePreset.LIGHT -> Color(0xFF1E1E1E)
        ReaderThemePreset.BEIGE -> Color(0xFF3C3024)
        ReaderThemePreset.MIDNIGHT -> Color(0xFFF0E4D8)
        ReaderThemePreset.OLED -> Color(0xFFF0F0F0)
    }

val ReaderThemePreset.linkColor: Color
    get() = when (this) {
        ReaderThemePreset.LIGHT, ReaderThemePreset.BEIGE -> Color(0xFF80432B)
        ReaderThemePreset.MIDNIGHT, ReaderThemePreset.OLED -> Color(0xFFEAB193)
    }
