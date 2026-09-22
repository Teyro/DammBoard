package de.oejendorferdamm.dammboard.ui.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.GeometrieWerkzeug
import de.oejendorferdamm.dammboard.model.HintergrundOptionen
import de.oejendorferdamm.dammboard.model.HintergrundStil
import de.oejendorferdamm.dammboard.model.MusterTyp
import de.oejendorferdamm.dammboard.model.RadiererGroesse
import de.oejendorferdamm.dammboard.model.StiftArt
import de.oejendorferdamm.dammboard.model.Werkzeug
import de.oejendorferdamm.dammboard.ui.AufnahmeZweck
import de.oejendorferdamm.dammboard.ui.TafelState
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllesLoeschenSymbol
import de.oejendorferdamm.dammboard.ui.icons.FormSymbol
import de.oejendorferdamm.dammboard.ui.icons.GeometrieSymbol
import de.oejendorferdamm.dammboard.ui.icons.LinienStilSymbol
import de.oejendorferdamm.dammboard.ui.icons.RadiererSymbol
import de.oejendorferdamm.dammboard.ui.icons.StiftArtSymbol
import de.oejendorferdamm.dammboard.ui.icons.WerkzeugSymbol
import de.oejendorferdamm.dammboard.ui.icons.WerkzeugkastenAktion
import de.oejendorferdamm.dammboard.ui.icons.WerkzeugkastenSymbol

private fun werkzeugBeschreibung(werkzeug: Werkzeug): String = when (werkzeug) {
    Werkzeug.STIFT -> "Stift"
    Werkzeug.FORMEN -> "Formen"
    Werkzeug.RADIERER -> "Radierer"
    Werkzeug.LASSO -> "Lasso"
    Werkzeug.GEOMETRIE -> "Geometrie"
    Werkzeug.AUSWAHL -> "Auswahl"
    Werkzeug.WERKZEUGKASTEN -> "Werkzeugkasten"
}

private val LeistenHintergrund = Color(0xFFF7F6F2)
private val LeistenAktiv = Color(0xFFDAD8D1)
private val SymbolFarbe = Color(0xFF2B2B28)
private val SymbolFarbeSchwach = Color(0xFF8A8880)

