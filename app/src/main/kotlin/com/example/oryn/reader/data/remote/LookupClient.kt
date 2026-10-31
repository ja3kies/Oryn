package com.example.oryn.reader.data.remote

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Un sens d'un mot : définition et, si elle existe, une phrase d'exemple. */
data class DictSense(val definition: String, val example: String? = null)

/** Un groupe de sens pour une nature grammaticale (nom, verbe…). */
data class DictEntry(val partOfSpeech: String, val senses: List<DictSense>)

data class DictionaryResult(
    val word: String,
    val phonetic: String?,
    val entries: List<DictEntry>,
    val source: String
)

data class TranslationResult(
    val text: String,
    /** Code de la langue détectée ou devinée ; null si inconnue. */
    val from: String?,
    val to: String,
    /** Vrai si le texte d'origine était plus long que la limite du service et a été coupé. */
    val truncated: Boolean,
    /** Vrai si la langue d'origine est déjà la langue cible (aucune traduction utile). */
    val sameLanguage: Boolean
)

/**
 * Définitions et traductions à la demande, sans compte ni clé :
 * - dictionnaire : Free Dictionary API puis Wiktionnaire (si le premier ne connaît pas le mot) ;
 * - traduction : MyMemory (quota gratuit limité, largement suffisant pour de la lecture).
 * Une erreur réseau lève [IOException] ; un mot inconnu donne simplement `null`.
 */
object LookupClient {
    /** MyMemory refuse les requêtes de plus de 500 octets : on reste en dessous. */
    private const val MAX_TRANSLATE_CHARS = 420
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

    val targetLanguages: List<Pair<String, String>> = listOf(
        "fr" to "Français", "en" to "English", "es" to "Español", "de" to "Deutsch", "it" to "Italiano", "pt" to "Português"
    )

