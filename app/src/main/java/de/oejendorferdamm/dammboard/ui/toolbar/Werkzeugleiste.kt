package de.oejendorferdamm.dammboard.ui.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.Werkzeug
import de.oejendorferdamm.dammboard.ui.TafelState
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import de.oejendorferdamm.dammboard.ui.icons.WerkzeugSymbol
import kotlin.math.roundToInt

/*
 * Untere Leiste wie in der Original-Tafel-App: über die ganze Bildschirmbreite verteilt,
 *   links:  Beenden, Menü, Teilen
 *   Mitte:  die sieben Werkzeuge (Stift … Werkzeugkasten)
 *   rechts: Papierkorb, Rückgängig, Wiederholen und die Seiten-Pille (+  <  1/1  >)
 * Das Panel eines Werkzeugs erscheint direkt über dessen Knopf, mit einem kleinen Zeiger.
 * Alle Maße in Vorbild-Pixeln (siehe ui/Skalierung.kt), ausgemessen am Original.
 */

private val RandAbstand = 9.dp
private val GruppenMindestabstand = 30.dp
private val PillenBreite = 350.dp
private val PillenHoehe = 70.dp
private val PanelLuecke = 8.dp

/** Mitte der Werkzeuggruppe im Original: 922 von 1920 Pixeln. */
private const val WERKZEUG_MITTE_ANTEIL = 922f / 1920f

private val Werkzeugreihe = listOf(
    Werkzeug.STIFT, Werkzeug.FORMEN, Werkzeug.RADIERER, Werkzeug.LASSO,
    Werkzeug.GEOMETRIE, Werkzeug.AUSWAHL, Werkzeug.WERKZEUGKASTEN
)

private const val ID_LINKS = "links"
private const val ID_WERKZEUGE = "werkzeuge"
private const val ID_RECHTS = "rechts"
private const val ID_PANEL = "panel"
private const val ID_ZEIGER = "zeiger"

internal fun werkzeugBeschreibung(werkzeug: Werkzeug): String = when (werkzeug) {
    Werkzeug.STIFT -> "Stift"
    Werkzeug.FORMEN -> "Formen"
    Werkzeug.RADIERER -> "Radierer"
    Werkzeug.LASSO -> "Lasso"
    Werkzeug.GEOMETRIE -> "Geometrie"
    Werkzeug.AUSWAHL -> "Auswahl"
    Werkzeug.WERKZEUGKASTEN -> "Werkzeugkasten"
}

/** Merkt sich das zuletzt offene Panel, damit es beim Ausblenden nicht schon vorher leer wird. */
private class PanelMerker {
    var zuletzt: Werkzeug? = null
}

/** Zählt schnelle Tipper auf den Stift (kleines Easter Egg, siehe TafelState). */
private class StiftTipper {
    var anzahl = 0
    var letzter = 0L
}

