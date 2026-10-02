package de.oejendorferdamm.dammboard.ui.toolbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.model.FormModus
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.StempelArt
import de.oejendorferdamm.dammboard.model.StempelItem
import de.oejendorferdamm.dammboard.ui.HelferArt
import de.oejendorferdamm.dammboard.ui.canvas.zeichneStempel
import de.oejendorferdamm.dammboard.model.GeometrieWerkzeug
import de.oejendorferdamm.dammboard.model.HintergrundOptionen
import de.oejendorferdamm.dammboard.model.MusterTyp
import de.oejendorferdamm.dammboard.model.RadiererGroesse
import de.oejendorferdamm.dammboard.model.StiftArt
import de.oejendorferdamm.dammboard.model.Werkzeug
import de.oejendorferdamm.dammboard.ui.AufnahmeZweck
import de.oejendorferdamm.dammboard.ui.TafelState
import de.oejendorferdamm.dammboard.ui.icons.AllesLoeschenSymbol
import de.oejendorferdamm.dammboard.ui.icons.FormSymbol
import de.oejendorferdamm.dammboard.ui.icons.GeometrieSymbol
import de.oejendorferdamm.dammboard.ui.icons.LinienStilSymbol
import de.oejendorferdamm.dammboard.ui.icons.RadiererSymbol
import de.oejendorferdamm.dammboard.ui.icons.StiftArtSymbol
import de.oejendorferdamm.dammboard.ui.icons.WerkzeugkastenAktion
import de.oejendorferdamm.dammboard.ui.icons.WerkzeugkastenSymbol
import de.oejendorferdamm.dammboard.ui.icons.kontur
import de.oejendorferdamm.dammboard.ui.icons.symbolRaster

/*
 * Inhalte der Panels über den Werkzeugknöpfen. Aufbau wie in der Original-App, Maße in
 * Vorbild-Pixeln (siehe ui/Skalierung.kt). Der Rahmen (grauer Kasten mit Zeiger) kommt aus
 * Werkzeugleiste.kt.
 */

/** Lasso und Auswahl haben kein Panel – alle anderen Werkzeuge schon. */
/** Die neuen Funktionen hinter "Extras" im Werkzeugkasten. */
enum class ExtraAktion(val titel: String, val symbol: WerkzeugkastenAktion) {
    PDF("PDF", WerkzeugkastenAktion.PDF),
    ARBEITSBLATT("Arbeitsblatt", WerkzeugkastenAktion.ARBEITSBLATT),
    ABDECKEN("Abdecken", WerkzeugkastenAktion.ABDECKEN),
    TIMER("Timer", WerkzeugkastenAktion.TIMER),
    WUERFEL("Würfel", WerkzeugkastenAktion.WUERFEL),
    ZUFALLSNAME("Zufallsname", WerkzeugkastenAktion.ZUFALLSNAME),
    GRUPPEN("Gruppen", WerkzeugkastenAktion.GRUPPEN),
    LAUTSTAERKE("Lautstärke", WerkzeugkastenAktion.LAUTSTAERKE),
    LERNUHR("Lernuhr", WerkzeugkastenAktion.LERNUHR)
}

internal fun hatPanel(werkzeug: Werkzeug): Boolean = werkzeug != Werkzeug.LASSO && werkzeug != Werkzeug.AUSWAHL

@Composable
internal fun PanelInhalt(panel: Werkzeug, state: TafelState, onIServ: () -> Unit, onExtra: (ExtraAktion) -> Unit) {
    when (panel) {
        Werkzeug.STIFT -> StiftPanel(state)
        Werkzeug.FORMEN -> FormenPanel(state)
        Werkzeug.RADIERER -> RadiererPanel(state)
        Werkzeug.GEOMETRIE -> GeometriePanel(state)
        Werkzeug.WERKZEUGKASTEN -> WerkzeugkastenPanel(state, onIServ, onExtra)
        Werkzeug.LASSO, Werkzeug.AUSWAHL -> Unit
    }
}

// ---------- Stift ----------

private val StiftFarbFeld = 40.dp
private val StiftFarbLuecke = 6.dp

