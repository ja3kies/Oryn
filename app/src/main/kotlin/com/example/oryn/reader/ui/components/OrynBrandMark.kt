package com.example.oryn.reader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.R
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.theme.OrynColors

/** Brand mark; the same safe-area artwork is shared with the adaptive launcher icon. */
@Composable
fun OrynBrandMark(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(OrynColors.editorial.plum)
            .clickable(role = Role.Button, onClickLabel = tr("Conseil de lecture"), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
