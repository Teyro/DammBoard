package de.oejendorferdamm.dammboard.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
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
enum class MusterTyp {
    KEIN, LINIERT, KARIERT, GEPUNKTET, NOTENLINIEN, FUSSBALLFELD, STUNDENPLAN,
    // Grundschule: Schreiblineaturen, Hundertertafel, Zahlenstrahl
    LINEATUR_1, LINEATUR_2, LINEATUR_3, HUNDERTERTAFEL, ZAHLENSTRAHL,
    // Stumme Karten (Umrisse aus assets/karten, siehe ui/canvas/Karten.kt)
    KARTE_DEUTSCHLAND, KARTE_HAMBURG, KARTE_WELT
}

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

/** FEIN = Stift, LEUCHT = Textmarker (halbdurchsichtig). */
enum class StiftArt { FEIN, LEUCHT }

/** Durchsichtigkeit des Textmarkers. */
const val TEXTMARKER_DECKKRAFT = 0.42f

/** Was das Formen-Werkzeug gerade setzt: eine Form, einen Stempel oder ein Textfeld. */
enum class FormModus { FORM, STEMPEL, TEXT }

enum class StempelArt { HAKEN, KREUZ, STERN, HERZ, FRAGE, AUSRUF, DAUMEN, LACHEN, NACHDENKEN, SUPER }

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

private fun grenzenVon(punkte: List<Offset>): Pair<Offset, Offset> {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    punkte.forEach { p ->
        minX = min(minX, p.x); minY = min(minY, p.y)
        maxX = max(maxX, p.x); maxY = max(maxY, p.y)
    }
    return Offset(minX, minY) to Offset(maxX, maxY)
}

/** Achsenparalleles Begrenzungsrechteck (min, max) eines Elements. */
fun BoardItem.begrenzendesRechteck(): Pair<Offset, Offset> = when (this) {
    is StrichItem -> grenzen
    is FormItem -> Offset(min(start.x, ende.x), min(start.y, ende.y)) to
        Offset(max(start.x, ende.x), max(start.y, ende.y))
    is LaengenEtikett -> position to position
    is TextItem -> position to Offset(position.x + breite, position.y + hoehe)
    is StempelItem -> {
        val halbeBreite = if (art == StempelArt.SUPER) groesse else groesse / 2
        Offset(mitte.x - halbeBreite, mitte.y - groesse / 2) to Offset(mitte.x + halbeBreite, mitte.y + groesse / 2)
    }
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
        // Erst das (zwischengespeicherte) Begrenzungsrechteck prüfen: Der Radierer fragt bei
        // jeder Bewegung ALLE Striche ab – ohne diese Vorprüfung würde jeder Punkt jedes
        // Strichs auf der Seite nachgerechnet, was auf vollen Tafeln spürbar ruckelt.
        val reichweite = radius + breite / 2
        val (min, max) = grenzen
        if (punkt.x < min.x - reichweite || punkt.x > max.x + reichweite ||
            punkt.y < min.y - reichweite || punkt.y > max.y + reichweite
        ) {
            false
        } else if (punkte.size < 2) {
            punkte.firstOrNull()?.let { hypot(punkt.x - it.x, punkt.y - it.y) <= reichweite } ?: false
        } else {
            (0 until punkte.size - 1).any { i -> abstandZuStrecke(punkt, punkte[i], punkte[i + 1]) <= reichweite }
        }
    }
    is FormItem -> {
        val (min, max) = begrenzendesRechteck()
        punkt.x >= min.x - radius && punkt.x <= max.x + radius &&
            punkt.y >= min.y - radius && punkt.y <= max.y + radius
    }
    is LaengenEtikett -> hypot(punkt.x - position.x, punkt.y - position.y) <= radius
    is TextItem, is StempelItem -> {
        val (min, max) = begrenzendesRechteck()
        punkt.x >= min.x - radius && punkt.x <= max.x + radius &&
            punkt.y >= min.y - radius && punkt.y <= max.y + radius
    }
}

