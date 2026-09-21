package de.oejendorferdamm.dammtafel.ui

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
import de.oejendorferdamm.dammtafel.model.FormTyp
import de.oejendorferdamm.dammtafel.model.GeometrieWerkzeug
import de.oejendorferdamm.dammtafel.model.KreidePalette
import de.oejendorferdamm.dammtafel.model.RadiererGroesse
import de.oejendorferdamm.dammtafel.model.Seite
import de.oejendorferdamm.dammtafel.model.StiftArt
import de.oejendorferdamm.dammtafel.model.Werkzeug
import java.util.concurrent.atomic.AtomicLong

private val idZaehler = AtomicLong(0)
fun naechsteId(): Long = idZaehler.incrementAndGet()

enum class AufnahmeZweck { SPEICHERN, TEILEN }

/** Winkel des Geometrie-Führungswerkzeugs (Lineal, Winkeldreieck, Winkelmesser, ...); die Position ist canvasbezogen fix. */
class GeometrieFuehrung {
    var winkelGrad by mutableFloatStateOf(0f)
}

/** Hält den kompletten Bearbeitungszustand der Tafel-App: Seiten, aktives Werkzeug, Panel-Sichtbarkeit. */
class TafelState {
    val seiten: SnapshotStateList<Seite> = mutableStateListOf(Seite())
    var aktiveSeite by mutableIntStateOf(0)
    val seite: Seite get() = seiten[aktiveSeite]

    var werkzeug by mutableStateOf(Werkzeug.STIFT)
    var offenesPanel by mutableStateOf<Werkzeug?>(null)

    // Stift
    var stiftArt by mutableStateOf(StiftArt.FEIN)
    var stiftFarbe by mutableStateOf(KreidePalette[2])
    var stiftBreiteFein by mutableFloatStateOf(6f)
    var stiftBreiteLeucht by mutableFloatStateOf(18f)
    val aktuelleStiftBreite: Float
        get() = if (stiftArt == StiftArt.FEIN) stiftBreiteFein else stiftBreiteLeucht

    // Formen
    var formTyp by mutableStateOf(FormTyp.LINIE)
    var formRandFarbe by mutableStateOf(KreidePalette[2])
    var formFuellFarbe by mutableStateOf<Color?>(null)
    var formRandBreite by mutableFloatStateOf(5f)
    var formTabIndex by mutableIntStateOf(0)

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

    // Lasso/Auswahl-Vorschau während des Ziehens
    var lassoPfad by mutableStateOf<List<Offset>?>(null)
    var auswahlRechteck by mutableStateOf<Pair<Offset, Offset>?>(null)

    fun waehleWerkzeug(neu: Werkzeug) {
        if (neu == Werkzeug.WERKZEUGKASTEN) {
            offenesPanel = if (offenesPanel == neu) null else neu
            return
        }
        werkzeug = neu
        lupeAktiv = false
        offenesPanel = when {
            neu == Werkzeug.LASSO || neu == Werkzeug.AUSWAHL -> null
            offenesPanel == neu -> null
            else -> neu
        }
    }

    fun schliessePanel() {
        offenesPanel = null
    }

    fun neueSeite() {
        seiten.add(Seite())
        aktiveSeite = seiten.lastIndex
    }

    fun vorherigeSeite() {
        if (aktiveSeite > 0) aktiveSeite -= 1
    }

    fun naechsteSeite() {
        if (aktiveSeite < seiten.lastIndex) aktiveSeite += 1
    }
}

@Composable
fun rememberTafelState(): TafelState = remember { TafelState() }
