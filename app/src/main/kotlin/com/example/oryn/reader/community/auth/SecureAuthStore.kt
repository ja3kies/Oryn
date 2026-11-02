package com.example.oryn.reader.community.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Atomic AES-256-GCM vault in noBackupFilesDir (excluded from cloud backup and device transfer).
 * Key is generated in Android Keystore, never exported. No plaintext fallback on any error.
 * Only this process accesses the vault; all read/modify/write operations are controller-serialized.
 */
class SecureAuthStore(context: Context) : AuthStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "community_auth/session.v1"))
    private val aad = (context.packageName + ":community-auth:v1").toByteArray()

    override suspend fun read(): StoredAuth? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withContext null
        try {
            val encrypted = file.openRead().use { input ->
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > MAX_BYTES) throw AuthStorageException()
                    bytes.write(buffer, 0, count)
                }
                bytes.toByteArray()
            }
            val plain = AuthVaultCipher.open(encrypted, key(create = false), aad)
            try { AuthRecordCodec.decode(plain) } finally { plain.fill(0) }
        } catch (_: Exception) {
            discardInvalidVault()
            throw AuthStorageException()
        }
    }

    override suspend fun write(value: StoredAuth) = withContext(Dispatchers.IO) {
        try {
            val plain = AuthRecordCodec.encode(value)
            val encrypted = try {
                AuthVaultCipher.seal(plain, key(), aad)
            } finally { plain.fill(0) }
            require(encrypted.size <= MAX_BYTES)
            check(file.baseFile.parentFile!!.isDirectory || file.baseFile.parentFile!!.mkdirs())
            val output = file.startWrite()
            try { output.write(encrypted); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
        } catch (_: Exception) {
            // A failed rotation cannot leave old credentials available for reuse.
            discardInvalidVault()
            throw AuthStorageException()
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            // Destroy the key first: leftover ciphertext cannot revive a logged-out session.
            val keyDeleted = runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) }
            }.isSuccess
            // Also attempt file removal if Keystore is temporarily unavailable.
            file.delete()
            if (!keyDeleted || file.baseFile.exists() || File(file.baseFile.path + ".bak").exists() || File(file.baseFile.path + ".new").exists()) throw AuthStorageException()
        } catch (_: Exception) {
            throw AuthStorageException()
        }
    }

    private fun key(create: Boolean = true): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) throw AuthStorageException()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    private fun discardInvalidVault() {
        file.delete()
        // Recover from key invalidation/loss, corrupt ciphertext or failed atomic publication.
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) } }
    }

    private companion object {
        const val ALIAS = "oryn.community.auth.v1"
        const val MAX_BYTES = 32 * 1024
    }
}

/** Small versioned format; lengths are bounded before allocating or returning any credentials. */
internal object AuthRecordCodec {
    fun encode(value: StoredAuth): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeUTF(value.binding)
            out.writeBoolean(value.session != null)
            value.session?.let {
                out.writeUTF(it.tokens.access); out.writeUTF(it.tokens.refresh); out.writeLong(it.tokens.expiresIn)
                out.writeUTF(it.userId.toString()); out.writeLong(it.issuedAt); out.writeLong(it.expiresAt)
            }
            out.writeBoolean(value.pending != null)
            value.pending?.let { out.writeUTF(it.verifier); out.writeUTF(it.nonce); out.writeLong(it.createdAt) }
        }
        bytes.toByteArray().also { require(it.size <= 31 * 1024) }
    }

    fun decode(bytes: ByteArray): StoredAuth {
        require(bytes.size <= 31 * 1024)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == 1)
            val binding = input.readUTF().also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            val session = if (input.readBoolean()) {
                val tokens = AuthTokens(input.readUTF(), input.readUTF(), input.readLong())
                val userId = UUID.fromString(input.readUTF())
                val issued = input.readLong(); val expires = input.readLong()
                require(issued > 0 && expires > issued && expires - issued == tokens.expiresIn)
                AuthSession(tokens, userId, issued, expires)
            } else null
            val pending = if (input.readBoolean()) {
                val verifier = input.readUTF(); val nonce = input.readUTF(); val created = input.readLong()
                require(verifier.matches(Regex("[A-Za-z0-9._~-]{43,128}")) && nonce.matches(Regex("[A-Za-z0-9._~-]{43,128}")) && created > 0)
                PkceAttempt(verifier, nonce, created)
            } else null
            require(input.available() == 0 && !(session != null && pending != null))
            StoredAuth(binding, session, pending)
        }
    }
}

/** Shared by Android Keystore storage and JVM cryptographic regression tests. */
internal object AuthVaultCipher {
    fun seal(plain: ByteArray, key: SecretKey, aad: ByteArray): ByteArray {
        require(plain.size <= 31 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        check(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }
    fun open(encrypted: ByteArray, key: SecretKey, aad: ByteArray): ByteArray {
        require(encrypted.size in (1 + 12 + 16)..(32 * 1024) && encrypted[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(encrypted, 13, encrypted.size - 13)
    }
}
