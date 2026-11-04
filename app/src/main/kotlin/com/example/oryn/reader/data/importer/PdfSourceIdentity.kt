package com.example.oryn.reader.data.importer

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Hash exactly the bounded private snapshot subsequently opened by PDFBox, not URI metadata. */
internal fun copyPdfSnapshot(
    input: InputStream, output: OutputStream, maxBytes: Long = 100L * 1024 * 1024,
    checkCancellation: () -> Unit = {}
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        checkCancellation()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("PDF exceeds its bounded snapshot limit")
        digest.update(buffer, 0, count)
        output.write(buffer, 0, count)
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** No verified content identity means no persisted cache reuse. */
internal fun pdfReflowCacheIdentity(uri: String, pageCount: Int, sourceFingerprint: String?): String? {
    if (sourceFingerprint == null || !sourceFingerprint.matches(Regex("[a-f0-9]{64}")) || pageCount <= 0) return null
    val key = "$uri\u0000$pageCount\u0000$sourceFingerprint"
    return MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
