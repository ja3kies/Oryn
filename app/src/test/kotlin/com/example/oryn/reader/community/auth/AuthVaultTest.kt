package com.example.oryn.reader.community.auth

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class AuthVaultTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val aad = "unit-test-auth:v1".toByteArray()
    private fun record() = StoredAuth(enabledConfig().binding, AuthSession(testTokens(), testUser, 1000, 4600))

    @Test fun encryptsRoundTripsWithUniqueIvAndRejectsTampering() {
        val plain = AuthRecordCodec.encode(record())
        val first = AuthVaultCipher.seal(plain, key, aad)
        val second = AuthVaultCipher.seal(plain, key, aad)
        assertFalse(first.contentEquals(second))
        assertFalse(String(first, Charsets.ISO_8859_1).contains("test-access"))
        val restored = AuthRecordCodec.decode(AuthVaultCipher.open(first, key, aad))
        assertEquals(testUser, restored.session!!.userId)
        assertTrue(restored.session.tokens.refresh.endsWith("new"))
        expectRejected { AuthVaultCipher.open(first.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }, key, aad) }
        expectRejected { AuthVaultCipher.open(first, key, "wrong-package".toByteArray()) }
        expectRejected { AuthVaultCipher.open(first, KeyGenerator.getInstance("AES").generateKey(), aad) }
    }

    @Test fun rejectsTruncationUnknownVersionOversizeAndTrailingBytes() {
        val plain = AuthRecordCodec.encode(record())
        expectRejected { AuthRecordCodec.decode(plain.copyOf(4)) }
        expectRejected { AuthRecordCodec.decode(plain.copyOf().also { it[3] = 2 }) }
        expectRejected { AuthRecordCodec.decode(plain + byteArrayOf(0)) }
        expectRejected { AuthRecordCodec.decode(ByteArray(32 * 1024)) }
        expectRejected { AuthVaultCipher.open(ByteArray(28), key, aad) }
    }

    @Test fun pendingVerifierSurvivesCodecWithoutRevealingSecretsInToString() {
        val pending = Pkce.create(1000)
        val encoded = AuthRecordCodec.encode(StoredAuth(enabledConfig().binding, pending = pending))
        val restored = AuthRecordCodec.decode(encoded)
        assertTrue(pending.verifier == restored.pending!!.verifier)
        for (value in listOf(pending, restored, record().session!!, testTokens(), Pkce.Callback("private-code"))) {
            assertFalse(value.toString().contains(pending.verifier))
            assertFalse(value.toString().contains("test-access"))
            assertFalse(value.toString().contains("test-refresh"))
            assertFalse(value.toString().contains("private-code"))
        }
    }

    private fun expectRejected(block: () -> Any) {
        try { block(); fail("Invalid vault data accepted") } catch (error: AssertionError) { throw error }
        catch (_: Exception) { /* Authentication or bounded-format validation rejected it. */ }
    }
}
