package de.oejendorferdamm.dammboard.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import de.oejendorferdamm.dammboard.model.SeitenAbbild
import de.oejendorferdamm.dammboard.ui.canvas.rendereSeite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Breite einer PDF-Seite in Punkt (A4 quer); die Höhe folgt dem Seitenverhältnis der Tafel. */
private const val PDF_BREITE = 842

/** Höchstens so breit (Pixel) wird eine Tafelseite für das PDF gezeichnet. */
private const val PDF_PIXEL = 2200f

/**
 * Alle Seiten als ein PDF – z. B. für Kinder, die gefehlt haben. Jede Seite wird als Bild
 * eingebettet (so sieht sie genau aus wie auf der Tafel, inklusive Arbeitsblatt).
 */
suspend fun erstellePdf(
    context: Context,
    seiten: List<SeitenAbbild>,
    breite: Int,
    hoehe: Int,
    fortschritt: (Int) -> Unit
): File = withContext(Dispatchers.Default) {
    val dokument = PdfDocument()
    val massstab = minOf(1f, PDF_PIXEL / breite)
    val seitenHoehe = (PDF_BREITE * hoehe.toFloat() / breite).roundToInt()
    val pinsel = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    try {
        seiten.forEachIndexed { i, abbild ->
            val blatt = abbild.bild?.let { Arbeitsblaetter.lade(context, it) }
            val bitmap = rendereSeite(abbild, breite, hoehe, massstab, blatt)
            val seite = dokument.startPage(PdfDocument.PageInfo.Builder(PDF_BREITE, seitenHoehe, i + 1).create())
            seite.canvas.drawBitmap(bitmap, null, Rect(0, 0, PDF_BREITE, seitenHoehe), pinsel)
            dokument.finishPage(seite)
            bitmap.recycle()
            withContext(Dispatchers.Main) { fortschritt(i + 1) }
        }
        val ordner = File(context.cacheDir, "pdf").apply { mkdirs() }
        ordner.listFiles()?.forEach { it.delete() } // alte Exporte wegräumen
        val name = "DammBoard_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.GERMANY).format(Date())}.pdf"
        val datei = File(ordner, name)
        FileOutputStream(datei).use { dokument.writeTo(it) }
        datei
    } finally {
        dokument.close()
    }
}

fun teilePdf(context: Context, datei: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Tafelbild als PDF teilen"))
}

/** Legt das PDF unter "Download/DammBoard" ab. Android 8/9 brauchen dafür die Speicherberechtigung. */
suspend fun speicherePdfInDownloads(context: Context, datei: File): Uri? = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val werte = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, datei.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/DammBoard")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, werte) ?: return@withContext null
            context.contentResolver.openOutputStream(uri)?.use { aus -> datei.inputStream().use { it.copyTo(aus) } }
            uri
        } else {
            @Suppress("DEPRECATION")
            val ordner = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DammBoard")
            if (!ordner.exists() && !ordner.mkdirs()) return@withContext null
            val ziel = File(ordner, datei.name)
            datei.copyTo(ziel, overwrite = true)
            Uri.fromFile(ziel)
        }
    } catch (e: Exception) {
        null
    }
}
