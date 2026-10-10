package de.oejendorferdamm.dammboard.data

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.oejendorferdamm.dammboard.model.BoardItem
import de.oejendorferdamm.dammboard.model.FormItem
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.HintergrundStil
import de.oejendorferdamm.dammboard.model.LaengenEtikett
import de.oejendorferdamm.dammboard.model.MusterTyp
import de.oejendorferdamm.dammboard.model.RadierStelle
import de.oejendorferdamm.dammboard.model.Seite
import de.oejendorferdamm.dammboard.model.SeitenAbbild
import de.oejendorferdamm.dammboard.model.StempelArt
import de.oejendorferdamm.dammboard.model.StempelItem
import de.oejendorferdamm.dammboard.model.StrichItem
import de.oejendorferdamm.dammboard.model.TextItem
import de.oejendorferdamm.dammboard.ui.Arbeitsblaetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Automatische Sicherung der Tafel: alle Seiten mit Inhalt, Hintergrund und Arbeitsblatt landen
 * kurz nach jeder Änderung in einer Datei im App-Ordner. Stürzt die App ab oder wird das Board
 * ausgeschaltet, ist beim nächsten Start alles wieder da. Kompaktes Binärformat, damit auch
 * volle Tafeln in Bruchteilen einer Sekunde geschrieben sind.
 */
object TafelSicherung {
    private const val KENNUNG = 0x44424F41 // "DBOA"
    private const val VERSION = 1

    private const val STRICH: Byte = 1
    private const val FORM: Byte = 2
    private const val ETIKETT: Byte = 3
    private const val TEXT: Byte = 4
    private const val STEMPEL: Byte = 5

    /** Nie zwei Sicherungen gleichzeitig (z. B. nach einer Änderung und beim Ausschalten). */
    private val sperre = Mutex()

    // Obergrenzen beim Lesen: eine beschädigte Datei darf keinen Absturz auslösen.
    private const val MAX_SEITEN = 1000
    private const val MAX_ELEMENTE = 2_000_000
    private const val MAX_PUNKTE = 5_000_000

    private fun datei(context: Context) = File(context.filesDir, "tafel.sicherung")

    fun loeschen(context: Context) {
        datei(context).delete()
    }

    suspend fun speichere(context: Context, seiten: List<SeitenAbbild>, aktiv: Int) = sperre.withLock { schreibe(context, seiten, aktiv) }

    private suspend fun schreibe(context: Context, seiten: List<SeitenAbbild>, aktiv: Int) = withContext(Dispatchers.IO) {
        val ziel = datei(context)
        val temp = File(context.filesDir, "tafel.sicherung.neu")
        DataOutputStream(BufferedOutputStream(FileOutputStream(temp), 64 * 1024)).use { aus -> schreibeInhalt(aus, seiten, aktiv) }
        // Erst vollständig schreiben, dann austauschen: ein Absturz mittendrin zerstört nie die alte Sicherung.
        if (!temp.renameTo(ziel)) {
            ziel.delete()
            temp.renameTo(ziel)
        }
        Arbeitsblaetter.aufraeumen(context, seiten.mapNotNull { it.bild }.toSet())
    }

