package de.oejendorferdamm.dammboard.ui.helfer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Buchstaben nachspuren: ein großer Buchstabe in Grundschrift-Lineatur, gepunktet vorgezeichnet,
 * mit nummerierten Startpunkten und Richtungspfeilen. Die Kinder spuren mit dem Finger nach;
 * „Vorführen“ zeigt die Schreibrichtung, „Nochmal“ löscht das Nachgespurte.
 *
 * Koordinaten der Buchstaben: y 0 = Oberlinie, 1 = Mittellinie, 2 = Grundlinie, 3 = Unterlinie;
 * x ab 0 in denselben Einheiten. Winkel in Grad, 0° = rechts, positiv im Uhrzeigersinn.
 */

/** Ein Teil eines Strichs: Gerade oder Ellipsenbogen. */
private sealed interface Teil
private data class Gerade(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Teil
private data class Bogen(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val start: Float, val schwenk: Float) : Teil
private data class Punkt(val x: Float, val y: Float) : Teil

/** Ein Strich = ohne Absetzen geschrieben; ein Buchstabe = Striche in Schreibreihenfolge. */
private class Zeichen(val breite: Float, val striche: List<List<Teil>>)

private fun g(x1: Number, y1: Number, x2: Number, y2: Number) = Gerade(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
private fun b(cx: Number, cy: Number, rx: Number, ry: Number, start: Number, schwenk: Number) =
    Bogen(cx.toFloat(), cy.toFloat(), rx.toFloat(), ry.toFloat(), start.toFloat(), schwenk.toFloat())
private fun p(x: Number, y: Number) = Punkt(x.toFloat(), y.toFloat())
private fun z(breite: Number, vararg striche: List<Teil>) = Zeichen(breite.toFloat(), striche.toList())
private fun s(vararg teile: Teil) = teile.toList()

private val GROSS: Map<Char, Zeichen> = mapOf(
    'A' to z(1.4, s(g(0.7, 0, 0, 2)), s(g(0.7, 0, 1.4, 2)), s(g(0.3, 1.25, 1.1, 1.25))),
    'B' to z(1.15, s(g(0, 0, 0, 2)), s(g(0, 0, 0.6, 0), b(0.6, 0.5, 0.5, 0.5, -90, 180), g(0.6, 1, 0, 1)),
        s(g(0, 1, 0.65, 1), b(0.65, 1.5, 0.5, 0.5, -90, 180), g(0.65, 2, 0, 2))),
    'C' to z(1.5, s(b(0.85, 1, 0.85, 1, -40, -280))),
    'D' to z(1.3, s(g(0, 0, 0, 2)), s(g(0, 0, 0.4, 0), b(0.4, 1, 0.85, 1, -90, 180), g(0.4, 2, 0, 2))),
    'E' to z(1.05, s(g(0, 0, 0, 2)), s(g(0, 0, 1, 0)), s(g(0, 1, 0.8, 1)), s(g(0, 2, 1, 2))),
    'F' to z(1.0, s(g(0, 0, 0, 2)), s(g(0, 0, 1, 0)), s(g(0, 1, 0.8, 1))),
    'G' to z(1.55, s(b(0.85, 1, 0.85, 1, -40, -310), g(1.7, 1.15, 1.7, 1), g(1.7, 1, 1.05, 1))),
    'H' to z(1.2, s(g(0, 0, 0, 2)), s(g(1.2, 0, 1.2, 2)), s(g(0, 1, 1.2, 1))),
    'I' to z(0.3, s(g(0.15, 0, 0.15, 2))),
    'J' to z(0.95, s(g(0.85, 0, 0.85, 1.5), b(0.45, 1.5, 0.4, 0.5, 0, 180))),
    'K' to z(1.15, s(g(0, 0, 0, 2)), s(g(1.1, 0, 0.05, 1.05), g(0.05, 1.05, 1.15, 2))),
    'L' to z(0.95, s(g(0, 0, 0, 2), g(0, 2, 0.95, 2))),
    'M' to z(1.5, s(g(0, 0, 0, 2)), s(g(0, 0, 0.75, 1.35), g(0.75, 1.35, 1.5, 0), g(1.5, 0, 1.5, 2))),
    'N' to z(1.25, s(g(0, 0, 0, 2)), s(g(0, 0, 1.25, 2), g(1.25, 2, 1.25, 0))),
    'O' to z(1.8, s(b(0.9, 1, 0.9, 1, -90, -360))),
    'P' to z(1.1, s(g(0, 0, 0, 2)), s(g(0, 0, 0.55, 0), b(0.55, 0.5, 0.5, 0.5, -90, 180), g(0.55, 1, 0, 1))),
    'Q' to z(1.85, s(b(0.9, 1, 0.9, 1, -90, -360)), s(g(1.0, 1.45, 1.75, 2.15))),
    'R' to z(1.2, s(g(0, 0, 0, 2)), s(g(0, 0, 0.55, 0), b(0.55, 0.5, 0.5, 0.5, -90, 180), g(0.55, 1, 0, 1)), s(g(0.5, 1, 1.15, 2))),
    'S' to z(1.25, s(b(0.6, 0.5, 0.6, 0.5, -25, -245), b(0.6, 1.5, 0.62, 0.5, -90, 250))),
    'T' to z(1.3, s(g(0.65, 0, 0.65, 2)), s(g(0, 0, 1.3, 0))),
    'U' to z(1.25, s(g(0, 0, 0, 1.35), b(0.625, 1.35, 0.625, 0.65, 180, -180), g(1.25, 1.35, 1.25, 0))),
    'V' to z(1.35, s(g(0, 0, 0.675, 2), g(0.675, 2, 1.35, 0))),
    'W' to z(1.9, s(g(0, 0, 0.45, 2), g(0.45, 2, 0.95, 0.7), g(0.95, 0.7, 1.45, 2), g(1.45, 2, 1.9, 0))),
    'X' to z(1.25, s(g(0, 0, 1.25, 2)), s(g(1.25, 0, 0, 2))),
    'Y' to z(1.3, s(g(0, 0, 0.65, 1)), s(g(1.3, 0, 0.65, 1), g(0.65, 1, 0.65, 2))),
    'Z' to z(1.2, s(g(0, 0, 1.2, 0), g(1.2, 0, 0, 2), g(0, 2, 1.2, 2))),
)

private val KLEIN: Map<Char, Zeichen> = mapOf(
    'a' to z(1.05, s(b(0.5, 1.5, 0.5, 0.5, -20, -340)), s(g(1.0, 1, 1.0, 2))),
    'b' to z(1.05, s(g(0, 0, 0, 2)), s(b(0.5, 1.5, 0.5, 0.5, 180, 360))),
    'c' to z(1.0, s(b(0.52, 1.5, 0.52, 0.5, -40, -280))),
    'd' to z(1.05, s(b(0.5, 1.5, 0.5, 0.5, -20, -340)), s(g(1.0, 0, 1.0, 2))),
    'e' to z(1.05, s(g(0.02, 1.5, 1.02, 1.5), b(0.52, 1.5, 0.5, 0.5, 0, -310))),
    'f' to z(0.75, s(b(0.55, 0.4, 0.3, 0.33, -40, -140), g(0.25, 0.4, 0.25, 2)), s(g(0, 1, 0.6, 1))),
    'g' to z(1.05, s(b(0.5, 1.5, 0.5, 0.5, -20, -340)), s(g(1.0, 1, 1.0, 2.5), b(0.55, 2.5, 0.45, 0.45, 0, 180))),
    'h' to z(0.95, s(g(0, 0, 0, 2)), s(b(0.47, 1.45, 0.47, 0.45, 180, 180), g(0.94, 1.45, 0.94, 2))),
    'i' to z(0.3, s(g(0.15, 1, 0.15, 2)), s(p(0.15, 0.55))),
    'j' to z(0.75, s(g(0.55, 1, 0.55, 2.6), b(0.25, 2.6, 0.3, 0.35, 0, 170)), s(p(0.55, 0.55))),
    'k' to z(0.9, s(g(0, 0, 0, 2)), s(g(0.8, 1, 0.03, 1.5), g(0.03, 1.5, 0.85, 2))),
    'l' to z(0.3, s(g(0.15, 0, 0.15, 2))),
    'm' to z(1.5, s(g(0, 1, 0, 2)), s(b(0.375, 1.4, 0.375, 0.4, 180, 180), g(0.75, 1.4, 0.75, 2)),
        s(b(1.125, 1.4, 0.375, 0.4, 180, 180), g(1.5, 1.4, 1.5, 2))),
    'n' to z(0.95, s(g(0, 1, 0, 2)), s(b(0.47, 1.45, 0.47, 0.45, 180, 180), g(0.94, 1.45, 0.94, 2))),
    'o' to z(1.05, s(b(0.52, 1.5, 0.52, 0.5, -90, -360))),
    'p' to z(1.05, s(g(0, 1, 0, 3)), s(b(0.5, 1.5, 0.5, 0.5, 180, 360))),
    'q' to z(1.05, s(b(0.5, 1.5, 0.5, 0.5, -20, -340)), s(g(1.0, 1, 1.0, 3))),
    'r' to z(0.75, s(g(0, 1, 0, 2)), s(b(0.42, 1.42, 0.42, 0.42, 180, 135))),
    's' to z(0.85, s(b(0.42, 1.25, 0.4, 0.25, -25, -245), b(0.42, 1.75, 0.42, 0.25, -90, 250))),
    't' to z(0.75, s(g(0.28, 0.35, 0.28, 1.7), b(0.6, 1.7, 0.32, 0.3, 180, -130)), s(g(0, 1, 0.65, 1))),
    'u' to z(0.95, s(g(0, 1, 0, 1.55), b(0.47, 1.55, 0.47, 0.45, 180, -180), g(0.94, 1.55, 0.94, 1)), s(g(0.94, 1, 0.94, 2))),
    'v' to z(1.0, s(g(0, 1, 0.5, 2), g(0.5, 2, 1.0, 1))),
    'w' to z(1.45, s(g(0, 1, 0.35, 2), g(0.35, 2, 0.72, 1.3), g(0.72, 1.3, 1.09, 2), g(1.09, 2, 1.45, 1))),
    'x' to z(0.95, s(g(0, 1, 0.95, 2)), s(g(0.95, 1, 0, 2))),
    'y' to z(1.0, s(g(0, 1, 0.5, 2)), s(g(1.0, 1, 0.2, 3))),
    'z' to z(0.95, s(g(0, 1, 0.95, 1), g(0.95, 1, 0, 2), g(0, 2, 0.95, 2))),
)

/** Umlaute = Grundbuchstabe + zwei Punkte. */
private fun mitPunkten(basis: Zeichen, y: Float, links: Float, rechts: Float) =
    Zeichen(basis.breite, basis.striche + listOf(s(p(links, y)), s(p(rechts, y))))

private val ALLE: Map<Char, Zeichen> = GROSS + KLEIN + mapOf(
    'Ä' to mitPunkten(GROSS.getValue('A'), -0.35f, 0.45f, 0.95f),
    'Ö' to mitPunkten(GROSS.getValue('O'), -0.35f, 0.6f, 1.2f),
    'Ü' to mitPunkten(GROSS.getValue('U'), -0.35f, 0.35f, 0.9f),
    'ä' to mitPunkten(KLEIN.getValue('a'), 0.55f, 0.3f, 0.75f),
    'ö' to mitPunkten(KLEIN.getValue('o'), 0.55f, 0.3f, 0.75f),
    'ü' to mitPunkten(KLEIN.getValue('u'), 0.55f, 0.25f, 0.7f),
)

private val REIHE_GROSS = "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜ".toList()
private val REIHE_KLEIN = "abcdefghijklmnopqrstuvwxyzäöü".toList()

/** Jeder Strich als dichte Punktfolge (Buchstaben-Einheiten). */
private fun abtasten(striche: List<List<Teil>>): List<List<Offset>> = striche.map { teile ->
    val punkte = mutableListOf<Offset>()
    for (t in teile) when (t) {
        is Punkt -> punkte += Offset(t.x, t.y)
        is Gerade -> {
            val n = (hypot(t.x2 - t.x1, t.y2 - t.y1) * 40).toInt().coerceAtLeast(2)
            for (i in 0..n) punkte += Offset(t.x1 + (t.x2 - t.x1) * i / n, t.y1 + (t.y2 - t.y1) * i / n)
        }
        is Bogen -> {
            val n = (kotlin.math.abs(t.schwenk) / 4).toInt().coerceAtLeast(4)
            for (i in 0..n) {
                val w = Math.toRadians((t.start + t.schwenk * i / n).toDouble())
                punkte += Offset(t.cx + t.rx * cos(w).toFloat(), t.cy + t.ry * sin(w).toFloat())
            }
        }
    }
    punkte
}

private val Lila = Color(0xFF6A4FB6)
private val StartGruen = Color(0xFF2E9E4F)
private val Hilfslinie = Color(0xFFB9B9B4)

@Composable
internal fun NachspurenHelfer(onVerschieben: (Offset) -> Unit, onSchliessen: () -> Unit, modifier: Modifier) {
    var gross by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }
    val reihe = if (gross) REIHE_GROSS else REIHE_KLEIN
    val zeichen = reihe[index.coerceIn(0, reihe.size - 1)]
    val linien = remember { mutableStateListOf<List<Offset>>() }   // Nachgespurtes (Bildschirmpunkte)
    val aktuell = remember { mutableStateListOf<Offset>() }
    var vorfuehren by remember { mutableIntStateOf(0) }
    var fortschritt by remember { mutableFloatStateOf(1f) }        // 1 = Vorlage komplett gezeigt
    var bewertung by remember { mutableStateOf<String?>(null) }
    // Umrechnung Buchstaben-Einheiten -> Bildpunkte, beim Zeichnen gemerkt (für die Bewertung)
    val masse = remember { Masse() }

    fun neu() { linien.clear(); aktuell.clear(); bewertung = null; fortschritt = 1f }

    // Vorführen: die Vorlage wird in Schreibrichtung nacheinander gezeichnet.
    LaunchedEffect(vorfuehren) {
        if (vorfuehren == 0) return@LaunchedEffect
        neu()
        fortschritt = 0f
        val schritte = 90
        for (i in 1..schritte) { fortschritt = i / schritte.toFloat(); delay(28) }
    }

    HelferKarte("Nachspuren", onVerschieben, onSchliessen, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(620.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                HelferTaste("‹", aktiviert = index > 0) { index--; neu() }
                Text("$zeichen", color = HelferText, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                HelferTaste("›", aktiviert = index < reihe.size - 1) { index++; neu() }
                HelferTaste(if (gross) "abc" else "ABC") {
                    val neuReihe = if (gross) REIHE_KLEIN else REIHE_GROSS
                    gross = !gross
                    index = index.coerceIn(0, neuReihe.size - 1)
                    neu()
                }
                HelferTaste("Vorführen", hervorgehoben = true) { vorfuehren++ }
                HelferTaste("Nochmal") { neu() }
            }
            Abstand(10)
            // Alle Buchstaben zum direkten Wählen
            Row(Modifier.width(620.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                reihe.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (i == index) HelferAkzent else HelferKnopf)
                            .clickable(role = Role.Button) { index = i; neu() },
                        contentAlignment = Alignment.Center
                    ) { Text("$c", color = if (i == index) Color.White else HelferText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            Abstand(12)
            val vorlage = ALLE.getValue(zeichen)
            val spuren = remember(zeichen) { abtasten(vorlage.striche) }
            Box(Modifier.width(620.dp).size(width = 620.dp, height = 470.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                Canvas(
                    Modifier.width(620.dp).size(width = 620.dp, height = 470.dp)
                        .pointerInput(zeichen) {
                            detectDragGestures(
                                onDragStart = { aktuell.clear(); aktuell.add(it); bewertung = null },
                                onDragEnd = { if (aktuell.size > 1) linien.add(aktuell.toList()); aktuell.clear() },
                                onDragCancel = { aktuell.clear() },
                                onDrag = { change, _ -> change.consume(); aktuell.add(change.position) }
                            )
                        }
                        .pointerInput(zeichen) { detectTapGestures { linien.add(listOf(it)); bewertung = null } }
                ) {
                    // Maßstab: 3 Einheiten (Ober- bis Unterlinie) + Rand oben für Umlaut-Punkte
                    val einheit = size.height / 3.7f
                    val oben = einheit * 0.45f
                    val links = (size.width - vorlage.breite * einheit) / 2f
                    masse.einheit = einheit; masse.oben = oben; masse.links = links
                    val zuBild = { o: Offset -> Offset(links + o.x * einheit, oben + o.y * einheit) }

                    lineatur(oben, einheit)

                    val bildSpuren = spuren.map { it.map(zuBild) }
                    val gesamt = bildSpuren.sumOf { it.size }
                    var bisher = 0
                    // Startpunkte, die (fast) aufeinanderliegen, nebeneinander rücken (z. B. A oben, g rechts)
                    val marken = startMarken(bildSpuren.map { it.first() }, einheit * 0.3f)
                    bildSpuren.forEachIndexed { nr, spur ->
                        // gepunktete Vorlage
                        zeichneSpur(spur, Hilfslinie, einheit * 0.11f, PathEffect.dashPathEffect(floatArrayOf(1f, einheit * 0.2f)))
                        // beim Vorführen: bis zum Fortschritt farbig
                        val sichtbar = ((fortschritt * gesamt).toInt() - bisher).coerceIn(0, spur.size)
                        if (fortschritt < 1f && sichtbar > 0) {
                            zeichneSpur(spur.take(sichtbar), Lila, einheit * 0.12f, null)
                            drawCircle(Lila, einheit * 0.12f, spur[sichtbar - 1])
                        }
                        bisher += spur.size
                        // Startpunkt mit Nummer und Richtungspfeil
                        val start = marken[nr]
                        drawCircle(StartGruen, einheit * 0.13f, start)
                        drawContext.canvas.nativeCanvas.drawText(
                            "${nr + 1}", start.x, start.y + einheit * 0.075f,
                            android.graphics.Paint().apply {
                                color = Color.White.toArgb(); textSize = einheit * 0.2f; isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
                            }
                        )
                        if (spur.size > 6) pfeil(spur, StartGruen, einheit)
                    }
                    // Nachgespurtes
                    for (l in linien) zeichneSpur(l, HelferAkzent, einheit * 0.14f, null, punktWennEinzeln = true)
                    if (aktuell.size > 1) zeichneSpur(aktuell.toList(), HelferAkzent, einheit * 0.14f, null)
                }
                bewertung?.let {
                    Text(it, color = HelferAkzent, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))
                }
            }
            Abstand(10)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                HelferTaste("Fertig – prüfen", aktiviert = linien.isNotEmpty()) {
                    bewertung = bewerte(spuren, linien, masse)
                }
                Text("Grüner Punkt = Start, Pfeil = Richtung", color = HelferTextSchwach, fontSize = 17.sp)
            }
        }
    }
}

/** Vier Linien wie im Grundschrift-Heft: Ober-, Mittel- (gestrichelt), Grund- (kräftig) und Unterlinie. */
private fun DrawScope.lineatur(oben: Float, einheit: Float) {
    for (i in 0..3) {
        val y = oben + i * einheit
        drawLine(
            if (i == 2) Color(0xFF8C8C86) else Hilfslinie, Offset(0f, y), Offset(size.width, y),
            strokeWidth = if (i == 2) 3f else 2f,
            pathEffect = if (i == 1) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
        )
    }
}

private fun DrawScope.zeichneSpur(spur: List<Offset>, farbe: Color, breite: Float, muster: PathEffect?, punktWennEinzeln: Boolean = true) {
    if (spur.size == 1) { if (punktWennEinzeln) drawCircle(farbe, breite * 0.8f, spur[0]); return }
    val pfad = Path().apply { moveTo(spur[0].x, spur[0].y); for (i in 1 until spur.size) lineTo(spur[i].x, spur[i].y) }
    drawPath(pfad, farbe, style = Stroke(breite, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = muster))
}

/** Kleiner Pfeil kurz hinter dem Start, in Schreibrichtung. */
private fun DrawScope.pfeil(spur: List<Offset>, farbe: Color, einheit: Float) {
    val i = (spur.size / 3).coerceAtLeast(3)
    val a = spur[i - 3]; val e = spur[i]
    val w = atan2(e.y - a.y, e.x - a.x)
    val l = einheit * 0.2f
    val seite = Offset(-sin(w), cos(w)) * (einheit * 0.2f)
    val spitze = e + Offset(cos(w), sin(w)) * l
    val pfad = Path().apply {
        moveTo(spitze.x, spitze.y)
        lineTo(e.x + seite.x * 0.6f, e.y + seite.y * 0.6f)
        lineTo(e.x - seite.x * 0.6f, e.y - seite.y * 0.6f)
        close()
    }
    drawPath(pfad, farbe)
}

/** Position der Startnummern: liegt ein Start dicht an einem früheren, wird er seitlich versetzt. */
private fun startMarken(starts: List<Offset>, abstand: Float): List<Offset> {
    val marken = mutableListOf<Offset>()
    for (st in starts) {
        var p = st
        var versuch = 1
        while (marken.any { (it - p).getDistance() < abstand } && versuch < 6) {
            p = st + Offset(if (versuch % 2 == 1) abstand * ((versuch + 1) / 2) else -abstand * (versuch / 2), 0f)
            versuch++
        }
        marken += p
    }
    return marken
}

private class Masse { var einheit = 0f; var oben = 0f; var links = 0f }

/**
 * Wie gut wurde nachgespurt? Abdeckung = Anteil der Vorlage, an dem ein Strich vorbeikam;
 * Genauigkeit = Anteil des Gespurten, der auf der Vorlage liegt. Das Schlechtere zählt.
 */
private fun bewerte(spuren: List<List<Offset>>, linien: List<List<Offset>>, m: Masse): String {
    if (m.einheit <= 0f) return ""
    val vorlage = spuren.flatten().map { Offset(m.links + it.x * m.einheit, m.oben + it.y * m.einheit) }
    val gespurt = linien.flatten()
    if (vorlage.isEmpty() || gespurt.isEmpty()) return ""
    val toleranz = m.einheit * 0.28f
    fun nah(p: Offset, liste: List<Offset>) = liste.any { (it - p).getDistance() < toleranz }
    val abdeckung = vorlage.count { nah(it, gespurt) } / vorlage.size.toFloat()
    val genauigkeit = gespurt.count { nah(it, vorlage) } / gespurt.size.toFloat()
    val wert = minOf(abdeckung, genauigkeit)
    return when {
        wert >= 0.85f -> "⭐⭐⭐ Super!"
        wert >= 0.65f -> "⭐⭐ Gut!"
        else -> "⭐ Nochmal?"
    }
}
