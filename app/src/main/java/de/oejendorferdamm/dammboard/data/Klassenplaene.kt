package de.oejendorferdamm.dammboard.data

import android.content.Context
import de.oejendorferdamm.dammboard.model.SchulOrdner
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Ersatz, wenn DammTable fehlt: Grundwoche aller Klassen aus WebUntis als Datei im Schul-Ordner
 * („DammBoard/stundenplaene.json“, Fächer je Stunde, ohne Lehrkräfte). Welche Klasse das Board hat,
 * steht im Namen des Ablage-Ordners (z. B. „2A“). Einmal am Tag neu geladen, sonst die gemerkte Datei.
 */
object Klassenplaene {
    const val DATEI = "stundenplaene.json"

    private fun cache(context: Context) = File(context.filesDir, DATEI)

    /** Aus dem Schul-Ordner holen (höchstens alle 12 Stunden); true = neue Datei */
    suspend fun aktualisieren(context: Context, schulPasswort: String): Boolean {
        if (schulPasswort.isBlank()) return false
        val f = cache(context)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 12 * 60 * 60_000L) return false
        val daten = IServClient(SchulOrdner.zugang(schulPasswort)).herunterladen(DATEI).getOrNull() ?: return false
        val text = String(daten, Charsets.UTF_8)
        if (klassen(text).isEmpty()) return false
        f.writeText(text)
        return true
    }

    // ---------------------------------------------------------------- Untis-Sicherung (täglich aus WebUntis, Schul-Ordner „Untis/untis-plan.json“)

    const val UNTIS = "untis-plan.json"
    private fun untisCache(context: Context) = File(context.filesDir, UNTIS)

    /** Untis-Sicherung holen (höchstens alle 6 Stunden) – liegt neben „DammBoard“ im Schul-Ordner */
    suspend fun untisAktualisieren(context: Context, schulPasswort: String): Boolean {
        if (schulPasswort.isBlank()) return false
        val f = untisCache(context)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 60 * 60_000L) return false
        val z = SchulOrdner.zugang(schulPasswort)
        val wurzel = z.copy(serverUrl = z.serverUrl.substringBeforeLast("/DammBoard"))
        val text = IServClient(wurzel).herunterladen("Untis/$UNTIS").getOrNull()?.toString(Charsets.UTF_8) ?: return false
        if (runCatching { JSONObject(text).getJSONObject("plaene") }.isFailure) return false
        f.writeText(text)
        return true
    }

    /** Nebenfächer und Doppelbesetzungen zählen nicht als Fach der Stunde (Förderung, Schulbegleitung …) */
    private val NEBEN = Regex("förder|sprachf|doppel|begleit|^sbl|sopäd|frühbetr|^bus|routine|vertretung|hospi|koop", RegexOption.IGNORE_CASE)

    /** Tag einer Klasse aus der Untis-Sicherung: je Raster-Stunde das Hauptfach (Langname, z. B. „Deutsch“) */
    fun ausUntis(text: String, klasse: String, datum: LocalDate): Stundenplan.Tag? = try {
        val j = JSONObject(text)
        val lang = j.optJSONArray("faecher")?.let { a -> (0 until a.length()).associate { a.getJSONObject(it).optString("name") to a.getJSONObject(it).optString("lang") } } ?: emptyMap()
        val alle = j.getJSONObject("plaene").getJSONObject("klassen")
        val name = alle.keys().asSequence().firstOrNull { it.equals(klasse.trim(), ignoreCase = true) }
        val liste = name?.let { alle.getJSONArray(it) }
        if (liste == null || datum.dayOfWeek.value > 5) null else {
            val heute = (0 until liste.length()).map { liste.getJSONObject(it) }.filter { it.optString("datum") == datum.toString() }
            Stundenplan.Tag(datum, false, RASTER.mapNotNull { (n, z) ->
                val faecher = heute.filter { it.optString("von") < z.second && it.optString("bis") > z.first }
                    .flatMap { o -> o.optJSONArray("faecher")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList() }
                    .map { lang[it]?.ifBlank { null } ?: it }
                faecher.firstOrNull { !NEBEN.containsMatchIn(it) }?.let { Stundenplan.Stunde(n, LocalTime.parse(z.first), LocalTime.parse(z.second), it, "") }
            })
        }
    } catch (_: Exception) { null }

    /** Hauptfach der Klasse in dieser Stunde laut Untis-Sicherung (null = unbekannt) */
    fun untisFach(context: Context, klasse: String, datum: LocalDate, stunde: Int?): String? {
        val f = untisCache(context)
        if (stunde == null || !f.exists()) return null
        return ausUntis(f.readText(), klasse, datum)?.stunden?.firstOrNull { it.stunde == stunde }?.fach
    }

    fun klassen(text: String): List<String> = try {
        JSONObject(text).getJSONObject("klassen").keys().asSequence().toList().sorted()
    } catch (_: Exception) { emptyList() }

    /** Tag einer Klasse aus der gemerkten Datei (Wochenende/unbekannte Klasse: null) */
    fun tag(context: Context, klasse: String, datum: LocalDate = LocalDate.now()): Stundenplan.Tag? {
        val f = cache(context)
        // ohne Klassenpläne-Datei: die tägliche Untis-Sicherung
        val u = untisCache(context)
        if (!f.exists()) return if (u.exists()) ausUntis(u.readText(), klasse, datum) else null
        return try { tag(f.readText(), klasse, datum) } catch (_: Exception) { null }
    }

    /** Stundenraster der Schule (wie DammTable) – ohne Stundenplan wird trotzdem zu jedem Stundenende abgelegt */
    private val RASTER = listOf(1 to ("08:30" to "09:15"), 2 to ("09:15" to "10:00"), 3 to ("10:25" to "11:10"), 4 to ("11:10" to "11:55"), 5 to ("12:20" to "13:05"), 6 to ("13:05" to "13:30"))

    fun raster(datum: LocalDate = LocalDate.now()): Stundenplan.Tag? =
        if (datum.dayOfWeek.value > 5) null
        else Stundenplan.Tag(datum, false, RASTER.map { (n, z) -> Stundenplan.Stunde(n, LocalTime.parse(z.first), LocalTime.parse(z.second), "Tafel", "") })

    /**
     * Nur ergänzen, nie überschreiben: Stunden aus DammTable bleiben, wie sie sind; aus der Untis-Datei kommen nur
     * Stunden dazu, die dort leer sind. Freier Tag in DammTable bleibt frei. Ohne DammTable: die Untis-Datei.
     */
    fun ergaenzen(dammTable: Stundenplan.Tag?, ersatz: Stundenplan.Tag?): Stundenplan.Tag? {
        if (dammTable == null) return ersatz
        if (dammTable.frei || ersatz == null) return dammTable
        val belegt = dammTable.stunden.map { it.stunde }.toSet()
        return dammTable.copy(stunden = (dammTable.stunden + ersatz.stunden.filter { it.stunde !in belegt }).sortedBy { it.stunde })
    }

    fun tag(text: String, klasse: String, datum: LocalDate): Stundenplan.Tag? {
      return try {
        val k = klasse.trim()
        val alle = JSONObject(text).getJSONObject("klassen")
        val name = alle.keys().asSequence().firstOrNull { it.equals(k, ignoreCase = true) } ?: return null
        val wt = datum.dayOfWeek.value // 1 = Montag
        if (wt > 5) return null
        val liste = alle.getJSONObject(name).getJSONObject("tage").optJSONArray(wt.toString())
        fun zeit(s: String) = runCatching { LocalTime.parse(s) }.getOrNull()
        Stundenplan.Tag(datum, false, (0 until (liste?.length() ?: 0)).map { i ->
            val s = liste!!.getJSONObject(i)
            Stundenplan.Stunde(s.optInt("stunde"), zeit(s.optString("beginn")), zeit(s.optString("ende")), s.optString("fach", "Fach"), "")
        })
    } catch (_: Exception) { null }
    }
}