/** Dasselbe Element um [delta] verschoben. */
fun BoardItem.verschoben(delta: Offset): BoardItem = when (this) {
    is StrichItem -> copy(punkte = punkte.map { it + delta })
    is FormItem -> copy(start = start + delta, ende = ende + delta, radiert = radiert.map { it.copy(mitte = it.mitte + delta) })
    is LaengenEtikett -> copy(position = position + delta)
    is TextItem -> copy(position = position + delta)
    is StempelItem -> copy(mitte = mitte + delta)
}

/** Dasselbe Element mit neuer ID (für Kopieren, Seite duplizieren, Wiederherstellen). */
fun BoardItem.mitId(neueId: Long): BoardItem = when (this) {
    is StrichItem -> copy(id = neueId)
    is FormItem -> copy(id = neueId)
    is LaengenEtikett -> copy(id = neueId)
    is TextItem -> copy(id = neueId)
    is StempelItem -> copy(id = neueId)
}

data class StrichItem(
    override val id: Long,
    val punkte: List<Offset>,
    val farbe: Color,
    val breite: Float,
    val gestrichelt: Boolean = false
) : BoardItem {
    /** Einmal berechnet: Striche ändern sich nie (Verschieben erzeugt per copy() einen neuen).
     *  Steht im Rumpf, damit es nicht zu equals/hashCode/copy zählt. */
    internal val grenzen: Pair<Offset, Offset> by lazy(LazyThreadSafetyMode.NONE) { grenzenVon(punkte) }
}

/**
 * Die Teile dieses Strichs, die außerhalb des Kreises (punkt, radius) liegen – jeweils als
 * eigener Strich. Damit die Schnittkante der Radiererform folgt und nicht zwischen zwei weit
 * auseinanderliegenden Messpunkten "springt", werden Abschnitte in der Nähe des Radierers
 * vorher fein unterteilt.
 */
internal fun StrichItem.ohneBereich(punkt: Offset, radius: Float, neueId: () -> Long): List<StrichItem> {
    val reichweite = radius + breite / 2
    val fein = ArrayList<Offset>(punkte.size + 16)
    val schrittweite = maxOf(1f, radius / 6f)
    for (i in punkte.indices) {
        val p = punkte[i]
        if (i > 0) {
            val a = punkte[i - 1]
            val laenge = hypot(p.x - a.x, p.y - a.y)
            if (laenge > schrittweite && abstandZuStrecke(punkt, a, p) <= reichweite + laenge) {
                val n = kotlin.math.ceil(laenge / schrittweite).toInt()
                for (k in 1 until n) {
                    val t = k.toFloat() / n
                    fein.add(Offset(a.x + (p.x - a.x) * t, a.y + (p.y - a.y) * t))
                }
            }
        }
        fein.add(p)
    }
    val stuecke = mutableListOf<StrichItem>()
    var aktuell = ArrayList<Offset>()
    fun abschliessen() {
        if (aktuell.size >= 2) stuecke.add(StrichItem(neueId(), aktuell, farbe, breite, gestrichelt))
        aktuell = ArrayList()
    }
    for (p in fein) {
        if (hypot(p.x - punkt.x, p.y - punkt.y) <= reichweite) abschliessen() else aktuell.add(p)
    }
    abschliessen()
    return stuecke
}

/** Eine weggewischte Stelle in einer Form: dort wird die Form beim Zeichnen ausgespart. */
data class RadierStelle(val mitte: Offset, val radius: Float)

data class FormItem(
    override val id: Long,
    val typ: FormTyp,
    val start: Offset,
    val ende: Offset,
    val randFarbe: Color,
    val fuellFarbe: Color?,
    val randBreite: Float,
    val gestrichelt: Boolean,
    /** Stückweise weggewischte Stellen (siehe Seite.radiereBeruehrte). */
    val radiert: List<RadierStelle> = emptyList()
) : BoardItem

