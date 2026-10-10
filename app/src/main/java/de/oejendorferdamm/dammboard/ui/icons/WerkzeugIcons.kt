package de.oejendorferdamm.dammboard.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.GeometrieWerkzeug
import de.oejendorferdamm.dammboard.model.RadiererGroesse
import de.oejendorferdamm.dammboard.model.Werkzeug
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Alle Symbole werden von Hand gezeichnet, beschrieben in einem virtuellen 24×24-Raster (wie
 * die üblichen Material-Symbole). Umgerechnet wird aber NICHT per Skalierung der Zeichenfläche:
 * Android 8 rendert Formen unter einer Skalierung klein und zieht das Bild dann hoch – auf den
 * alten CTOUCH-Boards sahen die Knöpfe dadurch unscharf aus. Stattdessen rechnet [Raster] jede
 * Form vorher in echte Bildschirmpixel um und zeichnet sie in voller Auflösung.
 */

private const val SYMBOL_RASTER = 24f

private val STRICHELUNG = floatArrayOf(3.0f, 2.4f)
private val PUNKTIERUNG = floatArrayOf(0.01f, 2.7f)

/** Zeichenstil im Raster: gefüllt oder als Linie (Maße in Rastereinheiten). */
internal sealed interface Stil
internal object Fuellen : Stil
internal class Linie(
    val breite: Float,
    val cap: StrokeCap = StrokeCap.Round,
    val join: StrokeJoin = StrokeJoin.Round,
    val muster: FloatArray? = null
) : Stil

internal fun kontur(breite: Float, muster: FloatArray? = null): Linie =
    Linie(breite, cap = if (muster != null && muster !== PUNKTIERUNG) StrokeCap.Butt else StrokeCap.Round, muster = muster)

/**
 * Zeichnet im 24er-Raster, rechnet aber jede Form direkt in Bildschirmpixel um (siehe oben).
 * Bietet dieselben Grundformen wie DrawScope, damit die Symbole gut lesbar bleiben.
 */
internal class Raster(private val flaeche: DrawScope, einheit: Float, val w: Float, val h: Float) {
    private val matrix = android.graphics.Matrix().apply { setScale(einheit, einheit) }
    private var faktor = einheit

    private fun mitTransformation(extraFaktor: Float, aendere: android.graphics.Matrix.() -> Unit, block: Raster.() -> Unit) {
        val vorher = android.graphics.Matrix(matrix)
        val vorherFaktor = faktor
        matrix.aendere()
        faktor *= extraFaktor
        block()
        matrix.set(vorher)
        faktor = vorherFaktor
    }

    fun rotate(degrees: Float, pivot: Offset, block: Raster.() -> Unit) =
        mitTransformation(1f, { preRotate(degrees, pivot.x, pivot.y) }, block)

    fun scale(scale: Float, pivot: Offset, block: Raster.() -> Unit) =
        mitTransformation(scale, { preScale(scale, scale, pivot.x, pivot.y) }, block)

    fun drawPath(pfad: Path, color: Color, style: Stil = Fuellen) {
        val echt = android.graphics.Path(pfad.asAndroidPath())
        echt.transform(matrix)
        val composePfad = echt.asComposePath()
        when (style) {
            is Fuellen -> flaeche.drawPath(composePfad, color)
            is Linie -> flaeche.drawPath(
                composePfad, color,
                style = Stroke(
                    width = style.breite * faktor, cap = style.cap, join = style.join,
                    pathEffect = style.muster?.let { m -> PathEffect.dashPathEffect(FloatArray(m.size) { m[it] * faktor }, 0f) }
                )
            )
        }
    }

    fun drawLine(color: Color, start: Offset, end: Offset, strokeWidth: Float, cap: StrokeCap = StrokeCap.Round) =
        drawPath(Path().apply { moveTo(start.x, start.y); lineTo(end.x, end.y) }, color, Linie(strokeWidth, cap = cap))

    fun drawCircle(color: Color, radius: Float, center: Offset, style: Stil = Fuellen) =
        drawPath(Path().apply { addOval(Rect(center, radius)) }, color, style)

    fun drawOval(color: Color, topLeft: Offset, size: Size, style: Stil = Fuellen) =
        drawPath(Path().apply { addOval(Rect(topLeft, size)) }, color, style)

    fun drawRect(color: Color, topLeft: Offset, size: Size, style: Stil = Fuellen) =
        drawPath(Path().apply { addRect(Rect(topLeft, size)) }, color, style)

    fun drawRoundRect(color: Color, topLeft: Offset, size: Size, cornerRadius: CornerRadius, style: Stil = Fuellen) =
        drawPath(Path().apply { addRoundRect(RoundRect(Rect(topLeft, size), cornerRadius)) }, color, style)

    fun drawArc(
        color: Color, startAngle: Float, sweepAngle: Float, useCenter: Boolean,
        topLeft: Offset, size: Size, style: Stil = Fuellen
    ) = drawPath(
        Path().apply {
            if (useCenter) moveTo(topLeft.x + size.width / 2, topLeft.y + size.height / 2)
            arcTo(Rect(topLeft, size), startAngle, sweepAngle, forceMoveTo = !useCenter)
            if (useCenter) close()
        },
        color, style
    )
}

