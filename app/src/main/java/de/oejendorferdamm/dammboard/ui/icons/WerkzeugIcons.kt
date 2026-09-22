package de.oejendorferdamm.dammboard.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.GeometrieWerkzeug
import de.oejendorferdamm.dammboard.model.RadiererGroesse
import de.oejendorferdamm.dammboard.model.Werkzeug
import kotlin.math.cos
import kotlin.math.sin

private val gestricheltEffekt = PathEffect.dashPathEffect(floatArrayOf(8f, 7f), 0f)

private fun DrawScope.linie(a: Offset, b: Offset, tint: Color, breite: Float = 2.4f, gestrichelt: Boolean = false) {
    drawLine(
        color = tint, start = a, end = b, strokeWidth = breite, cap = StrokeCap.Round,
        pathEffect = if (gestrichelt) gestricheltEffekt else null
    )
}

@Composable
fun WerkzeugSymbol(werkzeug: Werkzeug, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (werkzeug) {
            Werkzeug.STIFT -> {
                linie(Offset(w * 0.24f, h * 0.82f), Offset(w * 0.74f, h * 0.24f), tint, 3f)
                drawCircle(tint, radius = 2.6f, center = Offset(w * 0.78f, h * 0.20f))
                linie(Offset(w * 0.24f, h * 0.82f), Offset(w * 0.18f, h * 0.9f), tint, 2f)
            }
            Werkzeug.FORMEN -> {
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.46f),
                    style = stroke
                )
                drawCircle(
                    color = tint,
                    radius = w * 0.30f,
                    center = Offset(w * 0.66f, h * 0.64f),
                    style = stroke
                )
            }
            Werkzeug.RADIERER -> {
                drawRoundedShape(tint, w, h)
            }
            Werkzeug.LASSO -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.32f,
                    center = center,
                    style = Stroke(width = 2.2f, pathEffect = gestricheltEffekt)
                )
                drawCircle(tint, radius = 2.6f, center = Offset(w * 0.68f, h * 0.66f))
            }
            Werkzeug.GEOMETRIE -> geometrieSymbolLineal(tint, w, h)
            Werkzeug.AUSWAHL -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.14f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.72f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                    style = stroke
                )
                val p = Offset(w * 0.5f, h * 0.5f)
                linie(p, p + Offset(w * 0.22f, h * 0.22f), tint, 2.4f)
                linie(p, p + Offset(w * 0.22f, 0f), tint, 2.4f)
                linie(p, p + Offset(0f, h * 0.22f), tint, 2.4f)
            }
            Werkzeug.WERKZEUGKASTEN -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.36f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.46f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
                    style = stroke
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.36f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.16f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
                    style = stroke
                )
                linie(Offset(w * 0.14f, h * 0.56f), Offset(w * 0.86f, h * 0.56f), tint, 2f)
            }
        }
    }
}

private fun DrawScope.drawRoundedShape(tint: Color, w: Float, h: Float) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.2f, h * 0.38f),
        size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.34f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
        style = Stroke(width = 2.2f)
    )
    linie(Offset(w * 0.32f, h * 0.38f), Offset(w * 0.58f, h * 0.1f), tint, 2.2f)
    linie(Offset(w * 0.68f, h * 0.38f), Offset(w * 0.94f, h * 0.1f), tint, 2.2f)
}

private fun DrawScope.geometrieSymbolLineal(tint: Color, w: Float, h: Float) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.12f, h * 0.40f),
        size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.22f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
        style = Stroke(width = 2f)
    )
    for (i in 1..4) {
        val x = w * 0.12f + w * 0.76f * (i / 5f)
        linie(Offset(x, h * 0.40f), Offset(x, h * 0.40f + h * 0.1f), tint, 1.6f)
    }
}

@Composable
fun StiftArtSymbol(fein: Boolean, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        if (fein) {
            linie(Offset(w * 0.2f, h * 0.85f), Offset(w * 0.78f, h * 0.18f), tint, 2.6f)
            drawCircle(tint, radius = 2.2f, center = Offset(w * 0.82f, h * 0.14f))
        } else {
            linie(Offset(w * 0.18f, h * 0.82f), Offset(w * 0.68f, h * 0.24f), tint, 7f)
            drawRect(
                color = tint,
                topLeft = Offset(w * 0.66f, h * 0.14f),
                size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.16f)
            )
        }
    }
}