    /** Nombre de mots d'une sélection (pour décider si une définition a du sens). */
    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.any(Char::isLetter) }

    /** Retire la ponctuation autour du mot sélectionné (« Monte-Cristo, » → « Monte-Cristo »). */
    fun cleanWord(raw: String): String = raw.trim().trim { !it.isLetterOrDigit() }

    suspend fun define(raw: String, languages: List<String>): DictionaryResult? = withContext(Dispatchers.IO) {
        val word = cleanWord(raw)
        if (word.isEmpty()) return@withContext null
        val candidates = listOf(word, word.lowercase()).distinct()
        var lastError: IOException? = null
        for (lang in languages.distinct()) {
            for (candidate in candidates) {
                try {
                    fromFreeDictionary(candidate, lang)?.let { return@withContext it }
                    fromWiktionary(candidate, lang)?.let { return@withContext it }
                } catch (e: IOException) {
                    lastError = e
                }
            }
        }
        // Si rien n'a été trouvé et que le réseau a échoué à un moment, on le signale plutôt que « mot inconnu ».
        lastError?.let { throw it }
        null
    }

    private fun fromFreeDictionary(word: String, lang: String): DictionaryResult? {
        val body = httpGet("https://api.dictionaryapi.dev/api/v2/entries/$lang/${enc(word)}") ?: return null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val entries = LinkedHashMap<String, MutableList<DictSense>>()
        var phonetic: String? = null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (phonetic == null) phonetic = item.optString("phonetic").takeIf { it.isNotBlank() }
            val meanings = item.optJSONArray("meanings") ?: continue
            for (m in 0 until meanings.length()) {
                val meaning = meanings.optJSONObject(m) ?: continue
                val pos = meaning.optString("partOfSpeech").ifBlank { "—" }
                val defs = meaning.optJSONArray("definitions") ?: continue
                val list = entries.getOrPut(pos) { mutableListOf() }
                for (d in 0 until defs.length()) {
                    val def = defs.optJSONObject(d) ?: continue
                    val text = def.optString("definition").trim()
                    if (text.isEmpty()) continue
                    list += DictSense(text, def.optString("example").trim().takeIf { it.isNotEmpty() })
                }
            }
        }
        return build(word, phonetic, entries, "Free Dictionary")
    }

    private fun fromWiktionary(word: String, lang: String): DictionaryResult? {
        val body = httpGet("https://$lang.wiktionary.org/api/rest_v1/page/definition/${enc(word)}") ?: return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val entries = LinkedHashMap<String, MutableList<DictSense>>()
        // Les sections sont rangées par code de langue du mot ; on met d'abord la langue demandée.
        val keys = root.keys().asSequence().toList().sortedBy { if (it == lang) 0 else 1 }
        for (key in keys) {
            val sections = root.optJSONArray(key) ?: continue
            for (s in 0 until sections.length()) {
                val section = sections.optJSONObject(s) ?: continue
                val pos = section.optString("partOfSpeech").ifBlank { "—" }
                val defs = section.optJSONArray("definitions") ?: continue
                val list = entries.getOrPut(pos) { mutableListOf() }
                for (d in 0 until defs.length()) {
                    val def = defs.optJSONObject(d) ?: continue
                    val text = htmlToText(def.optString("definition"))
                    if (text.isEmpty()) continue
                    val example = def.optJSONArray("examples")?.optString(0)?.let { htmlToText(it) }?.takeIf { it.isNotEmpty() }
                    list += DictSense(text, example)
                }
            }
            if (entries.isNotEmpty()) break
        }
        return build(word, null, entries, "Wiktionnaire")
    }

    private fun build(word: String, phonetic: String?, raw: Map<String, List<DictSense>>, source: String): DictionaryResult? {
        val entries = raw.filterValues { it.isNotEmpty() }
            .map { (pos, senses) -> DictEntry(pos, senses.take(4)) }
            .take(4)
        return if (entries.isEmpty()) null else DictionaryResult(word, phonetic, entries, source)
    }

    suspend fun translate(raw: String, target: String): TranslationResult = withContext(Dispatchers.IO) {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        val truncated = clean.length > MAX_TRANSLATE_CHARS
        val text = if (truncated) clean.take(MAX_TRANSLATE_CHARS).substringBeforeLast(' ', clean.take(MAX_TRANSLATE_CHARS)) else clean
        val guessed = guessLanguage(text)
        if (guessed == target) return@withContext TranslationResult(text, guessed, target, truncated, sameLanguage = true)
        val pair = (guessed ?: "autodetect") + "|" + target
        val body = httpGet("https://api.mymemory.translated.net/get?q=${enc(text)}&langpair=${enc(pair)}")
            ?: throw IOException("Réponse vide")
        val json = JSONObject(body)
        val data = json.optJSONObject("responseData")
        val translated = htmlToText(data?.optString("translatedText").orEmpty())
        if (translated.isEmpty() || translated.uppercase().startsWith("PLEASE SELECT TWO DISTINCT")) {
            return@withContext TranslationResult(text, guessed, target, truncated, sameLanguage = true)
        }
        if (json.optBoolean("quotaFinished", false) || json.optInt("responseStatus", 200) == 429) {
            throw IOException("Quota de traduction atteint")
        }
        val detected = data?.optString("detectedLanguage")?.takeIf { it.isNotBlank() && it != "null" }?.take(2)
        TranslationResult(translated, guessed ?: detected, target, truncated, sameLanguage = false)
    }

    /** Devine la langue d'un passage avec quelques mots très fréquents ; null si l'indice est trop faible. */
    fun guessLanguage(text: String): String? {
        val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
        if (words.size < 3) return null
        val hints = mapOf(
            "fr" to setOf("le", "la", "les", "des", "une", "un", "est", "et", "que", "qui", "dans", "pour", "pas", "sur", "il", "elle", "je", "vous", "nous", "au", "du", "ce", "qu'il", "l'"),
            "en" to setOf("the", "and", "of", "to", "in", "is", "was", "that", "it", "he", "she", "you", "with", "for", "his", "her", "had", "not", "but"),
            "es" to setOf("el", "los", "las", "una", "es", "que", "y", "en", "por", "con", "para", "no", "se", "su", "del"),
            "de" to setOf("der", "die", "das", "und", "ist", "nicht", "ein", "eine", "zu", "mit", "den", "von", "sie", "ich"),
            "it" to setOf("il", "gli", "una", "che", "di", "per", "non", "con", "sono", "della", "ma"),
            "pt" to setOf("os", "uma", "que", "não", "para", "com", "em", "do", "da", "ele", "mas")
        )
        val scores = hints.mapValues { (_, set) -> words.count { it in set || it.substringBefore('\'') + "'" in set } }
        val best = scores.maxByOrNull { it.value } ?: return null
        val second = scores.values.sortedDescending().getOrElse(1) { 0 }
        return if (best.value >= 2 && best.value > second) best.key else null
    }

    private fun htmlToText(html: String): String =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Corps de la réponse, ou null si le serveur répond « introuvable » (404) ; IOException sinon. */
    private fun httpGet(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 12_000
        c.setRequestProperty("User-Agent", "OrynReader/1.0 (Android; lecture personnelle)")
        c.setRequestProperty("Accept", "application/json")
        return try {
            val code = c.responseCode
            when {
                code == 404 -> null
                code in 200..299 -> c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_RESPONSE_BYTES) throw IOException("Réponse trop volumineuse")
                        out.write(buffer, 0, n)
                    }
                    out.toString(Charsets.UTF_8.name())
                }
                else -> throw IOException("HTTP $code")
            }
        } finally {
            c.disconnect()
        }
    }
}