/** Stellt ein [Raster] für diese Zeichenfläche bereit (w/h = Rastermaße der Fläche). */
internal fun DrawScope.symbolRaster(zeichnen: Raster.(w: Float, h: Float) -> Unit) {
    val einheit = size.minDimension / SYMBOL_RASTER
    if (einheit <= 0f) return
    val raster = Raster(this, einheit, size.width / einheit, size.height / einheit)
    raster.zeichnen(raster.w, raster.h)
}

private fun Raster.linie(a: Offset, b: Offset, tint: Color, breite: Float = 1.9f, gestrichelt: Boolean = false) {
    // Gestrichelt mit geraden Enden – runde Enden würden die kleinen Lücken im Symbol zudecken.
    drawPath(
        Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y) }, tint,
        if (gestrichelt) kontur(breite, STRICHELUNG) else Linie(breite)
    )
}

// ---------------------------------------------------------------------------------------------
// Werkzeuge der unteren Leiste
// ---------------------------------------------------------------------------------------------

@Composable
fun WerkzeugSymbol(werkzeug: Werkzeug, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { _, _ ->
            when (werkzeug) {
                Werkzeug.STIFT -> stiftUmriss(tint, halbeBreite = 2.1f)
                Werkzeug.FORMEN -> formenSymbol(tint)
                Werkzeug.RADIERER -> {
                    radiererForm(tint)
                    linie(Offset(8f, 20.6f), Offset(20.5f, 20.6f), tint, 1.7f)
                }
                Werkzeug.LASSO -> lassoSymbol(tint)
                Werkzeug.GEOMETRIE -> geometrieSymbol(tint)
                Werkzeug.AUSWAHL -> auswahlSymbol(tint)
                Werkzeug.WERKZEUGKASTEN -> koffer(tint)
            }
        }
    }
}

