package com.example.oryn.reader.community.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, read-only probe for a dedicated isolated Supabase Auth project, never production. */
class IsolatedAuthContractTest {
    @Test fun verifiesDedicatedTestUserAgainstIsolatedAuth() = runBlocking {
        val settings = IsolatedAuthSettings.read { System.getenv(it).orEmpty() }
        assumeTrue("Isolated Auth configuration not supplied", settings != null)
        settings!!
        assertNotNull(SupabaseAuthApi(settings.config).userId(settings.access))
    }
}

/** Test-only configuration: no URL/key/token fallback and no credential in diagnostics. */
internal class IsolatedAuthSettings(val config: AuthConfiguration, val access: String) {
    override fun toString() = "IsolatedAuthSettings([redacted])"
    companion object {
        fun read(value: (String) -> String): IsolatedAuthSettings? {
            if (value("ORYN_AUTH_TEST_ISOLATED_VERIFIED") != "true") return null
            val config = AuthConfiguration(
                value("ORYN_AUTH_TEST_URL"), value("ORYN_AUTH_TEST_PUBLISHABLE_KEY"),
                value("ORYN_AUTH_TEST_REDIRECT_HOST"), value("ORYN_AUTH_TEST_PROVIDER"), true, true, true
            )
            val access = value("ORYN_AUTH_TEST_ACCESS_TOKEN")
            require(config.isEnabled && access.length in 1..8192 && access.all { it.code in 33..126 }) {
                "Complete isolated Auth configuration required after explicit opt-in"
            }
            return IsolatedAuthSettings(config, access)
        }
    }
}

class IsolatedAuthSettingsTest {
    @Test fun absentOptInNeverReadsCredentialsOrStartsProbe() {
        assertNull(IsolatedAuthSettings.read { key ->
            if (key == "ORYN_AUTH_TEST_ISOLATED_VERIFIED") "" else error("Credentials must not be accessed")
        })
    }
    @Test fun incompleteExplicitOptInFailsInsteadOfSilentlySkipping() {
        try {
            IsolatedAuthSettings.read { if (it == "ORYN_AUTH_TEST_ISOLATED_VERIFIED") "true" else "" }
            fail("Incomplete opted-in configuration accepted")
        } catch (error: IllegalArgumentException) {
            org.junit.Assert.assertEquals("Complete isolated Auth configuration required after explicit opt-in", error.message)
        }
    }
}