private val LINIENFORMEN = setOf(
    FormTyp.LINIE, FormTyp.LINIE_GESTRICHELT, FormTyp.PFEIL, FormTyp.PFEIL_GESTRICHELT,
    FormTyp.DOPPELPFEIL, FormTyp.DOPPELPFEIL_GESTRICHELT, FormTyp.FREIHANDPFEIL, FormTyp.FREIHANDPFEIL_GESTRICHELT
)

/**
 * Die Form mit einer zusätzlich weggewischten Stelle – oder null, wenn danach praktisch nichts
 * mehr von ihr zu sehen ist (dann verschwindet sie ganz). Gewischt wird wie mit einem Schwamm:
 * die Form wird beim Zeichnen an diesen Stellen ausgespart (TafelCanvas.zeichneForm).
 */
internal fun FormItem.mitRadierStelle(punkt: Offset, radius: Float, neueId: () -> Long): FormItem? {
    // Schon (fast) genau hier gewischt: nichts Neues – spart Arbeit beim Zeichnen.
    if (radiert.any { hypot(it.mitte.x - punkt.x, it.mitte.y - punkt.y) < it.radius * 0.2f && it.radius >= radius * 0.9f }) return this
    val stellen = radiert + RadierStelle(punkt, radius)
    fun bedeckt(p: Offset) = stellen.any { hypot(p.x - it.mitte.x, p.y - it.mitte.y) <= it.radius }
    val probe = if (typ in LINIENFORMEN) {
        (0..20).map { i -> Offset(start.x + (ende.x - start.x) * i / 20f, start.y + (ende.y - start.y) * i / 20f) }
    } else {
        // Raster über die Form; ohne Füllung zählt nur der Randbereich.
        val (a, b) = begrenzendesRechteck()
        val w = b.x - a.x
        val h = b.y - a.y
        (0..8).flatMap { ix -> (0..8).map { iy -> Offset(a.x + w * ix / 8f, a.y + h * iy / 8f) to (ix in 1..7 && iy in 1..7) } }
            .filter { (_, innen) -> fuellFarbe != null || !innen }
            .map { it.first }
    }
    if (probe.all(::bedeckt)) return null
    return copy(id = neueId(), radiert = stellen)
}

data class LaengenEtikett(
    override val id: Long,
    val position: Offset,
    val text: String
) : BoardItem

/** Textfeld (über die Tastatur geschrieben). [position] = linke obere Ecke, Maße beim Anlegen gemessen. */
data class TextItem(
    override val id: Long,
    val position: Offset,
    val text: String,
    val farbe: Color,
    val groesse: Float,
    val breite: Float,
    val hoehe: Float
) : BoardItem

data class StempelItem(
    override val id: Long,
    val mitte: Offset,
    val art: StempelArt,
    val groesse: Float,
    val farbe: Color
) : BoardItem

/** Eine Seite der Tafel: eigener Inhalt, eigener Hintergrund, eigene Undo/Redo-Historie. */
class Seite(hintergrundStart: HintergrundStil = HintergrundStil(TafelGruen)) {
    val items: SnapshotStateList<BoardItem> = mutableStateListOf()
    val ausgewaehlteIds: SnapshotStateList<Long> = mutableStateListOf()
    val hintergrund = mutableStateOf(hintergrundStart)
    val geteilteAnsicht = mutableStateOf(false)

    /** Geöffnetes Arbeitsblatt (Dateiname im Ordner "blaetter", siehe ui/Arbeitsblatt.kt) oder null. */
    val hintergrundBild = mutableStateOf<String?>(null)

    /**
     * Zählt jede inhaltliche Änderung an [items]. Die Zeichenfläche nutzt das, um fertige
     * Striche/Formen in einer Ebene zwischenzuspeichern, statt sie bei jedem Zeichen-Frame neu
     * zu rendern (siehe TafelCanvas.kt) – wichtig für die Performance bei vielen Strichen.
     */
    var versionsZaehler by mutableIntStateOf(0)
        private set

