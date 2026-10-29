package com.example.oryn.reader.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Remplace la couverture à l'intérieur même du fichier EPUB.
 * Le fichier est reconstruit dans un dossier temporaire, vérifié, puis recopié à la place de l'original.
 */
class EpubCoverWriter(private val context: Context) {

    enum class Result { OK, READ_ONLY, FAILED }

    fun replaceCover(uri: Uri, imageBytes: ByteArray): Result {
        val original = File(context.cacheDir, "epub_original.tmp")
        val rebuilt = File(context.cacheDir, "epub_rebuilt.tmp")
        return try {
            // 1. copie de travail de l'EPUB
            val input = context.contentResolver.openInputStream(uri) ?: return Result.FAILED
            input.use { src -> original.outputStream().use { src.copyTo(it) } }

            // 2. reconstruction avec la nouvelle image
            if (!rebuild(original, rebuilt, imageBytes)) return Result.FAILED

            // 3. contrôle : le nouveau fichier doit être une archive lisible avec son mimetype
            ZipFile(rebuilt).use { zip -> if (zip.getEntry("META-INF/container.xml") == null) return Result.FAILED }

            // 4. remplacement du fichier source
            val out = openForWrite(uri) ?: return Result.READ_ONLY
            try {
                out.use { dst -> rebuilt.inputStream().use { it.copyTo(dst) } }
            } catch (e: Exception) {
                // échec en cours d'écriture : on restaure l'original
                runCatching {
                    openForWrite(uri)?.use { dst -> original.inputStream().use { it.copyTo(dst) } }
                }
                return Result.FAILED
            }
            Result.OK
        } catch (_: SecurityException) {
            Result.READ_ONLY
        } catch (_: java.io.FileNotFoundException) {
            Result.READ_ONLY
        } catch (_: Exception) {
            Result.FAILED
        } finally {
            original.delete()
            rebuilt.delete()
        }
    }

    private fun openForWrite(uri: Uri): java.io.OutputStream? =
        if (uri.scheme == "file") uri.path?.let { java.io.FileOutputStream(File(it)) }
        else context.contentResolver.openOutputStream(uri, "wt")

    private fun rebuild(source: File, target: File, imageBytes: ByteArray): Boolean {
        ZipFile(source).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return false
            val containerXml = zip.getInputStream(container).readBytes().toString(Charsets.UTF_8)
            val opfPath = attr(Regex("<rootfile\\b[^>]*>").find(containerXml)?.value ?: return false, "full-path")
                ?: return false
            val opfEntry = zip.getEntry(opfPath) ?: return false
            var opf = zip.getInputStream(opfEntry).readBytes().toString(Charsets.UTF_8)
            val opfDir = opfPath.substringBeforeLast('/', "")

            // Cherche l'image de couverture existante
            val items = Regex("<item\\b[^>]*>").findAll(opf).map { it.value }.toList()
            val coverMetaId = Regex("<meta\\b[^>]*name\\s*=\\s*[\"']cover[\"'][^>]*>").find(opf)
                ?.value?.let { attr(it, "content") }
            val coverItem = items.firstOrNull { attr(it, "properties")?.split(' ')?.contains("cover-image") == true }
                ?: items.firstOrNull { coverMetaId != null && attr(it, "id") == coverMetaId }
            val href = coverItem?.let { attr(it, "href") }
            val ext = href?.substringAfterLast('.', "")?.lowercase()

            var replacePath: String? = null
            var newBytes = imageBytes
            if (href != null && (ext == "jpg" || ext == "jpeg" || ext == "png")) {
                val decoded = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
                val path = if (opfDir.isEmpty()) decoded else "$opfDir/$decoded"
                if (zip.getEntry(path) != null) {
                    replacePath = path
                    if (ext == "png") newBytes = toPng(imageBytes) ?: imageBytes
                }
            }

            // Pas d'image exploitable : on en ajoute une et on la déclare comme couverture
            var addedPath: String? = null
            if (replacePath == null) {
                val name = "oryn-cover.jpg"
                addedPath = if (opfDir.isEmpty()) name else "$opfDir/$name"
                val epub3 = Regex("<package\\b[^>]*version\\s*=\\s*[\"']3").containsMatchIn(opf)
                val props = if (epub3) " properties=\"cover-image\"" else ""
                opf = opf.replace("</manifest>", "<item id=\"oryn-cover\" href=\"$name\" media-type=\"image/jpeg\"$props/>\n</manifest>")
                opf = if (coverMetaId != null) {
                    Regex("(<meta\\b[^>]*name\\s*=\\s*[\"']cover[\"'][^>]*content\\s*=\\s*)([\"'])[^\"']*\\2")
                        .replace(opf) { "${it.groupValues[1]}\"oryn-cover\"" }
                } else {
                    opf.replace("</metadata>", "<meta name=\"cover\" content=\"oryn-cover\"/>\n</metadata>")
                }
            }

            target.outputStream().use { fileOut ->
                ZipOutputStream(fileOut).use { out ->
                    // le mimetype doit rester en premier, non compressé : on garde l'ordre d'origine
                    for (entry in zip.entries()) {
                        val bytes: ByteArray = when (entry.name) {
                            opfPath -> opf.toByteArray(Charsets.UTF_8)
                            replacePath -> newBytes
                            else -> if (entry.isDirectory) ByteArray(0) else zip.getInputStream(entry).readBytes()
                        }
                        val copy = ZipEntry(entry.name)
                        if (entry.method == ZipEntry.STORED && entry.name != opfPath && entry.name != replacePath) {
                            copy.method = ZipEntry.STORED
                            copy.size = bytes.size.toLong()
                            copy.compressedSize = bytes.size.toLong()
                            copy.crc = entry.crc
                        } else {
                            copy.method = ZipEntry.DEFLATED
                        }
                        out.putNextEntry(copy)
                        out.write(bytes)
                        out.closeEntry()
                    }
                    if (addedPath != null) {
                        out.putNextEntry(ZipEntry(addedPath))
                        out.write(imageBytes)
                        out.closeEntry()
                    }
                }
            }
        }
        return true
    }

    private fun toPng(jpeg: ByteArray): ByteArray? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }.getOrNull()

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)')").find(tag)?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }
}