/** Komplette Bedienung über der Tafel: untere Leiste plus das Panel des offenen Werkzeugs. */
@Composable
fun TafelBedienung(
    state: TafelState,
    animationsModus: AnimationsModus,
    zeigeUpdatePunkt: Boolean,
    onSchliessen: () -> Unit,
    onMenu: () -> Unit,
    onTeilen: () -> Unit,
    onIServ: () -> Unit,
    modifier: Modifier = Modifier
) {
    val merker = remember { PanelMerker() }
    val offen = state.offenesPanel?.takeIf { hatPanel(it) }
    if (offen != null) merker.zuletzt = offen
    val anzuzeigen = offen ?: merker.zuletzt
    val animiert = animationsModus == AnimationsModus.NORMAL

    Layout(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        content = {
            LinkeGruppe(zeigeUpdatePunkt, onSchliessen, onMenu, onTeilen, Modifier.layoutId(ID_LINKS))
            WerkzeugGruppe(state, Modifier.layoutId(ID_WERKZEUGE))
            RechteGruppe(state, Modifier.layoutId(ID_RECHTS))
            PanelEinblendung(sichtbar = offen != null, animiert = animiert, modifier = Modifier.layoutId(ID_PANEL)) {
                if (anzuzeigen != null) {
                    PanelRahmen { PanelInhalt(anzuzeigen, state, onIServ) }
                }
            }
            PanelEinblendung(sichtbar = offen != null, animiert = animiert, modifier = Modifier.layoutId(ID_ZEIGER)) {
                PanelZeiger()
            }
        }
    ) { messbare, constraints ->
        val locker = constraints.copy(minWidth = 0, minHeight = 0)
        // Panel und Zeiger fehlen ganz, solange nichts eingeblendet ist – deshalb über die
        // layoutId suchen statt über feste Positionen in der Liste.
        val links = messbare.firstOrNull { it.layoutId == ID_LINKS }?.measure(locker)
        val werkzeuge = messbare.firstOrNull { it.layoutId == ID_WERKZEUGE }?.measure(locker)
        val rechts = messbare.firstOrNull { it.layoutId == ID_RECHTS }?.measure(locker)
        val panel = messbare.firstOrNull { it.layoutId == ID_PANEL }?.measure(locker)
        val zeiger = messbare.firstOrNull { it.layoutId == ID_ZEIGER }?.measure(locker)

        val breite = constraints.maxWidth
        val hoehe = constraints.maxHeight
        val rand = RandAbstand.roundToPx()
        val mindestabstand = GruppenMindestabstand.roundToPx()

        val linksBreite = links?.width ?: 0
        val rechtsBreite = rechts?.width ?: 0
        val werkzeugBreite = werkzeuge?.width ?: 0
        val leistenHoehe = maxOf(links?.height ?: 0, werkzeuge?.height ?: 0, rechts?.height ?: 0)
        val leistenMitte = hoehe - rand - leistenHoehe / 2

        val linksX = rand
        val rechtsX = breite - rand - rechtsBreite
        // Werkzeuge möglichst an derselben Stelle wie im Original, aber nie über den Gruppen links/rechts.
        val fruehestens = linksX + linksBreite + mindestabstand
        val spaetestens = rechtsX - mindestabstand - werkzeugBreite
        val wunsch = (breite * WERKZEUG_MITTE_ANTEIL).roundToInt() - werkzeugBreite / 2
        val werkzeugX = if (fruehestens <= spaetestens) wunsch.coerceIn(fruehestens, spaetestens) else (fruehestens + spaetestens) / 2

        layout(breite, hoehe) {
            if (links != null) links.placeRelative(linksX, leistenMitte - links.height / 2)
            if (werkzeuge != null) werkzeuge.placeRelative(werkzeugX, leistenMitte - werkzeuge.height / 2)
            if (rechts != null) rechts.placeRelative(rechtsX, leistenMitte - rechts.height / 2)

            val index = anzuzeigen?.let { Werkzeugreihe.indexOf(it) } ?: -1
            if (index >= 0) {
                val knopf = KnopfDurchmesser.roundToPx()
                val schritt = knopf + KnopfAbstand.roundToPx()
                val knopfMitte = werkzeugX + knopf / 2 + index * schritt
                val knopfOben = leistenMitte - knopf / 2
                val zeigerHoehe = zeiger?.height ?: 0
                val zeigerY = knopfOben - PanelLuecke.roundToPx() - zeigerHoehe
                if (zeiger != null) zeiger.placeRelative(knopfMitte - zeiger.width / 2, zeigerY)
                if (panel != null) {
                    val maxX = maxOf(rand, breite - rand - panel.width)
                    val panelX = (knopfMitte - panel.width / 2).coerceIn(rand, maxX)
                    // 1 px Überlappung, damit zwischen Panel und Zeiger keine Haarlinie bleibt.
                    val panelY = maxOf(0, zeigerY - panel.height + 1)
                    panel.placeRelative(panelX, panelY)
                }
            }
        }
    }
}

@Composable
private fun PanelEinblendung(
    sichtbar: Boolean,
    animiert: Boolean,
    modifier: Modifier = Modifier,
    inhalt: @Composable () -> Unit
) {
    val ursprung = TransformOrigin(0.5f, 1f)
    AnimatedVisibility(
        visible = sichtbar,
        modifier = modifier,
        enter = if (animiert) fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.9f, transformOrigin = ursprung) else EnterTransition.None,
        exit = if (animiert) fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.9f, transformOrigin = ursprung) else ExitTransition.None
    ) {
        inhalt()
    }
}

