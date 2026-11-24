package com.prabhu.wallpaperstudio.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.prabhu.wallpaperstudio.BuildConfig
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks this app's GitHub "Releases" page for a newer version, downloads
 * the APK and hands it to the Android installer.
 */
object Updater {

    data class Release(val version: String, val notes: String, val apkUrl: String)

    // UI state shared by the Settings screen and the start-up dialog.
    var available by mutableStateOf<Release?>(null)
    var checking by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var downloadProgress by mutableStateOf<Float?>(null)
    var dialogDismissed by mutableStateOf(false)

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val repoUrl: String get() = "https://github.com/${BuildConfig.UPDATE_REPO}"

    /** Runs a check; [quiet] suppresses the "up to date" message. */
    suspend fun check(quiet: Boolean = false) {
        if (checking) return
        checking = true
        if (!quiet) message = null
        try {
            val rel = fetchLatest()
            Store.edit { putLong(Keys.LAST_UPDATE_CHECK, System.currentTimeMillis()) }
            if (rel != null && isNewer(rel.version, currentVersion)) {
                available = rel
                message = null
            } else {
                available = null
                if (!quiet) message = "You're on the latest version ($currentVersion)"
            }
        } catch (e: Exception) {
            if (!quiet) message = "Couldn't reach GitHub. Check your internet and try again."
        } finally {
            checking = false
        }
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apk = a.optString("browser_download_url")
                        break
                    }
                }
            }
            if (tag.isEmpty() || apk == null) null else Release(tag, json.optString("body").trim(), apk)
        } finally {
            c.disconnect()
        }
    }

    /** Compares "1.2.3" style versions. A "-dev" build is older than the same release. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return local.contains('-') && !remote.contains('-')
    }

    /** Downloads the APK, then opens the installer. */
    suspend fun downloadAndInstall(context: Context, rel: Release) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            message = "Allow Wallpaper Studio to install updates, then tap Update now again."
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        downloadProgress = 0f
        message = null
        try {
            val file = download(context, rel.apkUrl)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            message = "Download failed. Please try again."
        } finally {
            downloadProgress = null
        }
    }

    private suspend fun download(context: Context, apkUrl: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "update.apk")
        val c = URL(apkUrl).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            withContext(Dispatchers.Main) { downloadProgress = p }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        file
    }
}
