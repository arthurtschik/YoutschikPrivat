package de.youtschik.privat

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

sealed interface DlState {
    data object Idle : DlState
    /** progress < 0 = unbekannt (z. B. beim Umwandeln) */
    data class Running(val progress: Float) : DlState
    data class Done(val text: String) : DlState
    data class Error(val text: String) : DlState
}

object Engine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var state by mutableStateOf<DlState>(DlState.Idle)
        private set
    private var initialized = false

    @Synchronized
    private fun ensureInit(ctx: Context) {
        if (initialized) return
        YoutubeDL.getInstance().init(ctx)
        FFmpeg.getInstance().init(ctx)
        initialized = true
    }

    /** yt-dlp höchstens einmal pro Tag aktualisieren, Fehler ignorieren. */
    private fun maybeUpdateYtDlp(ctx: Context) {
        val prefs = ctx.getSharedPreferences("prefs", 0)
        val last = prefs.getLong("ytdlp_update", 0)
        if (System.currentTimeMillis() - last < 24 * 3600 * 1000) return
        try {
            YoutubeDL.getInstance().updateYoutubeDL(ctx)
            prefs.edit().putLong("ytdlp_update", System.currentTimeMillis()).apply()
        } catch (_: Exception) {
        }
    }

    fun start(appCtx: Context, url: String) {
        val sponsor = Storage.sponsorBlock(appCtx)
        if (state is DlState.Running) return
        state = DlState.Running(-1f)
        scope.launch {
            val tmp = File(appCtx.cacheDir, "dl/${System.nanoTime()}").apply { mkdirs() }
            try {
                ensureInit(appCtx)
                maybeUpdateYtDlp(appCtx)
                val file = fetch(url, tmp, sponsor)
                val where = Storage.save(appCtx, file)
                state = DlState.Done("${file.nameWithoutExtension}\n.${file.extension} → $where")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = DlState.Error((e.message ?: "Unbekannter Fehler").trim().takeLast(300))
            } finally {
                tmp.deleteRecursively()
            }
        }
    }

    private fun fetch(url: String, tmp: File, sponsor: Boolean): File {
        val template = "${tmp.absolutePath}/%(title).100B.%(ext)s"
        fun base() = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--windows-filenames")
            addOption("--no-mtime")
            addOption("-o", template)
        }
        val progress: (Float, Long, String) -> Unit = { p, _, _ -> state = DlState.Running(p / 100f) }
        // Versuche in dieser Reihenfolge: MP3 (ohne Werbung/Sponsor-Teile), MP3 pur, Originalformat
        data class Attempt(val mp3: Boolean, val skipSponsor: Boolean)
        val attempts = buildList {
            if (sponsor) add(Attempt(true, true))
            add(Attempt(true, false))
            add(Attempt(false, false))
        }
        var last: Exception? = null
        var ok = false
        for (a in attempts) {
            try {
                tmp.listFiles()?.forEach { it.delete() }
                state = DlState.Running(-1f)
                val r = base().apply {
                    if (a.skipSponsor) addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
                    if (a.mp3) {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        addOption("--audio-quality", "0")
                    } else addOption("-f", "bestaudio/best")
                }
                YoutubeDL.getInstance().execute(r, null, progress)
                ok = true
                break
            } catch (e: Exception) {
                last = e
            }
        }
        if (!ok) throw last ?: IllegalStateException("Download fehlgeschlagen")
        val audio = setOf("mp3", "m4a", "webm", "opus", "ogg", "aac", "flac", "wav")
        return tmp.listFiles()?.filter { it.isFile && it.extension.lowercase() in audio }
            ?.maxByOrNull { it.length() } ?: error("Keine Audiodatei erzeugt")
    }

    fun reset() {
        if (state !is DlState.Running) state = DlState.Idle
    }
}
