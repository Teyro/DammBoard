package de.oejendorferdamm.dammboard.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.oejendorferdamm.dammboard.model.HintergrundStil
import de.oejendorferdamm.dammboard.model.Seite
import de.oejendorferdamm.dammboard.model.TafelWeiss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Mehr Seiten nimmt ein PDF nicht mit – schützt alte Boards vor vollem Speicher. */
private const val MAX_PDF_SEITEN = 40

/** Höchstens so groß (Pixel) werden Arbeitsblätter gespeichert – reicht für 4K, spart Speicher. */
private const val MAX_KANTE = 2600

/**
 * Arbeitsblätter (PDF oder Bild), die als Hintergrund einer Seite geöffnet wurden. Jede Seite
 * eines PDFs wird einmal als JPEG im App-Ordner "blaetter" abgelegt; die Seite merkt sich nur den
 * Dateinamen. Geladen werden immer nur die gerade gebrauchten Bilder (kleiner Zwischenspeicher).
 */
object Arbeitsblaetter {
    private val cache = LruCache<String, ImageBitmap>(3)

    fun ordner(context: Context): File = File(context.filesDir, "blaetter").apply { mkdirs() }

    fun ausCache(name: String?): ImageBitmap? = name?.let { cache.get(it) }

    suspend fun lade(context: Context, name: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(name) ?: try {
            BitmapFactory.decodeFile(File(ordner(context), name).absolutePath)?.asImageBitmap()?.also { cache.put(name, it) }
        } catch (e: OutOfMemoryError) {
            cache.evictAll()
            null
        }
    }

    /** Liest eine Datei (PDF oder Bild) ein und gibt die Dateinamen der erzeugten Blätter zurück. */
    suspend fun importiere(context: Context, name: String, oeffnen: () -> InputStream): List<String> = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        try {
            oeffnen().use { ein -> FileOutputStream(temp).use { ein.copyTo(it) } }
            val istPdf = name.endsWith(".pdf", ignoreCase = true) || temp.inputStream().use { ein ->
                val kopf = ByteArray(5)
                ein.read(kopf) == 5 && String(kopf, Charsets.US_ASCII) == "%PDF-"
            }
            if (istPdf) pdfSeiten(context, temp) else listOf(bild(context, temp))
        } finally {
            temp.delete()
        }
    }

    suspend fun importiere(context: Context, uri: Uri): List<String> {
        val name = uri.lastPathSegment ?: ""
        val typ = context.contentResolver.getType(uri) ?: ""
        val anzeigeName = if (typ == "application/pdf" && !name.endsWith(".pdf", true)) "$name.pdf" else name
        return importiere(context, anzeigeName) {
            context.contentResolver.openInputStream(uri) ?: throw IOException("Datei kann nicht gelesen werden")
        }
    }

    private fun speichere(context: Context, bitmap: Bitmap): String {
        val name = "blatt_${UUID.randomUUID()}.jpg"
        FileOutputStream(File(ordner(context), name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return name
    }

    private fun pdfSeiten(context: Context, datei: File): List<String> {
        val namen = ArrayList<String>()
        ParcelFileDescriptor.open(datei, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, MAX_PDF_SEITEN)) {
                    renderer.openPage(i).use { seite ->
                        val massstab = MAX_KANTE.toFloat() / maxOf(seite.width, seite.height)
                        val b = maxOf(1, (seite.width * massstab).toInt())
                        val h = maxOf(1, (seite.height * massstab).toInt())
                        val bitmap = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        seite.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        namen.add(speichere(context, bitmap))
                        bitmap.recycle()
                    }
                }
            }
        }
        if (namen.isEmpty()) throw IOException("Das PDF enthält keine Seiten")
        return namen
    }

    private fun bild(context: Context, datei: File): String {
        val masse = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(datei.absolutePath, masse)
        if (masse.outWidth <= 0) throw IOException("Nur PDF-Dateien und Bilder (JPG, PNG) können geöffnet werden")
        var stichprobe = 1
        while (maxOf(masse.outWidth, masse.outHeight) / (stichprobe * 2) >= MAX_KANTE) stichprobe *= 2
        val roh = BitmapFactory.decodeFile(datei.absolutePath, BitmapFactory.Options().apply { inSampleSize = stichprobe })
            ?: throw IOException("Bild kann nicht gelesen werden")
        // Durchsichtige Stellen (PNG) auf Weiß – JPEG kennt keine Transparenz.
        val weiss = Bitmap.createBitmap(roh.width, roh.height, Bitmap.Config.ARGB_8888)
        weiss.eraseColor(android.graphics.Color.WHITE)
        android.graphics.Canvas(weiss).drawBitmap(roh, 0f, 0f, null)
        roh.recycle()
        return speichere(context, weiss).also { weiss.recycle() }
    }

    /** Löscht Blätter, die keine Seite mehr verwendet. */
    fun aufraeumen(context: Context, benutzt: Set<String>) {
        ordner(context).listFiles()?.forEach { if (it.name !in benutzt) it.delete() }
    }
}

/**
 * Legt die Blätter auf die Tafel: das erste auf die aktuelle Seite, falls sie noch leer ist,
 * alle weiteren auf neue Seiten direkt dahinter (weißer Hintergrund).
 */
fun TafelState.arbeitsblaetterEinfuegen(namen: List<String>) {
    if (namen.isEmpty()) return
    var rest = namen
    if (seite.items.isEmpty() && seite.hintergrundBild.value == null) {
        seite.hintergrundBild.value = namen.first()
        seite.hintergrund.value = HintergrundStil(TafelWeiss)
        rest = namen.drop(1)
    }
    var position = aktiveSeite
    rest.forEach { name ->
        val neu = Seite(HintergrundStil(TafelWeiss))
        neu.hintergrundBild.value = name
        position += 1
        seiten.add(position, neu)
    }
    // Aktuelle Seite schon belegt: zum ersten neuen Blatt springen.
    if (rest.size == namen.size) aktiveSeite += 1
}