@Composable
private fun LinkeGruppe(
    zeigeUpdatePunkt: Boolean,
    onSchliessen: () -> Unit,
    onMenu: () -> Unit,
    onTeilen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(KnopfAbstand), verticalAlignment = Alignment.CenterVertically) {
        RundKnopf("Beenden", onSchliessen) {
            AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(LeistenSymbol), SchliessenRot)
        }
        RundKnopf("Menü", onMenu) {
            AllgemeinSymbol(AllgemeinesSymbol.MENUE, Modifier.size(LeistenSymbol), SymbolFarbe)
            if (zeigeUpdatePunkt) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(HinweisRot)
                )
            }
        }
        RundKnopf("Teilen", onTeilen) {
            AllgemeinSymbol(AllgemeinesSymbol.TEILEN, Modifier.size(LeistenSymbol), SymbolFarbe)
        }
    }
}

@Composable
private fun WerkzeugGruppe(state: TafelState, modifier: Modifier = Modifier) {
    val tipper = remember { StiftTipper() }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(KnopfAbstand), verticalAlignment = Alignment.CenterVertically) {
        Werkzeugreihe.forEach { werkzeug ->
            // Solange der Werkzeugkasten offen ist, zeigt nur er den aktiven Zustand – sonst
            // wirkten zwei Knöpfe gleichzeitig ausgewählt (das Zeichenwerkzeug bleibt dabei aktiv).
            val aktiv = if (state.offenesPanel == Werkzeug.WERKZEUGKASTEN) {
                werkzeug == Werkzeug.WERKZEUGKASTEN
            } else {
                state.werkzeug == werkzeug
            }
            RundKnopf(
                beschreibung = werkzeugBeschreibung(werkzeug),
                onClick = {
                    state.waehleWerkzeug(werkzeug)
                    if (werkzeug == Werkzeug.STIFT) {
                        val jetzt = System.currentTimeMillis()
                        tipper.anzahl = if (jetzt - tipper.letzter > 2000L) 1 else tipper.anzahl + 1
                        tipper.letzter = jetzt
                        if (tipper.anzahl >= 5) {
                            tipper.anzahl = 0
                            state.loeseStiftEasterEggAus()
                        }
                    }
                },
                ausgewaehlt = aktiv
            ) {
                WerkzeugSymbol(werkzeug, Modifier.size(LeistenSymbol), SymbolFarbe)
            }
        }
    }
}

@Composable
private fun RechteGruppe(state: TafelState, modifier: Modifier = Modifier) {
    val seite = state.seite
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(KnopfAbstand), verticalAlignment = Alignment.CenterVertically) {
        RundKnopf("Papierkorb", onClick = { seite.entfernenAusgewaehlteOderAlles() }) {
            AllgemeinSymbol(AllgemeinesSymbol.PAPIERKORB, Modifier.size(LeistenSymbol), SymbolFarbe)
        }
        RundKnopf("Rückgängig", onClick = { seite.rueckgaengig() }, aktiviert = seite.kannRueckgaengig) {
            AllgemeinSymbol(
                AllgemeinesSymbol.RUECKGAENGIG, Modifier.size(LeistenSymbol),
                if (seite.kannRueckgaengig) SymbolFarbe else SymbolFarbeSchwach
            )
        }
        RundKnopf("Wiederholen", onClick = { seite.wiederholen() }, aktiviert = seite.kannWiederholen) {
            AllgemeinSymbol(
                AllgemeinesSymbol.WIEDERHOLEN, Modifier.size(LeistenSymbol),
                if (seite.kannWiederholen) SymbolFarbe else SymbolFarbeSchwach
            )
        }
        SeitenPille(state)
    }
}