    /** Die gesicherten Seiten und die zuletzt offene Seite – oder null, wenn nichts (Lesbares) da ist. */
    suspend fun lade(context: Context, neueId: () -> Long): Pair<List<Seite>, Int>? = withContext(Dispatchers.IO) {
        val quelle = datei(context)
        if (!quelle.exists()) return@withContext null
        try {
            DataInputStream(BufferedInputStream(FileInputStream(quelle), 64 * 1024)).use { ein -> liesInhalt(context, ein, neueId) }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Tafelinhalt (alle Seiten) in einen Strom schreiben – auch für Tafel-Dateien im Sync-Ordner. */
    fun schreibeInhalt(aus: DataOutputStream, seiten: List<SeitenAbbild>, aktiv: Int) {
        aus.writeInt(KENNUNG)
        aus.writeInt(VERSION)
        aus.writeInt(aktiv)
        aus.writeInt(seiten.size)
        seiten.forEach { seite ->
            aus.writeInt(seite.hintergrund.farbe.toArgb())
            aus.writeUTF(seite.hintergrund.muster.name)
            aus.writeBoolean(seite.geteilt)
            aus.writeUTF(seite.bild ?: "")
            aus.writeInt(seite.items.size)
            seite.items.forEach { schreibe(aus, it) }
        }
    }

    /** Tafelinhalt lesen; null bei fremden/kaputten Daten. Arbeitsblätter müssen schon im Ordner „blaetter“ liegen. */
    fun liesInhalt(context: Context, ein: DataInputStream, neueId: () -> Long): Pair<List<Seite>, Int>? {
        if (ein.readInt() != KENNUNG || ein.readInt() > VERSION) return null
        val aktiv = ein.readInt()
        val anzahl = ein.readInt()
        if (anzahl !in 0..MAX_SEITEN) return null
        val seiten = ArrayList<Seite>(anzahl)
        repeat(anzahl) {
            val farbe = Color(ein.readInt())
            val musterName = ein.readUTF()
            val muster = MusterTyp.entries.find { it.name == musterName } ?: MusterTyp.KEIN
            val seite = Seite(HintergrundStil(farbe, muster))
            seite.geteilteAnsicht.value = ein.readBoolean()
            val bild = ein.readUTF()
            if (bild.isNotEmpty() && File(Arbeitsblaetter.ordner(context), bild).exists()) seite.hintergrundBild.value = bild
            val itemAnzahl = ein.readInt()
            if (itemAnzahl !in 0..MAX_ELEMENTE) return null
            val items = ArrayList<BoardItem>(itemAnzahl)
            repeat(itemAnzahl) { lies(ein, neueId)?.let(items::add) }
            seite.setzeInhalt(items)
            seiten.add(seite)
        }
        return if (seiten.isEmpty()) null else seiten to aktiv.coerceIn(0, seiten.size - 1)
    }

    private fun DataOutputStream.offset(o: Offset) {
        writeFloat(o.x)
        writeFloat(o.y)
    }

    private fun DataInputStream.offset() = Offset(readFloat(), readFloat())

    private fun schreibe(aus: DataOutputStream, item: BoardItem) {
        when (item) {
            is StrichItem -> {
                aus.writeByte(STRICH.toInt())
                aus.writeInt(item.farbe.toArgb())
                aus.writeFloat(item.breite)
                aus.writeBoolean(item.gestrichelt)
                aus.writeInt(item.punkte.size)
                item.punkte.forEach { aus.offset(it) }
            }
            is FormItem -> {
                aus.writeByte(FORM.toInt())
                aus.writeUTF(item.typ.name)
                aus.offset(item.start)
                aus.offset(item.ende)
                aus.writeInt(item.randFarbe.toArgb())
                aus.writeBoolean(item.fuellFarbe != null)
                aus.writeInt(item.fuellFarbe?.toArgb() ?: 0)
                aus.writeFloat(item.randBreite)
                aus.writeBoolean(item.gestrichelt)
                aus.writeInt(item.radiert.size)
                item.radiert.forEach {
                    aus.offset(it.mitte)
                    aus.writeFloat(it.radius)
                }
            }
            is LaengenEtikett -> {
                aus.writeByte(ETIKETT.toInt())
                aus.offset(item.position)
                aus.writeUTF(item.text)
            }
            is TextItem -> {
                aus.writeByte(TEXT.toInt())
                aus.offset(item.position)
                aus.writeUTF(item.text.take(20000)) // writeUTF fasst höchstens 64 KB
                aus.writeInt(item.farbe.toArgb())
                aus.writeFloat(item.groesse)
                aus.writeFloat(item.breite)
                aus.writeFloat(item.hoehe)
            }
            is StempelItem -> {
                aus.writeByte(STEMPEL.toInt())
                aus.offset(item.mitte)
                aus.writeUTF(item.art.name)
                aus.writeFloat(item.groesse)
                aus.writeInt(item.farbe.toArgb())
            }
        }
    }

    private fun lies(ein: DataInputStream, neueId: () -> Long): BoardItem? {
        return when (ein.readByte()) {
            STRICH -> {
                val farbe = Color(ein.readInt())
                val breite = ein.readFloat()
                val gestrichelt = ein.readBoolean()
                val n = ein.readInt()
                if (n !in 0..MAX_PUNKTE) throw IllegalStateException("Sicherung beschädigt")
                val punkte = List(n) { ein.offset() }
                StrichItem(neueId(), punkte, farbe, breite, gestrichelt)
            }
            FORM -> {
                val typName = ein.readUTF()
                val start = ein.offset()
                val ende = ein.offset()
                val rand = Color(ein.readInt())
                val hatFuellung = ein.readBoolean()
                val fuellung = Color(ein.readInt())
                val randBreite = ein.readFloat()
                val gestrichelt = ein.readBoolean()
                val radiertAnzahl = ein.readInt()
                if (radiertAnzahl !in 0..MAX_PUNKTE) throw IllegalStateException("Sicherung beschädigt")
                val radiert = List(radiertAnzahl) { RadierStelle(ein.offset(), ein.readFloat()) }
                val typ = FormTyp.entries.find { it.name == typName } ?: return null
                FormItem(neueId(), typ, start, ende, rand, if (hatFuellung) fuellung else null, randBreite, gestrichelt, radiert)
            }
            ETIKETT -> LaengenEtikett(neueId(), ein.offset(), ein.readUTF())
            TEXT -> {
                val position = ein.offset()
                val text = ein.readUTF()
                TextItem(neueId(), position, text, Color(ein.readInt()), ein.readFloat(), ein.readFloat(), ein.readFloat())
            }
            STEMPEL -> {
                val mitte = ein.offset()
                val artName = ein.readUTF()
                val groesse = ein.readFloat()
                val farbe = Color(ein.readInt())
                val art = StempelArt.entries.find { it.name == artName } ?: return null
                StempelItem(neueId(), mitte, art, groesse, farbe)
            }
            else -> throw IllegalStateException("Unbekanntes Element in der Sicherung")
        }
    }

}
