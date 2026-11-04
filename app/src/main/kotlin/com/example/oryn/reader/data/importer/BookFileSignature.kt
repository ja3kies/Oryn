package com.example.oryn.reader.data.importer

/** A small prefix check before persisting an import; MIME/name alone never prove readability. */
internal object BookFileSignature {
    const val PREFIX_BYTES = 1024

    fun matches(format: String, prefix: ByteArray): Boolean {
        if (prefix.isEmpty() || prefix.size > PREFIX_BYTES) return false
        return when (format) {
            // PDF readers allow a short transport preamble before the PDF header.
            "PDF" -> prefix.toString(Charsets.ISO_8859_1).contains("%PDF-")
            "EPUB" -> prefix.size >= 4 && prefix[0] == 'P'.code.toByte() &&
                prefix[1] == 'K'.code.toByte() && prefix[2] == 3.toByte() && prefix[3] == 4.toByte()
            "TXT", "HTML" -> true
            else -> false
        }
    }
}
