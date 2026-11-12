package de.oejendorferdamm.dammboard.data

import android.content.Context
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.model.SchulOrdner
import org.json.JSONObject
import java.io.File

/**
 * Nextcloud (WebDAV): nach jeder Stunde die abgelegte Tafel auch dorthin – <Ordner>/<Board>/<Fach>/<Name>.dammboard (+ Vorschau).
 * Möglichst einfach: Server und Benutzer kommen aus einer versteckten Datei im Schul-Ordner („DammBoard/.nextcloud.json“,
 * {"server", "benutzer", "ordner", optional "passwort"}) oder sind hier fest eingetragen – am Board nur noch das App-Passwort.
 */
object Nextcloud {
    /** Fest eingebaut, sobald bekannt (Server und Benutzer sind kein Geheimnis – das App-Passwort kommt nie in den Code) */
    const val STANDARD_SERVER = ""
    const val STANDARD_BENUTZER = ""
    const val STANDARD_ORDNER = "DammBoard"
    const val VERSTECKT = ".nextcloud.json"

    data class Konfig(val server: String, val benutzer: String, val passwort: String, val ordner: String, val an: Boolean) {
        val bereit get() = an && server.isNotBlank() && benutzer.isNotBlank() && passwort.isNotBlank()
    }

    private fun p(context: Context) = context.getSharedPreferences("nextcloud", Context.MODE_PRIVATE)

    fun lese(context: Context): Konfig {
        val p = p(context)
        return Konfig(
            p.getString("server", null)?.ifBlank { null } ?: STANDARD_SERVER,
            p.getString("benutzer", null)?.ifBlank { null } ?: STANDARD_BENUTZER,
            p.getString("passwort", null)?.let { runCatching { Tresor.entschluesseln(it) }.getOrDefault("") } ?: "",
            p.getString("ordner", null)?.ifBlank { null } ?: STANDARD_ORDNER,
            p.getBoolean("an", true)
        )
    }

    fun speichere(context: Context, k: Konfig) {
        p(context).edit().putString("server", k.server.trim()).putString("benutzer", k.benutzer.trim())
            .putString("passwort", if (k.passwort.isEmpty()) "" else Tresor.verschluesseln(k.passwort))
            .putString("ordner", k.ordner.trim().trim('/')).putBoolean("an", k.an).apply()
    }

    /** „https://cloud.schule.de“ → WebDAV-Adresse des Benutzers; eine volle /remote.php/…-Adresse bleibt, wie sie ist */
    fun davAdresse(server: String, benutzer: String): String {
        var s = server.trim().trimEnd('/')
        if (!s.startsWith("http")) s = "https://$s"
        if (s.contains("/remote.php/")) return s
        // Freigabe-Link (…/s/<token>) taugt nicht – dann die Basis
        s = s.substringBefore("/index.php").substringBefore("/s/")
        return "$s/remote.php/dav/files/" + java.net.URLEncoder.encode(benutzer.trim(), "UTF-8").replace("+", "%20")
    }

    fun zugang(k: Konfig) = IServZugang(davAdresse(k.server, k.benutzer), k.benutzer.trim(), k.passwort)

    /** Versteckte Datei aus dem Schul-Ordner: füllt leere Felder (und das Passwort, falls dort eins steht) */
    suspend fun ausSchulOrdner(context: Context, schulPasswort: String): Boolean {
        if (schulPasswort.isBlank()) return false
        val text = IServClient(SchulOrdner.zugang(schulPasswort)).herunterladen(VERSTECKT).getOrNull()?.toString(Charsets.UTF_8) ?: return false
        val o = try { JSONObject(text) } catch (_: Exception) { return false }
        val alt = lese(context)
        val neu = alt.copy(
            server = alt.server.ifBlank { o.optString("server") },
            benutzer = alt.benutzer.ifBlank { o.optString("benutzer") },
            passwort = alt.passwort.ifBlank { o.optString("passwort") },
            ordner = if (alt.ordner == STANDARD_ORDNER) o.optString("ordner").ifBlank { STANDARD_ORDNER } else alt.ordner
        )
        if (neu != alt) speichere(context, neu)
        return true
    }

    /** Verbindung prüfen: Ordner auflisten */
    suspend fun testen(k: Konfig): String =
        IServClient(zugang(k)).liste("").fold({ "Verbunden – ${it.size} Einträge im Hauptordner." }, { "Keine Verbindung: ${it.message}" })

    /** Abgelegte Tafel (und Vorschau) hochladen; legt fehlende Ordner an */
    suspend fun hochladen(context: Context, datei: File, board: String, fach: String): Result<Unit> {
        val k = lese(context)
        if (!k.bereit) return Result.failure(IllegalStateException("Nextcloud nicht eingerichtet"))
        val c = IServClient(zugang(k))
        val pfad = listOf(k.ordner, Ablage.sicher(board), Ablage.sicher(fach)).filter { it.isNotBlank() }.joinToString("/")
        c.ordnerAnlegen(pfad).onFailure { return Result.failure(it) }
        c.hochladen(pfad, datei.name, datei.readBytes(), "application/octet-stream").onFailure { return Result.failure(it) }
        val vorschau = File(datei.parentFile, datei.name.removeSuffix(OrdnerSync.ENDUNG) + ".png")
        if (vorschau.exists()) c.hochladen(pfad, vorschau.name, vorschau.readBytes(), "image/png")
        p(context).edit().putString("status", java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMAN).format(java.util.Date()) + " – ${datei.name}").apply()
        return Result.success(Unit)
    }

    fun status(context: Context) = p(context).getString("status", "") ?: ""
}
