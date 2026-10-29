package com.example.oryn.reader.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.rememberMainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object CoverCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 10).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
    fun decodeFile(path: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth) })
    }.getOrNull()
    fun decodeBytes(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth) }
        )
    }.getOrNull()
    private fun sampleFor(width: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= 240) sample *= 2
        return sample
    }
}

@Composable
fun BookCover(book: BookEntity, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val path = book.coverPath?.takeIf { it.isNotBlank() }
    val bitmap by produceState<Bitmap?>(initialValue = path?.let { CoverCache.get(it) }, path) {
        value = if (path == null) null else CoverCache.get(path)
            ?: withContext(Dispatchers.IO) { CoverCache.decodeFile(path) }?.also { CoverCache.put(path, it) }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.primaryContainer
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Couverture de ${book.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = OrynIcons.BookOpen,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = book.format,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                )
            }
        }
    }
}

fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - timestamp) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 60 * 24 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (60 * 24)} j"
    }
}

@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isFavorite) colors.primary.copy(alpha = 0.18f) else colors.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isFavorite) OrynIcons.BookmarkFilled else OrynIcons.Bookmark,
            contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = if (isFavorite) colors.primary else colors.onSurface,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: BookEntity,
    onBookClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val tint = rememberCoverTint(book)
    val haptics = LocalHapticFeedback.current
    var actions by remember { mutableStateOf(false) }
    val state = when {
        book.progress < 0.01f -> "Non lu"
        book.progress >= 0.98f -> "Terminé"
        else -> "En cours"
    }

    Box(modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(116.dp)
                .glass(RoundedCornerShape(28.dp), tint)
                .combinedClickable(
                    onClick = onBookClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions = true
                    }
                )
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.width(80.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = inkSecondary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassTag(book.format)
                    GlassTag(state)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (book.progress < 0.01f) "" else "${(book.progress * 100).toInt()} %",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }
                LinearProgressIndicator(
                    progress = { book.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = colors.primary,
                    trackColor = colors.onSurface.copy(alpha = 0.14f)
                )
            }
            FavoriteButton(book.isFavorite, onToggleFavorite, Modifier.align(Alignment.Top))
        }
        BookCover(
            book,
            Modifier
                .padding(start = 14.dp)
                .offset(y = (-14).dp)
                .width(80.dp)
                .height(120.dp)
                .graphicsLayer { rotationZ = -3f }
                .shadow(10.dp, RoundedCornerShape(12.dp))
        )
    }

    BookActionsHost(book, visible = actions, onDismiss = { actions = false }, onSelect = onSelect)
}
