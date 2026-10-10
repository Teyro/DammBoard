package de.oejendorferdamm.dammboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import de.oejendorferdamm.dammboard.model.BoardItem
import de.oejendorferdamm.dammboard.model.FormModus
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.GeometrieWerkzeug
import de.oejendorferdamm.dammboard.model.HintergrundStil
import de.oejendorferdamm.dammboard.model.KreidePalette
import de.oejendorferdamm.dammboard.model.LaengenEtikett
import de.oejendorferdamm.dammboard.model.RadiererGroesse
import de.oejendorferdamm.dammboard.model.Seite
import de.oejendorferdamm.dammboard.model.StempelArt
import de.oejendorferdamm.dammboard.model.StiftArt
import de.oejendorferdamm.dammboard.model.TextItem
import de.oejendorferdamm.dammboard.model.TafelGruen
import de.oejendorferdamm.dammboard.model.Werkzeug
import de.oejendorferdamm.dammboard.model.mitId
import de.oejendorferdamm.dammboard.model.verschoben
import java.util.concurrent.atomic.AtomicLong

private val idZaehler = AtomicLong(0)
fun naechsteId(): Long = idZaehler.incrementAndGet()

enum class AufnahmeZweck { SPEICHERN, TEILEN, ISERV }

/** Kleine Helfer, die über der Tafel schweben (siehe ui/helfer). */
enum class HelferArt { TIMER, WUERFEL, ZUFALLSNAME, GRUPPEN, LAUTSTAERKE, LERNUHR }

/**
 * Die Tafel ist mit einer Datei im Sync-Ordner verbunden: Änderungen werden dorthin zurückgeschrieben,
 * Änderungen von anderen Boards werden geladen. [stand] = Änderungszeit der Datei beim letzten
 * Laden/Speichern, [signatur] = Inhaltsstand der Tafel zu diesem Zeitpunkt.
 */
data class VerbundeneTafel(val name: String, val uri: String, val stand: Long, val signatur: Any?)

/** Ein offenes Textfeld-Eingabefenster: neues Textfeld an [position] oder Bearbeiten von [vorhanden]. */
data class TextEingabe(val position: Offset, val vorhanden: TextItem? = null)

/** Winkel des Geometrie-Führungswerkzeugs (Lineal, Winkeldreieck, Winkelmesser, ...); die Position ist canvasbezogen fix. */
class GeometrieFuehrung {
    var winkelGrad by mutableFloatStateOf(0f)
}

/** Hält den kompletten Bearbeitungszustand der Tafel-App: Seiten, aktives Werkzeug, Panel-Sichtbarkeit. */
class TafelState(hintergrundStart: HintergrundStil = HintergrundStil(TafelGruen)) {
    val seiten: SnapshotStateList<Seite> = mutableStateListOf(Seite(hintergrundStart))

    /** Hintergrund, mit dem neue Seiten beginnen – standardmäßig das bekannte Tafelgrün, bei
     *  aktivierter Option „Letzten Hintergrund merken" der zuletzt verwendete (setzt AppWurzel). */
    var neueSeitenHintergrund: HintergrundStil = hintergrundStart
    var aktiveSeite by mutableIntStateOf(0)

    /** Emoji-Überraschung sofort zeigen (Einstellungen → „Jetzt ausprobieren“). */
    var ueberraschungJetzt by mutableStateOf(false)

    /** Verbindung zu einer Tafeldatei im Sync-Ordner (siehe data/OrdnerSync.kt), null = keine. */
    var verbundeneTafel by mutableStateOf<VerbundeneTafel?>(null)

    /** Inhaltsstand für den Abgleich (ohne die gerade offene Seite – Blättern ist keine Änderung). */
    fun syncSignatur(): Any = seiten.map { listOf(it.versionsZaehler, it.hintergrund.value, it.hintergrundBild.value, it.geteilteAnsicht.value) }
    val seite: Seite get() = seiten[aktiveSeite]

    var werkzeug by mutableStateOf(Werkzeug.STIFT)
    var offenesPanel by mutableStateOf<Werkzeug?>(null)

    // Stift – Standard: Weiß auf dem bekannten grünen Tafelhintergrund, etwas dickere Linie,
    // damit man beim App-Start sofort gut lesbar schreiben kann.
    var stiftArt by mutableStateOf(StiftArt.FEIN)
    var stiftFarbe by mutableStateOf(KreidePalette[0])
    var stiftBreiteFein by mutableFloatStateOf(8f)
    var stiftBreiteLeucht by mutableFloatStateOf(28f)
    val aktuelleStiftBreite: Float
        get() = if (stiftArt == StiftArt.FEIN) stiftBreiteFein else stiftBreiteLeucht

