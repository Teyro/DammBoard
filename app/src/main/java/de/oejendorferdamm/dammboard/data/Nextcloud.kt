package de.oejendorferdamm.dammboard.data

import android.content.Context
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.model.SchulOrdner
import androidx.compose.ui.graphics.asImageBitmap
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
    /** Normaler Ordner der Nextcloud, über den hochgeladen wird (siehe hochladenSchule) */
    const val ZWISCHEN = "DammBoard/.zwischen"

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

    // ---------------------------------------------------------------- Zugänge der Schule über DammTable

    /** Zugänge der Schule (Nextcloud je Haus) und Klasse dieses Boards – aus DammTable (nur mit demselben Signaturschlüssel) */
    data class Schule(val server: String, val benutzer: String, val passwort: String, val pfad: String, val klasse: String)

    /** Haus nach Jahrgang: die erste Ziffer der Klasse („4c“ → 4) */
    fun schuleAus(json: String, klasseErsatz: String): Schule? = try {
        val o = JSONObject(json)
        val nc = o.optJSONObject("nextcloud") ?: return null
        val klasse = o.optString("klasse").ifBlank { klasseErsatz }.trim().lowercase()
        val stufe = klasse.firstOrNull { it.isDigit() }?.digitToInt()
        val haeuser = nc.optJSONArray("haeuser")
        val passwort = (0 until (haeuser?.length() ?: 0)).map { haeuser!!.getJSONObject(it) }.firstOrNull { h ->
            val j = h.optJSONArray("jahrgaenge"); stufe != null && (0 until (j?.length() ?: 0)).any { j!!.optInt(it, -1) == stufe }
        }?.optString("passwort").orEmpty()
        val s = Schule(nc.optString("server"), nc.optString("benutzer"), passwort, nc.optString("pfad").ifBlank { "cleverTouch/{fach}/Klasse {klasse}" }, klasse)
        if (s.server.isBlank() || s.benutzer.isBlank() || s.passwort.isBlank() || klasse.isBlank()) null else s
    } catch (_: Exception) { null }

    fun schule(context: Context, klasseErsatz: String): Schule? = try {
        context.contentResolver.query(android.net.Uri.parse("content://de.oejendorferdamm.dammtable.stundenplan/zugaenge"), null, null, null, null)?.use { c ->
            if (c.moveToFirst()) schuleAus(c.getString(0), klasseErsatz) else null
        }
    } catch (_: Exception) { null } // DammTable fehlt oder ist zu alt

    /** Vorhandenen Fach-Ordner finden („Mathe“ → „Mathematik“); null = kein passender – die Fächer gibt die Schule vor, keine neuen anlegen */
    fun fachOrdner(fach: String, vorhanden: List<String>): String? {
        val f = fach.trim()
        // „Tafel“ = keine laufende Stunde – kein Fach, auch wenn der Ordner (von 2.3.0–2.5.0 angelegt) schon da ist
        if (f.isEmpty() || f.equals("Tafel", ignoreCase = true)) return null
        vorhanden.firstOrNull { it.equals(f, ignoreCase = true) }?.let { return it }
        if (f.length >= 3) vorhanden.firstOrNull { it.length >= 3 && (it.startsWith(f, ignoreCase = true) || f.startsWith(it, ignoreCase = true)) }?.let { return it }
        return null
    }

    /** „cleverTouch/{fach}/Klasse {klasse}“ → Pfad; null, wenn es keinen passenden Fach-Ordner gibt (z. B. Pause, ohne Stundenplan) */
    suspend fun zielPfad(c: IServClient, muster: String, fach: String, klasse: String): String? {
        val ordner = if (muster.contains("{fach}")) {
            val vorhanden = c.liste(muster.substringBefore("{fach}").trim('/')).getOrNull()?.filter { it.istOrdner }?.map { it.name.trimEnd('/') } ?: emptyList()
            fachOrdner(fach, vorhanden) ?: return null
        } else ""
        return muster.replace("{fach}", ordner).replace("{klasse}", klasse).split('/').filter { it.isNotBlank() }.joinToString("/")
    }

    /**
     * Tafel als PNG (volle Größe, je Seite eins) in die Nextcloud der Schule: cleverTouch/<Fach>/Klasse 4c/2026-11-12 3. Std.png.
     * Ohne Zugänge aus DammTable: die eigene Einrichtung (Ordner/Board/Fach, dort die .dammboard-Datei).
     */
    suspend fun hochladenSchule(context: Context, s: Schule, seiten: List<de.oejendorferdamm.dammboard.model.SeitenAbbild>, breite: Int, hoehe: Int,
                                fach: String, datum: java.time.LocalDate, stunde: Int?): Result<Unit> {
        val c = IServClient(IServZugang(davAdresse(s.server, s.benutzer), s.benutzer, s.passwort))
        // Ohne passenden Fach-Ordner nichts hochladen – die Tafel liegt trotzdem in der Ablage auf dem Board
        val pfad = zielPfad(c, s.pfad, fach, s.klasse) ?: return Result.success(Unit)
        c.ordnerAnlegen(pfad).onFailure { return Result.failure(it) }
        val name = datum.toString() + (stunde?.let { " $it. Std" } ?: "")
        seiten.forEachIndexed { i, seite ->
            val bild = seite.bild?.let { b -> File(de.oejendorferdamm.dammboard.ui.Arbeitsblaetter.ordner(context), b).takeIf { it.exists() }
                ?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }?.let { runCatching { it.asImageBitmap() }.getOrNull() } }
            val png = de.oejendorferdamm.dammboard.ui.canvas.rendereSeite(seite, breite.coerceAtLeast(1), hoehe.coerceAtLeast(1), 1f, bild).let { b ->
                java.io.ByteArrayOutputStream().also { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it); b.recycle() }.toByteArray()
            }
            val ziel = name + (if (seiten.size > 1) " Seite ${i + 1}" else "") + ".png"
            // „cleverTouch“ ist bei der Schule ein eingebundener Schuldock-Ordner: direktes Hochladen scheitert dort
            // (Zwischendatei .part wird abgelehnt) – deshalb erst in einen normalen Ordner, dann hineinverschieben
            val zwischen = ZWISCHEN
            val tmp = "upload-" + System.currentTimeMillis() + "-" + i + ".png"
            c.ordnerAnlegen(zwischen)
            c.hochladen(zwischen, tmp, png, "image/png").onFailure { return Result.failure(it) }
            c.verschieben(zwischen, tmp, pfad, ziel).onFailure { c.loeschen(zwischen, tmp); return Result.failure(it) }
        }
        p(context).edit().putString("status", java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMAN).format(java.util.Date()) + " – $pfad/$name.png").apply()
        return Result.success(Unit)
    }

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