    private val rueckgaengigStapel = mutableStateListOf<Aktion>()
    private val wiederholenStapel = mutableStateListOf<Aktion>()
    // Laufende Radier-Geste: welche ursprünglichen Elemente sie entfernt hat und welche
    // Reststücke (aufgeschnittene Striche) sie neu erzeugt hat – für EINEN Rückgängig-Schritt.
    private val ausstehendEntfernt = mutableListOf<BoardItem>()
    private val ausstehendNeu = LinkedHashMap<Long, BoardItem>()

    val kannRueckgaengig get() = rueckgaengigStapel.isNotEmpty()
    val kannWiederholen get() = wiederholenStapel.isNotEmpty()

    private sealed interface Aktion
    private data class Hinzugefuegt(val hinzugefuegteItems: List<BoardItem>) : Aktion
    private data class Entfernt(val entfernteItems: List<BoardItem>) : Aktion
    private data class Verschoben(val ids: List<Long>, val delta: Offset) : Aktion
    private data class Ersetzt(val entfernteItems: List<BoardItem>, val neueItems: List<BoardItem>) : Aktion

    /**
     * Radiert wie ein Schwamm: Freihand-Striche werden nur dort entfernt, wo der Radierer sie
     * berührt – der Rest des Strichs bleibt als eigene Stücke stehen. Formen und Beschriftungen
     * verschwinden weiterhin als Ganzes. Die Aktion kommt erst bei [radierenAbschliessen] (für
     * die ganze Geste als EIN Schritt) auf den Undo-Stapel.
     */
    fun radiereBeruehrte(punkt: Offset, radius: Float, neueId: () -> Long, stueckweise: Boolean = true) {
        val treffer = items.filter { it.beruehrtVon(punkt, radius) }
        if (treffer.isEmpty()) return
        val ersatz = HashMap<Long, List<BoardItem>>(treffer.size * 2)
        for (item in treffer) {
            ersatz[item.id] = when {
                !stueckweise -> emptyList() // Einstellung "Ganze Linie oder Form"
                item is StrichItem -> item.ohneBereich(punkt, radius, neueId)
                item is FormItem -> {
                    val neu = item.mitRadierStelle(punkt, radius, neueId)
                    if (neu === item) continue // nichts geändert
                    listOfNotNull(neu)
                }
                else -> emptyList()
            }
        }
        if (ersatz.isEmpty()) return
        val neu = ArrayList<BoardItem>(items.size + treffer.size)
        for (item in items) {
            val stuecke = ersatz[item.id]
            if (stuecke == null) {
                neu.add(item)
                continue
            }
            // An derselben Stelle der Liste einsetzen: die Stücke bleiben so über/unter dem, was
            // der Strich vorher überdeckt hat.
            neu.addAll(stuecke)
            if (ausstehendNeu.remove(item.id) == null) ausstehendEntfernt.add(item)
            stuecke.forEach { ausstehendNeu[it.id] = it }
        }
        items.clear()
        items.addAll(neu)
        versionsZaehler++
    }

    /** Wie [radiereBeruehrte], aber lückenlos entlang einer Strecke (für schnelles Wischen). */
    fun radiereStrecke(von: Offset, bis: Offset, radius: Float, neueId: () -> Long, stueckweise: Boolean = true) {
        val schritte = maxOf(1, kotlin.math.ceil(hypot(bis.x - von.x, bis.y - von.y) / (radius * 0.5f)).toInt())
        for (s in 1..schritte) {
            val t = s.toFloat() / schritte
            radiereBeruehrte(Offset(von.x + (bis.x - von.x) * t, von.y + (bis.y - von.y) * t), radius, neueId, stueckweise)
        }
    }

