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

    fun klassen(text: String): List<String> = try {
        JSONObject(text).getJSONObject("klassen").keys().asSequence().toList().sorted()
    } catch (_: Exception) { emptyList() }

    /** Tag einer Klasse aus der gemerkten Datei (Wochenende/unbekannte Klasse: null) */
    fun tag(context: Context, klasse: String, datum: LocalDate = LocalDate.now()): Stundenplan.Tag? {
        val f = cache(context)
        if (!f.exists()) return null
        return try { tag(f.readText(), klasse, datum) } catch (_: Exception) { null }
    }

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