@Composable
fun FormSymbol(typ: FormTyp, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val gestrichelt = typ in listOf(
            FormTyp.LINIE_GESTRICHELT, FormTyp.PFEIL_GESTRICHELT,
            FormTyp.DOPPELPFEIL_GESTRICHELT, FormTyp.FREIHANDPFEIL_GESTRICHELT
        )
        val strokeEffekt = if (gestrichelt) Stroke(width = 2f, pathEffect = gestricheltEffekt) else stroke
        when (typ) {
            FormTyp.DREIECK_RECHTS -> drawPath(dreieckPfad(w, h, rechts = true), tint, style = stroke)
            FormTyp.DREIECK -> drawPath(dreieckPfad(w, h, rechts = false), tint, style = stroke)
            FormTyp.KREIS -> drawCircle(tint, radius = w * 0.36f, center = center, style = stroke)
            FormTyp.ELLIPSE -> drawOval(
                tint,
                topLeft = Offset(w * 0.12f, h * 0.28f),
                size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.44f),
                style = stroke
            )
            FormTyp.QUADRAT -> drawRect(
                tint, topLeft = Offset(w * 0.18f, h * 0.18f),
                size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f), style = stroke
            )
            FormTyp.SECHSECK -> drawPath(vieleckPfad(w, h, 6), tint, style = stroke)
            FormTyp.ABGERUNDET -> drawRoundRect(
                tint, topLeft = Offset(w * 0.18f, h * 0.18f),
                size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f), style = stroke
            )
            FormTyp.FUENFECK -> drawPath(vieleckPfad(w, h, 5), tint, style = stroke)
            FormTyp.STERN -> drawPath(sternPfad(w, h), tint, style = stroke)
            FormTyp.WELLE -> drawPath(wellenPfad(w, h), tint, style = stroke)
            FormTyp.LINIE, FormTyp.LINIE_GESTRICHELT ->
                linie(Offset(w * 0.16f, h * 0.82f), Offset(w * 0.84f, h * 0.18f), tint, 2.4f, gestrichelt)
            FormTyp.PFEIL, FormTyp.PFEIL_GESTRICHELT ->
                pfeilSymbol(w, h, tint, doppelt = false, gestrichelt = gestrichelt)
            FormTyp.DOPPELPFEIL, FormTyp.DOPPELPFEIL_GESTRICHELT ->
                pfeilSymbol(w, h, tint, doppelt = true, gestrichelt = gestrichelt)
            FormTyp.FREIHANDPFEIL, FormTyp.FREIHANDPFEIL_GESTRICHELT -> {
                val pfad = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.16f, h * 0.8f)
                    quadraticTo(w * 0.3f, h * 0.2f, w * 0.82f, h * 0.24f)
                }
                drawPath(pfad, tint, style = strokeEffekt)
                pfeilSpitze(Offset(w * 0.82f, h * 0.24f), Offset(w * 0.6f, h * 0.16f), tint)
            }
        }
    }
}