/** Schlanker Bleistift, Spitze unten links. */
private fun Raster.stiftUmriss(tint: Color, halbeBreite: Float) {
    val spitze = Offset(4.4f, 19.6f)
    val ende = Offset(18.8f, 5.2f)
    val achse = ende - spitze
    val richtung = achse / achse.getDistance()
    val normale = Offset(-richtung.y, richtung.x)
    val kegelEnde = spitze + richtung * 4.4f
    val band = ende - richtung * 3.4f
    val umriss = Path().apply {
        moveTo(spitze.x, spitze.y)
        val a = kegelEnde + normale * halbeBreite
        val b = ende + normale * halbeBreite
        val c = ende - normale * halbeBreite
        val d = kegelEnde - normale * halbeBreite
        lineTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    drawPath(umriss, tint, style = kontur(1.7f))
    linie(kegelEnde + normale * halbeBreite, kegelEnde - normale * halbeBreite, tint, 1.4f)
    linie(band + normale * halbeBreite, band - normale * halbeBreite, tint, 1.4f)
}

/** Umrandetes Quadrat, darüber ein ausgefüllter Kreis – wie im Original. */
private fun Raster.formenSymbol(tint: Color) {
    drawRect(tint, topLeft = Offset(3.6f, 3.6f), size = Size(10.6f, 10.6f), style = kontur(1.8f))
    drawCircle(tint, radius = 5.3f, center = Offset(15.8f, 15.8f))
}

/** Schräg liegender Radiergummi, die untere Hälfte ausgefüllt. */
private fun Raster.radiererForm(tint: Color) {
    rotate(degrees = -40f, pivot = Offset(12f, 11.5f)) {
        drawRoundRect(
            tint, topLeft = Offset(4.4f, 8.2f), size = Size(6.8f, 6.6f),
            cornerRadius = CornerRadius(1.4f)
        )
        drawRoundRect(
            tint, topLeft = Offset(4.4f, 8.2f), size = Size(15.2f, 6.6f),
            cornerRadius = CornerRadius(1.6f), style = kontur(1.7f)
        )
    }
}

/** Gepunktete Schlinge mit kleinem Knoten unten. */
private fun Raster.lassoSymbol(tint: Color) {
    drawOval(
        tint, topLeft = Offset(2.4f, 3.4f), size = Size(19.2f, 11.2f),
        style = kontur(2.0f, PUNKTIERUNG)
    )
    drawCircle(tint, radius = 1.7f, center = Offset(11.2f, 16.2f), style = kontur(1.4f))
    val schwanz = Path().apply {
        moveTo(11.6f, 17.9f)
        quadraticTo(12.0f, 20.0f, 13.8f, 21.4f)
    }
    drawPath(schwanz, tint, style = kontur(1.5f))
}

/** Geodreieck mit Lineal daneben. */
private fun Raster.geometrieSymbol(tint: Color) {
    val aussen = Path().apply {
        moveTo(3.4f, 4.4f); lineTo(3.4f, 20.6f); lineTo(15.8f, 20.6f); close()
    }
    drawPath(aussen, tint, style = kontur(1.6f))
    val innen = Path().apply {
        moveTo(6.2f, 11.6f); lineTo(6.2f, 17.8f); lineTo(11.6f, 17.8f); close()
    }
    drawPath(innen, tint, style = kontur(1.3f))
    drawRect(tint, topLeft = Offset(17.2f, 3.0f), size = Size(3.6f, 18.0f), style = kontur(1.5f))
    for (i in 0 until 5) {
        val y = 6f + i * 3f
        linie(Offset(17.2f, y), Offset(18.9f, y), tint, 1.2f)
    }
}

/** Abgerundetes Quadrat, unten rechts offen, mit ausgefülltem Mauszeiger. */
private fun Raster.auswahlSymbol(tint: Color) {
    val rahmen = Path().apply {
        moveTo(18f, 10.6f)
        lineTo(18f, 6.6f)
        quadraticTo(18f, 4f, 15.4f, 4f)
        lineTo(6.6f, 4f)
        quadraticTo(4f, 4f, 4f, 6.6f)
        lineTo(4f, 15.4f)
        quadraticTo(4f, 18f, 6.6f, 18f)
        lineTo(10.6f, 18f)
    }
    drawPath(rahmen, tint, style = kontur(1.8f))
    val zeiger = Path().apply {
        moveTo(12.6f, 12.6f)
        lineTo(12.6f, 21.6f)
        lineTo(14.9f, 19.5f)
        lineTo(16.6f, 22.9f)
        lineTo(18.3f, 22.1f)
        lineTo(16.7f, 18.8f)
        lineTo(19.9f, 18.6f)
        close()
    }
    drawPath(zeiger, tint)
    drawPath(zeiger, tint, style = kontur(0.8f))
}

/** Koffer mit Griff und Schloss. */
private fun Raster.koffer(tint: Color) {
    drawRoundRect(
        tint, topLeft = Offset(3f, 7.6f), size = Size(18f, 12.6f),
        cornerRadius = CornerRadius(2f), style = kontur(1.7f)
    )
    val griff = Path().apply {
        moveTo(9f, 7.6f)
        lineTo(9f, 5.7f)
        quadraticTo(9f, 4.5f, 10.2f, 4.5f)
        lineTo(13.8f, 4.5f)
        quadraticTo(15f, 4.5f, 15f, 5.7f)
        lineTo(15f, 7.6f)
    }
    drawPath(griff, tint, style = kontur(1.6f))
    linie(Offset(3f, 13f), Offset(10.2f, 13f), tint, 1.5f)
    linie(Offset(13.8f, 13f), Offset(21f, 13f), tint, 1.5f)
    drawRoundRect(
        tint, topLeft = Offset(10.2f, 11.5f), size = Size(3.6f, 3.0f),
        cornerRadius = CornerRadius(0.6f), style = kontur(1.4f)
    )
}

// ---------------------------------------------------------------------------------------------
// Allgemeine Symbole (linke/rechte Knopfgruppe, Seitenanzeige, Menüs)
// ---------------------------------------------------------------------------------------------

enum class AllgemeinesSymbol {
    SCHLIESSEN, MENUE, TEILEN, PAPIERKORB, RUECKGAENGIG, WIEDERHOLEN, PLUS, PFEIL_LINKS, PFEIL_RECHTS, WUERFEL
}

@Composable
fun AllgemeinSymbol(symbol: AllgemeinesSymbol, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { _, _ ->
            when (symbol) {
                AllgemeinesSymbol.SCHLIESSEN -> {
                    linie(Offset(6.4f, 6.4f), Offset(17.6f, 17.6f), tint, 2.0f)
                    linie(Offset(17.6f, 6.4f), Offset(6.4f, 17.6f), tint, 2.0f)
                }
                AllgemeinesSymbol.MENUE -> {
                    linie(Offset(5f, 7.4f), Offset(19f, 7.4f), tint, 2.0f)
                    linie(Offset(5f, 12f), Offset(19f, 12f), tint, 2.0f)
                    linie(Offset(5f, 16.6f), Offset(19f, 16.6f), tint, 2.0f)
                }
                AllgemeinesSymbol.TEILEN -> {
                    val oben = Offset(17.4f, 6f)
                    val links = Offset(6.6f, 12f)
                    val unten = Offset(17.4f, 18f)
                    val radius = 2.7f
                    drawCircle(tint, radius = radius, center = oben, style = kontur(1.7f))
                    drawCircle(tint, radius = radius, center = links, style = kontur(1.7f))
                    drawCircle(tint, radius = radius, center = unten, style = kontur(1.7f))
                    linie(Offset(9.0f, 10.7f), Offset(15.0f, 7.3f), tint, 1.7f)
                    linie(Offset(9.0f, 13.3f), Offset(15.0f, 16.7f), tint, 1.7f)
                }
                AllgemeinesSymbol.PAPIERKORB -> {
                    linie(Offset(4.4f, 6.4f), Offset(19.6f, 6.4f), tint, 1.8f)
                    val griff = Path().apply {
                        moveTo(9.3f, 6.4f); lineTo(9.8f, 3.9f); lineTo(14.2f, 3.9f); lineTo(14.7f, 6.4f)
                    }
                    drawPath(griff, tint, style = kontur(1.6f))
                    val eimer = Path().apply {
                        moveTo(6.3f, 6.4f); lineTo(7.2f, 20.6f); lineTo(16.8f, 20.6f); lineTo(17.7f, 6.4f)
                    }
                    drawPath(eimer, tint, style = kontur(1.8f))
                    for (x in listOf(10f, 12f, 14f)) linie(Offset(x, 9.6f), Offset(x, 17.6f), tint, 1.3f)
                }
                AllgemeinesSymbol.RUECKGAENGIG -> drawPath(antwortPfeil(gespiegelt = false), tint, style = kontur(1.6f))
                AllgemeinesSymbol.WIEDERHOLEN -> drawPath(antwortPfeil(gespiegelt = true), tint, style = kontur(1.6f))
                AllgemeinesSymbol.PLUS -> {
                    linie(Offset(12f, 4.4f), Offset(12f, 19.6f), tint, 2.5f)
                    linie(Offset(4.4f, 12f), Offset(19.6f, 12f), tint, 2.5f)
                }
                AllgemeinesSymbol.PFEIL_LINKS -> drawPath(
                    Path().apply { moveTo(15f, 4.6f); lineTo(7.6f, 12f); lineTo(15f, 19.4f) },
                    tint, style = kontur(2.2f)
                )
                AllgemeinesSymbol.WUERFEL -> {
                    drawRoundRect(
                        tint, topLeft = Offset(3.4f, 3.4f), size = Size(17.2f, 17.2f),
                        cornerRadius = CornerRadius(3.6f), style = kontur(1.8f)
                    )
                    for ((x, y) in listOf(8f to 8f, 16f to 8f, 12f to 12f, 8f to 16f, 16f to 16f)) {
                        drawCircle(tint, radius = 1.6f, center = Offset(x, y))
                    }
                }
                AllgemeinesSymbol.PFEIL_RECHTS -> drawPath(
                    Path().apply { moveTo(9f, 4.6f); lineTo(16.4f, 12f); lineTo(9f, 19.4f) },
                    tint, style = kontur(2.2f)
                )
            }
        }
    }
}