/** Vollständige Werkzeugleiste am unteren Bildschirmrand inkl. aller Popup-Panels aus dem Design. */
@Composable
fun TafelWerkzeugleiste(
    state: TafelState,
    animationsModus: AnimationsModus,
    symbolSkalierung: Float,
    zeigeUpdatePunkt: Boolean,
    onMenu: () -> Unit,
    onTeilen: () -> Unit,
    onIServ: () -> Unit,
    modifier: Modifier = Modifier
) {
    var letztesPanel by remember { mutableStateOf<Werkzeug?>(null) }
    LaunchedEffect(state.offenesPanel) {
        state.offenesPanel?.let { letztesPanel = it }
    }

    // Skaliert die komplette Werkzeugleiste (Icons, Tippflächen und Abstände gleichermaßen –
    // eine reine Modifier.scale()-Lösung würde nur optisch vergrößern, die Tippflächen aber
    // klein lassen, was auf einem Touchscreen zu Fehltreffern führen würde).
    val basisDichte = LocalDensity.current
    val skalierteDichte = remember(basisDichte, symbolSkalierung) {
        Density(density = basisDichte.density * symbolSkalierung, fontScale = basisDichte.fontScale)
    }

    // modifier (u. a. navigationBarsPadding) bleibt bewusst außerhalb des CompositionLocalProvider,
    // damit echte System-Insets weiter mit der echten Dichte umgerechnet werden – nur der Inhalt
    // selbst (Icons, Popups, Tippflächen) wird skaliert.
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    CompositionLocalProvider(LocalDensity provides skalierteDichte) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (animationsModus == AnimationsModus.NORMAL) {
            AnimatedVisibility(
                visible = state.offenesPanel != null,
                enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.88f),
                exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.88f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    letztesPanel?.let { panel ->
                        PopupRahmen { PanelInhalt(panel, state, onIServ) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        } else {
            state.offenesPanel?.let { panel ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PopupRahmen { PanelInhalt(panel, state, onIServ) }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        HauptLeiste(state, zeigeUpdatePunkt, onMenu, onTeilen)
    }
    }
    }
}

@Composable
private fun PanelInhalt(panel: Werkzeug, state: TafelState, onIServ: () -> Unit) {
    when (panel) {
        Werkzeug.STIFT -> StiftPanelInhalt(state)
        Werkzeug.FORMEN -> FormenPanelInhalt(state)
        Werkzeug.RADIERER -> RadiererPanelInhalt(state)
        Werkzeug.GEOMETRIE -> GeometriePanelInhalt(state)
        Werkzeug.WERKZEUGKASTEN -> WerkzeugkastenPanelInhalt(state, onIServ)
        Werkzeug.LASSO, Werkzeug.AUSWAHL -> Unit
    }
}

@Composable
private fun PopupRahmen(inhalt: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(LeistenHintergrund)
                .padding(14.dp)
        ) {
            inhalt()
        }
        Box(
            modifier = Modifier
                .size(14.dp)
                .offset(y = (-7).dp)
                .graphicsLayer { rotationZ = 45f }
                .background(LeistenHintergrund, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun HauptLeiste(
    state: TafelState,
    zeigeUpdatePunkt: Boolean,
    onMenu: () -> Unit,
    onTeilen: () -> Unit
) {
    Row(
        modifier = Modifier.padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RundKnopf(hintergrund = Color.White, onClick = onMenu, modifier = Modifier.semantics { contentDescription = "Menü" }) {
            Box {
                AllgemeinSymbol(AllgemeinesSymbol.MENUE, Modifier.size(18.dp), SymbolFarbe)
                if (zeigeUpdatePunkt) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0402E))
                    )
                }
            }
        }
        RundKnopf(hintergrund = Color.White, onClick = onTeilen, modifier = Modifier.semantics { contentDescription = "Teilen" }) {
            AllgemeinSymbol(AllgemeinesSymbol.TEILEN, Modifier.size(18.dp), SymbolFarbe)
        }

        WerkzeugPille {
            // Fünfmal schnell hintereinander auf den Stift getippt: kleines Easter Egg.
            var stiftTapAnzahl by remember { mutableStateOf(0) }
            var letzterStiftTap by remember { mutableStateOf(0L) }

            listOf(
                Werkzeug.STIFT, Werkzeug.FORMEN, Werkzeug.RADIERER, Werkzeug.LASSO,
                Werkzeug.GEOMETRIE, Werkzeug.AUSWAHL, Werkzeug.WERKZEUGKASTEN
            ).forEach { werkzeug ->
                // Solange der Werkzeugkasten offen ist, zeigt nur dessen Knopf den aktiven
                // Zustand – vorher blieb zusätzlich das zuletzt genutzte Zeichenwerkzeug
                // markiert, weil state.werkzeug beim Öffnen des Werkzeugkastens unverändert
                // bleibt (siehe waehleWerkzeug) und dadurch zwei Knöpfe gleichzeitig aktiv wirkten.
                val aktiv = if (state.offenesPanel == Werkzeug.WERKZEUGKASTEN) {
                    werkzeug == Werkzeug.WERKZEUGKASTEN
                } else {
                    state.werkzeug == werkzeug
                }
                AuswahlKnopf(
                    ausgewaehlt = aktiv,
                    onClick = {
                        state.waehleWerkzeug(werkzeug)
                        if (werkzeug == Werkzeug.STIFT) {
                            val jetzt = System.currentTimeMillis()
                            stiftTapAnzahl = if (jetzt - letzterStiftTap > 2000L) 1 else stiftTapAnzahl + 1
                            letzterStiftTap = jetzt
                            if (stiftTapAnzahl >= 5) {
                                stiftTapAnzahl = 0
                                state.loeseStiftEasterEggAus()
                            }
                        }
                    },
                    groesse = 40.dp,
                    modifier = Modifier.semantics { contentDescription = werkzeugBeschreibung(werkzeug) }
                ) {
                    WerkzeugSymbol(werkzeug, Modifier.size(20.dp), if (aktiv) SymbolFarbe else SymbolFarbeSchwach)
                }
            }
        }

        WerkzeugPille {
            RundKnopfKlein(onClick = { state.seite.entfernenAusgewaehlteOderAlles() }, modifier = Modifier.semantics { contentDescription = "Papierkorb" }) {
                AllgemeinSymbol(AllgemeinesSymbol.PAPIERKORB, Modifier.size(18.dp), SymbolFarbe)
            }
            RundKnopfKlein(
                onClick = { state.seite.rueckgaengig() }, aktiviert = state.seite.kannRueckgaengig,
                modifier = Modifier.semantics { contentDescription = "Rückgängig" }
            ) {
                AllgemeinSymbol(AllgemeinesSymbol.RUECKGAENGIG, Modifier.size(18.dp), if (state.seite.kannRueckgaengig) SymbolFarbe else SymbolFarbeSchwach)
            }
            RundKnopfKlein(
                onClick = { state.seite.wiederholen() }, aktiviert = state.seite.kannWiederholen,
                modifier = Modifier.semantics { contentDescription = "Wiederholen" }
            ) {
                AllgemeinSymbol(AllgemeinesSymbol.WIEDERHOLEN, Modifier.size(18.dp), if (state.seite.kannWiederholen) SymbolFarbe else SymbolFarbeSchwach)
            }
        }

        RundKnopf(
            hintergrund = Color(0xFF262A26), onClick = { state.neueSeite() },
            modifier = Modifier.semantics { contentDescription = "Seite hinzufügen" }
        ) {
            AllgemeinSymbol(AllgemeinesSymbol.PLUS, Modifier.size(18.dp), Color.White)
        }

        WerkzeugPille(abstand = 10.dp) {
            RundKnopfKlein(
                onClick = { state.vorherigeSeite() }, aktiviert = state.aktiveSeite > 0,
                modifier = Modifier.semantics { contentDescription = "Vorherige Seite" }
            ) {
                AllgemeinSymbol(
                    AllgemeinesSymbol.PFEIL_LINKS, Modifier.size(20.dp),
                    if (state.aktiveSeite > 0) SymbolFarbe else SymbolFarbeSchwach
                )
            }
            Text(
                "${state.aktiveSeite + 1}/${state.seiten.size}",
                color = SymbolFarbe, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            RundKnopfKlein(
                onClick = { state.naechsteSeite() }, aktiviert = state.aktiveSeite < state.seiten.lastIndex,
                modifier = Modifier.semantics { contentDescription = "Nächste Seite" }
            ) {
                AllgemeinSymbol(
                    AllgemeinesSymbol.PFEIL_RECHTS, Modifier.size(20.dp),
                    if (state.aktiveSeite < state.seiten.lastIndex) SymbolFarbe else SymbolFarbeSchwach
                )
            }
        }
    }
}

