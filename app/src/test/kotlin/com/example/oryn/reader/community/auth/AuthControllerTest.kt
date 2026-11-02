package com.example.oryn.reader.community.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class AuthControllerTest {
    private val config = enabledConfig()
    private val store = MemoryAuthStore()
    private val api = FakeAuthApi()
    private var time = 1000L
    private fun controller(fake: AuthApi = api, vault: AuthStore = store) = SupabaseAuthController(config, fake, vault) { time }
    private fun callback() = config.redirectUri + "?oryn_state=${store.value!!.pending!!.nonce}&code=test-code"
    private fun savedSession(expired: Boolean = false) = StoredAuth(config.binding,
        AuthSession(testTokens("old"), testUser, if (expired) 1 else 900, if (expired) 3601 else 4500))

    @Test fun disabledDoesNotReadStoreOrCallNetwork() = runBlocking {
        val disabled = SupabaseAuthController(AuthConfiguration("", "", "", "", false, true, false), api, store) { time }
        disabled.restore(); disabled.refreshIfNeeded(); disabled.receiveCallback("anything")
        assertNull(disabled.beginSignIn()); assertNull(disabled.accessToken())
        assertEquals(0, store.reads); assertEquals(0, api.exchanges + api.refreshes + api.verifications)
        assertEquals(AuthStatus.Disabled, disabled.state.value.status)
    }

    @Test fun loginRestorationAndReplayProtection() = runBlocking {
        val first = controller()
        assertNotNull(first.beginSignIn())
        val raw = callback()
        val afterProcessDeath = controller()
        afterProcessDeath.restore()
        assertEquals(AuthStatus.AwaitingCallback, afterProcessDeath.state.value.status)
        afterProcessDeath.receiveCallback(raw)
        assertEquals(AuthStatus.SignedIn, afterProcessDeath.state.value.status)
        assertNull(store.value!!.pending)
        afterProcessDeath.receiveCallback(raw)
        assertEquals(1, api.exchanges)
        val restored = controller()
        restored.restore()
        assertEquals(AuthStatus.SignedIn, restored.state.value.status)
        assertTrue(api.verifications >= 2)
        assertNotNull(restored.accessToken())
    }

    @Test fun forgedAndExpiredCallbacksNeverExchange() = runBlocking {
        val model = controller()
        model.beginSignIn()
        val valid = callback()
        val attempt = store.value!!.pending
        model.receiveCallback(valid.replace("oryn_state=", "oryn_state=forged"))
        assertSame(attempt, store.value!!.pending)
        assertEquals(AuthStatus.InvalidCallback, model.state.value.status)
        time += 300
        model.receiveCallback(valid)
        assertNull(store.value!!.pending)
        assertEquals(AuthStatus.Expired, model.state.value.status)
        assertEquals(0, api.exchanges)
    }

    @Test fun providerDenialAndExplicitCancellationDiscardVerifier() = runBlocking {
        val model = controller()
        model.beginSignIn()
        model.receiveCallback(callback().substringBefore("&code=") + "&error=access_denied")
        assertEquals(AuthStatus.Cancelled, model.state.value.status)
        assertNull(store.value!!.pending)
        model.beginSignIn(); model.cancelSignIn()
        assertNull(store.value)
        assertEquals(AuthStatus.Cancelled, model.state.value.status)
    }

    @Test fun cancellationDuringExchangeConsumesAttemptAndPropagates() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val waitingApi = object : FakeAuthApi() {
            override suspend fun exchange(code: String, verifier: String): AuthTokens {
                entered.complete(Unit); awaitCancellation()
            }
        }
        val model = controller(waitingApi)
        model.beginSignIn(); val raw = callback()
        val job = launch { model.receiveCallback(raw) }
        entered.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertNull(store.value!!.pending)
        assertFalse(model.state.value.hasSession)
        assertEquals(AuthStatus.Cancelled, model.state.value.status)
    }

    @Test fun refreshIsSerializedAndRotationIsPersisted() = runBlocking {
        time = 4000; store.value = savedSession(expired = true)
        val model = controller()
        val one = async { model.accessToken() }; val two = async { model.accessToken() }
        assertNotNull(one.await()); assertNotNull(two.await())
        assertEquals(1, api.refreshes)
        assertTrue(store.value!!.session!!.tokens.refresh.endsWith("rotated"))
        assertEquals(AuthStatus.SignedIn, model.state.value.status)
    }

    @Test fun restoredSessionIsNotAuthorizedAfterOfflineVerificationFailure() = runBlocking {
        store.value = savedSession()
        api.failure = IOException("fixture offline")
        val model = controller()
        model.restore(); assertEquals(AuthStatus.Offline, model.state.value.status)
        assertNull(model.accessToken())
        val attempts = api.verifications
        model.refreshIfNeeded() // No automatic retry after a transport failure.
        assertEquals(attempts, api.verifications)
        api.failure = null
        assertNotNull(model.accessToken())
        assertTrue(api.verifications > attempts)
    }

    @Test fun rotatedRefreshTokenSurvivesOfflineUserValidation() = runBlocking {
        time = 4000; store.value = savedSession(expired = true)
        val fake = object : FakeAuthApi() {
            override suspend fun userId(accessToken: String): UUID = throw IOException("fixture offline")
        }
        val model = controller(fake)
        assertNull(model.accessToken())
        assertEquals(AuthStatus.Offline, model.state.value.status)
        assertTrue(store.value!!.session!!.tokens.refresh.endsWith("rotated"))
    }

    @Test fun invalidRefreshOrIdentityMismatchErasesCredentials() = runBlocking {
        time = 4000; store.value = savedSession(expired = true)
        api.failure = AuthHttpException(401)
        val model = controller()
        assertNull(model.accessToken())
        assertNull(store.value)
        assertEquals(AuthStatus.InvalidSession, model.state.value.status)
        store.value = savedSession(expired = true)
        val wrongUser = object : FakeAuthApi() {
            override suspend fun userId(accessToken: String): UUID = UUID.fromString("20000000-0000-4000-8000-000000000002")
        }
        assertNull(controller(wrongUser).accessToken())
        assertNull(store.value)
    }

    @Test fun logoutForgetsLocallyEvenIfRevocationIsOffline() = runBlocking {
        store.value = savedSession(); val model = controller(); model.restore()
        api.failure = IOException("fixture offline")
        model.signOut()
        assertNull(store.value)
        assertFalse(model.state.value.hasSession)
        assertEquals(1, api.logouts)
        assertEquals(AuthStatus.SignedOutRevocationUnconfirmed, model.state.value.status)
        assertNull(model.accessToken())
    }

    @Test fun configChangesCannotRestoreAnotherEnvironmentSession() = runBlocking {
        store.value = StoredAuth("different-binding", savedSession().session, Pkce.create(time))
        val model = controller(); model.restore()
        assertNull(store.value)
        assertEquals(0, api.verifications)
        assertEquals(AuthStatus.SignedOut, model.state.value.status)
    }

    @Test fun storageFailureFailsClosedWithoutExposingDetails() = runBlocking {
        val failing = object : AuthStore {
            override suspend fun read(): StoredAuth? = throw AuthStorageException()
            override suspend fun write(value: StoredAuth) = throw AuthStorageException()
            override suspend fun clear() = Unit
        }
        val model = controller(vault = failing)
        model.restore()
        assertEquals(AuthStatus.StorageError, model.state.value.status)
        assertNull(model.accessToken())
        assertFalse(model.state.value.hasSession)
    }
    @Test fun foregroundRestorationRevalidatesBeforeAuthorizingAgain() = runBlocking {
        store.value = savedSession()
        val model = controller()
        model.restore(); assertEquals(AuthStatus.SignedIn, model.state.value.status)
        api.failure = IOException("fixture offline")
        model.restore()
        assertEquals(AuthStatus.Offline, model.state.value.status)
        assertNull(model.accessToken())
    }

    @Test fun serverFailureDoesNotTriggerAutomaticRefreshRetry() = runBlocking {
        store.value = savedSession()
        val model = controller(); model.restore()
        time = 4500; api.failure = AuthHttpException(500)
        assertNull(model.accessToken())
        val attempts = api.refreshes
        model.refreshIfNeeded()
        assertEquals(attempts, api.refreshes)
        assertEquals(AuthStatus.Error, model.state.value.status)
    }

    @Test fun cancellationDuringRefreshAllowsLocalLogoutAndNeverReturnsExpiredToken() = runBlocking {
        time = 4000; store.value = savedSession(expired = true)
        val entered = CompletableDeferred<Unit>()
        val waiting = object : FakeAuthApi() {
            override suspend fun refresh(refreshToken: String): AuthTokens {
                entered.complete(Unit); awaitCancellation()
            }
        }
        val model = controller(waiting)
        val job = launch { model.accessToken() }
        entered.await(); job.cancelAndJoin()
        assertEquals(AuthStatus.Cancelled, model.state.value.status)
        model.signOut()
        assertNull(store.value)
        assertFalse(model.state.value.hasSession)
    }

    @Test fun tokenExpiringDuringVerificationIsNeverPublishedOrReturned() = runBlocking {
        val slowVerification = object : FakeAuthApi() {
            override suspend fun exchange(code: String, verifier: String) = testTokens(ttl = 2)
            override suspend fun userId(accessToken: String): UUID { time += 3; return testUser }
        }
        val model = controller(slowVerification)
        model.beginSignIn(); model.receiveCallback(callback())
        assertEquals(AuthStatus.InvalidSession, model.state.value.status)
        assertNull(store.value)
        assertNull(model.accessToken())
    }

    @Test fun restoredTokenExpiringDuringUserVerificationFailsClosed() = runBlocking {
        store.value = savedSession()
        val slowUser = object : FakeAuthApi() {
            override suspend fun userId(accessToken: String): UUID { time = 4600; return testUser }
        }
        val model = controller(slowUser)
        assertNull(model.accessToken())
        assertNull(store.value)
        assertEquals(AuthStatus.InvalidSession, model.state.value.status)
    }

    @Test fun oldAttemptCannotReplaceNewAttemptOrExchangeAfterCancellation() = runBlocking {
        val model = controller()
        model.beginSignIn(); val old = callback()
        model.beginSignIn(); val current = callback()
        model.receiveCallback(old)
        assertEquals(0, api.exchanges)
        assertTrue(model.state.value.hasPending)
        model.cancelSignIn(); model.receiveCallback(current)
        assertEquals(0, api.exchanges)
        assertFalse(model.state.value.hasSession)
    }

    @Test fun networkFailureDuringExchangeConsumesCodeAndCannotBeReplayedAfterRestart() = runBlocking {
        val model = controller(); model.beginSignIn(); val raw = callback()
        api.failure = IOException("fixture disconnected")
        model.receiveCallback(raw)
        assertEquals(AuthStatus.Offline, model.state.value.status)
        assertNull(store.value!!.pending)
        api.failure = null
        controller().receiveCallback(raw)
        assertEquals(1, api.exchanges)
        assertNull(store.value!!.session)
    }

    @Test fun offlineRefreshPreservesSessionWithoutReturningExpiredTokenOrRetrying() = runBlocking {
        time = 4000; store.value = savedSession(expired = true)
        api.failure = java.net.SocketTimeoutException("fixture timeout")
        val model = controller()
        assertNull(model.accessToken())
        assertEquals(AuthStatus.Offline, model.state.value.status)
        assertNotNull(store.value!!.session)
        model.refreshIfNeeded(); assertEquals(1, api.refreshes)
        api.failure = null
        assertNotNull(model.accessToken())
        assertEquals(2, api.refreshes)
    }

    @Test fun rotatedRefreshIsUsedAfterProcessRestartInsteadOfReusingOldToken() = runBlocking {
        time = 4000; store.value = savedSession(expired = true)
        controller().restore()
        val rotated = store.value!!.session!!.tokens.refresh
        time += 3600
        val validating = object : FakeAuthApi() {
            override suspend fun refresh(refreshToken: String): AuthTokens {
                assertTrue(refreshToken == rotated)
                assertFalse(refreshToken.endsWith("old"))
                return testTokens("second-rotation")
            }
        }
        assertNotNull(controller(validating).accessToken())
        assertTrue(store.value!!.session!!.tokens.refresh.endsWith("second-rotation"))
    }

    @Test fun logoutClearsDurablyBeforeRequestAndCancellationNeverRestoresCredentials() = runBlocking {
        store.value = savedSession()
        val entered = CompletableDeferred<Unit>()
        val waiting = object : FakeAuthApi() {
            override suspend fun logout(accessToken: String) {
                assertNull(store.value)
                entered.complete(Unit); awaitCancellation()
            }
        }
        val model = controller(waiting); model.restore()
        val job = launch { model.signOut() }
        entered.await(); job.cancelAndJoin()
        assertNull(store.value)
        assertEquals(AuthStatus.SignedOutRevocationUnconfirmed, model.state.value.status)
        val restarted = controller(); restarted.restore()
        assertFalse(restarted.state.value.hasSession)
        assertNull(restarted.accessToken())
    }

    @Test fun revocationServerFailureDoesNotResurrectLoggedOutSession() = runBlocking {
        store.value = savedSession(); val model = controller(); model.restore()
        api.failure = AuthHttpException(503)
        model.signOut()
        assertNull(store.value)
        assertFalse(model.state.value.hasSession)
        assertEquals(AuthStatus.SignedOutRevocationUnconfirmed, model.state.value.status)
    }

}
