package de.oejendorferdamm.dammboard.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import de.oejendorferdamm.dammboard.model.Seite
import de.oejendorferdamm.dammboard.model.SeitenAbbild
import de.oejendorferdamm.dammboard.ui.Arbeitsblaetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Tafeln in einem gemeinsamen Ordner, den der Nutzer einmal auswählt (Storage Access Framework):
 * interner Speicher, USB-Stick, Netzlaufwerk oder der Ordner einer Cloud-App (Nextcloud,
 * Synology Drive …). Den Transport zwischen den Boards übernimmt, was diesen Ordner abgleicht –
 * DammBoard liest und schreibt nur die Dateien. Eine Tafel = eine Datei „Name.dammboard“
 * (ZIP mit allen Seiten und den Arbeitsblättern).
 */
object OrdnerSync {
    const val ENDUNG = ".dammboard"
    private const val INHALT = "tafel.bin"
    private const val MAX_EINTRAG = 80L * 1024 * 1024

    data class TafelDatei(val name: String, val uri: Uri, val geaendert: Long, val groesse: Long)

    /** Zugriff auf den gewählten Ordner dauerhaft behalten (auch nach Neustart). */
    fun berechtigungMerken(context: Context, baum: Uri) {
        context.contentResolver.takePersistableUriPermission(baum, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    fun hatZugriff(context: Context, baum: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == baum && it.isReadPermission && it.isWritePermission }

    /** Anzeigename des Ordners. */
    fun ordnerName(context: Context, baum: Uri): String = try {
        val dok = DocumentsContract.buildDocumentUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum))
        context.contentResolver.query(dok, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: baum.lastPathSegment.orEmpty()
    } catch (_: Exception) {
        baum.lastPathSegment.orEmpty()
    }

    /** Alle Tafeln im Ordner, neueste zuerst. */
    suspend fun liste(context: Context, baum: Uri): List<TafelDatei> = withContext(Dispatchers.IO) {
        val kinder = DocumentsContract.buildChildDocumentsUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum))
        val spalten = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE
        )
        val ergebnis = ArrayList<TafelDatei>()
        context.contentResolver.query(kinder, spalten, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!name.endsWith(ENDUNG, ignoreCase = true)) continue
                ergebnis.add(
                    TafelDatei(
                        name.dropLast(ENDUNG.length), DocumentsContract.buildDocumentUriUsingTree(baum, c.getString(0)),
                        if (c.isNull(2)) 0L else c.getLong(2), if (c.isNull(3)) 0L else c.getLong(3)
                    )
                )
            }
        } ?: throw IOException("Ordner nicht erreichbar – bitte in den Einstellungen neu wählen.")
        ergebnis.sortedByDescending { it.geaendert }
    }

    /** Zeitpunkt der letzten Änderung einer Tafeldatei; null = gibt es nicht mehr. */
    suspend fun stand(context: Context, datei: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(datei, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                if (c.moveToFirst()) (if (c.isNull(0)) 0L else c.getLong(0)) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Erlaubte Tafelnamen: keine Pfadzeichen, nicht leer. */
    fun sichererName(name: String): String = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "-").take(60).ifEmpty { "Tafel" }

    /**
     * Tafel speichern: in [vorhanden] überschreiben oder neu anlegen. Die Datei wird erst komplett im
     * Speicher gebaut und dann in einem Rutsch geschrieben. Gibt Uri und neuen Stand zurück.
     */
    suspend fun speichere(context: Context, baum: Uri, name: String, seiten: List<SeitenAbbild>, aktiv: Int, vorhanden: Uri?): Pair<Uri, Long> = withContext(Dispatchers.IO) {
        val daten = packe(context, seiten, aktiv)
        val ziel = vorhanden?.takeIf { stand(context, it) != null } ?: run {
            val eltern = DocumentsContract.buildDocumentUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum))
            DocumentsContract.createDocument(context.contentResolver, eltern, "application/octet-stream", sichererName(name) + ENDUNG)
                ?: throw IOException("Datei lässt sich im Ordner nicht anlegen")
        }
        context.contentResolver.openOutputStream(ziel, "wt")?.use { it.write(daten) } ?: throw IOException("Datei lässt sich nicht schreiben")
        ziel to (stand(context, ziel) ?: System.currentTimeMillis())
    }

    /** Tafel öffnen: Arbeitsblätter landen im Blätter-Ordner der App, die Seiten kommen zurück. */
    suspend fun oeffne(context: Context, datei: Uri, neueId: () -> Long): Pair<List<Seite>, Int> = withContext(Dispatchers.IO) {
        var inhalt: ByteArray? = null
        context.contentResolver.openInputStream(datei)?.use { roh ->
            ZipInputStream(roh.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    when {
                        e.name == INHALT -> inhalt = lies(zip)
                        e.name.startsWith("blaetter/") -> {
                            val blatt = e.name.removePrefix("blaetter/")
                            // nur einfache Dateinamen – nie aus dem Ordner heraus schreiben
                            if (blatt.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) {
                                val ziel = File(Arbeitsblaetter.ordner(context), blatt)
                                if (!ziel.exists()) {
                                    val tmp = File(ziel.parentFile, "$blatt.tmp")
                                    tmp.writeBytes(lies(zip))
                                    tmp.renameTo(ziel)
                                }
                            }
                        }
                    }
                }
            }
        } ?: throw IOException("Datei lässt sich nicht öffnen")
        val daten = inhalt ?: throw IOException("Das ist keine DammBoard-Tafel.")
        DataInputStream(daten.inputStream()).use { TafelSicherung.liesInhalt(context, it, neueId) }
            ?: throw IOException("Die Tafel ist beschädigt oder von einer neueren DammBoard-Version.")
    }

    suspend fun loesche(context: Context, datei: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, datei)
        } catch (_: Exception) {
            false
        }
    }

    private fun packe(context: Context, seiten: List<SeitenAbbild>, aktiv: Int): ByteArray {
        val aus = ByteArrayOutputStream()
        ZipOutputStream(aus).use { zip ->
            zip.putNextEntry(ZipEntry(INHALT))
            val inhalt = ByteArrayOutputStream()
            DataOutputStream(inhalt).use { TafelSicherung.schreibeInhalt(it, seiten, aktiv) }
            zip.write(inhalt.toByteArray())
            zip.closeEntry()
            seiten.mapNotNull { it.bild }.toSet().forEach { blatt ->
                val f = File(Arbeitsblaetter.ordner(context), blatt)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("blaetter/$blatt"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return aus.toByteArray()
    }

    private fun lies(ein: ZipInputStream): ByteArray {
        val aus = ByteArrayOutputStream()
        val puffer = ByteArray(64 * 1024)
        while (true) {
            val n = ein.read(puffer)
            if (n < 0) break
            aus.write(puffer, 0, n)
            if (aus.size() > MAX_EINTRAG) throw IOException("Eintrag zu groß")
        }
        return aus.toByteArray()
    }
}
