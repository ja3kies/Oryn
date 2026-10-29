package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.ui.components.OrynIcons
import kotlinx.coroutines.delay
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.oryn.reader.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.WindowCompat
import android.view.ViewGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.oryn.reader.rememberMainViewModel

private class DetectedFile(val url: String, val name: String, val userAgent: String?)

/** Android 9+ refuse le HTTP en clair : on passe toujours par HTTPS. */
private fun String.toHttps(): String =
    if (startsWith("http://", ignoreCase = true)) "https://" + substring(7) else this

private val bookUrlPattern = Regex("""\.(epub\d?|pdf)(\.\w+)?$""")

private fun looksLikeBook(url: String): Boolean =
    bookUrlPattern.containsMatchIn(Uri.parse(url).path.orEmpty().lowercase())

private fun isSupportedDownload(name: String, mime: String?): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".epub") || lower.endsWith(".pdf") || lower.endsWith(".txt") ||
        mime == "application/epub+zip" || mime == "application/pdf"
}

/**
 * Navigateur intégré plein écran : on parcourt un catalogue, et dès qu'un EPUB / PDF est
 * touché, un bouton « Ajouter à la bibliothèque » apparaît en bas.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLibraryDialog(title: String, startUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val viewModel = rememberMainViewModel()
    val downloading by viewModel.webDownloads.collectAsState()
    val colors = MaterialTheme.colorScheme

    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(100) }
    var currentUrl by remember { mutableStateOf(startUrl.toHttps()) }
    var detected by remember { mutableStateOf<DetectedFile?>(null) }
    var started by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(0) }

    // Site qui limite les requêtes : on laisse passer ~30 s avant de proposer de réessayer
    LaunchedEffect(errorMessage) {
        while (cooldown > 0) {
            delay(1_000)
            cooldown--
        }
    }

    // Le fichier a fini d'être ajouté : on retire la bannière
    LaunchedEffect(downloading) {
        val file = detected
        if (started && file != null && file.url !in downloading) {
            detected = null
            started = false
        }
    }

    Dialog(
        onDismissRequest = {
            // Retour système : d'abord l'historique du site, puis la fermeture
            if (canGoBack) webView?.goBack() else onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val darkSurface = colors.surfaceContainerHighest.luminance() < 0.5f
        SideEffect {
            dialogWindow?.let { w ->
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.statusBarColor = android.graphics.Color.TRANSPARENT
                w.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(w, w.decorView).apply {
                    isAppearanceLightStatusBars = !darkSurface
                    isAppearanceLightNavigationBars = !darkSurface
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainerHighest) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = colors.surfaceContainerHighest, shadowElevation = 4.dp) {
                    Column(Modifier.statusBarsPadding()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(OrynIcons.Close, contentDescription = tr("Fermer"))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    Uri.parse(currentUrl).host.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                                Icon(OrynIcons.ArrowLeft, contentDescription = tr("Page précédente"))
                            }
                            IconButton(onClick = { webView?.reload() }) {
                                Icon(OrynIcons.Refresh, contentDescription = tr("Recharger"))
                            }
                            IconButton(onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                                } catch (_: ActivityNotFoundException) {
                                }
                            }) {
                                Icon(OrynIcons.External, contentDescription = tr("Ouvrir dans le navigateur"))
                            }
                        }
                        if (progress < 100) {
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth().background(colors.background)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val url = request.url.toString()
                                    val scheme = request.url.scheme?.lowercase()
                                    // Pas d'applications externes (intent://, market://…)
                                    if (scheme != "http" && scheme != "https") return true
                                    if (scheme == "http") {
                                        errorMessage = null
                                        view.loadUrl(url.toHttps())
                                        return true
                                    }
                                    if (looksLikeBook(url)) {
                                        detected = DetectedFile(
                                            url = url,
                                            name = URLUtil.guessFileName(url, null, null),
                                            userAgent = view.settings.userAgentString
                                        )
                                        started = false
                                        return true
                                    }
                                    return false
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (!request.isForMainFrame) return
                                    val failing = request.url.toString()
                                    // Filet de sécurité : une page « http » refusée est retentée en « https »
                                    if (failing.startsWith("http://", ignoreCase = true)) {
                                        view.loadUrl(failing.toHttps())
                                        return
                                    }
                                    errorMessage = tr("Impossible de charger cette page. Vérifie ta connexion, puis réessaie.")
                                }

                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                    if (!request.isForMainFrame) return
                                    if (errorResponse.statusCode == 429 || errorResponse.statusCode == 503) cooldown = 30
                                    errorMessage = when (errorResponse.statusCode) {
                                        429, 503 -> tr("Le site limite temporairement les requêtes (trop d'accès en peu de temps). Attends environ 30 secondes, puis réessaie.")
                                        403 -> tr("Le site refuse l'accès dans l'application. Essaie de l'ouvrir dans ton navigateur.")
                                        404 -> tr("Cette page n'existe plus.")
                                        else -> null
                                    }
                                }

                                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                    errorMessage = null
                                    currentUrl = url
                                    canGoBack = view.canGoBack()
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    currentUrl = url
                                    canGoBack = view.canGoBack()
                                }
                            }
                            // Le site déclenche un vrai téléchargement (en-têtes « attachment »)
                            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                val name = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                if (isSupportedDownload(name, mimetype)) {
                                    detected = DetectedFile(url, name, userAgent)
                                    started = false
                                } else {
                                    Toast.makeText(
                                        context,
                                        tr("Format non pris en charge. Cherche un EPUB ou un PDF."),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            loadUrl(startUrl.toHttps())
                            webView = this
                        }
                    },
                    onRelease = { it.destroy() }
                )

                errorMessage?.let { message ->
                    Column(
                        Modifier.fillMaxSize().background(colors.background).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.CloudOff, null, tint = colors.primary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                                } catch (_: ActivityNotFoundException) {
                                }
                            }) { Text(tr("Ouvrir dans le navigateur")) }
                            Button(
                                enabled = cooldown == 0,
                                onClick = { errorMessage = null; webView?.reload() }
                            ) { Text(if (cooldown > 0) trf("Réessayer (%s s)", cooldown) else tr("Réessayer")) }
                        }
                    }
                }
                }

                AnimatedVisibility(
                    visible = detected != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    val file = detected
                    Surface(
                        color = colors.surfaceContainerHighest,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            Modifier.navigationBarsPadding().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(OrynIcons.BookOpen, null, tint = colors.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file?.name.orEmpty(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        tr("Fichier détecté"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            val busy = file != null && file.url in downloading
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { detected = null }, enabled = !busy) { Text(tr("Annuler")) }
                                Button(
                                    enabled = !busy && file != null,
                                    onClick = {
                                        if (file != null) {
                                            started = true
                                            viewModel.addFromWeb(
                                                url = file.url,
                                                fileName = file.name,
                                                userAgent = file.userAgent,
                                                cookie = CookieManager.getInstance().getCookie(file.url),
                                                referer = currentUrl
                                            )
                                        }
                                    }
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.onPrimary
                                        )
                                    } else {
                                        Icon(OrynIcons.Plus, null, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        if (busy) tr("  Ajout…") else tr("  Ajouter à la bibliothèque")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
