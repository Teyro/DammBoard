package de.oejendorferdamm.dammboard.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.oejendorferdamm.dammboard.model.IServZugang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Vorschaubilder der Schul-Cloud (cleverTouch/…) im internen Speicher, damit der Bilder-Browser sofort zeigt.
 * Einmal am Tag holt das Board neue Vorschauen – je Jahrgang in einem eigenen Zeitfenster und darin zu einer
 * zufälligen Minute, damit nie alle Boards gleichzeitig auf den Server gehen. Schon vorhandene Vorschauen werden
 * nicht neu geladen. Alles liegt in einem Ordner, „Löschen“ in den Einstellungen leert ihn gefahrlos.
 */
object VorschauCache {
    private val BILD = Regex("(?i).+\\.(png|jpe?g|webp)$")
    /** Höchstens so viele neue Vorschauen je Lauf, mit Pause dazwischen – schont den Server */
    private const val MAX_NEU = 1500
    private const val PAUSE_MS = 400L

    fun ordner(context: Context) = File(context.filesDir, "vorschau-cache")

    private fun datei(context: Context, rel: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(rel.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(ordner(context), "$h.png")
    }

    fun lies(context: Context, rel: String): Bitmap? = datei(context, rel).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun schreibe(context: Context, rel: String, bytes: ByteArray) {
        try { ordner(context).mkdirs(); datei(context, rel).writeBytes(bytes) } catch (_: Exception) {}
    }

    fun loeschen(context: Context) { ordner(context).deleteRecursively() }

    /** (Anzahl, Bytes) */
    fun groesse(context: Context): Pair<Int, Long> = ordner(context).listFiles().orEmpty().let { f -> f.size to f.sumOf { it.length() } }

    /** Kleines Vorschaubild von Nextcloud (core/preview, 360×240), [rel] = „/cleverTouch/Mathe/Klasse 2b/bild.png“ */
    fun holen(s: Nextcloud.Schule, rel: String): ByteArray? = try {
        val basis = Nextcloud.davAdresse(s.server, s.benutzer).substringBefore("/remote.php/")
        val url = "$basis/index.php/core/preview.png?file=" + java.net.URLEncoder.encode(rel, "UTF-8").replace("+", "%20") + "&x=360&y=240&a=1"
        NetzwerkClient.instance.newCall(Request.Builder().url(url).header("Authorization", Credentials.basic(s.benutzer, s.passwort)).build())
            .execute().use { if (it.isSuccessful) it.body?.bytes() else null }
    } catch (_: Exception) { null }

    /** Jahrgang aus der Klasse: „vsk…“ → 0, „2b“ → 2, sonst null */
    fun stufe(klasse: String): Int? = if (klasse.lowercase().startsWith("vs")) 0 else klasse.firstOrNull { it.isDigit() }?.digitToInt()

    /**
     * Zeitpunkt heute (Minuten nach Mitternacht): Vorschule 8–9 Uhr, Jahrgang 1 9–10, 2 10–11, 3 11–12, 4 12–13,
     * unbekannt 13–14 – darin eine zufällige Minute, je Tag und Board verschieden.
     */
    fun zeitpunkt(datum: LocalDate, klasse: String, board: String): Int {
        val start = (8 + (stufe(klasse)?.coerceIn(0, 4) ?: 5)) * 60
        return start + kotlin.random.Random(datum.toEpochDay() * 7919 + (klasse + board).hashCode()).nextInt(60)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("vorschau", Context.MODE_PRIVATE)

    /** Heute dran? Dann gleich als erledigt merken (kein zweiter Lauf am selben Tag, auch nicht nach einem Fehler) */
    fun faellig(context: Context, s: Nextcloud.Schule, board: String, jetzt: java.time.LocalDateTime = java.time.LocalDateTime.now()): Boolean {
        val heute = jetzt.toLocalDate().toString()
        if (prefs(context).getString("tag", null) == heute) return false
        val m = jetzt.toLocalTime().let { it.hour * 60 + it.minute }
        if (m < zeitpunkt(jetzt.toLocalDate(), s.klasse, board) || m > 17 * 60) return false
        prefs(context).edit().putString("tag", heute).apply()
        return true
    }

    fun stand(context: Context): String = prefs(context).getString("stand", "").orEmpty()

    /** Alle Ordner unter [wurzel] (Fach/Klasse/…, bis 3 Ebenen) durchgehen und fehlende Vorschauen laden; nicht mehr vorhandene entfernen */
    suspend fun auffrischen(context: Context, s: Nextcloud.Schule) = withContext(Dispatchers.IO) {
        val c = IServClient(IServZugang(Nextcloud.davAdresse(s.server, s.benutzer), s.benutzer, s.passwort))
        val wurzel = s.pfad.substringBefore("{fach}").trim('/').ifBlank { "cleverTouch" }
        val gesehen = HashSet<String>()
        var neu = 0
        var vollstaendig = true
        val offen = ArrayDeque(listOf(wurzel to 0))
        while (offen.isNotEmpty()) {
            val (pfad, tiefe) = offen.removeFirst()
            val r = c.liste(pfad)
            if (r.isFailure) { vollstaendig = false; continue }
            val liste = r.getOrThrow()
            delay(PAUSE_MS)
            for (e in liste) {
                val rel = "/" + pfad.trim('/') + "/" + e.name.trimEnd('/')
                if (e.istOrdner) { if (tiefe < 3) offen.add(rel.trim('/') to tiefe + 1); continue }
                if (!BILD.matches(e.name)) continue
                val f = datei(context, rel)
                gesehen.add(f.name)
                if (f.exists()) continue
                if (neu >= MAX_NEU) { vollstaendig = false; continue }
                holen(s, rel)?.let { schreibe(context, rel, it); neu++ }
                delay(PAUSE_MS)
            }
        }
        // nur nach einem vollständigen Durchgang aufräumen – sonst gingen Vorschauen bei einem Netzfehler verloren
        if (vollstaendig) ordner(context).listFiles().orEmpty().filter { it.name !in gesehen }.forEach { it.delete() }
        prefs(context).edit().putString("stand", java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMAN).format(java.util.Date()) + " – $neu neu").apply()
    }
}
