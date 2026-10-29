package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import com.example.oryn.reader.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

private class HtmlState(var target: Float) {
    var loading = true
    var lastHtml: String? = null
}

/**
 * Page HTML affichée telle quelle (styles, images, tableaux, mise en page d'origine).
 * JavaScript désactivé : un fichier HTML n'exécute jamais de code. Seuls les liens web demandent confirmation.
 */
@OptIn(FlowPreview::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun HtmlReader(
    html: String,
    initialProgress: Float,
    pageColor: Color,
    textColor: Color,
    linkColor: Color,
    themeName: String,
    readerSettings: ReaderSettings,
    onProgress: (Float) -> Unit,
    onCenterTap: () -> Unit
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val latestTap by rememberUpdatedState(onCenterTap)
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    val progressFlow = remember { MutableStateFlow(initialProgress) }
    val state = remember { HtmlState(initialProgress) }

    // Thème : clair = page d'origine intacte ; beige = fond et texte ; sombre = tout est recoloré pour rester lisible
    val prepared = remember(html, pageColor, textColor, linkColor, themeName) {
        val bg = hex(pageColor)
        val fg = hex(textColor)
        val link = hex(linkColor)
        val css = when (themeName) {
            "LIGHT" -> ""
            "BEIGE" -> "<style>html,body{background:$bg !important;color:$fg !important;}</style>"
            else -> "<style>html,body{background:$bg !important;color:$fg !important;}" +
                "body *{background-color:transparent !important;color:inherit !important;border-color:rgba(255,255,255,.25) !important;}" +
                "a,a *{color:$link !important;}</style>"
        }
        val head = Regex("(?i)<head[^>]*>").find(html)
        if (css.isEmpty()) html
        else if (head != null) html.substring(0, head.range.last + 1) + css + html.substring(head.range.last + 1)
        else css + html
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            isHorizontalScrollBarEnabled = false

            // Un toucher sur du texte (pas sur un lien ni une image) affiche / masque l'interface
            val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val type = hitTestResult.type
                    if (type == WebView.HitTestResult.UNKNOWN_TYPE || type == WebView.HitTestResult.EDIT_TEXT_TYPE) latestTap()
                    return false
                }
            })
            setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                false
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    return when (request.url.scheme?.lowercase()) {
                        "http", "https", "mailto", "tel" -> {
                            pendingUrl = url
                            true
                        }
                        "about" -> false // ancres dans la page
                        else -> true
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    // Retour à la position de lecture une fois la page mise en forme
                    view.postDelayed({
                        val range = view.contentHeight * density - view.height
                        if (range > 0f && state.target > 0f) view.scrollTo(0, (range * state.target).toInt())
                        state.loading = false
                    }, 300)
                }
            }

            setOnScrollChangeListener { v, _, y, _, _ ->
                if (!state.loading) {
                    val wv = v as WebView
                    val range = wv.contentHeight * density - wv.height
                    progressFlow.value = if (range > 0f) (y / range).coerceIn(0f, 1f) else 0f
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        progressFlow.drop(1).debounce(500).collect { onProgress(it) }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    Box(Modifier.fillMaxSize().background(pageColor).statusBarsPadding()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.settings.textZoom = (readerSettings.fontSizePt.toFloat() / 18f * 100f).toInt().coerceIn(50, 250)
                if (state.lastHtml != prepared) {
                    state.lastHtml = prepared
                    state.loading = true
                    state.target = progressFlow.value
                    view.setBackgroundColor(pageColor.toArgb())
                    view.loadDataWithBaseURL(null, prepared, "text/html", "utf-8", null)
                }
            }
        )
    }

    pendingUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingUrl = null },
            title = { Text(tr("Ouvrir ce lien ?")) },
            text = { Text(url, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    pendingUrl = null
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(tr("Ouvrir")) }
            },
            dismissButton = { TextButton(onClick = { pendingUrl = null }) { Text(tr("Annuler")) } }
        )
    }
}

private fun hex(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)