/** Die Pille ganz rechts: neue Seite, zurück, "aktuelle/alle", vor – Maße wie im Original. */
@Composable
private fun SeitenPille(state: TafelState) {
    val aktiv = state.aktiveSeite
    val anzahl = state.seiten.size
    Row(
        modifier = Modifier
            .width(PillenBreite)
            .height(PillenHoehe)
            .clip(RoundedCornerShape(50))
            .background(KnopfFarbe),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillenKnopf("Seite hinzufügen", onClick = { state.neueSeite() }) {
            AllgemeinSymbol(AllgemeinesSymbol.PLUS, Modifier.size(LeistenSymbol), SymbolFarbe)
        }
        PillenKnopf("Vorherige Seite", onClick = { state.vorherigeSeite() }, aktiviert = aktiv > 0) {
            AllgemeinSymbol(
                AllgemeinesSymbol.PFEIL_LINKS, Modifier.size(32.dp),
                if (aktiv > 0) SymbolFarbe else SymbolFarbeSchwach
            )
        }
        Text(
            "${aktiv + 1}/$anzahl",
            modifier = Modifier.weight(1f),
            color = TextFarbe,
            fontSize = 27.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        PillenKnopf("Nächste Seite", onClick = { state.naechsteSeite() }, aktiviert = aktiv < anzahl - 1) {
            AllgemeinSymbol(
                AllgemeinesSymbol.PFEIL_RECHTS, Modifier.size(32.dp),
                if (aktiv < anzahl - 1) SymbolFarbe else SymbolFarbeSchwach
            )
        }
    }
}

@Composable
private fun PillenKnopf(
    beschreibung: String,
    onClick: () -> Unit,
    aktiviert: Boolean = true,
    groesse: Dp = PillenHoehe,
    inhalt: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(groesse)
            .clip(CircleShape)
            .clickable(enabled = aktiviert, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
        content = inhalt
    )
}

/** Merker für die Ziehgeste der Seitenleiste (kein Compose-State nötig). */
private class Zugweg {
    var summe = 0f
}

/**
 * Seitenanzeige am rechten Bildschirmrand: aktuelle Seite / Seitenzahl, dazu Pfeile – und
 * senkrechtes Ziehen über die Leiste blättert weiter (hoch = nächste Seite).
 */
@Composable
fun SeitenLeiste(state: TafelState, modifier: Modifier = Modifier) {
    val aktiv = state.aktiveSeite
    val anzahl = state.seiten.size
    val zug = remember { Zugweg() }
    Column(
        modifier = modifier
            .width(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(KnopfFarbe)
            .pointerInput(Unit) {
                val schwelle = 60.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { zug.summe = 0f },
                    onVerticalDrag = { change, betrag ->
                        change.consume()
                        zug.summe += betrag
                        while (zug.summe <= -schwelle) {
                            state.naechsteSeite()
                            zug.summe += schwelle
                        }
                        while (zug.summe >= schwelle) {
                            state.vorherigeSeite()
                            zug.summe -= schwelle
                        }
                    },
                    onDragEnd = { zug.summe = 0f },
                    onDragCancel = { zug.summe = 0f }
                )
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PillenKnopf("Seite zurück", onClick = { state.vorherigeSeite() }, aktiviert = aktiv > 0, groesse = 58.dp) {
            AllgemeinSymbol(
                AllgemeinesSymbol.PFEIL_LINKS, Modifier.size(28.dp).rotate(90f),
                if (aktiv > 0) SymbolFarbe else SymbolFarbeSchwach
            )
        }
        Text("${aktiv + 1}", color = TextFarbe, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.width(28.dp).height(2.dp).background(SymbolFarbeSchwach))
        Text("$anzahl", color = TextFarbeSchwach, fontSize = 22.sp)
        PillenKnopf("Seite vor", onClick = { state.naechsteSeite() }, aktiviert = aktiv < anzahl - 1, groesse = 58.dp) {
            AllgemeinSymbol(
                AllgemeinesSymbol.PFEIL_RECHTS, Modifier.size(28.dp).rotate(90f),
                if (aktiv < anzahl - 1) SymbolFarbe else SymbolFarbeSchwach
            )
        }
    }
}

/** Kleiner Helfer für Stellen, die nur tippbar sein sollen (Beschriftungen in Panels). */
internal fun Modifier.antippbar(onClick: () -> Unit): Modifier = clickable(role = Role.Button, onClick = onClick)
