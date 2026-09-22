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
                val text = antwort.body?.string() ?: return@withContext Result.failure(IOException("Leere Antwort"))
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
                            apkUrl = eintrag.optString("browser_download_url").takeIf { it.isNotBlank() }
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

    /** Lädt die Update-APK in [ziel] herunter; [fortschritt] wird mit Werten von 0f bis 1f aufgerufen. */
    suspend fun apkHerunterladen(url: String, ziel: File, fortschritt: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
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
                            if (gesamtgroesse > 0) fortschritt((gelesen.toFloat() / gesamtgroesse.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
                Result.success(ziel)
            }
        } catch (e: Exception) {
            ziel.delete()
            Result.failure(e)
        }
    }
}
