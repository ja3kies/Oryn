package com.example.oryn.reader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** In-app identity uses Oryn's mascot; the approved brand mark stays in launcher assets. */
@Composable
fun OrynBrandMark(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OrynMascot(
        modifier = modifier,
        size = 56.dp,
        onClick = onClick
    )
}