    fun radierenAbschliessen() {
        if (ausstehendEntfernt.isEmpty() && ausstehendNeu.isEmpty()) return
        rueckgaengigStapel.add(Ersetzt(ausstehendEntfernt.toList(), ausstehendNeu.values.toList()))
        wiederholenStapel.clear()
        ausstehendEntfernt.clear()
        ausstehendNeu.clear()
    }

    fun hinzufuegen(item: BoardItem) {
        items.add(item)
        rueckgaengigStapel.add(Hinzugefuegt(listOf(item)))
        wiederholenStapel.clear()
        versionsZaehler++
    }

    /** Mehrere Elemente auf einmal – ein Rückgängig-Schritt (Einfügen, Duplizieren). */
    fun hinzufuegenAlle(neu: List<BoardItem>) {
        if (neu.isEmpty()) return
        items.addAll(neu)
        rueckgaengigStapel.add(Hinzugefuegt(neu))
        wiederholenStapel.clear()
        versionsZaehler++
    }

    /** Ersetzt ein Element an seiner Stelle (z. B. ein bearbeitetes Textfeld) – rückgängig machbar. */
    fun ersetze(alt: BoardItem, neu: BoardItem) {
        val index = items.indexOfFirst { it.id == alt.id }
        if (index < 0) return
        items[index] = neu
        rueckgaengigStapel.add(Ersetzt(listOf(alt), listOf(neu)))
        wiederholenStapel.clear()
        versionsZaehler++
    }

    /** Inhalt ohne Rückgängig-Schritt setzen – nur beim Wiederherstellen/Duplizieren einer Seite. */
    fun setzeInhalt(neu: List<BoardItem>) {
        items.clear()
        items.addAll(neu)
        versionsZaehler++
    }

    /** Gemeinsames Begrenzungsrechteck der ausgewählten Elemente oder null. */
    fun auswahlGrenzen(): Pair<Offset, Offset>? {
        if (ausgewaehlteIds.isEmpty()) return null
        val ids = ausgewaehlteIds.toHashSet()
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var gefunden = false
        items.forEach { item ->
            if (item.id in ids) {
                gefunden = true
                val (a, b) = item.begrenzendesRechteck()
                minX = min(minX, a.x); minY = min(minY, a.y)
                maxX = max(maxX, b.x); maxY = max(maxY, b.y)
            }
        }
        return if (gefunden) Offset(minX, minY) to Offset(maxX, maxY) else null
    }

    /** Eigenständige Kopie (neue IDs, eigene Historie) – für "Seite duplizieren". */
    fun kopie(neueId: () -> Long): Seite {
        val neu = Seite(hintergrund.value)
        neu.geteilteAnsicht.value = geteilteAnsicht.value
        neu.hintergrundBild.value = hintergrundBild.value
        neu.setzeInhalt(items.map { it.mitId(neueId()) })
        return neu
    }

    fun allesLoeschen() {
        if (items.isEmpty()) return
        val alle = items.toList()
        items.clear()
        ausgewaehlteIds.clear()
        rueckgaengigStapel.add(Entfernt(alle))
        wiederholenStapel.clear()
        versionsZaehler++
    }

    fun entfernenAusgewaehlteOderAlles() {
        val zielItems = if (ausgewaehlteIds.isNotEmpty()) {
            val ids = ausgewaehlteIds.toHashSet()
            items.filter { it.id in ids }
        } else {
            items.toList()
        }
        if (zielItems.isEmpty()) return
        entferne(zielItems)
        ausgewaehlteIds.clear()
        rueckgaengigStapel.add(Entfernt(zielItems))
        wiederholenStapel.clear()
        versionsZaehler++
    }

