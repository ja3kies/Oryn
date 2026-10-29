package com.example.oryn.reader.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr

private data class IntroPage(val title: String, val body: String, val illustration: Int)

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pages = remember {
        listOf(
            IntroPage("Tes livres, à ton rythme", "Un espace calme pour retrouver le plaisir de lire, une page après l'autre.", 0),
            IntroPage("Toute ta bibliothèque avec toi", "Importe tes fichiers EPUB, PDF, TXT ou HTML, puis explore les catalogues.", 1),
            IntroPage("Une lecture qui te ressemble", "Choisis ton thème, ta police et le mode texte pour les PDF.", 2),
            IntroPage("Ton espace, tes choix", "Ta bibliothèque reste sur cet appareil. Ajuste l'apparence quand tu le souhaites.", 3)
        )
    }
    var page by remember { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().background(colors.background).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Oryn", style = MaterialTheme.typography.headlineSmall, color = colors.primary, fontWeight = FontWeight.Medium)
            Box(Modifier.weight(1f))
            TextButton(onClick = onFinish, modifier = Modifier.height(48.dp)) { Text("Passer") }
        }
        AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboardingPage", modifier = Modifier.weight(1f)) { index ->
            val item = pages[index]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IntroIllustration(item.illustration, Modifier.fillMaxWidth().weight(1f))
                Text(item.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = colors.onSurface)
                Text(item.body, modifier = Modifier.padding(top = 12.dp).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = colors.onSurfaceVariant, lineHeight = 27.sp)
            }
        }
        Row(Modifier.padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { index ->
                Box(Modifier.size(if (index == page) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (index == page) colors.primary else colors.outlineVariant))
            }
        }
        Button(onClick = { if (page == pages.lastIndex) onFinish() else page++ }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
            Text(if (page == pages.lastIndex) tr("Commencer") else tr("Suivant"), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IntroIllustration(kind: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.padding(12.dp)) {
        val unit = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(colors.primaryContainer, unit * 0.38f, center)
        when (kind) {
            0 -> {
                val w = unit * .28f; val h = unit * .42f
                drawRoundRect(colors.primary, Offset(center.x - w, center.y - h / 2), Size(w * 2, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * .035f))
                drawLine(colors.onPrimary.copy(alpha = .8f), Offset(center.x - w * .55f, center.y - h * .15f), Offset(center.x + w * .55f, center.y - h * .15f), unit * .012f)
                drawLine(colors.onPrimary.copy(alpha = .8f), Offset(center.x - w * .55f, center.y), Offset(center.x + w * .4f, center.y), unit * .012f)
            }
            1 -> {
                listOf(-.20f, 0f, .20f).forEachIndexed { index, dx ->
                    drawRoundRect(if (index == 1) colors.primary else colors.secondary, Offset(center.x + unit * dx - unit * .09f, center.y - unit * .20f), Size(unit * .18f, unit * .40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * .02f))
                }
            }
            2 -> {
                drawCircle(colors.secondary, unit * .20f, center, style = Stroke(unit * .025f))
                drawLine(colors.primary, Offset(center.x - unit * .15f, center.y), Offset(center.x + unit * .15f, center.y), unit * .035f)
                drawLine(colors.primary, Offset(center.x, center.y - unit * .15f), Offset(center.x, center.y + unit * .15f), unit * .035f)
            }
            else -> {
                val arch = Path().apply { moveTo(center.x - unit * .18f, center.y + unit * .17f); lineTo(center.x - unit * .18f, center.y); quadraticTo(center.x, center.y - unit * .28f, center.x + unit * .18f, center.y); lineTo(center.x + unit * .18f, center.y + unit * .17f) }
                drawPath(arch, colors.primary, style = Stroke(width = unit * .035f))
                drawCircle(colors.tertiary, unit * .035f, Offset(center.x, center.y - unit * .10f))
            }
        }
    }
}