/** Höhe des Dicke-Reglers = Höhe des Farbgitters (4 Zeilen). */
private val ReglerHoehe = StiftFarbFeld * 4 + StiftFarbLuecke * 3

@Composable
private fun StiftPanel(state: TafelState) {
    val fein = state.stiftArt == StiftArt.FEIN
    val bereich = if (fein) 2f..24f else 8f..48f
    val breite = if (fein) state.stiftBreiteFein else state.stiftBreiteLeucht

    // Wie im Original: links Stift oder Marker wählen, daneben EIN großer Regler für die Dicke
    // der gewählten Stiftart, rechts die Farben.
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AuswahlKreis(fein, onClick = { state.stiftArt = StiftArt.FEIN }, groesse = 54.dp, beschreibung = "Stift fein") {
                StiftArtSymbol(fein = true, modifier = Modifier.size(34.dp), tint = SymbolFarbe)
            }
            AuswahlKreis(!fein, onClick = { state.stiftArt = StiftArt.LEUCHT }, groesse = 54.dp, beschreibung = "Textmarker") {
                StiftArtSymbol(fein = false, modifier = Modifier.size(34.dp), tint = SymbolFarbe)
            }
        }
        DickeRegler(
            wert = breite,
            bereich = bereich,
            onWertGeaendert = { neu -> if (fein) state.stiftBreiteFein = neu else state.stiftBreiteLeucht = neu },
            modifier = Modifier.width(40.dp).height(ReglerHoehe)
        )
        // Geteilte Tafel: jede Hälfte hat ihre eigene Farbe (zwei Kinder gleichzeitig).
        val geteilt = state.seite.geteilteAnsicht.value
        val rechts = geteilt && state.aktiveHaelfte == 1
        Column {
            if (geteilt) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    HaelftenKnopf("Linke Hälfte", !rechts) { state.aktiveHaelfte = 0 }
                    HaelftenKnopf("Rechte Hälfte", rechts) { state.aktiveHaelfte = 1 }
                }
            }
            FarbAuswahl(
                ausgewaehlt = if (rechts) state.stiftFarbeRechts else state.stiftFarbe,
                onFarbe = { if (rechts) state.stiftFarbeRechts = it else state.stiftFarbe = it },
                spalten = 3,
                feld = StiftFarbFeld,
                luecke = StiftFarbLuecke,
                zeigeVerlauf = true
            )
        }
    }
}

@Composable
private fun HaelftenKnopf(text: String, aktiv: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (aktiv) PanelAuswahl else Color.White)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = TextFarbe, fontSize = 13.sp, maxLines = 1)
    }
}

/**
 * Senkrechter Regler für die Stiftdicke: kräftige Leiste mit Füllstand und großem Knopf, damit
 * die eingestellte Dicke auch aus einiger Entfernung gut zu erkennen ist.
 */
@Composable
private fun DickeRegler(
    wert: Float,
    bereich: ClosedFloatingPointRange<Float>,
    onWertGeaendert: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val spanne = bereich.endInclusive - bereich.start
    val anteil = if (spanne == 0f) 0f else ((wert - bereich.start) / spanne).coerceIn(0f, 1f)
    // Die Gesten laufen länger als eine Komposition – immer den aktuellen Rückruf verwenden.
    val aktuellerRueckruf by rememberUpdatedState(onWertGeaendert)

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Stiftdicke" }
            .pointerInput(bereich) {
                val rand = 12.dp.toPx()
                fun setze(y: Float) {
                    val nutzbar = (size.height - 2 * rand).coerceAtLeast(1f)
                    val neuerAnteil = 1f - ((y - rand) / nutzbar).coerceIn(0f, 1f)
                    aktuellerRueckruf(bereich.start + neuerAnteil * spanne)
                }
                detectDragGestures(onDragStart = { setze(it.y) }) { change, _ ->
                    change.consume()
                    setze(change.position.y)
                }
            }
            .pointerInput(bereich) {
                val rand = 12.dp.toPx()
                detectTapGestures { position ->
                    val nutzbar = (size.height - 2 * rand).coerceAtLeast(1f)
                    val neuerAnteil = 1f - ((position.y - rand) / nutzbar).coerceIn(0f, 1f)
                    aktuellerRueckruf(bereich.start + neuerAnteil * spanne)
                }
            }
    ) {
        val rand = 12.dp.toPx()
        val leiste = 8.dp.toPx()
        val x = size.width / 2
        val knopfY = rand + (size.height - 2 * rand) * (1f - anteil)
        drawLine(Color(0xFFD2D2D2), Offset(x, rand), Offset(x, size.height - rand), strokeWidth = leiste, cap = StrokeCap.Round)
        drawLine(Color(0xFF3F3F3F), Offset(x, knopfY), Offset(x, size.height - rand), strokeWidth = leiste, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(x, knopfY))
        drawCircle(Color(0xFFE33B3B), radius = 11.dp.toPx(), center = Offset(x, knopfY), style = Stroke(width = 3.dp.toPx()))
    }
}