    fun rueckgaengig() {
        val aktion = rueckgaengigStapel.removeLastOrNull() ?: return
        when (aktion) {
            is Hinzugefuegt -> {
                entferne(aktion.hinzugefuegteItems)
                wiederholenStapel.add(aktion)
            }
            is Entfernt -> {
                items.addAll(aktion.entfernteItems)
                wiederholenStapel.add(aktion)
            }
            is Verschoben -> {
                verschiebeItems(aktion.ids, -aktion.delta)
                wiederholenStapel.add(aktion)
            }
            is Ersetzt -> {
                entferne(aktion.neueItems)
                items.addAll(aktion.entfernteItems)
                wiederholenStapel.add(aktion)
            }
        }
        versionsZaehler++
    }

    fun wiederholen() {
        val aktion = wiederholenStapel.removeLastOrNull() ?: return
        when (aktion) {
            is Hinzugefuegt -> {
                items.addAll(aktion.hinzugefuegteItems)
                rueckgaengigStapel.add(aktion)
            }
            is Entfernt -> {
                entferne(aktion.entfernteItems)
                rueckgaengigStapel.add(aktion)
            }
            is Verschoben -> {
                verschiebeItems(aktion.ids, aktion.delta)
                rueckgaengigStapel.add(aktion)
            }
            is Ersetzt -> {
                entferne(aktion.entfernteItems)
                items.addAll(aktion.neueItems)
                rueckgaengigStapel.add(aktion)
            }
        }
        versionsZaehler++
    }

    /** Entfernt [weg] in EINEM Durchgang über die IDs – removeAll(Liste) würde jedes Element
     *  mit jedem vergleichen, was z. B. beim Wiederholen von "Alles löschen" quadratisch wird. */
    private fun entferne(weg: List<BoardItem>) {
        if (weg.isEmpty()) return
        val ids = weg.mapTo(HashSet(weg.size * 2)) { it.id }
        val rest = items.filterNot { it.id in ids }
        if (rest.size == items.size) return
        items.clear()
        items.addAll(rest)
    }

    private fun verschiebeItems(idListe: List<Long>, delta: Offset) {
        val ids = idListe.toHashSet()
        for (i in items.indices) {
            val item = items[i]
            if (item.id !in ids) continue
            items[i] = item.verschoben(delta)
        }
    }

    /** Bewegt die ausgewählten Elemente sofort sichtbar – wird während einer laufenden Ziehgeste
     *  bei jedem Bewegungsschritt aufgerufen. Für die Undo-Historie zählt erst der gesamte Weg
     *  der ganzen Geste, siehe [protokolliereVerschiebung] – sonst würde jeder einzelne
     *  Bewegungsschritt einen eigenen Rückgängig-Schritt erzeugen. */
    fun verschiebeAusgewaehlte(delta: Offset) {
        if (ausgewaehlteIds.isEmpty()) return
        verschiebeItems(ausgewaehlteIds.toList(), delta)
        versionsZaehler++
    }

    /** Trägt eine abgeschlossene Verschiebung (Summe aller Einzelschritte einer Ziehgeste) als
     *  EINEN Rückgängig-Schritt ein. Verschiebt dabei selbst nichts mehr – das ist während der
     *  Geste bereits über [verschiebeAusgewaehlte] passiert. */
    fun protokolliereVerschiebung(ids: List<Long>, gesamtDelta: Offset) {
        if (ids.isEmpty() || gesamtDelta == Offset.Zero) return
        rueckgaengigStapel.add(Verschoben(ids, gesamtDelta))
        wiederholenStapel.clear()
    }
}

/**
 * Unveränderlicher Schnappschuss einer Seite – zum Zeichnen außerhalb des UI-Threads (PDF,
 * Vorschaubilder, automatische Sicherung), ohne dass die Tafel dabei gesperrt werden muss.
 */
data class SeitenAbbild(
    val items: List<BoardItem>,
    val hintergrund: HintergrundStil,
    val geteilt: Boolean,
    val bild: String?
)

fun Seite.abbild(): SeitenAbbild = SeitenAbbild(items.toList(), hintergrund.value, geteilteAnsicht.value, hintergrundBild.value)