@Composable
private fun WerkzeugPille(abstand: Dp = 2.dp, inhalt: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .shadow(2.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(abstand)
    ) {
        inhalt()
    }
}

@Composable
private fun RundKnopf(hintergrund: Color, onClick: () -> Unit, modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(48.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(hintergrund)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { inhalt() }
}

@Composable
private fun RundKnopfKlein(onClick: () -> Unit, aktiviert: Boolean = true, modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(if (aktiviert) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { inhalt() }
}

@Composable
private fun AuswahlKnopf(ausgewaehlt: Boolean, onClick: () -> Unit, groesse: Dp = 40.dp, modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(groesse)
            .clip(CircleShape)
            .background(if (ausgewaehlt) LeistenAktiv else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { inhalt() }
}

// ---------- Stift-Panel ----------

/** Höhe des einen gemeinsamen Dicke-Reglers – an die Höhe des 3-spaltigen Farbgitters
 *  angeglichen (4 Zeilen à 44dp + 3 Abstände à 4dp), damit das Panel ausgewogen wirkt. */
private val StiftReglerHoehe = 188.dp

@Composable
private fun StiftPanelInhalt(state: TafelState) {
    val fein = state.stiftArt == StiftArt.FEIN
    val bereich = if (fein) 2f..24f else 8f..48f
    val breite = if (fein) state.stiftBreiteFein else state.stiftBreiteLeucht

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        // Wie im Original: links wählt man Stift oder Marker, EIN gemeinsamer großer Regler
        // daneben bestimmt die Dicke der jeweils ausgewählten Stiftart – nicht pro Stiftart ein
        // eigener kleiner Regler.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AuswahlKnopf(ausgewaehlt = fein, onClick = { state.stiftArt = StiftArt.FEIN }, groesse = 36.dp) {
                StiftArtSymbol(fein = true, modifier = Modifier.size(20.dp), tint = SymbolFarbe)
            }
            AuswahlKnopf(ausgewaehlt = !fein, onClick = { state.stiftArt = StiftArt.LEUCHT }, groesse = 36.dp) {
                StiftArtSymbol(fein = false, modifier = Modifier.size(20.dp), tint = SymbolFarbe)
            }
        }
        VertikalerRegler(
            wert = breite,
            bereich = bereich,
            onWertGeaendert = { neu -> if (fein) state.stiftBreiteFein = neu else state.stiftBreiteLeucht = neu },
            modifier = Modifier.width(26.dp).height(StiftReglerHoehe)
        )
        FarbGitterUndVerlauf(ausgewaehlt = state.stiftFarbe, onFarbe = { state.stiftFarbe = it }, spalten = 3)
    }
}

