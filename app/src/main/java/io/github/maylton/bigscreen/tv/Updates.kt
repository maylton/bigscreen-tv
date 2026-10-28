package io.github.maylton.bigscreen.tv

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Updates from the GitHub releases: the APK attached to the latest release. */
object Updates {
    class Release(val version: String, val notes: String, val apkUrl: String)

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pending: Release? = null  // waiting for the install permission

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** onResult(release, error): release is null when already up to date. */
    fun check(context: Context, onResult: (Release?, Exception?) -> Unit) {
        val current = currentVersion(context)
        executor.execute {
            val result = runCatching {
                val json = JSONObject(fetch(BuildConfig.UPDATE_URL) { it.inputStream.bufferedReader().readText() })
                val version = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                val apk = (0 until assets.length()).map(assets::getJSONObject)
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                if (apk == null || compare(version, current) <= 0) null
                else Release(version, json.optString("body"), apk.getString("browser_download_url"))
            }
            main.post { onResult(result.getOrNull(), result.exceptionOrNull() as? Exception) }
        }
    }

    /** Downloads the APK and hands it to the Android installer, which asks the user to confirm. */
    fun install(context: Context, release: Release, onProgress: (Int) -> Unit, onError: (Exception) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            try {
                val file = File(app.cacheDir, "update.apk")
                fetch(release.apkUrl) { connection ->
                    val total = connection.contentLengthLong
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) main.post { onProgress((done * 100 / total).toInt()) }
                            }
                        }
                    }
                }
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply { setAppPackageName(app.packageName) }
                val session = installer.openSession(installer.createSession(params))
                session.use {
                    it.openWrite("update.apk", 0, file.length()).use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                        it.fsync(output)
                    }
                    val intent = Intent(app, UpdateReceiver::class.java)
                    val status = PendingIntent.getBroadcast(app, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    it.commit(status.intentSender)
                }
            } catch (e: Exception) {
                main.post { onError(e) }
            }
        }
    }

    /** Asks whether to update, then shows the download progress. */
    fun offer(activity: android.app.Activity, release: Release) {
        val notes = release.notes.trim().take(700)
        android.app.AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_title, release.version))
            .setMessage(notes.ifEmpty { null })
            .setPositiveButton(R.string.update_now) { _, _ ->
                if (activity.packageManager.canRequestPackageInstalls()) {
                    download(activity, release)
                } else {
                    // The installer would drop the session while the user grants this, so ask first
                    pending = release
                    android.widget.Toast.makeText(activity, R.string.update_permission, android.widget.Toast.LENGTH_LONG).show()
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")))
                }
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    /** Called from onResume: continues an update that was waiting for the install permission. */
    fun resume(activity: android.app.Activity) {
        val release = pending ?: return
        pending = null
        if (activity.packageManager.canRequestPackageInstalls()) download(activity, release)
    }

    private fun download(activity: android.app.Activity, release: Release) {
        val progress = android.app.AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.update_downloading, 0))
            .setCancelable(false)
            .show()
        install(activity, release,
            onProgress = { percent ->
                progress.setMessage(activity.getString(R.string.update_downloading, percent))
                if (percent >= 100) progress.dismiss()
            },
            onError = { error ->
                progress.dismiss()
                android.widget.Toast.makeText(activity, activity.getString(R.string.update_failed, error.message.orEmpty()),
                    android.widget.Toast.LENGTH_LONG).show()
            })
    }

    private fun <T> fetch(url: String, read: (HttpURLConnection) -> T): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BigScreenTV")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            return read(connection)
        } finally {
            connection.disconnect()
        }
    }

    /** "0.10.0" > "0.9.1" */
    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val diff = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
