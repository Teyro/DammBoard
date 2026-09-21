package de.oejendorferdamm.dammboard.model

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Tafelgrün, das Standard-Erscheinungsbild einer klassischen Kreidetafel. */
val TafelGruen = Color(0xFF5E8C6A)
val TafelSchwarz = Color(0xFF262A26)
val TafelWeiss = Color(0xFFF7F5EF)
val TafelGrau = Color(0xFFB9BDB8)

val HintergrundOptionen = listOf(TafelGruen, TafelSchwarz, TafelWeiss, TafelGrau)

/** Musterüberlagerung für den Tafelhintergrund (zusätzlich zur reinen Farbe). */
enum class MusterTyp { KEIN, LINIERT, KARIERT, GEPUNKTET }

data class HintergrundStil(val farbe: Color, val muster: MusterTyp = MusterTyp.KEIN)

/** Die 12 Kreide-/Stiftfarben aus der Werkzeugleiste (4 Spalten x 3 Zeilen). */
val KreidePalette = listOf(
    Color(0xFFFDFCF9), Color(0xFF1A1A1A), Color(0xFFE33B3B), Color(0xFFFBD936),
    Color(0xFFF08A1C), Color(0xFF7A4A28), Color(0xFFB6E13B), Color(0xFF3FAE3A),
    Color(0xFF3BD9E8), Color(0xFF9C3FC9), Color(0xFF5A1E8C), Color(0xFF1E3AA8),
)

enum class Werkzeug {
    STIFT, FORMEN, RADIERER, LASSO, GEOMETRIE, AUSWAHL, WERKZEUGKASTEN
}

enum class StiftArt { FEIN, LEUCHT }

enum class FormTyp {
    DREIECK_RECHTS, DREIECK, KREIS, ELLIPSE, QUADRAT,
    SECHSECK, ABGERUNDET, FUENFECK, STERN, WELLE,
    LINIE, PFEIL, DOPPELPFEIL, FREIHANDPFEIL,
    LINIE_GESTRICHELT, PFEIL_GESTRICHELT, DOPPELPFEIL_GESTRICHELT, FREIHANDPFEIL_GESTRICHELT
}

enum class RadiererGroesse(val radius: Float) { KLEIN(18f), MITTEL(34f), GROSS(56f) }

enum class GeometrieWerkzeug { LINEAL, WINKELDREIECK, WINKELMESSER, RECHTWINKLIG, ZIRKEL, GLEICHSCHENKLIG }

sealed interface BoardItem {
    val id: Long
}

/** Achsenparalleles Begrenzungsrechteck (min, max) eines Elements. */
fun BoardItem.begrenzendesRechteck(): Pair<Offset, Offset> = when (this) {
    is StrichItem -> {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        punkte.forEach { p ->
            minX = min(minX, p.x); minY = min(minY, p.y)
            maxX = max(maxX, p.x); maxY = max(maxY, p.y)
        }
        Offset(minX, minY) to Offset(maxX, maxY)
    }
    is FormItem -> Offset(min(start.x, ende.x), min(start.y, ende.y)) to
        Offset(max(start.x, ende.x), max(start.y, ende.y))
    is LaengenEtikett -> position to position
}

private fun abstandZuStrecke(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x; val aby = b.y - a.y
    val laengeQuadrat = abx * abx + aby * aby
    if (laengeQuadrat == 0f) return hypot(p.x - a.x, p.y - a.y)
    var t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / laengeQuadrat
    t = t.coerceIn(0f, 1f)
    val naechster = Offset(a.x + t * abx, a.y + t * aby)
    return hypot(p.x - naechster.x, p.y - naechster.y)
}

/** Prüft, ob ein Punkt (z. B. der Radierer) dieses Element berührt. */
fun BoardItem.beruehrtVon(punkt: Offset, radius: Float): Boolean = when (this) {
    is StrichItem -> {
        if (punkte.size < 2) {
            punkte.firstOrNull()?.let { hypot(punkt.x - it.x, punkt.y - it.y) <= radius + breite / 2 } ?: false
        } else {
            (0 until punkte.size - 1).any { i -> abstandZuStrecke(punkt, punkte[i], punkte[i + 1]) <= radius + breite / 2 }
        }
    }
    is FormItem -> {
        val (min, max) = begrenzendesRechteck()
        punkt.x >= min.x - radius && punkt.x <= max.x + radius &&
            punkt.y >= min.y - radius && punkt.y <= max.y + radius
    }
    is LaengenEtikett -> hypot(punkt.x - position.x, punkt.y - position.y) <= radius
}