@Composable
private fun VertikalerRegler(
    wert: Float,
    bereich: ClosedFloatingPointRange<Float>,
    onWertGeaendert: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val spanne = bereich.endInclusive - bereich.start
    val anteil = if (spanne == 0f) 0f else ((wert - bereich.start) / spanne).coerceIn(0f, 1f)

    fun setzeAusPosition(y: Float, hoehe: Float) {
        val neuerAnteil = 1f - (y / hoehe).coerceIn(0f, 1f)
        onWertGeaendert(bereich.start + neuerAnteil * spanne)
    }

    Canvas(
        modifier = modifier
            .pointerInput(bereich) {
                detectDragGestures { change, _ ->
                    change.consume()
                    setzeAusPosition(change.position.y, size.height.toFloat())
                }
            }
            .pointerInput(bereich) {
                detectTapGestures { position -> setzeAusPosition(position.y, size.height.toFloat()) }
            }
    ) {
        drawLine(
            color = Color(0xFFC9C7C0), start = Offset(size.width / 2, 6f), end = Offset(size.width / 2, size.height - 6f),
            strokeWidth = 4f, cap = StrokeCap.Round
        )
        val knopfY = 6f + (size.height - 12f) * (1f - anteil)
        drawCircle(Color(0xFFE33B3B), radius = 6f, center = Offset(size.width / 2, knopfY))
        drawCircle(Color.White, radius = 6f, center = Offset(size.width / 2, knopfY), style = Stroke(width = 1.6f))
    }
}

// ---------- Formen-Panel ----------

private val FormenGitter = listOf(
    FormTyp.DREIECK_RECHTS, FormTyp.DREIECK, FormTyp.KREIS, FormTyp.ELLIPSE, FormTyp.QUADRAT,
    FormTyp.SECHSECK, FormTyp.ABGERUNDET, FormTyp.FUENFECK, FormTyp.STERN, FormTyp.WELLE,
    FormTyp.LINIE, FormTyp.PFEIL, FormTyp.DOPPELPFEIL, FormTyp.FREIHANDPFEIL,
    FormTyp.LINIE_GESTRICHELT, FormTyp.PFEIL_GESTRICHELT, FormTyp.DOPPELPFEIL_GESTRICHELT, FormTyp.FREIHANDPFEIL_GESTRICHELT
)

