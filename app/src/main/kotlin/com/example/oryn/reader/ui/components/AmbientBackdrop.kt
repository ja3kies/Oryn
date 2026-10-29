package com.example.oryn.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Fond de l'application : un aplat neutre (gris perle en clair, noir encre en sombre). Le calme vient de là. */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.background))
}
