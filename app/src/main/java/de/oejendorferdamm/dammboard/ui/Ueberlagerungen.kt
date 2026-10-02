package de.oejendorferdamm.dammboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.model.TextItem
import de.oejendorferdamm.dammboard.model.Werkzeug
import de.oejendorferdamm.dammboard.ui.canvas.messeText
import de.oejendorferdamm.dammboard.ui.canvas.pixelFaktorFuer
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import de.oejendorferdamm.dammboard.ui.toolbar.faengtBeruehrungen
import kotlin.math.roundToInt

/* Einblendungen über der Tafel (Maße in Vorbild-Pixeln, siehe ui/Skalierung.kt). */

private val KartenFlaeche = Color.White
private val KartenText = Color(0xFF2B2B2B)
private val KartenTextSchwach = Color(0xFF6E6E6E)
private val Akzent = Color(0xFF3A5C4A)

@Composable
internal fun RundTaste(text: String, hervorgehoben: Boolean = false, warnung: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(
                when {
                    warnung -> Color(0xFFC62828)
                    hervorgehoben -> Akzent
                    else -> Color(0xFFEBEBEB)
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (hervorgehoben || warnung) Color.White else KartenText,
            fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}

/** Abgedunkelter Hintergrund mit einer Karte in der Mitte; Tippen daneben schließt. */
@Composable
internal fun DialogKarte(onSchliessen: () -> Unit, breite: Int = 640, inhalt: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onSchliessen() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = breite.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(KartenFlaeche)
                .faengtBeruehrungen()
                .padding(32.dp)
        ) { inhalt() }
    }
}

/** Einfache Auswahl: Titel, Text und mehrere Knöpfe untereinander. */
@Composable
internal fun AuswahlDialog(
    titel: String,
    text: String?,
    optionen: List<Pair<String, () -> Unit>>,
    onSchliessen: () -> Unit
) {
    DialogKarte(onSchliessen) {
        Text(titel, color = KartenText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (text != null) {
            Spacer(Modifier.height(10.dp))
            Text(text, color = KartenTextSchwach, fontSize = 21.sp, lineHeight = 28.sp)
        }
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            optionen.forEachIndexed { i, (label, aktion) ->
                Box(Modifier.fillMaxWidth()) { RundTaste(label, hervorgehoben = i == 0, onClick = aktion) }
            }
            RundTaste("Abbrechen", onClick = onSchliessen)
        }
    }
}

/** "Bitte warten" mit Fortschrittsanzeige (PDF erstellen, Arbeitsblatt öffnen). */
@Composable
internal fun WarteHinweis(text: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).faengtBeruehrungen(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(18.dp)).background(KartenFlaeche).padding(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(color = Akzent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(20.dp))
            Text(text, color = KartenText, fontSize = 24.sp)
        }
    }
}

// ------------------------------------------------------------------ Abdecken

/**
 * Vorhang zum Abdecken: verdeckt die Tafel ab einer Kante, die sich am Griff nach unten ziehen
 * lässt – so wird Zeile für Zeile aufgedeckt. Das X nimmt den Vorhang weg.
 */
@Composable
internal fun Vorhang(state: TafelState) {
    val anteil = state.vorhang ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hoehePx = constraints.maxHeight.toFloat()
        val obenPx = (anteil * hoehePx).roundToInt()
        val dichte = LocalDensity.current
        Column(
            modifier = Modifier
                .offset { IntOffset(0, obenPx) }
                .fillMaxWidth()
                .height(with(dichte) { (hoehePx - obenPx).coerceAtLeast(0f).toDp() })
                .background(Color(0xFF3B3F3B))
                .pointerInput(hoehePx) {
                    detectDragGestures { change, ziehen ->
                        change.consume()
                        val aktuell = state.vorhang ?: return@detectDragGestures
                        state.vorhang = (aktuell + ziehen.y / hoehePx).coerceIn(0f, 0.97f)
                    }
                }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(220.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.6f))
                )
                Spacer(Modifier.width(30.dp))
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable(role = Role.Button) { state.vorhang = null }
                        .semantics { contentDescription = "Abdecken beenden" },
                    contentAlignment = Alignment.Center
                ) {
                    AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(22.dp), KartenText)
                }
            }
            Text(
                "Zum Aufdecken nach unten ziehen",
                color = Color.White.copy(alpha = 0.55f), fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ------------------------------------------------------------------ Auswahl: Kopieren & Co.

/** Kleine Knopfleiste über einer Lasso-/Rechteck-Auswahl bzw. "Einfügen", wenn etwas kopiert ist. */
@Composable
internal fun AuswahlAktionen(state: TafelState) {
    if (state.werkzeug != Werkzeug.LASSO && state.werkzeug != Werkzeug.AUSWAHL) return
    if (state.auswahlGesteLaeuft || state.offenesPanel != null) return
    val seite = state.seite
    val grenzen = seite.auswahlGrenzen()
    val p = pixelFaktorFuer(state.brettBreite.toFloat(), state.brettHoehe.toFloat())
    val versatz = Offset(40f * p, 40f * p)
    var leistenHoehe by remember { mutableStateOf(0) }

    if (grenzen != null) {
        val (oben, unten) = grenzen
        val abstand = (24f * p).roundToInt()
        val y = (oben.y - abstand - leistenHoehe).roundToInt().let { if (it < 0) (unten.y + abstand).roundToInt() else it }
        Row(
            modifier = Modifier
                .offset { IntOffset(oben.x.roundToInt().coerceIn(0, maxOf(0, state.brettBreite - 600)), y) }
                .onSizeChanged { leistenHoehe = it.height }
                .shadow(8.dp, RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp))
                .background(Color.White)
                .faengtBeruehrungen()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RundTaste("Kopieren") { state.kopiereAuswahl() }
            RundTaste("Duplizieren") { state.dupliziereAuswahl(versatz) }
            if (state.zwischenablage.isNotEmpty()) RundTaste("Einfügen") { state.einfuegen(versatz) }
            RundTaste("Löschen", warnung = true) { seite.entfernenAusgewaehlteOderAlles() }
        }
    } else if (state.zwischenablage.isNotEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .shadow(8.dp, RoundedCornerShape(40.dp))
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.White)
                    .faengtBeruehrungen()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${state.zwischenablage.size} kopiert",
                    color = KartenTextSchwach, fontSize = 19.sp, modifier = Modifier.padding(horizontal = 16.dp)
                )
                RundTaste("Einfügen", hervorgehoben = true) { state.einfuegen(versatz) }
                RundTaste("Leeren") { state.zwischenablage = emptyList() }
            }
        }
    }
}