@Composable
private fun FormenPanelInhalt(state: TafelState) {
    Column(modifier = Modifier.width(410.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            listOf("2D", "3D", "Anpassen", "Farbe").forEachIndexed { index, titel ->
                val aktiv = state.formTabIndex == index
                Text(
                    titel,
                    color = if (aktiv) SymbolFarbe else SymbolFarbeSchwach,
                    fontSize = 14.sp,
                    fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clickable { state.formTabIndex = index }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when (state.formTabIndex) {
            0 -> FormenTab2D(state)
            1 -> Text(
                "3D-Formen folgen in einer späteren Version.",
                color = SymbolFarbeSchwach, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 20.dp)
            )
            2 -> FormenTabAnpassen(state)
            else -> FarbGitterUndVerlauf(
                ausgewaehlt = state.formFuellFarbe ?: Color.Transparent,
                onFarbe = { state.formFuellFarbe = it }
            )
        }
    }
}

@Composable
private fun FormenTab2D(state: TafelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.width(190.dp).height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(FormenGitter) { typ ->
                AuswahlKnopf(ausgewaehlt = state.formTyp == typ, onClick = { state.formTyp = typ }, groesse = 32.dp) {
                    FormSymbol(typ, Modifier.size(20.dp), SymbolFarbe)
                }
            }
        }
        Column {
            Text("Rand", color = SymbolFarbeSchwach, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            RandVorschau(farbe = state.formRandFarbe, breite = state.formRandBreite)
            Spacer(Modifier.height(8.dp))
            FarbGitterUndVerlauf(
                ausgewaehlt = state.formRandFarbe,
                onFarbe = { state.formRandFarbe = it },
                zeigeVerlauf = false
            )
        }
    }
}

@Composable
private fun RandVorschau(farbe: Color, breite: Float) {
    Row(
        modifier = Modifier
            .width(188.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E0D8), RoundedCornerShape(50))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Canvas(modifier = Modifier.width(90.dp).height(12.dp)) {
            drawLine(
                color = farbe,
                start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2),
                strokeWidth = breite.coerceIn(2f, 8f), cap = StrokeCap.Round
            )
        }
        AllgemeinSymbol(AllgemeinesSymbol.PFEIL_RECHTS, Modifier.size(10.dp), SymbolFarbeSchwach)
    }
}

@Composable
private fun FormenTabAnpassen(state: TafelState) {
    Column {
        Text("Randstärke", color = SymbolFarbeSchwach, fontSize = 12.sp)
        Slider(
            value = state.formRandBreite, onValueChange = { state.formRandBreite = it },
            valueRange = 2f..16f, modifier = Modifier.width(260.dp),
            colors = SliderDefaults.colors(thumbColor = SymbolFarbe, activeTrackColor = SymbolFarbe)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Füllen", color = SymbolFarbeSchwach, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            Switch(
                checked = state.formFuellFarbe != null,
                onCheckedChange = { angeschaltet ->
                    state.formFuellFarbe = if (angeschaltet) state.formRandFarbe else null
                },
                colors = SwitchDefaults.colors(checkedTrackColor = SymbolFarbe)
            )
        }
    }
}

// ---------- Radierer-Panel ----------

@Composable
private fun RadiererPanelInhalt(state: TafelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(RadiererGroesse.KLEIN, RadiererGroesse.MITTEL, RadiererGroesse.GROSS).forEach { groesse ->
            AuswahlKnopf(ausgewaehlt = state.radiererGroesse == groesse, onClick = { state.radiererGroesse = groesse }, groesse = 44.dp) {
                RadiererSymbol(groesse, Modifier.size(26.dp), SymbolFarbe)
            }
        }
        AuswahlKnopf(ausgewaehlt = false, onClick = { state.seite.allesLoeschen() }, groesse = 44.dp) {
            AllesLoeschenSymbol(Modifier.size(26.dp), SymbolFarbe)
        }
    }
}

// ---------- Geometrie-Panel ----------

