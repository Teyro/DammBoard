package de.oejendorferdamm.dammboard.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import de.oejendorferdamm.dammboard.model.SeitenAbbild
import de.oejendorferdamm.dammboard.model.Seite
import de.oejendorferdamm.dammboard.ui.Arbeitsblaetter
import de.oejendorferdamm.dammboard.ui.canvas.rendereSeite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Feste Ablage auf dem Board: <App-Ordner>/Tafeln/<Name des Boards bzw. der Klasse>/<Fach>/<Fach> <Datum> <n>. Std.dammboard
 * (+ gleichnamiges .png als Vorschau). Wird automatisch nach jeder Stunde und am Ende des Tages befüllt (siehe
 * AppWurzel), alte Tafeln (.ubmx2) lassen sich hierher importieren. Liegt im App-Ordner auf dem Gerätespeicher
 * (Android/data/de.oejendorferdamm.dammboard/files/Tafeln) – ohne Speicher-Berechtigung, auf allen Android-Versionen.
 */
object Ablage {
    data class Fach(val name: String, val ordner: File, val anzahl: Int, val zuletzt: Long)
    data class Eintrag(val name: String, val datei: File, val vorschau: File, val geaendert: Long)

    fun wurzel(context: Context, ordnerName: String): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "Tafeln/" + sicher(ordnerName.ifBlank { "Tafel" })).apply { mkdirs() }

    fun sicher(name: String) = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "-").take(60).ifEmpty { "Tafel" }

    fun faecher(context: Context, ordnerName: String): List<Fach> =
        (wurzel(context, ordnerName).listFiles() ?: emptyArray()).filter { it.isDirectory }.map { d ->
            val dateien = d.listFiles { f -> f.name.endsWith(OrdnerSync.ENDUNG) } ?: emptyArray()
            Fach(d.name, d, dateien.size, dateien.maxOfOrNull { it.lastModified() } ?: d.lastModified())
        }.filter { it.anzahl > 0 }.sortedBy { it.name.lowercase() }

    fun eintraege(fach: File): List<Eintrag> =
        (fach.listFiles { f -> f.name.endsWith(OrdnerSync.ENDUNG) } ?: emptyArray()).map { f ->
            Eintrag(f.name.removeSuffix(OrdnerSync.ENDUNG), f, File(f.parentFile, f.name.removeSuffix(OrdnerSync.ENDUNG) + ".png"), f.lastModified())
        }.sortedByDescending { it.geaendert }

    /** Name für eine Stunde: „Mathematik 2026-11-10 3. Std“ (ohne Stunde: nur Fach und Datum) */
    fun name(fach: String, datum: LocalDate, stunde: Int?) =
        sicher(fach) + " " + datum.format(DateTimeFormatter.ISO_LOCAL_DATE) + (stunde?.let { " $it. Std" } ?: "")

    /**
     * Tafel ablegen (überschreibt dieselbe Stunde). Schreibt erst in eine Zwischendatei – ein Stromausfall lässt keine
     * halbe Datei zurück. Vorschau = erste Seite, klein.
     */
    suspend fun speichere(context: Context, ordnerName: String, fach: String, name: String, seiten: List<SeitenAbbild>, aktiv: Int, breite: Int, hoehe: Int): File = withContext(Dispatchers.IO) {
        val ordner = File(wurzel(context, ordnerName), sicher(fach)).apply { mkdirs() }
        val ziel = File(ordner, sicher(name) + OrdnerSync.ENDUNG)
        val tmp = File(ordner, ziel.name + ".tmp")
        tmp.writeBytes(OrdnerSync.packe(context, seiten, aktiv))
        if (!tmp.renameTo(ziel)) { ziel.delete(); if (!tmp.renameTo(ziel)) throw IOException("Ablegen fehlgeschlagen") }
        try {
            seiten.firstOrNull()?.let { s ->
                val bild = s.bild?.let { b -> File(Arbeitsblaetter.ordner(context), b).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmapOderNull() } }
                val v = rendereSeite(s, breite.coerceAtLeast(1), hoehe.coerceAtLeast(1), 320f / breite.coerceAtLeast(1), bild)
                File(ordner, sicher(name) + ".png").outputStream().use { v.compress(Bitmap.CompressFormat.PNG, 90, it) }
                v.recycle()
            }
        } catch (_: Throwable) { /* ohne Vorschau weiter */ }
        ziel
    }

    suspend fun oeffne(context: Context, datei: File, neueId: () -> Long): Pair<List<Seite>, Int> = withContext(Dispatchers.IO) {
        datei.inputStream().use { OrdnerSync.oeffneStrom(context, it, neueId) }
    }

    fun loesche(e: Eintrag) { e.datei.delete(); e.vorschau.delete() }

    private fun Bitmap.asImageBitmapOderNull() = try { asImageBitmap() } catch (_: Throwable) { null }
}
