package com.example.oryn.reader.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.orynColorScheme

/**
 * The Reader Pill and Reader Sheet are app chrome, not editorial page content.
 * Always resolve their colors from the same parent app theme and user accent.
 * Call before installing a nested MaterialTheme, or it would read itself.
 */
internal fun isDarkReaderChrome(background: Color): Boolean = background.luminance() < 0.5f

@Composable
internal fun readerChromeScheme(accent: AppAccentTheme): ColorScheme {
    val dark = isDarkReaderChrome(MaterialTheme.colorScheme.background)
    return remember(dark, accent) { orynColorScheme(dark, accent) }
}