    /** Geteilte Tafel: die rechte Hälfte hat eine eigene Stiftfarbe (zwei Kinder gleichzeitig). */
    var stiftFarbeRechts by mutableStateOf(KreidePalette[3])
    /** Welche Hälfte zuletzt beschrieben wurde – deren Farbe zeigt und ändert das Stift-Panel. */
    var aktiveHaelfte by mutableIntStateOf(0)
    var formErkennung by mutableStateOf(true)

    // Formen
    var formTyp by mutableStateOf(FormTyp.LINIE)
    var formRandFarbe by mutableStateOf(KreidePalette[2])
    var formFuellFarbe by mutableStateOf<Color?>(null)
    var formRandBreite by mutableFloatStateOf(5f)
    var formTabIndex by mutableIntStateOf(0)

    // Stempel & Textfelder (Formen-Panel, Reiter "Stempel")
    var formModus by mutableStateOf(FormModus.FORM)
    var stempelArt by mutableStateOf(StempelArt.HAKEN)
    var stempelFarbe by mutableStateOf(KreidePalette[0])
    var stempelGroesse by mutableFloatStateOf(110f)
    var textGroesse by mutableFloatStateOf(64f)
    var textEingabe by mutableStateOf<TextEingabe?>(null)

    // Radierer
    var radiererGroesse by mutableStateOf(RadiererGroesse.MITTEL)

    // Geometrie
    var geometrieWerkzeug by mutableStateOf(GeometrieWerkzeug.LINEAL)
    var geometrieGestrichelt by mutableStateOf(false)
    var zeigeLaenge by mutableStateOf(false)
    val geometrieFuehrung = GeometrieFuehrung()

    // Werkzeugkasten
    var lupeAktiv by mutableStateOf(false)
    var lupePosition by mutableStateOf<Offset?>(null)
    var aufnahmeAnfrage by mutableStateOf<AufnahmeZweck?>(null)

    // Kopieren/Einfügen (Lasso und Auswahl)
    var zwischenablage by mutableStateOf<List<BoardItem>>(emptyList())
    var auswahlGesteLaeuft by mutableStateOf(false)
    var zwischenablageQuelle: Seite? = null

    // Abdecken: null = aus, sonst sichtbarer Anteil von oben (0 = alles verdeckt)
    var vorhang by mutableStateOf<Float?>(null)

    val offeneHelfer: SnapshotStateList<HelferArt> = mutableStateListOf()
    var zeigeSeitenUebersicht by mutableStateOf(false)

    /** Größe der Zeichenfläche in Pixeln (für PDF, Vorschaubilder, Arbeitsblätter). */
    var brettBreite by mutableIntStateOf(1920)
    var brettHoehe by mutableIntStateOf(1080)

    // Lasso/Auswahl-Vorschau während des Ziehens
    var lassoPfad by mutableStateOf<List<Offset>?>(null)
    var auswahlRechteck by mutableStateOf<Pair<Offset, Offset>?>(null)

    fun waehleWerkzeug(neu: Werkzeug) {
        if (neu == Werkzeug.WERKZEUGKASTEN) {
            offenesPanel = if (offenesPanel == neu) null else neu
            return
        }
        // Eine Auswahl gehört zum Lasso-/Auswahl-Werkzeug – bei jedem echten Werkzeugwechsel
        // verschwindet sie, sonst bleibt sie sonst unsichtbar "hängen" und verwirrt beim nächsten
        // Markieren.
        if (neu != werkzeug) {
            seite.ausgewaehlteIds.clear()
        }
        werkzeug = neu
        lupeAktiv = false
        offenesPanel = when {
            neu == Werkzeug.LASSO || neu == Werkzeug.AUSWAHL -> null
            offenesPanel == neu -> null
            else -> neu
        }
    }

    /** Wann zuletzt ein offenes Panel geschlossen wurde – ein Tipp, der nur ein Panel schließt, soll keinen Punkt malen. */
    var panelGeschlossenUm = 0L
        private set

    fun schliessePanel() {
        if (offenesPanel != null) panelGeschlossenUm = android.os.SystemClock.uptimeMillis()
        offenesPanel = null
    }

