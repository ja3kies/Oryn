import re, sys
R = "app/src/main/kotlin/com/example/oryn/reader/"
problems = []

def load(p):
    with open(R + p, encoding="utf-8") as f:
        return f.read()

def save(p, s):
    with open(R + p, "w", encoding="utf-8") as f:
        f.write(s)

def replace_once(p, pairs, marker):
    s = load(p)
    if marker in s:
        print("= %s déjà durci" % p)
        return
    for old, new in pairs:
        if s.count(old) != 1:
            problems.append("%s (bloc introuvable : %s…)" % (p, old[:50].strip()))
            return
    for old, new in pairs:
        s = s.replace(old, new)
    save(p, s)
    print("+ %s durci" % p)

# 1. Lectures de fichiers bornées (TXT, HTML, entrées d'EPUB)
replace_once("data/importer/BookTextLoader.kt", [
 ("class BookTextLoader(private val context: Context) {\n",
  '''class BookTextLoader(private val context: Context) {
    private companion object {
        const val MAX_TEXT_BYTES = 64 * 1024 * 1024        // TXT / HTML
        const val MAX_EPUB_ENTRY_BYTES = 24 * 1024 * 1024  // un chapitre, une feuille de style…
        const val MAX_EPUB_TOTAL_BYTES = 192L * 1024 * 1024 // somme décompressée des entrées lues
        const val MAX_EPUB_ENTRIES = 20_000                // protège des archives piégées
    }

    /** Lecture bornée : au-delà de [limit] octets on s'arrête avec un message clair au lieu de saturer la mémoire. */
    private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw java.io.IOException(trf("Fichier trop volumineux (limite : %s Mo).", limit / 1_048_576))
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

'''),
 ("    private fun loadTxt(uri: Uri): String? {\n        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null\n",
  "    private fun loadTxt(uri: Uri): String? {\n        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_TEXT_BYTES) } ?: return null\n"),
 ("    private fun loadHtml(uri: Uri): String? {\n        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null\n",
  "    private fun loadHtml(uri: Uri): String? {\n        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_TEXT_BYTES) } ?: return null\n"),
 ("        val files = LinkedHashMap<String, ByteArray>()\n        context.contentResolver.openInputStream(uri)?.use { input ->\n            ZipInputStream(input.buffered()).use { zip ->\n                while (true) {\n                    val entry = zip.nextEntry ?: break\n",
  "        val files = LinkedHashMap<String, ByteArray>()\n        var entryCount = 0\n        var totalBytes = 0L\n        context.contentResolver.openInputStream(uri)?.use { input ->\n            ZipInputStream(input.buffered()).use { zip ->\n                while (true) {\n                    val entry = zip.nextEntry ?: break\n                    if (++entryCount > MAX_EPUB_ENTRIES) throw java.io.IOException(tr(\"Archive EPUB invalide (trop de fichiers).\"))\n"),
 ("                        files[name] = zip.readBytes()\n",
  "                        val data = zip.readBounded(MAX_EPUB_ENTRY_BYTES)\n                        totalBytes += data.size\n                        if (totalBytes > MAX_EPUB_TOTAL_BYTES) throw java.io.IOException(tr(\"Ce livre est trop volumineux pour être ouvert.\"))\n                        files[name] = data\n"),
], "readBounded")

# 2. WebView de la bibliothèque en ligne : jamais de contenu mixte
replace_once("ui/screen/WebLibraryDialog.kt", [
 ("MIXED_CONTENT_COMPATIBILITY_MODE", "MIXED_CONTENT_NEVER_ALLOW"),
], "MIXED_CONTENT_NEVER_ALLOW")

# 3. Remplacement de couverture EPUB : mimetype en premier et non compressé, CRC recalculé
replace_once("data/importer/EpubCoverWriter.kt", [
 ("                    for (entry in zip.entries()) {\n",
  "                    for (entry in zip.entries().asSequence().sortedBy { if (it.name == \"mimetype\") 0 else 1 }) {\n"),
 ("if (entry.method == ZipEntry.STORED && entry.name != opfPath && entry.name != replacePath) {",
  "if ((entry.method == ZipEntry.STORED || entry.name == \"mimetype\") && entry.name != opfPath && entry.name != replacePath) {"),
 ("                            copy.crc = entry.crc\n",
  "                            copy.crc = java.util.zip.CRC32().also { it.update(bytes) }.value\n"),
], "CRC32().also")

# 4. Traductions anglaises (facultatives)
en = {"Fichier trop volumineux (limite : %s Mo).": "File too large (limit: %s MB).",
      "Archive EPUB invalide (trop de fichiers).": "Invalid EPUB archive (too many files).",
      "Ce livre est trop volumineux pour être ouvert.": "This book is too large to open."}
s = load("i18n/I18n.kt")
todo = {k: v for k, v in en.items() if '"%s" to' % k not in s}
m = re.search(r'^( *)"Fermer" to "Close",\n', s, re.M)
if todo and m:
    add = "".join('%s"%s" to "%s",\n' % (m.group(1), k, v) for k, v in todo.items())
    save("i18n/I18n.kt", s[:m.end()] + add + s[m.end():])
    print("+ I18n.kt : %d entrées" % len(todo))
if problems:
    print("ECHEC partiel : " + " ; ".join(problems))
    sys.exit(2)
