package com.example.oryn.reader.ui.components

import com.example.oryn.reader.i18n.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class MascotMood { HAPPY, WOW, SLEEPY }

/**
 * Mascotte d'Oryn, dans l'esprit « Material 3 Expressive » : corps en forme de cookie à 8 lobes (forme
 * expressive), aplat de la couleur d'accent (style vectoriel plat, sans dégradé ni ombre), joues, yeux qui clignent et suivent du regard, signet
 * en guise d'antenne. Respire doucement, se tortille et s'écrase au toucher (ressort).
 * Les animations s'arrêtent si l'animation système est coupée.
 */
@Composable
fun OrynMascot(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    mood: MascotMood = MascotMood.HAPPY,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val motion = systemAnimationsEnabled(context)
    val scope = rememberCoroutineScope()

    val squish = remember { Animatable(1f) }
    val wiggle = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "mascot")
    val cycle by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4600, easing = LinearEasing), RepeatMode.Restart),
        label = "cycle"
    )
    val breathe by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "breathe"
    )

    val body = colors.primary
    val ribbon = colors.tertiary

    val click = if (onClick == null) Modifier else Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Button,
        onClickLabel = tr("Saluer la mascotte")
    ) {
        scope.launch { squish.snapTo(0.82f); squish.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow)) }
        scope.launch {
            wiggle.snapTo(14f)
            wiggle.animateTo(0f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow))
        }
        onClick()
    }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                val b = if (motion) breathe else 0.5f
                scaleX = squish.value * (1f + (b - 0.5f) * 0.03f)
                scaleY = squish.value * (1f + (0.5f - b) * 0.03f) * (2f - squish.value).coerceIn(1f, 1.2f)
                rotationZ = wiggle.value
                translationY = if (motion) (b - 0.5f) * -3f else 0f
            }
            .then(click)
    ) {
        val r = this.size.minDimension / 2f * 0.86f
        val c = Offset(this.size.width / 2f, this.size.height * 0.54f)

        // Signet-antenne (derrière le corps)
        rotate(14f, c) {
            val w = r * 0.26f
            val top = c.y - r * 1.12f
            val rib = Path().apply {
                moveTo(c.x - w / 2, top)
                lineTo(c.x + w / 2, top)
                lineTo(c.x + w / 2, top + r * 0.52f)
                lineTo(c.x, top + r * 0.40f)
                lineTo(c.x - w / 2, top + r * 0.52f)
                close()
            }
            drawPath(rib, ribbon)
        }

        // Corps : cookie à 8 lobes, aplat (style vectoriel plat)
        val blob = Path()
        val steps = 96
        for (i in 0..steps) {
            val a = (i.toFloat() / steps) * 2f * PI.toFloat()
            val rr = r * (1f + 0.055f * cos(8f * a))
            val x = c.x + rr * cos(a); val y = c.y + rr * sin(a)
            if (i == 0) blob.moveTo(x, y) else blob.lineTo(x, y)
        }
        blob.close()
        drawPath(blob, body)

        // Yeux : clignement toutes les ~4,6 s, regard qui balaie
        val blink = if (motion && cycle > 0.94f) 0.12f else 1f
        val look = if (motion) sin(cycle * 2f * PI.toFloat()) * r * 0.05f else 0f
        val eyeY = c.y - r * 0.10f
        val eyeDx = r * 0.36f
        val eyeW = r * 0.26f
        val eyeH = (if (mood == MascotMood.WOW) r * 0.40f else r * 0.34f) * blink
        for (sx in listOf(-1f, 1f)) {
            val ex = c.x + sx * eyeDx
            if (mood == MascotMood.SLEEPY) {
                drawArc(
                    Color(0xFF14231F), 0f, 180f, false,
                    topLeft = Offset(ex - eyeW / 2, eyeY - eyeW * 0.2f), size = Size(eyeW, eyeW * 0.5f),
                    style = Stroke(width = r * 0.07f, cap = StrokeCap.Round)
                )
            } else {
                drawOval(Color.White, Offset(ex - eyeW / 2, eyeY - eyeH / 2), Size(eyeW, eyeH))
                if (blink > 0.5f) {
                    val pr = eyeW * 0.30f
                    drawCircle(Color(0xFF14231F), pr, Offset(ex + look, eyeY + eyeH * 0.06f))
                }
            }
        }

        // Joues
        for (sx in listOf(-1f, 1f)) {
            drawCircle(lerp(body, Color(0xFFFF5C8A), 0.65f), r * 0.11f, Offset(c.x + sx * r * 0.60f, c.y + r * 0.22f))
        }

        // Bouche
        val my = c.y + r * 0.26f
        when (mood) {
            MascotMood.WOW -> drawOval(Color(0xFF14231F), Offset(c.x - r * 0.08f, my - r * 0.02f), Size(r * 0.16f, r * 0.20f))
            MascotMood.SLEEPY -> drawLine(
                Color(0xFF14231F), Offset(c.x - r * 0.10f, my + r * 0.04f), Offset(c.x + r * 0.10f, my + r * 0.04f),
                strokeWidth = r * 0.06f, cap = StrokeCap.Round
            )
            else -> drawArc(
                Color(0xFF14231F), 10f, 160f, false,
                topLeft = Offset(c.x - r * 0.16f, my - r * 0.14f), size = Size(r * 0.32f, r * 0.26f),
                style = Stroke(width = r * 0.07f, cap = StrokeCap.Round)
            )
        }
    }
}
