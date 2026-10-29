package com.glyphora.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Onglets de la barre. Modifie les libellés / icônes ici. */
enum class ReaderTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("Accueil", Icons.Outlined.Home, Icons.Filled.Home),
    Explore("Explorer", Icons.Outlined.Explore, Icons.Filled.Explore),
    Library("Bibliothèque", Icons.Outlined.LocalLibrary, Icons.Filled.LocalLibrary),
    Settings("Réglages", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Types de fichiers proposés dans le sélecteur Android (SAF). */
private val READER_MIME_TYPES = arrayOf(
    "application/epub+zip",
    "application/pdf",
    "text/html",
    "application/xhtml+xml",
    "text/plain",
    // Certains appareils exposent les .epub en octet-stream : filtre par extension côté import.
    "application/octet-stream",
)

/**
 * Barre de navigation flottante avec encoche centrale et bouton « + ».
 *
 * - Les couleurs viennent de MaterialTheme.colorScheme : elles suivent donc
 *   automatiquement le thème de l'app (clair / sombre / sépia / dynamic color).
 * - Le « + » ouvre le sélecteur de fichiers Android et renvoie les Uri choisis.
 *   Permission de lecture persistante prise automatiquement.
 */
@Composable
fun FloatingActionNavBar(
    selected: ReaderTab,
    onTabSelected: (ReaderTab) -> Unit,
    onFilesPicked: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Le provider n'autorise pas la permission persistante : on garde l'accès temporaire.
                }
            }
            onFilesPicked(uris)
        }
    }

    val barHeight = 72.dp
    val fabOverflow = 46.dp // partie du bouton qui dépasse au-dessus de la barre
    val barShape = remember {
        NotchedBarShape(corner = 28.dp, notchHalfWidth = 52.dp, notchDepth = 24.dp)
    }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(barHeight + fabOverflow)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barHeight),
            shape = barShape,
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurfaceVariant,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = ReaderTab.entries
                tabs.take(2).forEach { tab ->
                    NavTab(tab, tab == selected, { onTabSelected(tab) }, Modifier.weight(1f))
                }
                Spacer(Modifier.width(72.dp)) // place du « + »
                tabs.drop(2).forEach { tab ->
                    NavTab(tab, tab == selected, { onTabSelected(tab) }, Modifier.weight(1f))
                }
            }
        }

        CenterAddButton(
            onClick = { picker.launch(READER_MIME_TYPES) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun NavTab(
    tab: ReaderTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val pill by animateColorAsState(
        if (selected) colors.primaryContainer else Color.Transparent,
        label = "tabPill",
    )
    val tint by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        label = "tabTint",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CenterAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fabScale",
    )
    val brush = Brush.linearGradient(
        listOf(colors.primary, lerp(colors.primary, colors.tertiary, 0.6f))
    )

    Box(
        modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = 18.dp,
                shape = CircleShape,
                ambientColor = colors.primary,
                spotColor = colors.primary,
            )
            .clip(CircleShape)
            .background(brush)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Importer un fichier",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Ajouter un document",
            tint = colors.onPrimary,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Rectangle arrondi avec une encoche lisse (courbes de Bézier) au centre du bord haut. */
private class NotchedBarShape(
    private val corner: Dp,
    private val notchHalfWidth: Dp,
    private val notchDepth: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { corner.toPx() }
        val hw = with(density) { notchHalfWidth.toPx() }
        val d = with(density) { notchDepth.toPx() }
        val w = size.width
        val h = size.height
        val cx = w / 2f

        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(cx - hw, 0f)
            cubicTo(cx - hw * 0.6f, 0f, cx - hw * 0.4f, d, cx, d)
            cubicTo(cx + hw * 0.4f, d, cx + hw * 0.6f, 0f, cx + hw, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}