/** Umriss-Pfeil wie beim "Antworten"-Symbol (Rückgängig), gespiegelt für Wiederholen. */
private fun antwortPfeil(gespiegelt: Boolean): Path {
    fun x(wert: Float) = if (gespiegelt) 24f - wert else wert
    return Path().apply {
        moveTo(x(10f), 9f)
        lineTo(x(10f), 5f)
        lineTo(x(3f), 12f)
        lineTo(x(10f), 19f)
        lineTo(x(10f), 14.9f)
        cubicTo(x(15f), 14.9f, x(18.5f), 16.5f, x(21f), 20f)
        cubicTo(x(20f), 15f, x(17f), 10f, x(10f), 9f)
        close()
    }
}

// ---------------------------------------------------------------------------------------------
// Symbole in den Panels
// ---------------------------------------------------------------------------------------------

@Composable
fun StiftArtSymbol(fein: Boolean, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { _, _ ->
            if (fein) {
                stiftUmriss(tint, halbeBreite = 1.9f)
            } else {
                // Marker: breiter Körper, schräg abgeschnittene, ausgefüllte Spitze.
                rotate(degrees = -45f, pivot = Offset(12f, 12f)) {
                    drawRoundRect(
                        tint, topLeft = Offset(8.2f, 3.2f), size = Size(7.6f, 12.6f),
                        cornerRadius = CornerRadius(1.6f), style = kontur(1.7f)
                    )
                    linie(Offset(8.2f, 7.2f), Offset(15.8f, 7.2f), tint, 1.4f)
                    val spitze = Path().apply {
                        moveTo(9.4f, 15.8f); lineTo(14.6f, 15.8f); lineTo(13.4f, 20.8f); lineTo(10.6f, 20.0f); close()
                    }
                    drawPath(spitze, tint)
                }
            }
        }
    }
}

