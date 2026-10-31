package com.example.oryn.reader.ui.components

import com.example.oryn.reader.i18n.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BarHeight = 64.dp
private val OuterVertical = 12.dp

/** Hauteur à réserver en bas des écrans (voir navBarClearance dans ScreenScaffold). */
val FloatingNavBarHeight: Dp = BarHeight + OuterVertical * 2

/**
 * Onglets d'origine conservés pour l'hôte de navigation : seuls Accueil, Bibliothèque et Favoris
 * apparaissent dans la barre. Les Réglages s'ouvrent depuis le menu « hamburger » de l'accueil.
 */
enum class ReaderTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    Home("home", "Accueil", OrynIcons.NavHome, OrynIcons.NavHomeFilled),
    Explore("explore", "Bibliothèque", OrynIcons.NavLibrary, OrynIcons.NavLibraryFilled),
    Favorites("bookmarks", "Favoris", HugeIcons.Bookmark, HugeIcons.BookmarkFilled),
    Settings("settings", "Réglages", OrynIcons.Settings, OrynIcons.Settings)
}

enum class HomeSection { HOME, LIBRARY }

/**
 * Pont de navigation : permet à n'importe quel écran (menu de l'accueil, catalogues…) de changer d'onglet
 * sans connaître l'hôte, et mémorise si l'écran « home » montre l'accueil ou la bibliothèque.
 */
object OrynNav {
    var section by mutableStateOf(HomeSection.HOME)
    var go: ((String) -> Unit)? = null

    /** Filtre à appliquer à l'ouverture de la bibliothèque (nom de HomeFilter), consommé par elle. */
    var libraryFilter by mutableStateOf<String?>(null)

    fun open(route: String) {
        if (route == ReaderTab.Home.route) section = HomeSection.HOME
        go?.invoke(route)
    }

    fun openLibrary(filter: String? = null) {
        libraryFilter = filter
        section = HomeSection.LIBRARY
        go?.invoke(ReaderTab.Home.route)
    }
}

private val visibleTabs = listOf(ReaderTab.Home, ReaderTab.Explore, ReaderTab.Favorites)

/**
 * Barre flottante : pastille de verre (onglet actif déployé avec son libellé, les autres en icônes)
 * + bouton « + » rond et lumineux, à part, à droite.
 */
@Composable
fun FloatingActionNavBar(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    SideEffect { OrynNav.go = onRouteSelected }

    val selected: ReaderTab? = when (selectedRoute) {
        ReaderTab.Favorites.route -> ReaderTab.Favorites
        ReaderTab.Home.route -> if (OrynNav.section == HomeSection.LIBRARY) ReaderTab.Explore else ReaderTab.Home
        else -> null
    }

    // Voile qui s'estompe vers le haut : le contenu qui passe derrière la barre reste lisible
    val scrim = colors.background
    Row(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to scrim.copy(alpha = 0f),
                    0.45f to scrim.copy(alpha = 0.82f),
                    1f to scrim.copy(alpha = 0.96f)
                )
            )
            .padding(top = 28.dp)
            .navigationBarsPadding()
            .padding(vertical = OuterVertical),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.height(BarHeight),
            shape = CircleShape,
            color = if (dark) colors.surfaceContainerHigh.copy(alpha = 0.97f) else colors.surfaceContainerLowest.copy(alpha = 0.97f),
            shadowElevation = 10.dp
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                visibleTabs.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = tab == selected,
                        onClick = {
                            when (tab) {
                                ReaderTab.Home -> OrynNav.open(ReaderTab.Home.route)
                                ReaderTab.Explore -> OrynNav.openLibrary()
                                else -> OrynNav.open(tab.route)
                            }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        AddButton(onClick = onAddClick)
    }
}

@Composable
private fun NavItem(tab: ReaderTab, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pill by animateColorAsState(
        if (selected) colors.primaryContainer else Color.Transparent,
        label = "navPill"
    )
    val ink by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        label = "navInk"
    )
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(pill)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            // L'onglet actif se déploie avec un petit rebond
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = if (selected) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Deux états : contour quand l'onglet est inactif, forme pleine quand il est actif (fondu enchaîné)
        androidx.compose.animation.Crossfade(
            targetState = selected,
            animationSpec = androidx.compose.animation.core.tween(160),
            label = "navIcon"
        ) { on ->
            Icon(
                imageVector = if (on) tab.selectedIcon else tab.icon,
                contentDescription = tr(tab.label),
                tint = ink,
                modifier = Modifier.size(24.dp)
            )
        }
        if (selected) {
            Text(
                text = tab.label,
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                color = ink,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** Bouton « + » : rond, encre pleine (noir en clair, blanc en sombre), ombre douce, rebond à l'appui. */
@Composable
private fun AddButton(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "addScale"
    )
    Box(
        Modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = tr("Importer un fichier"),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = OrynIcons.Plus,
            contentDescription = tr("Ajouter un document"),
            tint = colors.onPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}
