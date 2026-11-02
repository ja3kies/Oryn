package com.example.oryn.reader.community.auth

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** RFC 7636 S256, 256 bits of entropy per verifier and independent redirect nonce. */
object Pkce {
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    fun create(now: Long): PkceAttempt {
        val random = SecureRandom()
        fun secret() = buildString { repeat(64) { append(alphabet[random.nextInt(alphabet.length)]) } }
        return PkceAttempt(secret(), secret(), now)
    }
    fun challenge(verifier: String): String {
        require(verifier.matches(Regex("[A-Za-z0-9._~-]{43,128}")))
        // Android API 24 compatible URL-safe Base64, without java.util.Base64 (API 26).
        val bytes = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val alphabet64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return buildString {
            var bits = 0; var count = 0
            bytes.forEach { byte ->
                bits = (bits shl 8) or (byte.toInt() and 255); count += 8
                while (count >= 6) { count -= 6; append(alphabet64[(bits ushr count) and 63]) }
            }
            if (count > 0) append(alphabet64[(bits shl (6 - count)) and 63])
        }
    }
    fun authorizeUrl(config: AuthConfiguration, attempt: PkceAttempt): String {
        check(config.isEnabled)
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val redirect = config.redirectUri + "?oryn_state=" + enc(attempt.nonce)
        return config.origin.trimEnd('/') + "/auth/v1/authorize?provider=" + enc(config.provider) +
            "&redirect_to=" + enc(redirect) + "&code_challenge=" + challenge(attempt.verifier) +
            "&code_challenge_method=s256"
    }

    /** Reject implicit tokens, alternate origins, encoded paths, duplicate keys and login CSRF.
     * The nonce is in redirect_to, separate from Supabase's own OAuth provider state.
     */
    fun callback(raw: String, config: AuthConfiguration, attempt: PkceAttempt): Callback? = runCatching {
        require(raw.length <= 8192)
        val uri = URI(raw)
        require(uri.scheme == "https" && uri.rawAuthority == config.redirectHost &&
            uri.rawPath == AuthConfiguration.CALLBACK_PATH && uri.rawFragment == null)
        val params = linkedMapOf<String, String>()
        require(!uri.rawQuery.isNullOrBlank())
        uri.rawQuery.split('&').forEach { part ->
            val pair = part.split('=', limit = 2)
            require(pair.size == 2)
            val key = URLDecoder.decode(pair[0], "UTF-8")
            val value = URLDecoder.decode(pair[1], "UTF-8")
            require(key in setOf("oryn_state", "code", "error", "error_code", "error_description"))
            require(params.put(key, value) == null)
        }
        val nonce = params["oryn_state"] ?: error("Missing state")
        require(MessageDigest.isEqual(nonce.toByteArray(), attempt.nonce.toByteArray()))
        if (params.containsKey("error")) {
            require(!params.containsKey("code"))
            Callback(null)
        } else {
            require(params.keys == setOf("oryn_state", "code"))
            val code = params.getValue("code")
            require(code.length in 1..2048 && code.none { it.isWhitespace() || it.isISOControl() })
            Callback(code)
        }
    }.getOrNull()

    class Callback(val code: String?) {
        override fun toString() = "AuthCallback([redacted])"
    }
}
