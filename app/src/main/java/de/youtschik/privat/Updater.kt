package de.youtschik.privat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val code: Int, val assetUrl: String)

object Updater {
    private val client = OkHttpClient()
    val enabled get() = BuildConfig.UPDATE_TOKEN.isNotBlank()

    private fun req(url: String, accept: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${BuildConfig.UPDATE_TOKEN}")
        .header("Accept", accept)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()

    /** Gibt ein Update zurück, wenn eine neuere Version als Release existiert. */
    fun check(): UpdateInfo? {
        if (!enabled) return null
        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
        client.newCall(req(url, "application/vnd.github+json")).execute().use { r ->
            if (!r.isSuccessful) error("Update-Prüfung fehlgeschlagen (${r.code})")
            val json = JSONObject(r.body!!.string())
            val code = json.getString("tag_name").substringAfter("build-").toIntOrNull() ?: return null
            if (code <= BuildConfig.VERSION_CODE) return null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) return UpdateInfo(code, a.getString("url"))
            }
        }
        return null
    }

    fun download(ctx: Context, info: UpdateInfo): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "update.apk")
        client.newCall(req(info.assetUrl, "application/octet-stream")).execute().use { r ->
            if (!r.isSuccessful) error("Download fehlgeschlagen (${r.code})")
            apk.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        return apk
    }

    /** Prüft, lädt und startet die Installation in einem Schritt. Gibt eine Statusmeldung zurück. */
    fun checkAndInstall(ctx: Context, onStatus: (String) -> Unit, known: UpdateInfo? = null): String {
        if (!enabled) return "Kein Update-Token in dieser Version"
        return try {
            onStatus("Prüfe…")
            val info = known ?: check() ?: return "Aktuell"
            onStatus("Lade Update…")
            val apk = download(ctx, info)
            if (install(ctx, apk)) "Installation gestartet"
            else "Bitte „Installieren erlauben“ aktivieren, zurückgehen und erneut tippen"
        } catch (e: Exception) {
            e.message ?: "Fehler"
        }
    }

    /** Startet die Installation; false, wenn zuerst die Berechtigung erteilt werden muss. */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
