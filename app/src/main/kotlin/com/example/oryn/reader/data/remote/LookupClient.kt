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
    val translated: Boolean = false,
    val availableLanguages: List<String> = emptyList()
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
    class UnreadableResult : IOException()
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
        val key = Key(word, language, target)
        definitions.get(key)?.let { return@withContext it }
        val normalized = java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFC)
        val titles = listOf(normalized, normalized.lowercase(java.util.Locale.ROOT)).distinct()
        var failure: IOException? = null
        fun fetch(url: String): String? = try { httpGet(url) } catch (e: IOException) { failure = e; null }
        fun french(): List<DictionaryResult> {
            for (title in titles) {
                val html = fetch("https://fr.wiktionary.org/wiki/${enc(title)}") ?: continue
                val entries = frenchEntries(title, html)
                if (entries.isNotEmpty()) return entries
            }
            return emptyList()
        }
        fun english(): List<DictionaryResult> {
            for (title in titles) {
                val body = fetch("https://en.wiktionary.org/api/rest_v1/page/definition/${enc(title)}") ?: continue
                val entries = englishEntries(title, JSONObject(body))
                if (entries.isNotEmpty()) return entries
            }
            return emptyList()
        }
        var glossLanguage = if (target == "fr") "fr" else "en"
        var candidates = if (glossLanguage == "fr") french() else english()
        if (candidates.isEmpty() || (language.isNotEmpty() && candidates.none { it.language == language })) {
            val other = if (glossLanguage == "fr") english() else french()
            if (other.isNotEmpty() && (candidates.isEmpty() || other.any { it.language == language })) {
                candidates = other
                glossLanguage = if (glossLanguage == "fr") "en" else "fr"
            }
        }
        if (language.isNotEmpty() && candidates.none { it.language == language }) {
            val body = fetch("https://freedictionaryapi.com/api/v1/entries/${enc(language)}/${enc(titles.last())}")
            val fallback = body?.let { dictionaryEntry(JSONObject(it), language) }
            if (fallback != null) {
                candidates = listOf(fallback)
                glossLanguage = "en"
            }
        }
        val entry = candidates.firstOrNull { it.language == language } ?: candidates.firstOrNull()
        if (entry == null) { failure?.let { throw it }; return@withContext null }
        val result = entry.copy(availableLanguages = candidates.map { it.language })
        val localized = if (target == glossLanguage) result else result.copy(translated = true, entries = result.entries.map { group ->
            group.copy(senses = group.senses.map { sense ->
                sense.copy(definition = translate(sense.definition, glossLanguage, target).text)
            })
        })
        definitions.put(key, localized)
        localized
    }

    internal fun frenchEntry(word: String, language: String, html: String): DictionaryResult? {
        return frenchEntries(word, html).firstOrNull { it.language == language }
    }

    internal fun frenchEntries(word: String, html: String): List<DictionaryResult> {
        val groups = linkedMapOf<String, MutableList<DictEntry>>()
        val doc = org.jsoup.Jsoup.parse(html)
        for (title in doc.select(".titredef[id]")) {
            val language = title.id().substringBeforeLast('-').substringBeforeLast('-', "")
            if (language.isBlank()) continue
            val heading = title.closest("h2, h3, h4, h5, h6")
            val block = heading?.parent()?.takeIf { it.hasClass("mw-heading") } ?: heading
            val list = block?.nextElementSiblings()?.asSequence()
                ?.takeWhile { !it.hasClass("mw-heading") && it.tagName() !in setOf("section", "h2", "h3", "h4", "h5", "h6") }
                ?.firstOrNull { it.tagName() == "ol" } ?: continue
            val senses = list.children().asSequence().filter { it.tagName() == "li" }.take(2).mapNotNull { item ->
                val example = item.selectFirst(".example q")?.text()
                val copy = item.clone()
                copy.select("ul, ol, dl, sup.reference").remove()
                copy.text().takeIf(String::isNotBlank)?.let { DictSense(it, example) }
            }
            val available = senses.toList()
            if (available.isNotEmpty()) {
                val entries = groups.getOrPut(language) { mutableListOf() }
                if (entries.size < 2) entries += DictEntry(title.text(), available)
            }
        }
        return groups.map { (code, entries) -> DictionaryResult(word, null, entries, "Wiktionnaire · CC BY-SA 4.0", code) }
    }

    internal fun englishEntries(word: String, json: JSONObject): List<DictionaryResult> {
        return json.keys().asSequence().filter { it != "other" }.mapNotNull { code ->
            val sections = json.optJSONArray(code) ?: return@mapNotNull null
            val entries = mutableListOf<DictEntry>()
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val definitions = section.optJSONArray("definitions") ?: continue
                val senses = (0 until definitions.length()).asSequence().mapNotNull { index ->
                    val sense = definitions.optJSONObject(index) ?: return@mapNotNull null
                    val gloss = org.jsoup.Jsoup.parseBodyFragment(sense.optString("definition")).text()
                    val example = sense.optJSONArray("examples")?.optString(0)?.let { org.jsoup.Jsoup.parseBodyFragment(it).text() }
                    gloss.takeIf(String::isNotBlank)?.let { DictSense(it, example) }
                }.take(2).toList()
                if (senses.isNotEmpty()) entries += DictEntry(section.optString("partOfSpeech"), senses)
                if (entries.size == 2) break
            }
            entries.takeIf { it.isNotEmpty() }?.let { DictionaryResult(word, null, it, "Wiktionary · CC BY-SA 4.0", code) }
        }.toList()
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
        val decoded = org.jsoup.parser.Parser.unescapeEntities(value, false).trim()
        if (decoded.any { it == '\uFFFD' } || (decoded.contains('?') && decoded.none(Char::isLetterOrDigit))) throw UnreadableResult()
        return decoded
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
        c.setRequestProperty("Accept", if (url.startsWith("https://fr.wiktionary.org/wiki/")) "text/html" else "application/json")
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
