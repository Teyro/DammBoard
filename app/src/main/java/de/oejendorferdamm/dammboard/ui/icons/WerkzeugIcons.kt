package de.oejendorferdamm.dammboard.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.GeometrieWerkzeug
import de.oejendorferdamm.dammboard.model.RadiererGroesse
import de.oejendorferdamm.dammboard.model.Werkzeug
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Alle Symbole werden von Hand gezeichnet, in einem virtuellen 24×24-Raster (wie bei den
 * üblichen Material-Symbolen) und dann auf die echte Symbolgröße skaliert. Linienstärken und
 * Radien beziehen sich deshalb auf dieses Raster und wachsen mit der Symbolgröße mit – auf
 * jedem Bildschirm gleich kräftig. Die Formen der Leisten-Symbole sind der Original-Tafel-App
 * nachempfunden (Umriss-Symbole in Dunkelgrau).
 */

private const val SYMBOL_RASTER = 24f

private val strichelung = PathEffect.dashPathEffect(floatArrayOf(3.0f, 2.4f), 0f)
private val punktierung = PathEffect.dashPathEffect(floatArrayOf(0.01f, 2.7f), 0f)

/** Zeichnet [zeichnen] im virtuellen Symbolraster (w/h = Rastermaße) und skaliert es auf die echte Größe. */
internal fun DrawScope.symbolRaster(zeichnen: DrawScope.(w: Float, h: Float) -> Unit) {
    val faktor = size.minDimension / SYMBOL_RASTER
    if (faktor <= 0f) return
    val w = size.width / faktor
    val h = size.height / faktor
    scale(scale = faktor, pivot = Offset.Zero) {
        this.zeichnen(w, h)
    }
}

private fun DrawScope.linie(a: Offset, b: Offset, tint: Color, breite: Float = 1.9f, gestrichelt: Boolean = false) {
    if (!gestrichelt) {
        drawLine(color = tint, start = a, end = b, strokeWidth = breite, cap = StrokeCap.Round)
        return
    }
    // Gestrichelt als Pfad (Android 8 ignoriert Strichelungen bei drawLine) und mit geraden
    // Enden – runde Enden würden die kleinen Lücken im Symbol zudecken.
    val pfad = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
    }
    drawPath(pfad, tint, style = Stroke(width = breite, cap = StrokeCap.Butt, pathEffect = strichelung))
}

private fun kontur(breite: Float) = Stroke(width = breite, cap = StrokeCap.Round, join = StrokeJoin.Round)

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
private fun DrawScope.stiftUmriss(tint: Color, halbeBreite: Float) {
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
private fun DrawScope.formenSymbol(tint: Color) {
    drawRect(tint, topLeft = Offset(3.6f, 3.6f), size = Size(10.6f, 10.6f), style = kontur(1.8f))
    drawCircle(tint, radius = 5.3f, center = Offset(15.8f, 15.8f))
}

/** Schräg liegender Radiergummi, die untere Hälfte ausgefüllt. */
private fun DrawScope.radiererForm(tint: Color) {
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
private fun DrawScope.lassoSymbol(tint: Color) {
    drawOval(
        tint, topLeft = Offset(2.4f, 3.4f), size = Size(19.2f, 11.2f),
        style = Stroke(width = 2.0f, cap = StrokeCap.Round, pathEffect = punktierung)
    )
    drawCircle(tint, radius = 1.7f, center = Offset(11.2f, 16.2f), style = kontur(1.4f))
    val schwanz = Path().apply {
        moveTo(11.6f, 17.9f)
        quadraticTo(12.0f, 20.0f, 13.8f, 21.4f)
    }
    drawPath(schwanz, tint, style = kontur(1.5f))
}

/** Geodreieck mit Lineal daneben. */
private fun DrawScope.geometrieSymbol(tint: Color) {
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
private fun DrawScope.auswahlSymbol(tint: Color) {
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
private fun DrawScope.koffer(tint: Color) {
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
    SCHLIESSEN, MENUE, TEILEN, PAPIERKORB, RUECKGAENGIG, WIEDERHOLEN, PLUS, PFEIL_LINKS, PFEIL_RECHTS
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
                    drawPath(
                        pfad, tint,
                        style = Stroke(
                            width = 1.7f,
                            cap = if (gestrichelt) StrokeCap.Butt else StrokeCap.Round,
                            pathEffect = if (gestrichelt) strichelung else null
                        )
                    )
                    pfeilSpitze(Offset(w * 0.82f, h * 0.24f), Offset(w * 0.6f, h * 0.16f), tint)
                }
            }
        }
    }
}

private fun DrawScope.pfeilSpitze(spitze: Offset, ansatz: Offset, tint: Color) {
    val laenge = 6f
    val winkel = atan2(ansatz.y - spitze.y, ansatz.x - spitze.x)
    val a1 = winkel + Math.PI.toFloat() * 0.2f
    val a2 = winkel - Math.PI.toFloat() * 0.2f
    linie(spitze, spitze + Offset(cos(a1) * laenge, sin(a1) * laenge), tint, 1.7f)
    linie(spitze, spitze + Offset(cos(a2) * laenge, sin(a2) * laenge), tint, 1.7f)
}

private fun DrawScope.pfeilSymbol(w: Float, h: Float, tint: Color, doppelt: Boolean, gestrichelt: Boolean) {
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

enum class WerkzeugkastenAktion { HINTERGRUND, BILD_TEILEN, BILDSCHIRMFOTO, LUPE, ISERV }

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
            }
        }
    }
}