private fun pfeilSpitzeWinkel(spitze: Offset, richtung: Offset, tint: Color, scope: DrawScope) = with(scope) {
    val laenge = 8f
    val winkel = kotlin.math.atan2(richtung.y - spitze.y, richtung.x - spitze.x)
    val a1 = winkel + Math.PI.toFloat() * 0.78f
    val a2 = winkel - Math.PI.toFloat() * 0.78f
    drawLine(tint, spitze, spitze + Offset(cos(a1) * laenge, sin(a1) * laenge), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawLine(tint, spitze, spitze + Offset(cos(a2) * laenge, sin(a2) * laenge), strokeWidth = 2.2f, cap = StrokeCap.Round)
}

private fun DrawScope.pfeilSpitze(spitze: Offset, ansatz: Offset, tint: Color) = pfeilSpitzeWinkel(spitze, ansatz, tint, this)

private fun DrawScope.pfeilSymbol(w: Float, h: Float, tint: Color, doppelt: Boolean, gestrichelt: Boolean) {
    val start = Offset(w * 0.14f, h * 0.82f)
    val ende = Offset(w * 0.86f, h * 0.18f)
    linie(start, ende, tint, 2.4f, gestrichelt)
    pfeilSpitzeWinkel(ende, start, tint, this)
    if (doppelt) pfeilSpitzeWinkel(start, ende, tint, this)
}

private fun dreieckPfad(w: Float, h: Float, rechts: Boolean) = androidx.compose.ui.graphics.Path().apply {
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

private fun vieleckPfad(w: Float, h: Float, ecken: Int) = androidx.compose.ui.graphics.Path().apply {
    val cx = w * 0.5f; val cy = h * 0.52f; val r = w * 0.36f
    for (i in 0 until ecken) {
        val winkel = -Math.PI / 2 + i * (2 * Math.PI / ecken)
        val x = cx + r * cos(winkel).toFloat()
        val y = cy + r * sin(winkel).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun sternPfad(w: Float, h: Float) = androidx.compose.ui.graphics.Path().apply {
    val cx = w * 0.5f; val cy = h * 0.52f
    val rAussen = w * 0.38f; val rInnen = w * 0.16f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) rAussen else rInnen
        val winkel = -Math.PI / 2 + i * (Math.PI / 5)
        val x = cx + r * cos(winkel).toFloat()
        val y = cy + r * sin(winkel).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun wellenPfad(w: Float, h: Float) = androidx.compose.ui.graphics.Path().apply {
    moveTo(w * 0.1f, h * 0.5f)
    cubicTo(w * 0.28f, h * 0.15f, w * 0.38f, h * 0.85f, w * 0.55f, h * 0.5f)
    cubicTo(w * 0.68f, h * 0.2f, w * 0.78f, h * 0.8f, w * 0.9f, h * 0.5f)
}

@Composable
fun RadiererSymbol(groesse: RadiererGroesse, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val faktor = when (groesse) {
            RadiererGroesse.KLEIN -> 0.4f
            RadiererGroesse.MITTEL -> 0.6f
            RadiererGroesse.GROSS -> 0.82f
        }
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * (0.5f - faktor / 2), h * (0.5f - faktor * 0.32f)),
            size = androidx.compose.ui.geometry.Size(w * faktor, h * faktor * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = Stroke(width = 2f)
        )
    }
}

@Composable
fun AllesLoeschenSymbol(modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = Stroke(width = 2f)
        )
        linie(Offset(w * 0.3f, h * 0.4f), Offset(w * 0.7f, h * 0.6f), tint, 2f)
        linie(Offset(w * 0.7f, h * 0.4f), Offset(w * 0.3f, h * 0.6f), tint, 2f)
    }
}

@Composable
fun GeometrieSymbol(werkzeug: GeometrieWerkzeug, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (werkzeug) {
            GeometrieWerkzeug.LINEAL -> geometrieSymbolLineal(tint, w, h)
            GeometrieWerkzeug.WINKELDREIECK -> {
                drawPath(dreieckPfad(w, h, rechts = true), tint, style = stroke)
                arcBogen(Offset(w * 0.3f, h * 0.7f), 8f, tint)
            }
            GeometrieWerkzeug.WINKELMESSER -> {
                drawArc(
                    color = tint,
                    startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.2f),
                    size = androidx.compose.ui.geometry.Size(w * 0.76f, w * 0.76f),
                    style = stroke
                )
                linie(Offset(w * 0.12f, h * 0.2f + w * 0.38f), Offset(w * 0.88f, h * 0.2f + w * 0.38f), tint, 2f)
                for (i in 1..5) {
                    val winkel = Math.PI * i / 6
                    val cx = w * 0.5f; val cy = h * 0.2f + w * 0.38f
                    val r = w * 0.38f
                    val x1 = cx - r * cos(winkel).toFloat(); val y1 = cy - r * sin(winkel).toFloat()
                    val x2 = cx - (r - 5f) * cos(winkel).toFloat(); val y2 = cy - (r - 5f) * sin(winkel).toFloat()
                    linie(Offset(x1, y1), Offset(x2, y2), tint, 1.4f)
                }
            }
            GeometrieWerkzeug.RECHTWINKLIG -> {
                drawPath(dreieckPfad(w, h, rechts = true), tint, style = stroke)
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.18f, h * 0.7f),
                    size = androidx.compose.ui.geometry.Size(w * 0.08f, h * 0.08f),
                    style = Stroke(width = 1.4f)
                )
            }
            GeometrieWerkzeug.ZIRKEL -> {
                drawCircle(tint, radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.58f), style = Stroke(width = 1.6f, pathEffect = gestricheltEffekt))
                linie(Offset(w * 0.5f, h * 0.14f), Offset(w * 0.34f, h * 0.86f), tint, 2.4f)
                linie(Offset(w * 0.5f, h * 0.14f), Offset(w * 0.72f, h * 0.3f), tint, 2.4f)
                drawCircle(tint, radius = 2.4f, center = Offset(w * 0.5f, h * 0.14f))
            }
            GeometrieWerkzeug.GLEICHSCHENKLIG -> {
                val pfad = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.14f)
                    lineTo(w * 0.86f, h * 0.85f)
                    lineTo(w * 0.14f, h * 0.85f)
                    close()
                }
                drawPath(pfad, tint, style = stroke)
            }
        }
    }
}

