package com.example.oryn.reader.ui.screen

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.oryn.reader.BuildConfig
import com.example.oryn.reader.i18n.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

private const val repository = "ja3kies/Oryn"
private const val channel = "release/reading-experience"
private const val limit = 128L * 1024 * 1024
private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).followSslRedirects(false).build()
private data class Update(val build: Int, val sha: String, val artifact: Long, val digest: String)

private fun metadata(endpoint: String): JSONObject {
    val request = Request.Builder().url("https://api.github.com/repos/$repository/actions/$endpoint").build()
    return client.newCall(request).execute().use {
        check(it.isSuccessful)
        JSONObject(it.body!!.string())
    }
}

private fun latest(): Update? {
    val runs = metadata("workflows/android-ci.yml/runs?branch=release%2Freading-experience&status=success&per_page=50").getJSONArray("workflow_runs")
    val run = (0 until runs.length()).map(runs::getJSONObject).filter {
        it.optString("head_branch") == channel && it.optString("conclusion") == "success" &&
            it.optJSONObject("head_repository")?.optString("full_name").equals(repository, true)
    }.maxByOrNull { it.getInt("run_number") } ?: error("No validated build")
    val number = run.getInt("run_number")
    if (number <= BuildConfig.CI_RUN) return null
    val artifacts = metadata("runs/${run.getLong("id")}/artifacts").getJSONArray("artifacts")
    val apk = (0 until artifacts.length()).map(artifacts::getJSONObject).single {
        it.getString("name") == "oryn-debug-apk" && !it.getBoolean("expired")
    }
    val digest = apk.getString("digest").removePrefix("sha256:")
    require(digest.matches(Regex("[a-f0-9]{64}")))
    return Update(number, run.getString("head_sha"), apk.getLong("id"), digest)
}

private suspend fun copyBounded(source: InputStream, target: OutputStream) {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        val count = source.read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit)
        target.write(buffer, 0, count)
    }
}

@Suppress("DEPRECATION")
private suspend fun download(context: Context, update: Update): File {
    val dir = File(context.cacheDir, "verified-updates").apply { mkdirs() }
    val archive = File.createTempFile("ci-", ".zip", context.cacheDir)
    val apk = File.createTempFile("oryn-", ".apk", dir)
    try {
        val request = Request.Builder().url("https://nightly.link/$repository/actions/artifacts/${update.artifact}.zip").build()
        val hash = MessageDigest.getInstance("SHA-256")
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful)
            archive.outputStream().use { output ->
                java.security.DigestInputStream(response.body!!.byteStream(), hash).use { copyBounded(it, output) }
            }
        }
        require(hash.digest().joinToString("") { "%02x".format(it) } == update.digest)
        ZipFile(archive).use { zip ->
            zip.getInputStream(requireNotNull(zip.getEntry("app-debug.apk"))).use { input ->
                apk.outputStream().use { copyBounded(input, it) }
            }
        }
        val pm = context.packageManager
        val candidate = requireNotNull(pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES))
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        require(candidate.packageName == context.packageName && candidate.versionCode >= installed.versionCode)
        require(!candidate.signatures.isNullOrEmpty() && candidate.signatures.contentEquals(installed.signatures))
        return apk
    } catch (e: Exception) {
        apk.delete()
        throw e
    } finally {
        archive.delete()
    }
}

@Composable
internal fun AppUpdates() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val english = Lang.isEnglish
    fun label(fr: String, en: String) = if (english) en else fr
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<Update?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    Text("Oryn ${BuildConfig.VERSION_NAME} · ${BuildConfig.CI_RUN}")
    val candidate = update
    Text(when {
        failed -> label("Opération impossible. Réessayez.", "Could not complete the operation. Try again.")
        checking && candidate != null -> label("Téléchargement et vérification…", "Downloading and verifying…")
        checking -> label("Vérification…", "Checking…")
        candidate != null -> label("Version validée", "Validated build") + " #${candidate.build} · ${candidate.sha.take(7)}"
        checked -> label("Votre application est à jour.", "Your app is up to date.")
        else -> label("Rechercher la dernière version validée par les tests Android.", "Check for the latest build that passed Android tests.")
    })
    Button(enabled = !checking, onClick = {
        scope.launch {
            failed = false
            checking = true
            try {
                val downloaded = file
                when {
                    candidate == null -> { update = withContext(Dispatchers.IO) { latest() }; checked = true }
                    downloaded == null -> file = withContext(Dispatchers.IO) { download(context, candidate) }
                    Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls() ->
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    else -> {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", downloaded)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true }
            finally { checking = false }
        }
    }) {
        Text(when {
            file != null -> label("Installer la mise à jour", "Install update")
            candidate != null -> label("Télécharger la mise à jour", "Download update")
            else -> label("Vérifier les mises à jour", "Check for updates")
        })
    }
}
