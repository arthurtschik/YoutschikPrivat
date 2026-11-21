package de.youtschik.privat

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File

object Storage {
    private const val PREFS = "prefs"
    const val KEY_PRIMARY = "primary"
    const val KEY_FALLBACK = "fallback"

    fun get(ctx: Context, key: String): String? =
        ctx.getSharedPreferences(PREFS, 0).getString(key, null)

    fun set(ctx: Context, key: String, value: String?) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(key, value).apply()
    }

    const val KEY_AUTO_EXT = "auto_external"
    fun autoExternal(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY_AUTO_EXT, true)
    fun setAutoExternal(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_AUTO_EXT, on).apply()
    }

    fun hasAllFilesAccess() = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    /** Erster eingehängter Wechseldatenträger (SD/USB), egal wie er heißt. */
    fun externalLabel(ctx: Context): String? =
        if (Build.VERSION.SDK_INT >= 30) externalVolume(ctx)?.getDescription(ctx) else null

    private fun externalVolume(ctx: Context) =
        if (Build.VERSION.SDK_INT < 30 || !hasAllFilesAccess()) null
        else ctx.getSystemService(StorageManager::class.java).storageVolumes.firstOrNull {
            !it.isPrimary && !it.isEmulated && it.state == Environment.MEDIA_MOUNTED && it.directory != null
        }

    const val KEY_SPONSOR = "sponsorblock"
    fun sponsorBlock(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY_SPONSOR, true)
    fun setSponsorBlock(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_SPONSOR, on).apply()
    }

    /** Liefert den Ordner nur, wenn er gespeichert, erreichbar und beschreibbar ist. */
    fun usable(ctx: Context, uriStr: String?): DocumentFile? {
        if (uriStr.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriStr)
            val granted = ctx.contentResolver.persistedUriPermissions
                .any { it.uri == uri && it.isWritePermission }
            if (!granted) return null
            val dir = DocumentFile.fromTreeUri(ctx, uri) ?: return null
            if (dir.exists() && dir.isDirectory && dir.canWrite()) dir else null
        } catch (e: Exception) {
            null
        }
    }

    fun folderLabel(ctx: Context, key: String): String {
        val s = get(ctx, key) ?: return "Nicht festgelegt"
        val d = usable(ctx, s) ?: return "Nicht verfügbar"
        return d.name ?: "Ordner"
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "webm" -> "audio/webm"
        "opus", "ogg" -> "audio/ogg"
        else -> "audio/mpeg"
    }

    /** Speichert die Datei: 1. Standardordner, 2. Ersatzordner, 3. Music/YoutschikPrivat. */
    fun save(ctx: Context, src: File): String {
        val mime = mimeFor(src.name)
        if (autoExternal(ctx)) {
            try {
                val vol = externalVolume(ctx)
                if (vol != null && Build.VERSION.SDK_INT >= 30) {
                    val target = File(vol.directory!!, src.name)
                    src.copyTo(target, overwrite = true)
                    MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), null, null)
                    return vol.getDescription(ctx)
                }
            } catch (e: Exception) {
                // weiter mit den normalen Ordnern
            }
        }
        for (key in listOf(KEY_PRIMARY, KEY_FALLBACK)) {
            val dir = usable(ctx, get(ctx, key)) ?: continue
            try {
                dir.findFile(src.name)?.delete()
                val target = dir.createFile(mime, src.name) ?: continue
                ctx.contentResolver.openOutputStream(target.uri)!!.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                return dir.name ?: "Ordner"
            } catch (e: Exception) {
                // nächsten Ordner versuchen
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, src.name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/YoutschikPrivat")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Speichern nicht möglich")
        resolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "Music/YoutschikPrivat"
    }
}