private fun DrawScope.arcBogen(zentrum: Offset, radius: Float, tint: Color) {
    drawArc(
        color = tint, startAngle = -90f, sweepAngle = 90f, useCenter = false,
        topLeft = zentrum - Offset(radius, radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = 1.4f)
    )
}

@Composable
fun LinienStilSymbol(gestrichelt: Boolean, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.5f),
            end = Offset(w * 0.85f, h * 0.5f),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
            pathEffect = if (gestrichelt) gestricheltEffekt else null
        )
    }
}

enum class WerkzeugkastenAktion { HINTERGRUND, BILD_TEILEN, BILDSCHIRMFOTO, LUPE, ISERV }

enum class AllgemeinesSymbol { SCHLIESSEN, MENUE, TEILEN, PAPIERKORB, RUECKGAENGIG, WIEDERHOLEN, PLUS, PFEIL_LINKS, PFEIL_RECHTS }

@Composable
fun AllgemeinSymbol(symbol: AllgemeinesSymbol, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (symbol) {
            AllgemeinesSymbol.SCHLIESSEN -> {
                linie(Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f), tint, 2.6f)
                linie(Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), tint, 2.6f)
            }
            AllgemeinesSymbol.MENUE -> {
                linie(Offset(w * 0.22f, h * 0.32f), Offset(w * 0.78f, h * 0.32f), tint, 2.4f)
                linie(Offset(w * 0.22f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), tint, 2.4f)
                linie(Offset(w * 0.22f, h * 0.68f), Offset(w * 0.78f, h * 0.68f), tint, 2.4f)
            }
            AllgemeinesSymbol.TEILEN -> {
                drawCircle(tint, radius = 4f, center = Offset(w * 0.76f, h * 0.24f), style = Stroke(width = 2f))
                drawCircle(tint, radius = 4f, center = Offset(w * 0.76f, h * 0.76f), style = Stroke(width = 2f))
                drawCircle(tint, radius = 4f, center = Offset(w * 0.26f, h * 0.5f), style = Stroke(width = 2f))
                linie(Offset(w * 0.3f, h * 0.46f), Offset(w * 0.72f, h * 0.28f), tint, 1.8f)
                linie(Offset(w * 0.3f, h * 0.54f), Offset(w * 0.72f, h * 0.72f), tint, 1.8f)
            }
            AllgemeinesSymbol.PAPIERKORB -> {
                drawRoundRect(
                    tint, topLeft = Offset(w * 0.28f, h * 0.32f), size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f), style = Stroke(width = 2.2f)
                )
                linie(Offset(w * 0.22f, h * 0.3f), Offset(w * 0.78f, h * 0.3f), tint, 2.2f)
                linie(Offset(w * 0.42f, h * 0.3f), Offset(w * 0.46f, h * 0.2f), tint, 1.8f)
                linie(Offset(w * 0.58f, h * 0.3f), Offset(w * 0.54f, h * 0.2f), tint, 1.8f)
                linie(Offset(w * 0.42f, h * 0.42f), Offset(w * 0.42f, h * 0.72f), tint, 1.8f)
                linie(Offset(w * 0.58f, h * 0.42f), Offset(w * 0.58f, h * 0.72f), tint, 1.8f)
            }
            AllgemeinesSymbol.RUECKGAENGIG -> pfeilBogen(tint, w, h, gespiegelt = false)
            AllgemeinesSymbol.WIEDERHOLEN -> pfeilBogen(tint, w, h, gespiegelt = true)
            AllgemeinesSymbol.PLUS -> {
                linie(Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.78f), tint, 2.8f)
                linie(Offset(w * 0.22f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), tint, 2.8f)
            }
            // Schlichter, kräftiger Chevron statt Pfeil-mit-Schaft – näher am Original.
            AllgemeinesSymbol.PFEIL_LINKS -> {
                linie(Offset(w * 0.64f, h * 0.24f), Offset(w * 0.36f, h * 0.5f), tint, 3f)
                linie(Offset(w * 0.36f, h * 0.5f), Offset(w * 0.64f, h * 0.76f), tint, 3f)
            }
            AllgemeinesSymbol.PFEIL_RECHTS -> {
                linie(Offset(w * 0.36f, h * 0.24f), Offset(w * 0.64f, h * 0.5f), tint, 3f)
                linie(Offset(w * 0.64f, h * 0.5f), Offset(w * 0.36f, h * 0.76f), tint, 3f)
            }
        }
    }
}

