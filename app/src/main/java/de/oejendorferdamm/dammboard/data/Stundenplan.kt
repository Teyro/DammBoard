package de.oejendorferdamm.dammboard.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Stundenplan des Tages aus DammTable (gemeinsamer Speicher über dessen Schnittstelle; nur mit demselben Signaturschlüssel
 * lesbar). Ohne DammTable: kein Plan – dann legt die Tafel unter „Tafel“ ab.
 */
object Stundenplan {
    private const val QUELLE = "content://de.oejendorferdamm.dammtable.stundenplan"

    data class Stunde(val stunde: Int, val beginn: LocalTime?, val ende: LocalTime?, val fach: String, val karte: String)
    data class Tag(val datum: LocalDate, val frei: Boolean, val stunden: List<Stunde>)

    fun heute(context: Context, datum: LocalDate = LocalDate.now()): Tag? = try {
        context.contentResolver.query(Uri.parse("$QUELLE/heute?datum=$datum"), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null else lesen(c.getString(0))
        }
    } catch (_: Exception) { null } // DammTable fehlt oder ist zu alt

    fun lesen(json: String): Tag? = try {
        val o = JSONObject(json)
        val a = o.optJSONArray("stunden")
        fun zeit(s: String) = s.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        Tag(LocalDate.parse(o.getString("datum")), o.optBoolean("frei"), (0 until (a?.length() ?: 0)).map { i ->
            val s = a!!.getJSONObject(i)
            Stunde(s.optInt("stunde"), zeit(s.optString("beginn")), zeit(s.optString("ende")), s.optString("fach", "Fach"), s.optString("karte"))
        })
    } catch (_: Exception) { null }

    /** Welche Stunde läuft (bis zum Beginn der nächsten zählt die Zeit danach – Pausen gehören zur Stunde davor) */
    fun laufend(tag: Tag?, jetzt: LocalTime): Stunde? {
        val mit = tag?.stunden?.filter { it.beginn != null } ?: return null
        return mit.lastOrNull { jetzt >= it.beginn!! }?.takeIf { s -> s.ende == null || jetzt < s.ende.plusMinutes(20) }
    }

    /** Alle Karten aus DammTable: (Id, Name) – für die Symbole der Fach-Ordner */
    fun karten(context: Context): List<Pair<String, String>> = try {
        context.contentResolver.query(Uri.parse("$QUELLE/karten"), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) emptyList() else {
                val a = org.json.JSONArray(c.getString(0))
                (0 until a.length()).map { a.getJSONObject(it).let { o -> o.optString("id") to o.optString("name") } }.filter { it.second.isNotBlank() }
            }
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    /** Karte (METACOM-Symbol der Schule, bleibt auf dem Board) zum Fach */
    fun symbol(context: Context, karte: String): Bitmap? = try {
        if (karte.isBlank()) null else context.contentResolver.openInputStream(Uri.parse("$QUELLE/karte/$karte"))?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }
}
