package com.example.oryn.reader.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.example.oryn.reader.data.local.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private class V3(val x: Float, val y: Float, val z: Float)

private const val BOOK_W = 1f
private const val BOOK_H = 1.5f
private const val BOOK_T = 0.22f
private const val CAMERA = 5f

private class Face(
    val corners: List<V3>,
    val normal: V3,
    val kind: Int // 0 couverture, 1 dos, 2 pages, 3 haut/bas, 4 arrière
)

/**
 * Livre 3D « vectoriel » : un parallélépipède projeté en perspective (rotation lacet/tangage/roulis),
 * faces cachées éliminées, faces triées par profondeur. La couverture réelle est plaquée sur la face avant
 * (matrice 4 points = perspective correcte), le dos reprend la teinte de la couverture, la tranche montre
 * des feuillets, un reflet glisse sur la couverture et l'ensemble flotte doucement au-dessus d'une ombre.
 */
@Composable
fun Book3D(
    book: BookEntity,
    modifier: Modifier = Modifier,
    yawDeg: Float = 26f,
    pitchDeg: Float = -16f,
    rollDeg: Float = -8f,
    floating: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val motion = floating && systemAnimationsEnabled(context)
    val path = book.coverPath?.takeIf { it.isNotBlank() }
    val bitmap by produceState<Bitmap?>(initialValue = path?.let { CoverCache.get(it) }, path) {
        value = if (path == null) null else CoverCache.get(path)
            ?: withContext(Dispatchers.IO) { CoverCache.decodeFile(path) }?.also { CoverCache.put(path, it) }
    }
    val tint = rememberCoverTint(book) ?: colors.primary
    val spine = remember(tint) { lerp(tint, Color.Black, 0.35f) }
    val spineLight = remember(tint) { lerp(tint, Color.White, 0.10f) }

    val transition = rememberInfiniteTransition(label = "book3d")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )

    Canvas(modifier) {
        val t = if (motion) bob else 0.5f
        val lift = (t - 0.5f) * 0.10f
        val yaw = Math.toRadians((yawDeg + (t - 0.5f) * 4f).toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val roll = Math.toRadians(rollDeg.toDouble()).toFloat()

        fun rot(x: Float, y: Float, z: Float): V3 {
            val x1 = x * cos(yaw) + z * sin(yaw)
            val z1 = -x * sin(yaw) + z * cos(yaw)
            val y2 = y * cos(pitch) - z1 * sin(pitch)
            val z2 = y * sin(pitch) + z1 * cos(pitch)
            val x3 = x1 * cos(roll) - y2 * sin(roll)
            val y3 = x1 * sin(roll) + y2 * cos(roll)
            return V3(x3, y3 + lift, z2)
        }

        val hw = BOOK_W / 2; val hh = BOOK_H / 2; val ht = BOOK_T / 2
        fun p(x: Float, y: Float, z: Float) = rot(x, y, z)
        fun n(x: Float, y: Float, z: Float) = rot(x, y, z).let { V3(it.x, it.y - lift, it.z) }

        val faces = listOf(
            Face(listOf(p(-hw, hh, ht), p(hw, hh, ht), p(hw, -hh, ht), p(-hw, -hh, ht)), n(0f, 0f, 1f), 0),
            Face(listOf(p(-hw, hh, -ht), p(-hw, hh, ht), p(-hw, -hh, ht), p(-hw, -hh, -ht)), n(-1f, 0f, 0f), 1),
            Face(listOf(p(hw, hh, ht), p(hw, hh, -ht), p(hw, -hh, -ht), p(hw, -hh, ht)), n(1f, 0f, 0f), 2),
            Face(listOf(p(-hw, hh, -ht), p(hw, hh, -ht), p(hw, hh, ht), p(-hw, hh, ht)), n(0f, 1f, 0f), 3),
            Face(listOf(p(-hw, -hh, ht), p(hw, -hh, ht), p(hw, -hh, -ht), p(-hw, -hh, -ht)), n(0f, -1f, 0f), 3),
            Face(listOf(p(hw, hh, -ht), p(-hw, hh, -ht), p(-hw, -hh, -ht), p(hw, -hh, -ht)), n(0f, 0f, -1f), 4)
        )

        // Projection en perspective → coordonnées écran normalisées
        fun proj(v: V3): Offset {
            val s = CAMERA / (CAMERA - v.z)
            return Offset(v.x * s, -v.y * s)
        }
        val visible = faces.filter { f ->
            val c = f.corners
            val cx = c.sumOf { it.x.toDouble() }.toFloat() / 4
            val cy = c.sumOf { it.y.toDouble() }.toFloat() / 4
            val cz = c.sumOf { it.z.toDouble() }.toFloat() / 4
            f.normal.x * (0f - cx) + f.normal.y * (0f - cy) + f.normal.z * (CAMERA - cz) > 0f
        }.sortedBy { f -> f.corners.sumOf { it.z.toDouble() } }

        val all = visible.flatMap { f -> f.corners.map { proj(it) } }
        if (all.isEmpty()) return@Canvas
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
        // Réserve de la place en bas pour l'ombre
        val shadowRoom = size.height * 0.10f
        val fit = minOf(size.width / (maxX - minX), (size.height - shadowRoom) / (maxY - minY)) * 0.96f
        val ox = (size.width - (maxX - minX) * fit) / 2 - minX * fit
        val oy = (size.height - shadowRoom - (maxY - minY) * fit) / 2 - minY * fit
        fun toScreen(o: Offset) = Offset(o.x * fit + ox, o.y * fit + oy)

        // Ombre portée (diminue quand le livre monte)
        val bottomY = (maxY * fit + oy)
        val shW = (maxX - minX) * fit * (0.78f - lift * 0.8f)
        val shCx = (minX + maxX) / 2 * fit + ox
        withTransform({ scale(1f, 0.20f, Offset(shCx, bottomY + shadowRoom * 0.55f)) }) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(shCx, bottomY + shadowRoom * 0.55f),
                    radius = shW / 2
                ),
                radius = shW / 2,
                center = Offset(shCx, bottomY + shadowRoom * 0.55f)
            )
        }

        visible.forEach { f ->
            val pts = f.corners.map { toScreen(proj(it)) }
            val outline = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            when (f.kind) {
                0 -> drawCover(outline, pts, bitmap, tint)
                1 -> {
                    drawPath(outline, Brush.linearGradient(listOf(spineLight, spine), pts[0], pts[1]))
                    // Deux filets dorés façon reliure
                    for (k in listOf(0.16f, 0.84f)) {
                        val a = lerpO(pts[0], pts[3], k); val b = lerpO(pts[1], pts[2], k)
                        drawLine(Color.White.copy(alpha = 0.35f), a, b, strokeWidth = 1.4f)
                    }
                }
                2 -> {
                    drawPath(outline, Brush.linearGradient(listOf(Color(0xFFFFFBF0), Color(0xFFE6DCC4)), pts[0], pts[3]))
                    for (i in 1..9) {
                        val k = i / 10f
                        drawLine(
                            Color(0xFF8B7A58).copy(alpha = 0.22f),
                            lerpO(pts[0], pts[1], k), lerpO(pts[3], pts[2], k), strokeWidth = 1f
                        )
                    }
                }
                3 -> drawPath(outline, Brush.linearGradient(listOf(Color(0xFFF6EFDD), Color(0xFFD8CCB0)), pts[0], pts[2]))
                else -> drawPath(outline, spine)
            }
            drawPath(outline, Color.Black.copy(alpha = 0.18f), style = Stroke(width = 1.2f))
        }
    }
}