@Composable
fun FormSymbol(typ: FormTyp, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { w, h ->
            val strich = kontur(1.6f)
            val gestrichelt = typ == FormTyp.LINIE_GESTRICHELT || typ == FormTyp.PFEIL_GESTRICHELT ||
                typ == FormTyp.DOPPELPFEIL_GESTRICHELT || typ == FormTyp.FREIHANDPFEIL_GESTRICHELT
            when (typ) {
                FormTyp.DREIECK_RECHTS -> drawPath(dreieckPfad(w, h, rechts = true), tint, style = strich)
                FormTyp.DREIECK -> drawPath(dreieckPfad(w, h, rechts = false), tint, style = strich)
                FormTyp.KREIS -> drawCircle(tint, radius = w * 0.36f, center = Offset(w / 2, h / 2), style = strich)
                FormTyp.ELLIPSE -> drawOval(
                    tint, topLeft = Offset(w * 0.12f, h * 0.28f), size = Size(w * 0.76f, h * 0.44f), style = strich
                )
                FormTyp.QUADRAT -> drawRect(
                    tint, topLeft = Offset(w * 0.18f, h * 0.18f), size = Size(w * 0.64f, h * 0.64f), style = strich
                )
                FormTyp.SECHSECK -> drawPath(vieleckPfad(w, h, 6), tint, style = strich)
                FormTyp.ABGERUNDET -> drawRoundRect(
                    tint, topLeft = Offset(w * 0.18f, h * 0.18f), size = Size(w * 0.64f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.18f), style = strich
                )
                FormTyp.FUENFECK -> drawPath(vieleckPfad(w, h, 5), tint, style = strich)
                FormTyp.STERN -> drawPath(sternPfad(w, h), tint, style = strich)
                FormTyp.WELLE -> drawPath(wellenPfad(w, h), tint, style = strich)
                FormTyp.LINIE, FormTyp.LINIE_GESTRICHELT ->
                    linie(Offset(w * 0.18f, h * 0.82f), Offset(w * 0.82f, h * 0.18f), tint, 1.7f, gestrichelt)
                FormTyp.PFEIL, FormTyp.PFEIL_GESTRICHELT -> pfeilSymbol(w, h, tint, doppelt = false, gestrichelt = gestrichelt)
                FormTyp.DOPPELPFEIL, FormTyp.DOPPELPFEIL_GESTRICHELT -> pfeilSymbol(w, h, tint, doppelt = true, gestrichelt = gestrichelt)
                FormTyp.FREIHANDPFEIL, FormTyp.FREIHANDPFEIL_GESTRICHELT -> {
                    val pfad = Path().apply {
                        moveTo(w * 0.16f, h * 0.8f)
                        quadraticTo(w * 0.3f, h * 0.2f, w * 0.82f, h * 0.24f)
                    }
                    drawPath(pfad, tint, style = if (gestrichelt) kontur(1.7f, STRICHELUNG) else kontur(1.7f))
                    pfeilSpitze(Offset(w * 0.82f, h * 0.24f), Offset(w * 0.6f, h * 0.16f), tint)
                }
            }
        }
    }
}

private fun Raster.pfeilSpitze(spitze: Offset, ansatz: Offset, tint: Color) {
    val laenge = 6f
    val winkel = atan2(ansatz.y - spitze.y, ansatz.x - spitze.x)
    val a1 = winkel + Math.PI.toFloat() * 0.2f
    val a2 = winkel - Math.PI.toFloat() * 0.2f
    linie(spitze, spitze + Offset(cos(a1) * laenge, sin(a1) * laenge), tint, 1.7f)
    linie(spitze, spitze + Offset(cos(a2) * laenge, sin(a2) * laenge), tint, 1.7f)
}

private fun Raster.pfeilSymbol(w: Float, h: Float, tint: Color, doppelt: Boolean, gestrichelt: Boolean) {
    val start = Offset(w * 0.16f, h * 0.84f)
    val ende = Offset(w * 0.84f, h * 0.16f)
    linie(start, ende, tint, 1.7f, gestrichelt)
    pfeilSpitze(ende, start, tint)
    if (doppelt) pfeilSpitze(start, ende, tint)
}

private fun dreieckPfad(w: Float, h: Float, rechts: Boolean) = Path().apply {
    if (rechts) {
        moveTo(w * 0.18f, h * 0.85f)
        lineTo(w * 0.18f, h * 0.15f)
        lineTo(w * 0.85f, h * 0.85f)
    } else {
        moveTo(w * 0.5f, h * 0.14f)
        lineTo(w * 0.86f, h * 0.85f)
        lineTo(w * 0.14f, h * 0.85f)
    }
    close()
}

private fun vieleckPfad(w: Float, h: Float, ecken: Int) = Path().apply {
    val cx = w * 0.5f
    val cy = h * 0.52f
    val r = w * 0.36f
    for (i in 0 until ecken) {
        val winkel = -Math.PI / 2 + i * (2 * Math.PI / ecken)
        val x = cx + r * cos(winkel).toFloat()
        val y = cy + r * sin(winkel).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun sternPfad(w: Float, h: Float) = Path().apply {
    val cx = w * 0.5f
    val cy = h * 0.52f
    val rAussen = w * 0.38f
    val rInnen = w * 0.16f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) rAussen else rInnen
        val winkel = -Math.PI / 2 + i * (Math.PI / 5)
        val x = cx + r * cos(winkel).toFloat()
        val y = cy + r * sin(winkel).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun wellenPfad(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.1f, h * 0.5f)
    cubicTo(w * 0.28f, h * 0.15f, w * 0.38f, h * 0.85f, w * 0.55f, h * 0.5f)
    cubicTo(w * 0.68f, h * 0.2f, w * 0.78f, h * 0.8f, w * 0.9f, h * 0.5f)
}

/** Radierer in drei Größen (Panel) – derselbe Radiergummi wie in der Leiste, unterschiedlich groß. */
@Composable
fun RadiererSymbol(groesse: RadiererGroesse, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { _, _ ->
            val faktor = when (groesse) {
                RadiererGroesse.KLEIN -> 0.55f
                RadiererGroesse.MITTEL -> 0.78f
                RadiererGroesse.GROSS -> 1.0f
            }
            scale(scale = faktor, pivot = Offset(12f, 12f)) { radiererForm(tint) }
        }
    }
}

/** "Alles löschen": großer Radiergummi mit Wischlinie, wie das vierte Symbol im Original. */
@Composable
fun AllesLoeschenSymbol(modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { _, _ ->
            radiererForm(tint)
            linie(Offset(8f, 20.6f), Offset(20.5f, 20.6f), tint, 1.8f)
        }
    }
}