    fun neueSeite() {
        seiten.add(Seite(neueSeitenHintergrund))
        aktiveSeite = seiten.lastIndex
    }

    fun geheZuSeite(index: Int) {
        if (index !in seiten.indices || index == aktiveSeite) return
        seite.ausgewaehlteIds.clear()
        aktiveSeite = index
    }

    fun seiteDuplizieren(index: Int) {
        val quelle = seiten.getOrNull(index) ?: return
        seiten.add(index + 1, quelle.kopie(::naechsteId))
        aktiveSeite = index + 1
    }

    fun seiteLoeschen(index: Int) {
        if (index !in seiten.indices) return
        if (seiten.size == 1) {
            seiten[0] = Seite(neueSeitenHintergrund)
            aktiveSeite = 0
            return
        }
        seiten.removeAt(index)
        if (aktiveSeite >= seiten.size || aktiveSeite > index) aktiveSeite = (aktiveSeite - 1).coerceAtLeast(0)
    }

    fun seiteVerschieben(von: Int, nach: Int) {
        if (von !in seiten.indices || nach !in seiten.indices || von == nach) return
        val aktiv = seiten[aktiveSeite]
        val s = seiten.removeAt(von)
        seiten.add(nach, s)
        aktiveSeite = seiten.indexOf(aktiv)
    }

    /** Alles weg, eine leere Seite – wie eine frisch geputzte Tafel. */
    fun neueTafel() {
        seiten.clear()
        seiten.add(Seite(neueSeitenHintergrund))
        aktiveSeite = 0
    }

    /** Nach dem Wiederherstellen der automatischen Sicherung. */
    fun seitenErsetzen(neu: List<Seite>, aktiv: Int) {
        if (neu.isEmpty()) return
        seiten.clear()
        seiten.addAll(neu)
        aktiveSeite = aktiv.coerceIn(0, seiten.lastIndex)
    }

    /** Fügt die Zwischenablage ein (auf derselben Seite leicht versetzt) und wählt das Eingefügte aus. */
    fun einfuegen(versatz: Offset) {
        if (zwischenablage.isEmpty()) return
        // Auf einer anderen Seite an derselben Stelle, auf derselben Seite leicht versetzt.
        val wirksam = if (zwischenablageQuelle === seite) versatz else Offset.Zero
        val neu = zwischenablage.map { it.verschoben(wirksam).mitId(naechsteId()) }
        seite.hinzufuegenAlle(neu)
        seite.ausgewaehlteIds.clear()
        seite.ausgewaehlteIds.addAll(neu.map { it.id })
    }

    fun kopiereAuswahl() {
        val ids = seite.ausgewaehlteIds.toHashSet()
        zwischenablage = seite.items.filter { it.id in ids }
        zwischenablageQuelle = seite
    }

    /** Kopie der Auswahl direkt daneben – ohne die Zwischenablage zu verändern. */
    fun dupliziereAuswahl(versatz: Offset) {
        val ids = seite.ausgewaehlteIds.toHashSet()
        val neu = seite.items.filter { it.id in ids }.map { it.verschoben(versatz).mitId(naechsteId()) }
        if (neu.isEmpty()) return
        seite.hinzufuegenAlle(neu)
        seite.ausgewaehlteIds.clear()
        seite.ausgewaehlteIds.addAll(neu.map { it.id })
    }

    fun vorherigeSeite() {
        if (aktiveSeite > 0) aktiveSeite -= 1
    }

    fun naechsteSeite() {
        if (aktiveSeite < seiten.lastIndex) aktiveSeite += 1
    }

    /** Kleines Easter Egg für aufmerksame Kolleg:innen: 5x schnell hintereinander auf den
     *  Stift getippt legt eine neue Seite mit dem Namens-Wortspiel an. Ganz normale Seite
     *  danach – über den Papierkorb oder Rückgängig genauso wieder loszuwerden wie alles andere. */
    fun loeseStiftEasterEggAus() {
        neueSeite()
        seite.hinzufuegen(LaengenEtikett(naechsteId(), Offset(420f, 340f), "DammBoard"))
        seite.hinzufuegen(LaengenEtikett(naechsteId(), Offset(380f, 420f), "😭 💀 😔 😢 😞"))
    }
}

@Composable
fun rememberTafelState(hintergrundStart: HintergrundStil = HintergrundStil(TafelGruen)): TafelState =
    remember { TafelState(hintergrundStart) }
