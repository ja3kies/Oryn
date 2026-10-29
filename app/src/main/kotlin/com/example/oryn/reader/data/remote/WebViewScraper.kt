package com.example.oryn.reader.data.remote

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

object WebViewScraper {
    private const val TIMEOUT_MS = 20_000L
    private const val DETECT_DELAY_MS = 2_000L

    suspend fun fetchHtml(url: String): String? = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val activity = AppContextHolder.activity?.get()
            if (activity == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            Handler(Looper.getMainLooper()).post {
                val webView = createWebView(activity)
                val windowManager = activity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
                val params = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )
                
                var finished = false
                fun cleanup(html: String?) {
                    if (finished) return
                    finished = true
                    try { windowManager.removeView(webView) } catch (_: Exception) {}
                    try { webView.destroy() } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(html)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Attendre que le JS DDoS-Guard termine
                        view?.postDelayed({
                            view.evaluateJavascript(
                                "(function(){return document.documentElement.outerHTML;})()"
                            ) { raw ->
                                val clean = raw
                                    ?.removeSurrounding("\"")
                                    ?.replace("\\u003c", "<")
                                    ?.replace("\\u003e", ">")
                                    ?.replace("\\\"", "\"")
                                    ?.replace("\\n", "\n")
                                // Si on voit un challenge DDoS-Guard, on ne resume pas encore
                                if (clean != null && (clean.contains("ddos-guard", true) || clean.contains("checking your browser", true))) {
                                    // On laisse le JS continuer : la redirection va déclencher un nouveau onPageFinished
                                    return@evaluateJavascript
                                }
                                cleanup(clean)
                            }
                        }, DETECT_DELAY_MS)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        // Si on reçoit une erreur, on nettoie
                        cleanup(null)
                    }
                }
                try {
                    windowManager.addView(webView, params)
                } catch (e: Exception) {
                    cleanup(null)
                    return@post
                }
                webView.loadUrl(url)
                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { cleanup(null) }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(activity: Activity): WebView {
        return WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }
}

object AppContextHolder {
    var activity: WeakReference<Activity>? = null
}