data class StrichItem(
    override val id: Long,
    val punkte: List<Offset>,
    val farbe: Color,
    val breite: Float,
    val gestrichelt: Boolean = false
) : BoardItem

data class FormItem(
    override val id: Long,
    val typ: FormTyp,
    val start: Offset,
    val ende: Offset,
    val randFarbe: Color,
    val fuellFarbe: Color?,
    val randBreite: Float,
    val gestrichelt: Boolean
) : BoardItem

data class LaengenEtikett(
    override val id: Long,
    val position: Offset,
    val text: String
) : BoardItem

/** Eine Seite der Tafel: eigener Inhalt, eigener Hintergrund, eigene Undo/Redo-Historie. */
class Seite(hintergrundStart: HintergrundStil = HintergrundStil(TafelGruen)) {
    val items: SnapshotStateList<BoardItem> = mutableStateListOf()
    val ausgewaehlteIds: SnapshotStateList<Long> = mutableStateListOf()
    val hintergrund = mutableStateOf(hintergrundStart)
    val geteilteAnsicht = mutableStateOf(false)

    private val rueckgaengigStapel = mutableStateListOf<Aktion>()
    private val wiederholenStapel = mutableStateListOf<Aktion>()
    private val ausstehendRadiert = mutableListOf<BoardItem>()

    val kannRueckgaengig get() = rueckgaengigStapel.isNotEmpty()
    val kannWiederholen get() = wiederholenStapel.isNotEmpty()

    private sealed interface Aktion
    private data class Hinzugefuegt(val hinzugefuegteItems: List<BoardItem>) : Aktion
    private data class Entfernt(val entfernteItems: List<BoardItem>) : Aktion

    /** Entfernt sofort alle Elemente unter dem Radierer; die Aktion wird erst bei [radierenAbschliessen] auf den Undo-Stapel gelegt. */
    fun radiereBeruehrte(punkt: Offset, radius: Float) {
        val treffer = items.filter { it.beruehrtVon(punkt, radius) }
        if (treffer.isEmpty()) return
        items.removeAll(treffer)
        ausstehendRadiert.addAll(treffer)
    }

    fun radierenAbschliessen() {
        if (ausstehendRadiert.isEmpty()) return
        rueckgaengigStapel.add(Entfernt(ausstehendRadiert.toList()))
        wiederholenStapel.clear()
        ausstehendRadiert.clear()
    }

    fun hinzufuegen(item: BoardItem) {
        items.add(item)
        rueckgaengigStapel.add(Hinzugefuegt(listOf(item)))
        wiederholenStapel.clear()
    }

    fun allesLoeschen() {
        if (items.isEmpty()) return
        val alle = items.toList()
        items.clear()
        ausgewaehlteIds.clear()
        rueckgaengigStapel.add(Entfernt(alle))
        wiederholenStapel.clear()
    }

    fun entfernenAusgewaehlteOderAlles() {
        val zielItems = if (ausgewaehlteIds.isNotEmpty()) {
            items.filter { it.id in ausgewaehlteIds }
        } else {
            items.toList()
        }
        if (zielItems.isEmpty()) return
        items.removeAll(zielItems)
        ausgewaehlteIds.clear()
        rueckgaengigStapel.add(Entfernt(zielItems))
        wiederholenStapel.clear()
    }

    fun rueckgaengig() {
        val aktion = rueckgaengigStapel.removeLastOrNull() ?: return
        when (aktion) {
            is Hinzugefuegt -> {
                items.removeAll(aktion.hinzugefuegteItems)
                wiederholenStapel.add(aktion)
            }
            is Entfernt -> {
                items.addAll(aktion.entfernteItems)
                wiederholenStapel.add(aktion)
            }
        }
    }

    fun wiederholen() {
        val aktion = wiederholenStapel.removeLastOrNull() ?: return
        when (aktion) {
            is Hinzugefuegt -> {
                items.addAll(aktion.hinzugefuegteItems)
                rueckgaengigStapel.add(aktion)
            }
            is Entfernt -> {
                items.removeAll(aktion.entfernteItems)
                rueckgaengigStapel.add(aktion)
            }
        }
    }

    fun verschiebeAusgewaehlte(delta: Offset) {
        if (ausgewaehlteIds.isEmpty()) return
        for (i in items.indices) {
            val item = items[i]
            if (item.id !in ausgewaehlteIds) continue
            items[i] = when (item) {
                is StrichItem -> item.copy(punkte = item.punkte.map { it + delta })
                is FormItem -> item.copy(start = item.start + delta, ende = item.ende + delta)
                is LaengenEtikett -> item.copy(position = item.position + delta)
            }
        }
    }
}