@Composable
fun GeometrieSymbol(werkzeug: GeometrieWerkzeug, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { w, h ->
            val strich = kontur(1.6f)
            when (werkzeug) {
                GeometrieWerkzeug.LINEAL -> {
                    drawRoundRect(
                        tint, topLeft = Offset(w * 0.1f, h * 0.38f), size = Size(w * 0.8f, h * 0.24f),
                        cornerRadius = CornerRadius(1.5f), style = strich
                    )
                    for (i in 1..5) {
                        val x = w * 0.1f + w * 0.8f * (i / 6f)
                        linie(Offset(x, h * 0.38f), Offset(x, h * 0.38f + h * (if (i % 2 == 0) 0.12f else 0.08f)), tint, 1.2f)
                    }
                }
                GeometrieWerkzeug.WINKELDREIECK -> {
                    drawPath(dreieckPfad(w, h, rechts = true), tint, style = strich)
                    drawArc(
                        tint, startAngle = -90f, sweepAngle = 45f, useCenter = false,
                        topLeft = Offset(w * 0.18f - 6f, h * 0.15f + 2f), size = Size(12f, 12f), style = kontur(1.2f)
                    )
                }
                GeometrieWerkzeug.WINKELMESSER -> {
                    val cx = w * 0.5f
                    val cy = h * 0.66f
                    val r = w * 0.38f
                    drawArc(
                        tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2), style = strich
                    )
                    linie(Offset(cx - r, cy), Offset(cx + r, cy), tint, 1.6f)
                    for (i in 1..5) {
                        val winkel = Math.PI * i / 6
                        val aussen = Offset(cx - r * cos(winkel).toFloat(), cy - r * sin(winkel).toFloat())
                        val innen = Offset(cx - (r - 3f) * cos(winkel).toFloat(), cy - (r - 3f) * sin(winkel).toFloat())
                        linie(aussen, innen, tint, 1.2f)
                    }
                }
                GeometrieWerkzeug.RECHTWINKLIG -> {
                    drawPath(dreieckPfad(w, h, rechts = true), tint, style = strich)
                    drawRect(tint, topLeft = Offset(w * 0.18f, h * 0.85f - 4f), size = Size(4f, 4f), style = kontur(1.2f))
                }
                GeometrieWerkzeug.ZIRKEL -> {
                    drawCircle(tint, radius = 1.6f, center = Offset(w * 0.5f, h * 0.14f))
                    linie(Offset(w * 0.5f, h * 0.16f), Offset(w * 0.3f, h * 0.86f), tint, 1.8f)
                    linie(Offset(w * 0.5f, h * 0.16f), Offset(w * 0.7f, h * 0.86f), tint, 1.8f)
                    linie(Offset(w * 0.38f, h * 0.58f), Offset(w * 0.62f, h * 0.58f), tint, 1.3f)
                }
                GeometrieWerkzeug.GLEICHSCHENKLIG -> drawPath(dreieckPfad(w, h, rechts = false), tint, style = strich)
            }
        }
    }
}

@Composable
fun LinienStilSymbol(gestrichelt: Boolean, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { w, h ->
            linie(Offset(w * 0.16f, h * 0.84f), Offset(w * 0.84f, h * 0.16f), tint, 2.0f, gestrichelt)
        }
    }
}

enum class WerkzeugkastenAktion {
    HINTERGRUND, BILD_TEILEN, BILDSCHIRMFOTO, LUPE, ISERV,
    EXTRAS, PDF, ARBEITSBLATT, ABDECKEN, TIMER, WUERFEL, ZUFALLSNAME, GRUPPEN, LAUTSTAERKE, LERNUHR, TAFELN
}

