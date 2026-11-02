package com.example.oryn.reader.community.auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Application-context singleton: callbacks and UI use the same serialized session owner. */
class CommunityAuthRuntime private constructor(context: Context) {
    private val app = context.applicationContext
    private val config = AuthConfiguration.fromBuild()
    val controller = SupabaseAuthController(config, SupabaseAuthApi(config), SecureAuthStore(app))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operation: Job? = null
    private var pendingCallback: String? = null
    private val browserRequests = Channel<String>(Channel.RENDEZVOUS)
    val browserUrls = browserRequests.receiveAsFlow()

    fun restore() = launchIfIdle { controller.restore() }
    fun tick() = launchIfIdle { controller.refreshIfNeeded() }
    fun signIn() = replaceOperation {
        if (!verifiedRedirect()) { controller.redirectUnavailable(); return@replaceOperation }
        controller.beginSignIn()?.let { browserRequests.send(it) }
    }
    fun cancel() = replaceOperation { controller.cancelSignIn() }
    fun signOut() = replaceOperation { controller.signOut() }
    fun receiveCallback(raw: String) {
        if (!config.isEnabled || raw.length > 8192) return
        // Verification can be revoked while the browser is open or while the process is stopped.
        if (!verifiedRedirect()) { controller.redirectUnavailable(); return }
        // Untrusted intents never cancel refresh/exchange. Buffer at most one callback.
        if (operation?.isActive == true) {
            if (pendingCallback == null) pendingCallback = raw
        } else startOperation { controller.receiveCallback(raw) }
    }
    fun browserUnavailable() = replaceOperation { controller.cancelSignIn(); controller.redirectUnavailable() }

    private fun launchIfIdle(block: suspend () -> Unit) {
        if (operation?.isActive != true) startOperation(block)
    }
    private fun replaceOperation(block: suspend () -> Unit) {
        pendingCallback = null
        operation?.cancel()
        startOperation(block)
    }
    private fun startOperation(block: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        operation = job
        job.invokeOnCompletion {
            scope.launch {
                if (operation === job) {
                    operation = null
                    pendingCallback?.let { raw -> pendingCallback = null; receiveCallback(raw) }
                }
            }
        }
        job.start()
    }

    private fun verifiedRedirect(): Boolean {
        if (!config.isEnabled) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val state = app.getSystemService(DomainVerificationManager::class.java)
                    .getDomainVerificationUserState(app.packageName)
                state?.isLinkHandlingAllowed == true &&
                    state.hostToStateMap[config.redirectHost] == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
            } else {
                // Older Android has no public per-host verification API. Require OS default resolution
                // to Oryn; isolated setup must additionally verify assetlinks via adb before enabling.
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.redirectUri)).addCategory(Intent.CATEGORY_BROWSABLE)
                @Suppress("DEPRECATION")
                app.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == app.packageName
            }
        }.getOrDefault(false)
    }

    companion object {
        @Volatile private var instance: CommunityAuthRuntime? = null
        fun get(context: Context): CommunityAuthRuntime = instance ?: synchronized(this) {
            instance ?: CommunityAuthRuntime(context.applicationContext).also { instance = it }
        }
    }
}
