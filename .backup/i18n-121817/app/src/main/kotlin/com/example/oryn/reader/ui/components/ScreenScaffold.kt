package com.example.oryn.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val navBarClearance: Dp = 88.dp

/** Écran vide : carte de verre, icône arrondie et bouton d'action. */
@Composable
fun LibraryEmptyState(
    title: String,
    subtitle: String,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .solidSurface(RoundedCornerShape(32.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(OrynIcons.BookOpen, null, tint = colors.primary, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = inkSecondary(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(onClick = onImportClick)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(OrynIcons.Plus, null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                Text("Importer un livre", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.onPrimary)
            }
        }
    }
}