@Composable
fun WerkzeugkastenSymbol(aktion: WerkzeugkastenAktion, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        symbolRaster { _, _ ->
            when (aktion) {
                WerkzeugkastenAktion.HINTERGRUND -> {
                    // Farbeimer: gekippte Raute, untere Hälfte gefüllt, daneben ein Tropfen.
                    val raute = Path().apply {
                        moveTo(3.6f, 12.4f); lineTo(10.6f, 5.4f); lineTo(17.6f, 12.4f); lineTo(10.6f, 19.4f); close()
                    }
                    drawPath(raute, tint, style = kontur(1.7f))
                    val fuellung = Path().apply {
                        moveTo(4.4f, 12.4f); lineTo(16.8f, 12.4f); lineTo(10.6f, 18.6f); close()
                    }
                    drawPath(fuellung, tint)
                    linie(Offset(8.2f, 7.8f), Offset(5.0f, 4.6f), tint, 1.6f)
                    val tropfen = Path().apply {
                        moveTo(20.2f, 13.4f)
                        quadraticTo(22.2f, 16.4f, 20.2f, 17.9f)
                        quadraticTo(18.2f, 16.4f, 20.2f, 13.4f)
                        close()
                    }
                    drawPath(tropfen, tint)
                }
                WerkzeugkastenAktion.BILD_TEILEN -> {
                    drawRoundRect(
                        tint, topLeft = Offset(3f, 5.4f), size = Size(18f, 13.2f),
                        cornerRadius = CornerRadius(1.6f), style = kontur(1.7f)
                    )
                    linie(Offset(12f, 5.4f), Offset(12f, 18.6f), tint, 1.7f)
                }
                WerkzeugkastenAktion.BILDSCHIRMFOTO -> {
                    // Zuschneide-Rahmen mit Diagonale, wie im Original.
                    drawPath(Path().apply { moveTo(7f, 3f); lineTo(7f, 17f); lineTo(21f, 17f) }, tint, style = kontur(1.7f))
                    drawPath(Path().apply { moveTo(3f, 7f); lineTo(17f, 7f); lineTo(17f, 21f) }, tint, style = kontur(1.7f))
                    linie(Offset(9.4f, 14.6f), Offset(14.6f, 9.4f), tint, 1.4f)
                }
                WerkzeugkastenAktion.LUPE -> {
                    drawCircle(tint, radius = 6.4f, center = Offset(10.4f, 10.4f), style = kontur(1.8f))
                    linie(Offset(15.0f, 15.0f), Offset(20.6f, 20.6f), tint, 2.2f)
                    linie(Offset(10.4f, 7.6f), Offset(10.4f, 13.2f), tint, 1.5f)
                    linie(Offset(7.6f, 10.4f), Offset(13.2f, 10.4f), tint, 1.5f)
                }
                WerkzeugkastenAktion.ISERV -> {
                    // Wolke mit Pfeil nach oben (in die Schul-Cloud speichern).
                    val wolke = Path().apply {
                        moveTo(7f, 18.4f)
                        cubicTo(4.4f, 18.4f, 2.8f, 16.6f, 2.8f, 14.5f)
                        cubicTo(2.8f, 12.4f, 4.4f, 10.9f, 6.4f, 10.8f)
                        cubicTo(7.1f, 8f, 9.5f, 6f, 12.4f, 6f)
                        cubicTo(15.9f, 6f, 18.4f, 8.6f, 18.6f, 11.8f)
                        cubicTo(20.4f, 12.1f, 21.4f, 13.5f, 21.4f, 15.1f)
                        cubicTo(21.4f, 16.9f, 20f, 18.4f, 18.2f, 18.4f)
                        close()
                    }
                    drawPath(wolke, tint, style = kontur(1.6f))
                    linie(Offset(12f, 16.4f), Offset(12f, 10.6f), tint, 1.6f)
                    linie(Offset(9.8f, 12.8f), Offset(12f, 10.6f), tint, 1.6f)
                    linie(Offset(14.2f, 12.8f), Offset(12f, 10.6f), tint, 1.6f)
                }
                WerkzeugkastenAktion.TAFELN -> {
                    // Zwei Tafeln übereinander mit Pfeilen hin und her (Abgleich zwischen Boards).
                    drawRect(tint, topLeft = Offset(3f, 4f), size = androidx.compose.ui.geometry.Size(9f, 7f), style = kontur(1.5f))
                    drawRect(tint, topLeft = Offset(12f, 13f), size = androidx.compose.ui.geometry.Size(9f, 7f), style = kontur(1.5f))
                    linie(Offset(14f, 6f), Offset(19f, 6f), tint, 1.5f)
                    linie(Offset(19f, 6f), Offset(19f, 10.5f), tint, 1.5f)
                    linie(Offset(17.2f, 8.7f), Offset(19f, 10.5f), tint, 1.5f)
                    linie(Offset(20.8f, 8.7f), Offset(19f, 10.5f), tint, 1.5f)
                    linie(Offset(10f, 18f), Offset(5f, 18f), tint, 1.5f)
                    linie(Offset(5f, 18f), Offset(5f, 13.5f), tint, 1.5f)
                    linie(Offset(3.2f, 15.3f), Offset(5f, 13.5f), tint, 1.5f)
                    linie(Offset(6.8f, 15.3f), Offset(5f, 13.5f), tint, 1.5f)
                }
                WerkzeugkastenAktion.EXTRAS -> {
                    // Vier Kacheln, eine davon als Plus
                    drawRoundRect(tint, Offset(3.5f, 3.5f), Size(7.2f, 7.2f), CornerRadius(1.6f), kontur(1.6f))
                    drawRoundRect(tint, Offset(13.3f, 3.5f), Size(7.2f, 7.2f), CornerRadius(1.6f), kontur(1.6f))
                    drawRoundRect(tint, Offset(3.5f, 13.3f), Size(7.2f, 7.2f), CornerRadius(1.6f), kontur(1.6f))
                    linie(Offset(16.9f, 13.4f), Offset(16.9f, 20.4f), tint, 1.7f)
                    linie(Offset(13.4f, 16.9f), Offset(20.4f, 16.9f), tint, 1.7f)
                }
                WerkzeugkastenAktion.PDF -> {
                    val blatt = Path().apply {
                        moveTo(5.5f, 2.8f); lineTo(14.6f, 2.8f); lineTo(18.6f, 6.8f); lineTo(18.6f, 21.2f); lineTo(5.5f, 21.2f); close()
                    }
                    drawPath(blatt, tint, kontur(1.6f))
                    // zweites Blatt dahinter = "alle Seiten"
                    drawPath(Path().apply { moveTo(20.8f, 8.4f); lineTo(20.8f, 23f); lineTo(8f, 23f) }, tint, kontur(1.3f))
                    linie(Offset(8.4f, 11f), Offset(15.6f, 11f), tint, 1.3f)
                    linie(Offset(8.4f, 14.2f), Offset(15.6f, 14.2f), tint, 1.3f)
                    linie(Offset(8.4f, 17.4f), Offset(13f, 17.4f), tint, 1.3f)
                }
                WerkzeugkastenAktion.ARBEITSBLATT -> {
                    drawRoundRect(tint, Offset(4.5f, 2.8f), Size(15f, 18.4f), CornerRadius(1.2f), kontur(1.6f))
                    // Pfeil ins Blatt hinein (öffnen)
                    linie(Offset(12f, 7f), Offset(12f, 15.6f), tint, 1.7f)
                    linie(Offset(8.8f, 12.4f), Offset(12f, 15.6f), tint, 1.7f)
                    linie(Offset(15.2f, 12.4f), Offset(12f, 15.6f), tint, 1.7f)
                    linie(Offset(8f, 18.2f), Offset(16f, 18.2f), tint, 1.4f)
                }
                WerkzeugkastenAktion.ABDECKEN -> {
                    drawRoundRect(tint, Offset(3f, 4f), Size(18f, 16f), CornerRadius(1.4f), kontur(1.5f))
                    drawRect(tint, Offset(3.8f, 11f), Size(16.4f, 8.2f))
                    linie(Offset(9.5f, 11f), Offset(14.5f, 11f), Color.White, 1.4f)
                }
                WerkzeugkastenAktion.TIMER -> {
                    drawCircle(tint, 8f, Offset(12f, 13.4f), kontur(1.7f))
                    linie(Offset(12f, 13.4f), Offset(12f, 8.6f), tint, 1.7f)
                    linie(Offset(12f, 13.4f), Offset(15.2f, 15.4f), tint, 1.7f)
                    linie(Offset(9.6f, 2.8f), Offset(14.4f, 2.8f), tint, 1.7f)
                    linie(Offset(12f, 2.8f), Offset(12f, 5.2f), tint, 1.5f)
                }
                WerkzeugkastenAktion.WUERFEL -> {
                    drawRoundRect(tint, Offset(4f, 4f), Size(16f, 16f), CornerRadius(3.4f), kontur(1.7f))
                    for ((x, y) in listOf(8.4f to 8.4f, 15.6f to 15.6f, 12f to 12f, 15.6f to 8.4f, 8.4f to 15.6f)) {
                        drawCircle(tint, 1.4f, Offset(x, y))
                    }
                }
                WerkzeugkastenAktion.ZUFALLSNAME -> {
                    drawCircle(tint, 3.6f, Offset(10f, 8f), kontur(1.6f))
                    drawArc(tint, 200f, 140f, false, Offset(3f, 13.4f), Size(14f, 12f), kontur(1.6f))
                    // Fragezeichen daneben
                    drawArc(tint, 180f, 250f, false, Offset(16.2f, 4.2f), Size(5f, 5f), kontur(1.4f))
                    linie(Offset(18.7f, 9.2f), Offset(18.7f, 11.2f), tint, 1.4f)
                    drawCircle(tint, 0.9f, Offset(18.7f, 14f))
                }
                WerkzeugkastenAktion.GRUPPEN -> {
                    for ((x, y) in listOf(7f to 7.5f, 17f to 7.5f, 12f to 15.5f)) {
                        drawCircle(tint, 2.4f, Offset(x, y - 1.6f), kontur(1.4f))
                        drawArc(tint, 200f, 140f, false, Offset(x - 4.4f, y + 1.4f), Size(8.8f, 7f), kontur(1.4f))
                    }
                }
                WerkzeugkastenAktion.LAUTSTAERKE -> {
                    // Ampel
                    drawRoundRect(tint, Offset(7.4f, 2.6f), Size(9.2f, 18.8f), CornerRadius(2.6f), kontur(1.6f))
                    drawCircle(tint, 2f, Offset(12f, 6.6f), kontur(1.3f))
                    drawCircle(tint, 2f, Offset(12f, 12f), kontur(1.3f))
                    drawCircle(tint, 2f, Offset(12f, 17.4f))
                }
                WerkzeugkastenAktion.LERNUHR -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), kontur(1.7f))
                    for (i in 0 until 12) {
                        val w = Math.toRadians(i * 30.0)
                        val innen = if (i % 3 == 0) 6.4f else 7.4f
                        linie(
                            Offset(12f + innen * sin(w).toFloat(), 12f - innen * cos(w).toFloat()),
                            Offset(12f + 8.4f * sin(w).toFloat(), 12f - 8.4f * cos(w).toFloat()), tint, 1f
                        )
                    }
                    linie(Offset(12f, 12f), Offset(12f, 6.4f), tint, 1.5f)
                    linie(Offset(12f, 12f), Offset(15.4f, 13.6f), tint, 1.9f)
                }
            }
        }
    }
}