private fun DrawScope.pfeilBogen(tint: Color, w: Float, h: Float, gespiegelt: Boolean) {
    val zentrum = Offset(w * 0.5f, h * 0.56f)
    val radius = w * 0.26f
    val startWinkel = if (gespiegelt) 200f else -20f
    val schwenk = if (gespiegelt) -220f else 220f
    drawArc(
        color = tint, startAngle = startWinkel, sweepAngle = schwenk, useCenter = false,
        topLeft = zentrum - Offset(radius, radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round)
    )
    val spitzenWinkelRad = Math.toRadians(startWinkel.toDouble())
    val spitze = zentrum + Offset(cos(spitzenWinkelRad).toFloat() * radius, sin(spitzenWinkelRad).toFloat() * radius)
    val a1 = spitzenWinkelRad + Math.PI * 0.65
    val a2 = spitzenWinkelRad - Math.PI * 0.05
    linie(spitze, spitze + Offset(cos(a1).toFloat() * 9f, sin(a1).toFloat() * 9f), tint, 2.2f)
    linie(spitze, spitze + Offset(cos(a2).toFloat() * 9f, sin(a2).toFloat() * 9f), tint, 2.2f)
}

@Composable
fun WerkzeugkastenSymbol(aktion: WerkzeugkastenAktion, modifier: Modifier = Modifier, tint: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (aktion) {
            WerkzeugkastenAktion.HINTERGRUND -> {
                val pfad = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.24f, h * 0.55f)
                    lineTo(w * 0.55f, h * 0.18f)
                    lineTo(w * 0.86f, h * 0.5f)
                    lineTo(w * 0.55f, h * 0.86f)
                    close()
                }
                drawPath(pfad, tint, style = stroke)
                drawCircle(tint, radius = 3f, center = Offset(w * 0.28f, h * 0.72f))
            }
            WerkzeugkastenAktion.BILD_TEILEN -> {
                drawRoundRect(
                    tint, topLeft = Offset(w * 0.14f, h * 0.2f),
                    size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f), style = stroke
                )
                drawRoundRect(
                    tint, topLeft = Offset(w * 0.54f, h * 0.2f),
                    size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f), style = stroke
                )
            }
            WerkzeugkastenAktion.BILDSCHIRMFOTO -> {
                drawRoundRect(
                    tint, topLeft = Offset(w * 0.16f, h * 0.28f),
                    size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f), style = stroke
                )
                drawCircle(tint, radius = w * 0.13f, center = center, style = Stroke(width = 1.8f))
                drawRect(tint, topLeft = Offset(w * 0.4f, h * 0.16f), size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.12f))
            }
            WerkzeugkastenAktion.LUPE -> {
                drawCircle(tint, radius = w * 0.28f, center = Offset(w * 0.42f, h * 0.42f), style = Stroke(width = 2.2f))
                linie(Offset(w * 0.62f, h * 0.62f), Offset(w * 0.86f, h * 0.86f), tint, 2.6f)
                linie(Offset(w * 0.3f, h * 0.42f), Offset(w * 0.54f, h * 0.42f), tint, 1.6f)
                linie(Offset(w * 0.42f, h * 0.3f), Offset(w * 0.42f, h * 0.54f), tint, 1.6f)
            }
            WerkzeugkastenAktion.ISERV -> {
                drawArc(
                    color = tint, startAngle = 20f, sweepAngle = 320f, useCenter = false,
                    topLeft = Offset(w * 0.14f, h * 0.32f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.5f),
                    style = stroke
                )
                linie(Offset(w * 0.5f, h * 0.82f), Offset(w * 0.5f, h * 0.42f), tint, 2.4f)
                linie(Offset(w * 0.5f, h * 0.42f), Offset(w * 0.38f, h * 0.56f), tint, 2.2f)
                linie(Offset(w * 0.5f, h * 0.42f), Offset(w * 0.62f, h * 0.56f), tint, 2.2f)
            }
        }
    }
}