// ---------- Formen ----------

private val FormenGitter = listOf(
    FormTyp.DREIECK_RECHTS, FormTyp.DREIECK, FormTyp.KREIS, FormTyp.ELLIPSE, FormTyp.QUADRAT,
    FormTyp.SECHSECK, FormTyp.ABGERUNDET, FormTyp.FUENFECK, FormTyp.STERN, FormTyp.WELLE,
    FormTyp.LINIE, FormTyp.PFEIL, FormTyp.DOPPELPFEIL, FormTyp.FREIHANDPFEIL,
    FormTyp.LINIE_GESTRICHELT, FormTyp.PFEIL_GESTRICHELT, FormTyp.DOPPELPFEIL_GESTRICHELT, FormTyp.FREIHANDPFEIL_GESTRICHELT
)

/** Name einer Form – für Bedienungshilfen (Vorlesen) und die automatischen Oberflächentests. */
private fun formBeschreibung(typ: FormTyp): String = when (typ) {
    FormTyp.DREIECK_RECHTS -> "Rechtwinkliges Dreieck"
    FormTyp.DREIECK -> "Dreieck"
    FormTyp.KREIS -> "Kreis"
    FormTyp.ELLIPSE -> "Ellipse"
    FormTyp.QUADRAT -> "Quadrat"
    FormTyp.SECHSECK -> "Sechseck"
    FormTyp.ABGERUNDET -> "Abgerundetes Rechteck"
    FormTyp.FUENFECK -> "Fünfeck"
    FormTyp.STERN -> "Stern"
    FormTyp.WELLE -> "Welle"
    FormTyp.LINIE -> "Linie"
    FormTyp.PFEIL -> "Pfeil"
    FormTyp.DOPPELPFEIL -> "Doppelpfeil"
    FormTyp.FREIHANDPFEIL -> "Bogenpfeil"
    FormTyp.LINIE_GESTRICHELT -> "Linie gestrichelt"
    FormTyp.PFEIL_GESTRICHELT -> "Pfeil gestrichelt"
    FormTyp.DOPPELPFEIL_GESTRICHELT -> "Doppelpfeil gestrichelt"
    FormTyp.FREIHANDPFEIL_GESTRICHELT -> "Bogenpfeil gestrichelt"
}

@Composable
private fun FormenPanel(state: TafelState) {
    Column(Modifier.width(470.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("2D", "Stempel", "Anpassen", "Farbe").forEachIndexed { index, titel ->
                val aktiv = state.formTabIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (aktiv) PanelAuswahl else Color.Transparent)
                        .clickable(role = Role.Tab) { state.formTabIndex = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        titel,
                        color = if (aktiv) TextFarbe else TextFarbeSchwach,
                        fontSize = 17.sp,
                        fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelLinie))
        Spacer(Modifier.height(14.dp))
        when (state.formTabIndex) {
            0 -> Formen2D(state)
            1 -> StempelReiter(state)
            2 -> FormenAnpassen(state)
            else -> FarbAuswahl(
                ausgewaehlt = state.formFuellFarbe ?: Color.Transparent,
                onFarbe = { state.formFuellFarbe = it },
                spalten = 4,
                feld = 38.dp,
                luecke = 6.dp,
                zeigeVerlauf = true
            )
        }
    }
}