private fun lerpO(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun DrawScope.drawCover(outline: Path, pts: List<Offset>, bitmap: Bitmap?, tint: Color) {
    if (bitmap != null) {
        val m = Matrix()
        val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
        val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val dst = floatArrayOf(pts[0].x, pts[0].y, pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y)
        m.setPolyToPoly(src, 0, dst, 0, 4)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.clipPath(outline.asAndroidPath())
            nc.drawBitmap(bitmap, m, paint)
            nc.restore()
        }
    } else {
        drawPath(outline, Brush.linearGradient(listOf(lerp(tint, Color.White, 0.25f), lerp(tint, Color.Black, 0.30f)), pts[0], pts[2]))
        // Motif de secours : filets de titre
        for (k in listOf(0.34f, 0.42f, 0.50f)) {
            val a = lerpO(pts[0], pts[3], k); val b = lerpO(pts[1], pts[2], k)
            drawLine(Color.White.copy(alpha = 0.55f), lerpO(a, b, 0.18f), lerpO(a, b, 0.82f), strokeWidth = 5f)
        }
    }
    // Léger creux de la charnière près du dos + reflet diagonal
    val hinge = lerpO(pts[0], pts[1], 0.07f)
    val hingeB = lerpO(pts[3], pts[2], 0.07f)
    drawLine(Color.Black.copy(alpha = 0.22f), hinge, hingeB, strokeWidth = 2.2f)
    clipPath(outline) {
        drawPath(
            outline,
            Brush.linearGradient(
                0.00f to Color.White.copy(alpha = 0.34f),
                0.38f to Color.Transparent,
                1.00f to Color.Black.copy(alpha = 0.14f),
                start = pts[0], end = pts[2]
            )
        )
    }
}