// ------------------------------------------------------------------ Textfeld

@Composable
internal fun TextEingabeDialog(state: TafelState) {
    val eingabe = state.textEingabe ?: return
    val vorhanden = eingabe.vorhanden
    var text by remember(eingabe) { mutableStateOf(vorhanden?.text ?: "") }
    val fokus = remember { FocusRequester() }
    val tastatur = LocalSoftwareKeyboardController.current
    val p = pixelFaktorFuer(state.brettBreite.toFloat(), state.brettHoehe.toFloat())
    // Gewählte Größe in "Original-Pixeln" (ohne Pixelfaktor)
    var groesse by remember(eingabe) { mutableStateOf(vorhanden?.let { it.groesse / p } ?: state.textGroesse) }

    LaunchedEffect(eingabe) {
        fokus.requestFocus()
        tastatur?.show()
    }

    fun schliessen() {
        tastatur?.hide()
        state.textEingabe = null
    }

    fun uebernehmen() {
        val seite = state.seite
        val inhalt = text.trimEnd()
        val farbe = vorhanden?.farbe ?: state.stempelFarbe
        val px = groesse * p
        if (inhalt.isBlank()) {
            if (vorhanden != null) {
                seite.ausgewaehlteIds.clear()
                seite.ausgewaehlteIds.add(vorhanden.id)
                seite.entfernenAusgewaehlteOderAlles()
            }
        } else {
            val (b, h) = messeText(inhalt, px)
            if (vorhanden != null) {
                seite.ersetze(vorhanden, vorhanden.copy(text = inhalt, groesse = px, breite = b, hoehe = h))
            } else {
                // Getippte Stelle = Mitte der ersten Zeile, links bündig.
                val position = Offset(eingabe.position.x, eingabe.position.y - px * 0.6f)
                seite.hinzufuegen(TextItem(naechsteId(), position, inhalt, farbe, px, b, h))
            }
        }
        state.textGroesse = groesse
        schliessen()
    }

    DialogKarte(onSchliessen = ::schliessen, breite = 760) {
        Text(if (vorhanden != null) "Text ändern" else "Text schreiben", color = KartenText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.width(690.dp).heightIn(min = 160.dp).focusRequester(fokus),
            textStyle = TextStyle(fontSize = 28.sp, color = KartenText),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Akzent, cursorColor = Akzent)
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Größe", color = KartenTextSchwach, fontSize = 20.sp)
            listOf("Klein" to 44f, "Mittel" to 64f, "Groß" to 100f, "Riesig" to 150f).forEach { (name, wert) ->
                val gewaehlt = kotlin.math.abs(groesse - wert) < 1f
                Box(
                    Modifier
                        .clip(RoundedCornerShape(40.dp))
                        .background(if (gewaehlt) Color(0xFFBDBDBD) else Color(0xFFEBEBEB))
                        .clickable(role = Role.RadioButton) { groesse = wert }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text(name, color = KartenText, fontSize = 19.sp) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RundTaste("Abbrechen", onClick = ::schliessen)
            if (vorhanden != null) RundTaste("Löschen", warnung = true) { text = ""; uebernehmen() }
            RundTaste("Fertig", hervorgehoben = true, onClick = ::uebernehmen)
        }
    }
}
