package com.example.oryn.reader.community.auth

import com.example.oryn.reader.community.CommunitySessionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** One serialized owner of refresh-token rotation, callbacks, sign-out and durable state.
 * Nothing observes Room. The catalogue remains independently disabled.
 */
class SupabaseAuthController(
    private val config: AuthConfiguration,
    private val api: AuthApi,
    private val store: AuthStore,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }
) : CommunitySessionProvider {
    private val mutex = Mutex()
    private var initialized = false
    private var validated = false
    private var session: AuthSession? = null
    private var pending: PkceAttempt? = null
    private val mutableState = MutableStateFlow(AuthUiState(if (config.isEnabled) AuthStatus.SignedOut else AuthStatus.Disabled))
    val state = mutableState.asStateFlow()

    fun redirectUnavailable() {
        if (config.isEnabled) publish(AuthStatus.RedirectUnavailable)
    }

    suspend fun restore() = mutex.withLock {
        if (!config.isEnabled) return@withLock
        guarded {
            publish(AuthStatus.Busy)
            initialize()
            if (session != null) { validated = false; ensureSession() } else {
                expireAttempt()
                publish(if (pending == null) AuthStatus.SignedOut else AuthStatus.AwaitingCallback)
            }
        }
    }

    suspend fun beginSignIn(): String? = mutex.withLock {
        if (!config.isEnabled) return@withLock null
        var url: String? = null
        guarded {
            initialize()
            if (session != null) { ensureSession(); return@guarded }
            pending = Pkce.create(now())
            persist()
            publish(AuthStatus.AwaitingCallback)
            url = Pkce.authorizeUrl(config, pending!!)
        }
        url
    }

    suspend fun receiveCallback(raw: String) = mutex.withLock {
        if (!config.isEnabled) return@withLock
        guarded {
            initialize()
            val attempt = pending
            if (attempt == null) {
                if (session == null) publish(AuthStatus.InvalidCallback)
                return@guarded
            }
            // A forged callback cannot consume the genuine in-flight attempt.
            val callback = Pkce.callback(raw, config, attempt)
            if (callback == null) {
                publish(AuthStatus.InvalidCallback)
                return@guarded
            }
            // Consume before exchange: replay, cancellation or ambiguous network failure needs a new flow.
            pending = null
            persist()
            if (!attempt.isFresh(now())) { publish(AuthStatus.Expired); return@guarded }
            if (callback.code == null) { publish(AuthStatus.Cancelled); return@guarded }
            publish(AuthStatus.Busy)
            val tokens = api.exchange(callback.code, attempt.verifier)
            // Persist rotations even if UI cancellation races with a successful HTTP response.
            accept(tokens)
        }
    }

    suspend fun cancelSignIn() = mutex.withLock {
        if (!config.isEnabled) return@withLock
        guarded {
            initialize()
            val old = session
            withContext(NonCancellable) { erase(); publish(AuthStatus.Cancelled) }
            if (old != null) {
                try { api.logout(old.tokens.access) }
                catch (error: CancellationException) { publish(AuthStatus.SignedOutRevocationUnconfirmed); throw error }
                catch (_: Exception) { publish(AuthStatus.SignedOutRevocationUnconfirmed) }
            }
        }
    }

    override suspend fun accessToken(): String? = mutex.withLock {
        if (!config.isEnabled) return@withLock null
        var access: String? = null
        guarded {
            initialize()
            if (ensureSession()) access = session?.tokens?.access
        }
        access
    }

    /** Foreground tick only. No scheduler, background service, or automatic network retry. */
    suspend fun refreshIfNeeded() = mutex.withLock {
        if (!config.isEnabled) return@withLock
        // An offline failure requires a visible/manual retry or a new foreground restore.
        if (session != null && state.value.status != AuthStatus.SignedIn) return@withLock
        guarded {
            initialize()
            if (session != null) ensureSession()
            else if (pending != null && !pending!!.isFresh(now())) {
                pending = null; persist(); publish(AuthStatus.Expired)
            }
        }
    }

    suspend fun signOut() = mutex.withLock {
        if (!config.isEnabled) return@withLock
        guarded {
            initialize()
            val old = session
            // Forget credentials locally even if logout is cancelled or the server is unreachable.
            withContext(NonCancellable) { erase(); publish(AuthStatus.SignedOut) }
            if (old != null) {
                try {
                    api.logout(old.tokens.access)
                } catch (error: CancellationException) {
                    publish(AuthStatus.SignedOutRevocationUnconfirmed)
                    throw error
                } catch (_: Exception) {
                    publish(AuthStatus.SignedOutRevocationUnconfirmed)
                }
            }
        }
    }

    override suspend fun isCurrent(accessToken: String): Boolean = mutex.withLock {
        val current = session
        val time = now()
        config.isEnabled && validated && current != null && current.tokens.access == accessToken &&
            time >= current.issuedAt - 60 && time < current.expiresAt
    }

    override suspend fun invalidate(accessToken: String) = mutex.withLock {
        if (session?.tokens?.access != accessToken) return@withLock
        guarded { withContext(NonCancellable) { erase(); publish(if (config.isEnabled) AuthStatus.SignedOut else AuthStatus.Disabled) } }
    }

    override suspend fun clear() = mutex.withLock {
        guarded { withContext(NonCancellable) { erase(); publish(if (config.isEnabled) AuthStatus.SignedOut else AuthStatus.Disabled) } }
    }

    private suspend fun initialize() {
        if (initialized) return
        val saved = store.read()
        initialized = true
        if (saved != null && saved.binding == config.binding) {
            session = saved.session
            pending = saved.pending
        } else if (saved != null) erase()
    }

    private suspend fun expireAttempt() {
        if (pending?.isFresh(now()) == false) { pending = null; persist() }
    }

    private suspend fun ensureSession(): Boolean {
        val current = session ?: run { publish(AuthStatus.SignedOut); return false }
        if (current.needsRefresh(now())) {
            publish(AuthStatus.Busy)
            val tokens = api.refresh(current.tokens.refresh)
            accept(tokens, current.userId)
        } else {
            if (!validated) {
                if (api.userId(current.tokens.access) != current.userId) throw InvalidAuthSession()
                // A slow verification must not authorize a token that expired while awaiting /user.
                if (current.expiresAt <= now()) throw InvalidAuthSession()
                validated = true
            }
            publish(AuthStatus.SignedIn)
        }
        return true
    }

    private suspend fun accept(tokens: AuthTokens, expectedUser: java.util.UUID? = null) {
        val issued = now()
        validated = false
        if (expectedUser != null) {
            // Save the rotated refresh token before user verification can fail offline.
            withContext(NonCancellable) {
                session = AuthSession(tokens, expectedUser, issued, issued + tokens.expiresIn)
                pending = null
                persist()
            }
        }
        val user = api.userId(tokens.access)
        if (expectedUser != null && user != expectedUser) throw InvalidAuthSession()
        if (issued + tokens.expiresIn <= now()) throw InvalidAuthSession()
        withContext(NonCancellable) {
            session = AuthSession(tokens, user, issued, issued + tokens.expiresIn)
            pending = null
            persist()
            validated = true
            publish(AuthStatus.SignedIn)
        }
    }

    private suspend fun persist() = store.write(StoredAuth(config.binding, session, pending))

    private suspend fun erase() {
        session = null; pending = null; validated = false
        store.clear()
        initialized = true
    }

    private fun publish(status: AuthStatus) {
        mutableState.value = AuthUiState(status, session != null, pending != null)
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            if (state.value.status != AuthStatus.SignedOutRevocationUnconfirmed) {
                publish(if (session != null && validated && !session!!.needsRefresh(now())) AuthStatus.SignedIn else if (pending != null) AuthStatus.AwaitingCallback else AuthStatus.Cancelled)
            }
            throw error
        } catch (_: AuthStorageException) {
            session = null; pending = null; validated = false; initialized = false
            publish(AuthStatus.StorageError)
        } catch (error: AuthHttpException) {
            if (error.status in setOf(400, 401, 403, 422)) invalidate() else publish(AuthStatus.Error)
        } catch (_: InvalidAuthSession) {
            invalidate()
        } catch (_: IOException) {
            publish(AuthStatus.Offline)
        } catch (_: Exception) {
            // No exception body, request, URL or credential reaches UI or logs.
            publish(AuthStatus.Error)
        }
    }

    private suspend fun invalidate() {
        withContext(NonCancellable) {
            try { erase(); publish(AuthStatus.InvalidSession) }
            catch (_: AuthStorageException) { session = null; pending = null; publish(AuthStatus.StorageError) }
        }
    }
}
