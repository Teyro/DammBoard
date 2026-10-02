package de.oejendorferdamm.dammboard.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/*
 * Vorlagen für die Grundschule: Schreiblineaturen (Klasse 1, 2 und 3/4), Hundertertafel und
 * Zahlenstrahl. Alles bleibt oberhalb der Werkzeugleiste, damit nichts verdeckt wird.
 */

/** Unterer Rand, den Vorlagen frei lassen (Werkzeugleiste). */
private const val LEISTEN_RAND = 120f

private val vorlagenPinsel = ThreadLocal.withInitial {
    android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
}

/**
 * Vierlinien-System: Ober-, Mittel- und Unterband gleich hoch, das Mittelband leicht
 * hinterlegt. [band] = Höhe eines Bandes. Bei [nurGrundlinie] (Klasse 3/4) nur Grund- und
 * gestrichelte Mittellinie.
 */
internal fun DrawScope.zeichneLineatur(band: Float, linie: Color, flaeche: Color, p: Float, nurGrundlinie: Boolean = false) {
    val randX = 50f * p
    val unten = size.height - LEISTEN_RAND * p
    val gruppe = if (nurGrundlinie) band * 2 else band * 3
    val abstand = if (nurGrundlinie) band * 1.1f else band * 1.2f
    var y = 50f * p
    val mitteEffekt = PathEffect.dashPathEffect(floatArrayOf(10f * p, 8f * p), 0f)
    while (y + gruppe <= unten) {
        if (nurGrundlinie) {
            // Mittellinie gestrichelt, Grundlinie kräftig
            // Als Pfad: Android 8 ignoriert die Strichelung bei drawLine.
            val mitte = Path().apply { moveTo(randX, y + band); lineTo(size.width - randX, y + band) }
            drawPath(mitte, linie, style = Stroke(width = 1.3f * p, pathEffect = mitteEffekt))
            drawLine(linie, Offset(randX, y + gruppe), Offset(size.width - randX, y + gruppe), strokeWidth = 2.2f * p)
        } else {
            drawRect(flaeche, topLeft = Offset(randX, y + band), size = Size(size.width - 2 * randX, band))
            drawLine(linie, Offset(randX, y), Offset(size.width - randX, y), strokeWidth = 1.3f * p)
            drawLine(linie, Offset(randX, y + band), Offset(size.width - randX, y + band), strokeWidth = 1.6f * p)
            drawLine(linie, Offset(randX, y + 2 * band), Offset(size.width - randX, y + 2 * band), strokeWidth = 2.4f * p)
            drawLine(linie, Offset(randX, y + 3 * band), Offset(size.width - randX, y + 3 * band), strokeWidth = 1.3f * p)
        }
        y += gruppe + abstand
    }
}

/** Hundertertafel 1–100 mit Fünfer-Gliederung (dickere Linien nach der 5. Spalte/Zeile). */
internal fun DrawScope.zeichneHundertertafel(linie: Color, text: Color, p: Float) {
    val oben = 40f * p
    val unten = size.height - LEISTEN_RAND * p
    val zelle = minOf((size.width - 120f * p) / 10f, (unten - oben) / 10f)
    if (zelle <= 0f) return
    val x0 = (size.width - zelle * 10) / 2
    val y0 = oben + ((unten - oben) - zelle * 10) / 2
    for (i in 0..10) {
        val dick = (if (i == 0 || i == 10) 3f else if (i == 5) 2.6f else 1.3f) * p
        drawLine(linie, Offset(x0 + i * zelle, y0), Offset(x0 + i * zelle, y0 + 10 * zelle), strokeWidth = dick)
        drawLine(linie, Offset(x0, y0 + i * zelle), Offset(x0 + 10 * zelle, y0 + i * zelle), strokeWidth = dick)
    }
    val pinsel = vorlagenPinsel.get()!!
    pinsel.color = text.toArgb()
    pinsel.textSize = zelle * 0.4f
    val leinwand = drawContext.canvas.nativeCanvas
    for (zahl in 1..100) {
        val spalte = (zahl - 1) % 10
        val zeile = (zahl - 1) / 10
        leinwand.drawText(zahl.toString(), x0 + (spalte + 0.5f) * zelle, y0 + (zeile + 0.5f) * zelle + pinsel.textSize * 0.36f, pinsel)
    }
}

/** Zwei Zahlenstrahle: 0–20 (jede Zahl beschriftet) und 0–100 (Zehner beschriftet). */
internal fun DrawScope.zeichneZahlenstrahl(linie: Color, text: Color, p: Float) {
    val pinsel = vorlagenPinsel.get()!!
    pinsel.color = text.toArgb()
    val leinwand = drawContext.canvas.nativeCanvas
    val randX = 80f * p
    val laenge = size.width - 2 * randX - 30f * p
    val hoehe = size.height - LEISTEN_RAND * p

    fun strahl(y: Float, bis: Int, beschriftung: (Int) -> Boolean, mittel: (Int) -> Boolean, lang: (Int) -> Boolean, schrift: Float) {
        drawLine(linie, Offset(randX, y), Offset(randX + laenge + 24f * p, y), strokeWidth = 2.6f * p)
        // Pfeilspitze
        val spitze = Offset(randX + laenge + 30f * p, y)
        drawLine(linie, spitze, spitze + Offset(-16f * p, -9f * p), strokeWidth = 2.6f * p)
        drawLine(linie, spitze, spitze + Offset(-16f * p, 9f * p), strokeWidth = 2.6f * p)
        pinsel.textSize = schrift
        for (n in 0..bis) {
            val x = randX + laenge * n / bis
            val strich = when {
                lang(n) -> 22f
                mittel(n) -> 15f
                else -> 9f
            } * p
            drawLine(linie, Offset(x, y - strich), Offset(x, y + strich), strokeWidth = (if (lang(n)) 2.4f else 1.5f) * p)
            if (beschriftung(n)) leinwand.drawText(n.toString(), x, y + strich + schrift * 1.05f, pinsel)
        }
    }

    strahl(hoehe * 0.3f, 20, { true }, { it % 5 == 0 }, { it % 10 == 0 }, 26f * p)
    strahl(hoehe * 0.72f, 100, { it % 10 == 0 }, { it % 5 == 0 }, { it % 10 == 0 }, 24f * p)
}

