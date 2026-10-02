package de.oejendorferdamm.dammboard.data

import de.oejendorferdamm.dammboard.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Fest verdrahtet auf das öffentliche DammBoard-Repository – niemals eine benutzerdefinierte Adresse. */
private const val NEUESTE_RELEASE_URL = "https://api.github.com/repos/Teyro/DammBoard/releases/latest"

/** Nur APKs, die wirklich aus den Releases dieses Repositories kommen, werden geladen. */
private const val ERLAUBTER_DOWNLOAD_PRAEFIX = "https://github.com/Teyro/DammBoard/releases/download/"

/** Obergrenze für die Update-Datei – schützt vor einer endlos großen Antwort. */
private const val MAX_APK_BYTES = 150L * 1024 * 1024

/** Obergrenze für die Release-Info von GitHub. */
private const val MAX_INFO_BYTES = 2L * 1024 * 1024

/** Prüft auf GitHub nach der neuesten Version und lädt bei Bedarf die zugehörige APK herunter. */
class UpdateClient {
    private val client = NetzwerkClient.instance

    suspend fun neuesteVersionAbrufen(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val anfrage = Request.Builder()
                .url(NEUESTE_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(anfrage).execute().use { antwort ->
                if (!antwort.isSuccessful) {
                    return@withContext Result.failure(IOException("GitHub antwortete mit HTTP ${antwort.code}"))
                }
                val koerper = antwort.body ?: return@withContext Result.failure(IOException("Leere Antwort"))
                // Die Release-Info ist klein – eine riesige Antwort wird gar nicht erst eingelesen.
                if (koerper.contentLength() > MAX_INFO_BYTES) return@withContext Result.failure(IOException("Antwort zu groß"))
                val text = koerper.source().use { quelle ->
                    quelle.request(MAX_INFO_BYTES + 1)
                    if (quelle.buffer.size > MAX_INFO_BYTES) throw IOException("Antwort zu groß")
                    quelle.buffer.readUtf8()
                }
                val json = JSONObject(text)
                val version = json.optString("tag_name", "").removePrefix("v")
                val changelog = json.optString("body", "")
                var apkUrl: String? = null
                var apkGroesse = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val eintrag = assets.optJSONObject(i) ?: continue
                        val name = eintrag.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = eintrag.optString("browser_download_url")
                                .takeIf { it.startsWith(ERLAUBTER_DOWNLOAD_PRAEFIX) }
                            apkGroesse = eintrag.optLong("size", 0L)
                            break
                        }
                    }
                }
                if (version.isBlank() || apkUrl == null) {
                    return@withContext Result.failure(IOException("Release ohne APK-Anhang gefunden"))
                }
                Result.success(UpdateInfo(version, apkUrl, changelog, apkGroesse))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lädt die Update-APK in [ziel] herunter; [fortschritt] wird mit Werten von 0f bis 1f
     * aufgerufen. [erwarteteGroesse] (aus der Release-Info, 0 = unbekannt) wird geprüft, damit
     * keine abgebrochene oder fremde Datei beim Installer landet.
     */
    suspend fun apkHerunterladen(
        url: String,
        ziel: File,
        erwarteteGroesse: Long = 0L,
        fortschritt: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!url.startsWith(ERLAUBTER_DOWNLOAD_PRAEFIX)) {
                return@withContext Result.failure(IOException("Unerwartete Download-Adresse"))
            }
            val anfrage = Request.Builder().url(url).build()
            client.newCall(anfrage).execute().use { antwort ->
                if (!antwort.isSuccessful) {
                    return@withContext Result.failure(IOException("Herunterladen fehlgeschlagen: HTTP ${antwort.code}"))
                }
                val koerper = antwort.body ?: return@withContext Result.failure(IOException("Leere Antwort"))
                val gesamtgroesse = koerper.contentLength()
                var gelesen = 0L
                koerper.byteStream().use { eingabe ->
                    FileOutputStream(ziel).use { ausgabe ->
                        val puffer = ByteArray(8192)
                        while (true) {
                            val anzahl = eingabe.read(puffer)
                            if (anzahl == -1) break
                            ausgabe.write(puffer, 0, anzahl)
                            gelesen += anzahl
                            if (gelesen > MAX_APK_BYTES) throw IOException("Update-Datei ist unerwartet groß")
                            if (gesamtgroesse > 0) fortschritt((gelesen.toFloat() / gesamtgroesse.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
                if (erwarteteGroesse > 0 && gelesen != erwarteteGroesse) {
                    throw IOException("Download unvollständig (${gelesen} von ${erwarteteGroesse} Bytes)")
                }
                Result.success(ziel)
            }
        } catch (e: Exception) {
            ziel.delete()
            Result.failure(e)
        }
    }
}
