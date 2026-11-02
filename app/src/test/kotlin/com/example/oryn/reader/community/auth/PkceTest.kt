package com.example.oryn.reader.community.auth

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class PkceTest {
    private val config = enabledConfig()
    private val attempt = Pkce.create(1000)
    private fun url(query: String) = config.redirectUri + "?oryn_state=${attempt.nonce}&$query"

    @Test fun matchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun usesIndependentRandomSecretsAndS256Only() {
        val other = Pkce.create(1000)
        assertEquals(64, attempt.verifier.length)
        assertNotEquals(attempt.verifier, attempt.nonce)
        assertNotEquals(attempt.verifier, other.verifier)
        val raw = Pkce.authorizeUrl(config, attempt)
        assertFalse(raw.contains(attempt.verifier))
        val params = URI(raw).rawQuery.split('&').associate {
            val pair = it.split('=', limit = 2); pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }
        assertEquals("s256", params["code_challenge_method"])
        assertEquals(Pkce.challenge(attempt.verifier), params["code_challenge"])
        val redirect = URI(params.getValue("redirect_to"))
        assertEquals(config.redirectUri, redirect.toString().substringBefore('?'))
        assertEquals("oryn_state=" + attempt.nonce, URLDecoder.decode(redirect.rawQuery, "UTF-8"))
    }

    @Test fun acceptsOnlyExactCallbackWithMatchingNonce() {
        assertEquals("test-code", Pkce.callback(url("code=test-code"), config, attempt)?.code)
        val bad = listOf(
            url("code=a&code=b"), url("%63ode=a&code=b"), url("code=a&unexpected=b"),
            url("code=a&access_token=leak"), url("code=a#access_token=leak"), url("code="),
            url("code=a&error=denied"), url("code=a&oryn_state=duplicate"),
            url("code=%0A"), url("code=%zz"), url("code=a").replace(attempt.nonce, "wrong"),
            url("code=a").replace("https:", "http:"), url("code=a").replace("reader.example.test", "reader.example.test.attacker.test"),
            url("code=a").replace("reader.example.test", "user@reader.example.test"),
            url("code=a").replace("reader.example.test", "reader.example.test:443"),
            url("code=a").replace("/auth/callback", "/auth/%63allback"),
            url("code=a").replace("/auth/callback", "/auth/callback/"), url("code=" + "a".repeat(8200))
        )
        bad.forEachIndexed { index, raw -> assertNull("Rejected callback case $index", Pkce.callback(raw, config, attempt)) }
        assertNotNull(Pkce.callback(url("error=access_denied&error_description=ignored"), config, attempt))
    }

    @Test fun expiryAndFutureClockFailClosed() {
        assertTrue(attempt.isFresh(1299))
        assertFalse(attempt.isFresh(1300))
        assertFalse(attempt.isFresh(999))
    }

    @Test fun disabledForMissingConfigurationReleaseOrUnverifiedEnvironment() {
        assertTrue(config.isEnabled)
        assertFalse(AuthConfiguration("", "", "", "", true, true, true).isEnabled)
        assertFalse(AuthConfiguration(config.origin, config.publishableKey, config.redirectHost, config.provider, true, false, true).isEnabled)
        assertFalse(AuthConfiguration(config.origin, config.publishableKey, config.redirectHost, config.provider, true, true, false).isEnabled)
        assertFalse(AuthConfiguration(config.origin, "sb_secret_not_allowed", config.redirectHost, config.provider, true, true, true).isEnabled)
        assertFalse(AuthConfiguration(config.origin, config.publishableKey, "reader.example.test/path", config.provider, true, true, true).isEnabled)
    }
    @Test fun rejectsNestedRedirectsEncodedOriginsAndPollutedErrorCallbacks() {
        val attacks = listOf(
            url("code=ok&redirect_to=https%3A%2F%2Fattacker.example.test"),
            url("code=ok&refresh_token=fixture"), url("code=ok&state=unbound"),
            url("code=ok").replace("reader.example.test", "reader.example.test."),
            url("code=ok").replace("reader.example.test", "%72eader.example.test"),
            url("code=ok").replace("/auth/callback", "/auth/../auth/callback"),
            url("error=denied&error=again"), url("error=denied&%6fryn_state=duplicate"),
            url("code=ok").replace("https:", "com.example.oryn.reader:")
        )
        attacks.forEachIndexed { index, raw -> assertNull("Attack case $index", Pkce.callback(raw, config, attempt)) }
    }

    @Test fun refusesSupabaseHostedCallbackAndInvalidProvider() {
        assertFalse(AuthConfiguration(config.origin, config.publishableKey, "auth.supabase.co", config.provider, true, true, true).isEnabled)
        assertFalse(AuthConfiguration(config.origin, config.publishableKey, config.redirectHost, "", true, true, true).isEnabled)
    }

}
