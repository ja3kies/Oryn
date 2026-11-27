package com.example.oryn.reader.data.remote

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val source: String,
    val language: String,
    val translated: Boolean = false
)

data class TranslationResult(
    val text: String,
    val from: String,
    val to: String,
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
    private const val MAX_REQUEST_BYTES = 500
    class QuotaExceeded : IOException()
    private data class Key(val text: String, val source: String, val target: String)
    private val definitions by lazy { android.util.LruCache<Key, DictionaryResult>(32) }
    private val translations by lazy { android.util.LruCache<Key, TranslationResult>(16) }
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

    val targetLanguages: List<Pair<String, String>> = listOf(
        "fr" to "Français", "en" to "English", "es" to "Español", "de" to "Deutsch", "it" to "Italiano", "pt" to "Português"
    )

    /** Nombre de mots d'une sélection (pour décider si une définition a du sens). */
    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.any(Char::isLetter) }

    /** Retire la ponctuation autour du mot sélectionné (« Monte-Cristo, » → « Monte-Cristo »). */
    fun cleanWord(raw: String): String = raw.trim().trim { !it.isLetterOrDigit() }

    suspend fun define(raw: String, language: String, target: String): DictionaryResult? = withContext(Dispatchers.IO) {
        val word = cleanWord(raw)
        if (word.isEmpty()) return@withContext null
        val key = Key(word.lowercase(java.util.Locale.ROOT), language, target)
        definitions.get(key)?.let { return@withContext it }
        if (target == "fr") {
            val article = try {
                httpGet("https://fr.wiktionary.org/wiki/${enc(word.lowercase(java.util.Locale.ROOT))}")
            } catch (_: IOException) { null }
            article?.let { frenchEntry(word, language, it) }?.let { definitions.put(key, it); return@withContext it }
        }
        val body = httpGet("https://freedictionaryapi.com/api/v1/entries/${enc(language)}/${enc(word.lowercase(java.util.Locale.ROOT))}")
            ?: return@withContext null
        val result = dictionaryEntry(JSONObject(body), language) ?: return@withContext null
        val localized = if (target == "en") result else result.copy(translated = true, entries = result.entries.map { entry ->
            entry.copy(senses = entry.senses.map { sense ->
                sense.copy(definition = translate(sense.definition, "en", target).text)
            })
        })
        definitions.put(key, localized)
        localized
    }

    internal fun frenchEntry(word: String, language: String, html: String): DictionaryResult? {
        val groups = mutableListOf<DictEntry>()
        val doc = org.jsoup.Jsoup.parse(html)
        for (title in doc.select(".titredef")) {
            if (!title.id().startsWith("$language-")) continue
            val section = title.closest("section") ?: continue
            val list = section.children().firstOrNull { it.tagName() == "ol" } ?: continue
            val senses = list.children().asSequence().filter { it.tagName() == "li" }.take(2).mapNotNull { item ->
                val example = item.selectFirst(".example q")?.text()
                val copy = item.clone()
                copy.select("ul, ol, dl, sup.reference").remove()
                copy.text().takeIf(String::isNotBlank)?.let { DictSense(it, example) }
            }
            val available = senses.toList()
            if (available.isNotEmpty()) groups += DictEntry(title.text(), available)
            if (groups.size == 2) break
        }
        return if (groups.isEmpty()) null else DictionaryResult(word, null, groups, "Wiktionnaire · CC BY-SA 4.0", language)
    }

    internal fun dictionaryEntry(root: JSONObject, language: String): DictionaryResult? {
        val entries = root.optJSONArray("entries") ?: return null
        val groups = mutableListOf<DictEntry>()
        var phonetic: String? = null
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optJSONObject("language")?.optString("code") != language) continue
            phonetic = phonetic ?: entry.optJSONArray("pronunciations")?.optJSONObject(0)?.optString("text")
            val senses = entry.optJSONArray("senses") ?: continue
            val definitions = mutableListOf<DictSense>()
            for (j in 0 until senses.length()) {
                val sense = senses.optJSONObject(j) ?: continue
                val definition = sense.optString("definition").trim()
                if (definition.isEmpty()) continue
                val example = sense.optJSONArray("examples")?.optString(0)?.takeIf(String::isNotBlank)
                    ?: sense.optJSONArray("quotes")?.optJSONObject(0)?.optString("text")?.takeIf(String::isNotBlank)
                definitions += DictSense(definition, example)
                if (definitions.size == 2) break
            }
            if (definitions.isNotEmpty()) groups += DictEntry(entry.optString("partOfSpeech"), definitions)
            if (groups.size == 2) break
        }
        return if (groups.isEmpty()) null else DictionaryResult(root.getString("word"), phonetic, groups, "Wiktionary · CC BY-SA 4.0", language)
    }

    /** Split at whitespace when possible and never bisect a Unicode code point. */
    internal fun chunks(text: String): Sequence<String> = sequence {
        var start = 0
        while (start < text.length) {
            var end = start
            var bytes = 0
            var boundary = start
            while (end < text.length) {
                val cp = text.codePointAt(end)
                val width = when { cp < 128 -> 1; cp < 2048 -> 2; cp < 65536 -> 3; else -> 4 }
                if (bytes + width > MAX_REQUEST_BYTES) break
                bytes += width
                end += Character.charCount(cp)
                if (Character.isWhitespace(cp)) boundary = end
            }
            if (end < text.length && boundary > start) end = boundary
            yield(text.substring(start, end))
            start = end
        }
    }

    internal fun translationText(json: JSONObject): String {
        if (json.optBoolean("quotaFinished") || json.optInt("responseStatus") == 429) throw QuotaExceeded()
        if (json.optInt("responseStatus") != 200) throw IOException("Translation service rejected request")
        val value = json.optJSONObject("responseData")?.optString("translatedText").orEmpty()
        if (value.isBlank()) throw IOException("Empty translation")
        return org.jsoup.parser.Parser.unescapeEntities(value, false).trim()
    }

    suspend fun translate(raw: String, source: String, target: String): TranslationResult = withContext(Dispatchers.IO) {
        val text = raw.trim()
        require(text.isNotEmpty() && targetLanguages.any { it.first == source } && target.matches(Regex("[a-z]{2,3}")))
        if (source == target) return@withContext TranslationResult(text, source, target, true)
        val key = Key(text, source, target)
        translations.get(key)?.let { return@withContext it }
        val translated = buildString {
            for (chunk in chunks(text)) {
                ensureActive()
                if (chunk.isBlank()) { append(chunk); continue }
                val body = httpGet("https://api.mymemory.translated.net/get?q=${enc(chunk.trim())}&langpair=${enc("$source|$target")}")
                    ?: throw IOException("Translation unavailable")
                append(chunk.takeWhile(Char::isWhitespace))
                append(translationText(JSONObject(body)))
                append(chunk.takeLastWhile(Char::isWhitespace))
            }
        }
        TranslationResult(translated, source, target, false).also { translations.put(key, it) }
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
                code == 429 -> throw QuotaExceeded()
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
