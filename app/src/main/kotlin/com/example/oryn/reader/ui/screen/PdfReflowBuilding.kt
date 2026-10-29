package com.example.oryn.reader.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr

/** Écran d'attente pendant que le texte du PDF est préparé (une seule fois par livre : le résultat est gardé). */
@Composable
internal fun PdfReflowBuilding(progress: Float, color: Color, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            tr("Préparation du texte…"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = color.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(8.dp))
        Text("${(progress.coerceIn(0f, 1f) * 100).toInt()} %", style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.75f))
        Spacer(Modifier.height(12.dp))
        Text(
            tr("Une seule fois par livre : le résultat est ensuite gardé."),
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text(tr("Revenir aux pages")) }
    }
}