@Composable
private fun Formen2D(state: TafelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FormenGitter.chunked(5).forEach { zeile ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    zeile.forEach { typ ->
                        AuswahlKreis(
                            state.formModus == FormModus.FORM && state.formTyp == typ,
                            onClick = { state.formTyp = typ; state.formModus = FormModus.FORM },
                            groesse = 48.dp, beschreibung = formBeschreibung(typ)
                        ) {
                            FormSymbol(typ, Modifier.size(30.dp), SymbolFarbe)
                        }
                    }
                }
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rand", color = TextFarbeSchwach, fontSize = 15.sp)
                Spacer(Modifier.width(10.dp))
                RandVorschau(farbe = state.formRandFarbe, breite = state.formRandBreite)
            }
            Spacer(Modifier.height(10.dp))
            FarbAuswahl(
                ausgewaehlt = state.formRandFarbe,
                onFarbe = { state.formRandFarbe = it },
                spalten = 4,
                feld = 36.dp,
                luecke = 6.dp,
                zeigeVerlauf = false
            )
        }
    }
}

@Composable
private fun RandVorschau(farbe: Color, breite: Float) {
    Box(
        modifier = Modifier
            .width(108.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.dp, PanelLinie, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawLine(
                color = farbe,
                start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2),
                strokeWidth = breite.coerceIn(2f, 14f) / 14f * size.height, cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun FormenAnpassen(state: TafelState) {
    Column {
        Text("Randstärke", color = TextFarbeSchwach, fontSize = 16.sp)
        Slider(
            value = state.formRandBreite,
            onValueChange = { state.formRandBreite = it },
            valueRange = 2f..16f,
            modifier = Modifier.width(320.dp),
            colors = SliderDefaults.colors(thumbColor = SymbolFarbe, activeTrackColor = SymbolFarbe)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Füllen", color = TextFarbeSchwach, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
            Switch(
                checked = state.formFuellFarbe != null,
                onCheckedChange = { an -> state.formFuellFarbe = if (an) state.formRandFarbe else null },
                colors = SwitchDefaults.colors(checkedTrackColor = SymbolFarbe)
            )
        }
    }
}

// ---------- Stempel & Text ----------

private fun stempelBeschreibung(art: StempelArt): String = when (art) {
    StempelArt.HAKEN -> "Haken"
    StempelArt.KREUZ -> "Kreuz"
    StempelArt.STERN -> "Stern"
    StempelArt.HERZ -> "Herz"
    StempelArt.FRAGE -> "Fragezeichen"
    StempelArt.AUSRUF -> "Ausrufezeichen"
    StempelArt.DAUMEN -> "Daumen hoch"
    StempelArt.LACHEN -> "Lachendes Gesicht"
    StempelArt.NACHDENKEN -> "Nachdenkliches Gesicht"
    StempelArt.SUPER -> "Super"
}

@Composable
private fun StempelReiter(state: TafelState) {
    val textModus = state.formModus == FormModus.TEXT
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            val eintraege: List<StempelArt?> = StempelArt.entries + listOf(null) // null = Textfeld
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                eintraege.chunked(4).forEach { zeile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        zeile.forEach { art ->
                            if (art == null) {
                                AuswahlKreis(textModus, onClick = { state.formModus = FormModus.TEXT }, groesse = 52.dp, beschreibung = "Textfeld") {
                                    Text("T", color = SymbolFarbe, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                AuswahlKreis(
                                    state.formModus == FormModus.STEMPEL && state.stempelArt == art,
                                    onClick = { state.stempelArt = art; state.formModus = FormModus.STEMPEL },
                                    groesse = 52.dp,
                                    beschreibung = stempelBeschreibung(art)
                                ) {
                                    Canvas(Modifier.size(36.dp)) {
                                        zeichneStempel(
                                            StempelItem(-1, center, art, if (art == StempelArt.SUPER) size.width * 0.5f else size.width * 0.95f, SymbolFarbe)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (textModus) "Schriftgröße" else "Stempelgröße", color = TextFarbeSchwach, fontSize = 15.sp)
            Slider(
                value = if (textModus) state.textGroesse else state.stempelGroesse,
                onValueChange = { if (textModus) state.textGroesse = it else state.stempelGroesse = it },
                valueRange = if (textModus) 30f..160f else 50f..240f,
                modifier = Modifier.width(220.dp),
                colors = SliderDefaults.colors(thumbColor = SymbolFarbe, activeTrackColor = SymbolFarbe, inactiveTrackColor = PanelLinie)
            )
            Text(
                if (textModus) "Auf die Tafel tippen, um zu schreiben. Vorhandenen Text antippen zum Ändern." else "Auf die Tafel tippen, um zu stempeln.",
                color = TextFarbeSchwach, fontSize = 13.sp, modifier = Modifier.width(220.dp)
            )
        }
        FarbAuswahl(
            ausgewaehlt = state.stempelFarbe,
            onFarbe = { state.stempelFarbe = it },
            spalten = 4,
            feld = 36.dp,
            luecke = 6.dp,
            zeigeVerlauf = false
        )
    }
}

// ---------- Radierer ----------

@Composable
private fun RadiererPanel(state: TafelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            RadiererGroesse.KLEIN to "Radierer klein",
            RadiererGroesse.MITTEL to "Radierer mittel",
            RadiererGroesse.GROSS to "Radierer groß"
        ).forEach { (groesse, beschreibung) ->
            AuswahlKreis(
                state.radiererGroesse == groesse,
                onClick = { state.radiererGroesse = groesse },
                groesse = 60.dp,
                beschreibung = beschreibung
            ) {
                RadiererSymbol(groesse, Modifier.size(40.dp), SymbolFarbe)
            }
        }
        AuswahlKreis(false, onClick = { state.seite.allesLoeschen() }, groesse = 60.dp, beschreibung = "Alles löschen") {
            AllesLoeschenSymbol(Modifier.size(40.dp), SymbolFarbe)
        }
    }
}

// ---------- Geometrie ----------

@Composable
private fun GeometriePanel(state: TafelState) {
    Box {
        Column(Modifier.padding(end = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    GeometrieWerkzeug.LINEAL to "Lineal",
                    GeometrieWerkzeug.WINKELDREIECK to "Winkeldreieck",
                    GeometrieWerkzeug.WINKELMESSER to "Winkelmesser",
                    GeometrieWerkzeug.RECHTWINKLIG to "Rechtwinkliges Dreieck",
                    GeometrieWerkzeug.ZIRKEL to "Zirkel",
                    GeometrieWerkzeug.GLEICHSCHENKLIG to "Gleichschenkliges Dreieck"
                ).forEach { (werkzeug, beschreibung) ->
                    AuswahlKreis(
                        state.geometrieWerkzeug == werkzeug,
                        onClick = { state.geometrieWerkzeug = werkzeug },
                        groesse = 54.dp,
                        beschreibung = beschreibung
                    ) {
                        GeometrieSymbol(werkzeug, Modifier.size(34.dp), SymbolFarbe)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AuswahlKreis(!state.geometrieGestrichelt, onClick = { state.geometrieGestrichelt = false }, groesse = 48.dp, beschreibung = "Durchgezogene Linie") {
                    LinienStilSymbol(gestrichelt = false, modifier = Modifier.size(30.dp), tint = SymbolFarbe)
                }
                AuswahlKreis(state.geometrieGestrichelt, onClick = { state.geometrieGestrichelt = true }, groesse = 48.dp, beschreibung = "Gestrichelte Linie") {
                    LinienStilSymbol(gestrichelt = true, modifier = Modifier.size(30.dp), tint = SymbolFarbe)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Zeigen Sie die Länge der gezeichneten Linie an",
                    color = TextFarbeSchwach, fontSize = 15.sp,
                    modifier = Modifier.width(260.dp)
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.zeigeLaenge,
                    onCheckedChange = { state.zeigeLaenge = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = SymbolFarbe)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF9E9E9E)),
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- Werkzeugkasten ----------

@Composable
private fun WerkzeugkastenPanel(state: TafelState, onIServ: () -> Unit, onExtra: (ExtraAktion) -> Unit) {
    var zeigeHintergrundAuswahl by remember { mutableStateOf(false) }
    var zeigeExtras by remember { mutableStateOf(false) }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WerkzeugkastenEintrag("Hintergrund", WerkzeugkastenAktion.HINTERGRUND, aktiv = zeigeHintergrundAuswahl) {
                zeigeHintergrundAuswahl = !zeigeHintergrundAuswahl
                zeigeExtras = false
            }
            WerkzeugkastenEintrag("Bild teilen", WerkzeugkastenAktion.BILD_TEILEN, aktiv = state.seite.geteilteAnsicht.value) {
                state.seite.geteilteAnsicht.value = !state.seite.geteilteAnsicht.value
            }
            WerkzeugkastenEintrag("Bildschirmfoto", WerkzeugkastenAktion.BILDSCHIRMFOTO) {
                state.aufnahmeAnfrage = AufnahmeZweck.SPEICHERN
                state.schliessePanel()
            }
            WerkzeugkastenEintrag("Lupe", WerkzeugkastenAktion.LUPE, aktiv = state.lupeAktiv) {
                state.lupeAktiv = !state.lupeAktiv
                state.schliessePanel()
            }
            WerkzeugkastenEintrag("IServ", WerkzeugkastenAktion.ISERV) {
                state.schliessePanel()
                onIServ()
            }
            WerkzeugkastenEintrag("Extras", WerkzeugkastenAktion.EXTRAS, aktiv = zeigeExtras) {
                zeigeExtras = !zeigeExtras
                zeigeHintergrundAuswahl = false
            }
        }
        if (zeigeExtras) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(596.dp).height(1.dp).background(PanelLinie))
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExtraAktion.entries.chunked(5).forEach { zeile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        zeile.forEach { aktion ->
                            val offen = when (aktion) {
                                ExtraAktion.ABDECKEN -> state.vorhang != null
                                else -> helferFuer(aktion)?.let { it in state.offeneHelfer } ?: false
                            }
                            WerkzeugkastenEintrag(aktion.titel, aktion.symbol, aktiv = offen) {
                                state.schliessePanel()
                                onExtra(aktion)
                            }
                        }
                    }
                }
            }
        }
        if (zeigeHintergrundAuswahl) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(496.dp).height(1.dp).background(PanelLinie))
            Spacer(Modifier.height(12.dp))
            val aktuellerStil = state.seite.hintergrund.value
            Text("Farbe", color = TextFarbeSchwach, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HintergrundOptionen.forEach { farbe ->
                    val gewaehlt = aktuellerStil.farbe == farbe
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(farbe)
                            .border(if (gewaehlt) 4.dp else 1.dp, if (gewaehlt) SymbolFarbe else PanelLinie, CircleShape)
                            .clickable(role = Role.RadioButton) { state.seite.hintergrund.value = aktuellerStil.copy(farbe = farbe) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Muster / Vorlagen", color = TextFarbeSchwach, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            val musterOptionen = listOf(
                MusterTyp.KEIN to "Einfarbig",
                MusterTyp.LINIERT to "Liniert",
                MusterTyp.KARIERT to "Kariert",
                MusterTyp.GEPUNKTET to "Gepunktet",
                MusterTyp.NOTENLINIEN to "Noten",
                MusterTyp.FUSSBALLFELD to "Fußball",
                MusterTyp.STUNDENPLAN to "Stundenplan",
                MusterTyp.LINEATUR_1 to "Lineatur 1",
                MusterTyp.LINEATUR_2 to "Lineatur 2",
                MusterTyp.LINEATUR_3 to "Lineatur 3",
                MusterTyp.HUNDERTERTAFEL to "100er-Tafel",
                MusterTyp.ZAHLENSTRAHL to "Zahlenstrahl",
                MusterTyp.KARTE_DEUTSCHLAND to "Deutschland",
                MusterTyp.KARTE_HAMBURG to "Hamburg",
                MusterTyp.KARTE_WELT to "Welt"
            )
            if (state.seite.hintergrundBild.value != null) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(role = Role.Button) { state.seite.hintergrundBild.value = null }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Arbeitsblatt von dieser Seite entfernen", color = TextFarbe, fontSize = 14.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                musterOptionen.chunked(5).forEach { zeile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        zeile.forEach { (muster, label) ->
                            MusterKnopf(
                                muster = muster,
                                label = label,
                                ausgewaehlt = aktuellerStil.muster == muster,
                                onClick = { state.seite.hintergrund.value = aktuellerStil.copy(muster = muster) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WerkzeugkastenEintrag(
    label: String,
    aktion: WerkzeugkastenAktion,
    aktiv: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(95.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (aktiv) PanelAuswahl else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            WerkzeugkastenSymbol(aktion, Modifier.size(36.dp), SymbolFarbe)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextFarbe, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun MusterKnopf(muster: MusterTyp, label: String, ausgewaehlt: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(if (ausgewaehlt) PanelAuswahl else Color.White),
            contentAlignment = Alignment.Center
        ) {
            MusterSymbol(muster, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextFarbeSchwach, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun MusterSymbol(muster: MusterTyp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        symbolRaster { w, h ->
            when (muster) {
                MusterTyp.KEIN -> drawLine(SymbolFarbeSchwach, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), strokeWidth = 2f)
                MusterTyp.LINIERT -> {
                    drawLine(SymbolFarbe, Offset(w * 0.12f, h * 0.36f), Offset(w * 0.88f, h * 0.36f), strokeWidth = 1.6f)
                    drawLine(SymbolFarbe, Offset(w * 0.12f, h * 0.64f), Offset(w * 0.88f, h * 0.64f), strokeWidth = 1.6f)
                }
                MusterTyp.KARIERT -> {
                    for (a in listOf(0.36f, 0.64f)) {
                        drawLine(SymbolFarbe, Offset(w * a, h * 0.12f), Offset(w * a, h * 0.88f), strokeWidth = 1.4f)
                        drawLine(SymbolFarbe, Offset(w * 0.12f, h * a), Offset(w * 0.88f, h * a), strokeWidth = 1.4f)
                    }
                }
                MusterTyp.GEPUNKTET -> {
                    for (fx in listOf(0.3f, 0.7f)) for (fy in listOf(0.3f, 0.7f)) {
                        drawCircle(SymbolFarbe, radius = 1.8f, center = Offset(w * fx, h * fy))
                    }
                }
                MusterTyp.NOTENLINIEN -> {
                    for (i in 0 until 5) {
                        val y = h * (0.24f + i * 0.13f)
                        drawLine(SymbolFarbe, Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = 1.2f)
                    }
                }
                MusterTyp.FUSSBALLFELD -> {
                    drawRect(SymbolFarbe, topLeft = Offset(w * 0.12f, h * 0.2f), size = Size(w * 0.76f, h * 0.6f), style = kontur(1.5f))
                    drawLine(SymbolFarbe, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), strokeWidth = 1.5f)
                    drawCircle(SymbolFarbe, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.5f), style = kontur(1.5f))
                }
                MusterTyp.STUNDENPLAN -> {
                    drawRect(SymbolFarbe, topLeft = Offset(w * 0.12f, h * 0.18f), size = Size(w * 0.76f, h * 0.64f), style = kontur(1.5f))
                    drawLine(SymbolFarbe, Offset(w * 0.12f, h * 0.38f), Offset(w * 0.88f, h * 0.38f), strokeWidth = 1.5f)
                    drawLine(SymbolFarbe, Offset(w * 0.4f, h * 0.18f), Offset(w * 0.4f, h * 0.82f), strokeWidth = 1.3f)
                    drawLine(SymbolFarbe, Offset(w * 0.64f, h * 0.18f), Offset(w * 0.64f, h * 0.82f), strokeWidth = 1.3f)
                }
                MusterTyp.LINEATUR_1, MusterTyp.LINEATUR_2 -> {
                    val band = if (muster == MusterTyp.LINEATUR_1) 0.17f else 0.12f
                    val y0 = 0.5f - band * 1.5f
                    drawRect(SymbolFarbeSchwach, topLeft = Offset(w * 0.1f, h * (y0 + band)), size = Size(w * 0.8f, h * band))
                    for (i in 0..3) drawLine(SymbolFarbe, Offset(w * 0.1f, h * (y0 + i * band)), Offset(w * 0.9f, h * (y0 + i * band)), strokeWidth = if (i == 2) 1.8f else 1.1f)
                }
                MusterTyp.LINEATUR_3 -> {
                    for (y in listOf(0.38f, 0.72f)) {
                        drawLine(SymbolFarbe, Offset(w * 0.1f, h * (y - 0.12f)), Offset(w * 0.9f, h * (y - 0.12f)), strokeWidth = 1f)
                        drawLine(SymbolFarbe, Offset(w * 0.1f, h * y), Offset(w * 0.9f, h * y), strokeWidth = 1.8f)
                    }
                }
                MusterTyp.HUNDERTERTAFEL -> {
                    for (i in 0..4) {
                        val a = 0.14f + i * 0.18f
                        val dick = if (i == 0 || i == 4) 1.6f else 1f
                        drawLine(SymbolFarbe, Offset(w * a, h * 0.14f), Offset(w * a, h * 0.86f), strokeWidth = dick)
                        drawLine(SymbolFarbe, Offset(w * 0.14f, h * a), Offset(w * 0.86f, h * a), strokeWidth = dick)
                    }
                }
                MusterTyp.ZAHLENSTRAHL -> {
                    drawLine(SymbolFarbe, Offset(w * 0.08f, h * 0.55f), Offset(w * 0.92f, h * 0.55f), strokeWidth = 1.6f)
                    for (i in 0..6) {
                        val x = 0.12f + i * 0.12f
                        val lang = i % 3 == 0
                        drawLine(SymbolFarbe, Offset(w * x, h * (if (lang) 0.4f else 0.47f)), Offset(w * x, h * (if (lang) 0.7f else 0.63f)), strokeWidth = 1.2f)
                    }
                }
                MusterTyp.KARTE_WELT -> {
                    drawCircle(SymbolFarbe, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.5f), style = kontur(1.5f))
                    drawOval(SymbolFarbe, topLeft = Offset(w * 0.33f, h * 0.14f), size = Size(w * 0.34f, h * 0.72f), style = kontur(1.2f))
                    drawLine(SymbolFarbe, Offset(w * 0.14f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), strokeWidth = 1.2f)
                    drawLine(SymbolFarbe, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.86f), strokeWidth = 1.2f)
                }
                MusterTyp.KARTE_DEUTSCHLAND, MusterTyp.KARTE_HAMBURG -> {
                    // Landkarten-Symbol (gefaltete Karte)
                    val karte = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.12f, h * 0.24f); lineTo(w * 0.37f, h * 0.16f); lineTo(w * 0.63f, h * 0.24f); lineTo(w * 0.88f, h * 0.16f)
                        lineTo(w * 0.88f, h * 0.76f); lineTo(w * 0.63f, h * 0.84f); lineTo(w * 0.37f, h * 0.76f); lineTo(w * 0.12f, h * 0.84f); close()
                    }
                    drawPath(karte, SymbolFarbe, style = kontur(1.4f))
                    drawLine(SymbolFarbe, Offset(w * 0.37f, h * 0.16f), Offset(w * 0.37f, h * 0.76f), strokeWidth = 1.1f)
                    drawLine(SymbolFarbe, Offset(w * 0.63f, h * 0.24f), Offset(w * 0.63f, h * 0.84f), strokeWidth = 1.1f)
                    if (muster == MusterTyp.KARTE_HAMBURG) drawCircle(SymbolFarbe, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.48f))
                }
            }
        }
    }
}

/** Welcher schwebende Helfer zu einer Extra-Aktion gehört (für die Markierung "ist offen"). */
internal fun helferFuer(aktion: ExtraAktion): HelferArt? = when (aktion) {
    ExtraAktion.TIMER -> HelferArt.TIMER
    ExtraAktion.WUERFEL -> HelferArt.WUERFEL
    ExtraAktion.ZUFALLSNAME -> HelferArt.ZUFALLSNAME
    ExtraAktion.GRUPPEN -> HelferArt.GRUPPEN
    ExtraAktion.LAUTSTAERKE -> HelferArt.LAUTSTAERKE
    ExtraAktion.LERNUHR -> HelferArt.LERNUHR
    else -> null
}
