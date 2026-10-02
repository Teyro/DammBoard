package de.oejendorferdamm.dammboard.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import de.oejendorferdamm.dammboard.model.StempelArt
import de.oejendorferdamm.dammboard.model.StempelItem
import de.oejendorferdamm.dammboard.model.TextItem
import kotlin.math.cos
import kotlin.math.sin

/* Zeichnen von Stempeln und Textfeldern (Formen-Panel, Reiter "Stempel"). */

private val textPinsel = ThreadLocal.withInitial {
    android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
    }
}

private const val ZEILENHOEHE = 1.22f

/** Breite und Höhe eines Textfelds – beim Anlegen gemessen und im TextItem gespeichert. */
fun messeText(text: String, groesse: Float): Pair<Float, Float> {
    val pinsel = textPinsel.get()!!
    pinsel.textSize = groesse
    val zeilen = text.split('\n')
    val breite = zeilen.maxOf { pinsel.measureText(it) }
    return breite to zeilen.size * groesse * ZEILENHOEHE
}

internal fun DrawScope.zeichneText(item: TextItem) {
    val pinsel = textPinsel.get()!!
    pinsel.textSize = item.groesse
    pinsel.color = item.farbe.toArgb()
    pinsel.textAlign = android.graphics.Paint.Align.LEFT
    val leinwand = drawContext.canvas.nativeCanvas
    val oberkante = -pinsel.fontMetrics.ascent
    item.text.split('\n').forEachIndexed { i, zeile ->
        leinwand.drawText(zeile, item.position.x, item.position.y + oberkante + i * item.groesse * ZEILENHOEHE, pinsel)
    }
}

/** Emoji- und Text-Stempel; die übrigen werden als Formen in der Stempelfarbe gezeichnet. */
internal fun emojiFuer(art: StempelArt): String? = when (art) {
    StempelArt.DAUMEN -> "👍"
    StempelArt.LACHEN -> "😀"
    StempelArt.NACHDENKEN -> "🤔"
    else -> null
}

internal fun DrawScope.zeichneStempel(item: StempelItem) {
    val g = item.groesse
    val m = item.mitte
    val farbe = item.farbe
    val linie = Stroke(width = g * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val leinwand = drawContext.canvas.nativeCanvas
    val pinsel = textPinsel.get()!!
    pinsel.textAlign = android.graphics.Paint.Align.CENTER

    fun schreibe(text: String, groesse: Float, fett: Boolean) {
        pinsel.textSize = groesse
        pinsel.color = farbe.toArgb()
        pinsel.isFakeBoldText = fett
        val mitteY = (pinsel.fontMetrics.ascent + pinsel.fontMetrics.descent) / 2
        leinwand.drawText(text, m.x, m.y - mitteY, pinsel)
        pinsel.isFakeBoldText = false
    }

    emojiFuer(item.art)?.let {
        schreibe(it, g * 0.82f, false)
        return
    }
    when (item.art) {
        StempelArt.HAKEN -> drawPath(Path().apply {
            moveTo(m.x - g * 0.38f, m.y + g * 0.02f)
            lineTo(m.x - g * 0.1f, m.y + g * 0.3f)
            lineTo(m.x + g * 0.4f, m.y - g * 0.32f)
        }, farbe, style = linie)
        StempelArt.KREUZ -> {
            val d = g * 0.32f
            drawLine(farbe, m + Offset(-d, -d), m + Offset(d, d), strokeWidth = linie.width, cap = StrokeCap.Round)
            drawLine(farbe, m + Offset(d, -d), m + Offset(-d, d), strokeWidth = linie.width, cap = StrokeCap.Round)
        }
        StempelArt.STERN -> drawPath(Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) g * 0.48f else g * 0.2f
                val w = -Math.PI / 2 + i * Math.PI / 5
                val x = m.x + r * cos(w).toFloat()
                val y = m.y + g * 0.04f + r * sin(w).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }, farbe)
        StempelArt.HERZ -> drawPath(Path().apply {
            val s = g * 0.46f
            moveTo(m.x, m.y + s * 0.85f)
            cubicTo(m.x - s * 1.25f, m.y + s * 0.05f, m.x - s * 0.85f, m.y - s * 0.95f, m.x, m.y - s * 0.4f)
            cubicTo(m.x + s * 0.85f, m.y - s * 0.95f, m.x + s * 1.25f, m.y + s * 0.05f, m.x, m.y + s * 0.85f)
            close()
        }, farbe)
        StempelArt.FRAGE, StempelArt.AUSRUF -> {
            drawCircle(farbe, radius = g * 0.44f, center = m, style = Stroke(width = g * 0.07f))
            schreibe(if (item.art == StempelArt.FRAGE) "?" else "!", g * 0.62f, true)
        }
        StempelArt.SUPER -> {
            drawRoundRect(
                farbe, topLeft = Offset(m.x - g * 0.95f, m.y - g * 0.4f), size = Size(g * 1.9f, g * 0.8f),
                cornerRadius = CornerRadius(g * 0.4f), style = Stroke(width = g * 0.07f)
            )
            schreibe("Super!", g * 0.42f, true)
        }
        else -> Unit
    }
}