@Composable
private fun GeometriePanelInhalt(state: TafelState) {
    Box {
        Column(modifier = Modifier.padding(end = 18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                GeometrieWerkzeug.LINEAL, GeometrieWerkzeug.WINKELDREIECK, GeometrieWerkzeug.WINKELMESSER,
                GeometrieWerkzeug.RECHTWINKLIG, GeometrieWerkzeug.ZIRKEL, GeometrieWerkzeug.GLEICHSCHENKLIG
            ).forEach { werkzeug ->
                AuswahlKnopf(ausgewaehlt = state.geometrieWerkzeug == werkzeug, onClick = { state.geometrieWerkzeug = werkzeug }, groesse = 38.dp) {
                    GeometrieSymbol(werkzeug, Modifier.size(22.dp), SymbolFarbe)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuswahlKnopf(ausgewaehlt = !state.geometrieGestrichelt, onClick = { state.geometrieGestrichelt = false }, groesse = 34.dp) {
                LinienStilSymbol(gestrichelt = false, modifier = Modifier.size(22.dp), tint = SymbolFarbe)
            }
            AuswahlKnopf(ausgewaehlt = state.geometrieGestrichelt, onClick = { state.geometrieGestrichelt = true }, groesse = 34.dp) {
                LinienStilSymbol(gestrichelt = true, modifier = Modifier.size(22.dp), tint = SymbolFarbe)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Zeigen Sie die Länge der gezeichneten Linie an",
                color = SymbolFarbeSchwach, fontSize = 12.sp,
                modifier = Modifier.width(220.dp)
            )
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
                .size(20.dp)
                .clip(CircleShape)
                .border(1.dp, SymbolFarbeSchwach, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = SymbolFarbeSchwach, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Werkzeugkasten-Panel ----------

@Composable
private fun WerkzeugkastenPanelInhalt(state: TafelState, onIServ: () -> Unit) {
    var zeigeHintergrundAuswahl by remember { mutableStateOf(false) }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WerkzeugkastenEintrag("Hintergrund", WerkzeugkastenAktion.HINTERGRUND) {
                zeigeHintergrundAuswahl = !zeigeHintergrundAuswahl
            }
            WerkzeugkastenEintrag("Bild teilen", WerkzeugkastenAktion.BILD_TEILEN) {
                state.seite.geteilteAnsicht.value = !state.seite.geteilteAnsicht.value
            }
            WerkzeugkastenEintrag("Bildschirmfoto", WerkzeugkastenAktion.BILDSCHIRMFOTO) {
                state.aufnahmeAnfrage = AufnahmeZweck.SPEICHERN
                state.schliessePanel()
            }
            WerkzeugkastenEintrag("Lupe", WerkzeugkastenAktion.LUPE) {
                state.lupeAktiv = !state.lupeAktiv
                state.schliessePanel()
            }
            WerkzeugkastenEintrag("IServ", WerkzeugkastenAktion.ISERV) {
                state.schliessePanel()
                onIServ()
            }
        }
        if (zeigeHintergrundAuswahl) {
            Spacer(Modifier.height(10.dp))
            val aktuellerStil = state.seite.hintergrund.value
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HintergrundOptionen.forEach { farbe ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(farbe)
                            .border(
                                width = if (aktuellerStil.farbe == farbe) 3.dp else 0.dp,
                                color = SymbolFarbe,
                                shape = CircleShape
                            )
                            .clickable { state.seite.hintergrund.value = aktuellerStil.copy(farbe = farbe) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Muster / Vorlagen", color = SymbolFarbeSchwach, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            val musterOptionen = listOf(
                MusterTyp.KEIN to "Einfarbig",
                MusterTyp.LINIERT to "Liniert",
                MusterTyp.KARIERT to "Kariert",
                MusterTyp.GEPUNKTET to "Gepunktet",
                MusterTyp.NOTENLINIEN to "Noten",
                MusterTyp.FUSSBALLFELD to "Fußball",
                MusterTyp.STUNDENPLAN to "Stundenplan"
            )
            musterOptionen.chunked(4).forEach { zeile ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    zeile.forEach { (muster, label) ->
                        MusterKnopf(
                            muster = muster, label = label,
                            ausgewaehlt = aktuellerStil.muster == muster,
                            onClick = { state.seite.hintergrund.value = aktuellerStil.copy(muster = muster) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MusterSymbol(muster: MusterTyp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        when (muster) {
            MusterTyp.KEIN -> drawLine(SymbolFarbeSchwach, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), strokeWidth = 2f)
            MusterTyp.LINIERT -> {
                drawLine(SymbolFarbe, Offset(w * 0.1f, h * 0.35f), Offset(w * 0.9f, h * 0.35f), strokeWidth = 1.4f)
                drawLine(SymbolFarbe, Offset(w * 0.1f, h * 0.65f), Offset(w * 0.9f, h * 0.65f), strokeWidth = 1.4f)
            }
            MusterTyp.KARIERT -> {
                drawLine(SymbolFarbe, Offset(w * 0.35f, h * 0.1f), Offset(w * 0.35f, h * 0.9f), strokeWidth = 1.2f)
                drawLine(SymbolFarbe, Offset(w * 0.65f, h * 0.1f), Offset(w * 0.65f, h * 0.9f), strokeWidth = 1.2f)
                drawLine(SymbolFarbe, Offset(w * 0.1f, h * 0.35f), Offset(w * 0.9f, h * 0.35f), strokeWidth = 1.2f)
                drawLine(SymbolFarbe, Offset(w * 0.1f, h * 0.65f), Offset(w * 0.9f, h * 0.65f), strokeWidth = 1.2f)
            }
            MusterTyp.GEPUNKTET -> {
                listOf(0.3f to 0.3f, 0.7f to 0.3f, 0.3f to 0.7f, 0.7f to 0.7f).forEach { (fx, fy) ->
                    drawCircle(SymbolFarbe, radius = 1.6f, center = Offset(w * fx, h * fy))
                }
            }
            MusterTyp.NOTENLINIEN -> {
                for (i in 0 until 5) {
                    val y = h * (0.22f + i * 0.14f)
                    drawLine(SymbolFarbe, Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = 1f)
                }
            }
            MusterTyp.FUSSBALLFELD -> {
                drawRect(
                    SymbolFarbe, topLeft = Offset(w * 0.14f, h * 0.2f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.6f), style = Stroke(width = 1.4f)
                )
                drawLine(SymbolFarbe, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), strokeWidth = 1.4f)
                drawCircle(SymbolFarbe, radius = w * 0.1f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = 1.4f))
            }
            MusterTyp.STUNDENPLAN -> {
                drawRect(
                    SymbolFarbe, topLeft = Offset(w * 0.14f, h * 0.18f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.64f), style = Stroke(width = 1.4f)
                )
                drawLine(SymbolFarbe, Offset(w * 0.14f, h * 0.38f), Offset(w * 0.86f, h * 0.38f), strokeWidth = 1.4f)
                drawLine(SymbolFarbe, Offset(w * 0.42f, h * 0.18f), Offset(w * 0.42f, h * 0.82f), strokeWidth = 1.2f)
                drawLine(SymbolFarbe, Offset(w * 0.68f, h * 0.18f), Offset(w * 0.68f, h * 0.82f), strokeWidth = 1.2f)
            }
        }
    }
}

@Composable
private fun MusterKnopf(muster: MusterTyp, label: String, ausgewaehlt: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(58.dp).clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(if (ausgewaehlt) LeistenAktiv else Color.White),
            contentAlignment = Alignment.Center
        ) {
            MusterSymbol(muster, Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = SymbolFarbeSchwach, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 11.sp)
    }
}

@Composable
private fun WerkzeugkastenEintrag(label: String, aktion: WerkzeugkastenAktion, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            WerkzeugkastenSymbol(aktion, Modifier.size(24.dp), SymbolFarbe)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = SymbolFarbeSchwach, fontSize = 10.sp)
    }
}
