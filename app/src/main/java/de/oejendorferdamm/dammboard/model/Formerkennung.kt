package de.oejendorferdamm.dammboard.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Formerkennung für den Stift: Wer am Ende eines Strichs den Stift kurz still hält, bekommt
 * statt der krakeligen Linie eine saubere Form – Gerade, Kreis, Ellipse, Dreieck, Rechteck oder
 * Vieleck. Das Ergebnis ist wieder ein ganz normaler Freihandstrich (nur mit "perfekten"
 * Punkten), lässt sich also genauso stückweise wegwischen, verschieben und rückgängig machen.
 */

private fun abstandZuGerade(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val laenge = hypot(dx, dy)
    if (laenge == 0f) return hypot(p.x - a.x, p.y - a.y)
    return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / laenge
}

/** Douglas-Peucker: behält nur die "Ecken" einer Punktfolge. */
private fun vereinfache(punkte: List<Offset>, toleranz: Float): List<Offset> {
    if (punkte.size < 3) return punkte
    val behalten = BooleanArray(punkte.size)
    behalten[0] = true
    behalten[punkte.lastIndex] = true
    val stapel = ArrayDeque<Pair<Int, Int>>()
    stapel.addLast(0 to punkte.lastIndex)
    while (stapel.isNotEmpty()) {
        val (a, b) = stapel.removeLast()
        var groesster = 0f
        var index = -1
        for (i in a + 1 until b) {
            val d = abstandZuGerade(punkte[i], punkte[a], punkte[b])
            if (d > groesster) {
                groesster = d
                index = i
            }
        }
        if (index >= 0 && groesster > toleranz) {
            behalten[index] = true
            stapel.addLast(a to index)
            stapel.addLast(index to b)
        }
    }
    return punkte.filterIndexed { i, _ -> behalten[i] }
}

/** Füllt gerade Kanten mit Zwischenpunkten – die Kurvenglättung beim Zeichnen rundet so die Ecken kaum ab. */
private fun verdichte(ecken: List<Offset>, schritt: Float): List<Offset> {
    val ergebnis = ArrayList<Offset>()
    for (i in 0 until ecken.size - 1) {
        val a = ecken[i]
        val b = ecken[i + 1]
        val n = max(1, (hypot(b.x - a.x, b.y - a.y) / schritt).toInt())
        for (k in 0 until n) ergebnis.add(Offset(a.x + (b.x - a.x) * k / n, a.y + (b.y - a.y) * k / n))
        // Ecke doppelt setzen: zwischen zwei gleichen Punkten bleibt die Ecke spitz.
        if (i < ecken.size - 2) ergebnis.add(b)
    }
    ergebnis.add(ecken.last())
    return ergebnis
}

private fun innenwinkel(vorher: Offset, ecke: Offset, nachher: Offset): Double {
    val ax = vorher.x - ecke.x
    val ay = vorher.y - ecke.y
    val bx = nachher.x - ecke.x
    val by = nachher.y - ecke.y
    val produkt = (ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by)).coerceAtLeast(1e-3f)
    return Math.toDegrees(acos(produkt.toDouble().coerceIn(-1.0, 1.0)))
}

/**
 * Erkennt eine Form in [punkte] und gibt die sauberen Punkte dafür zurück – oder null, wenn
 * nichts Eindeutiges erkannt wurde (dann bleibt der Strich, wie er gezeichnet wurde).
 * [pixelFaktor] gleicht hohe Auflösungen aus (siehe TafelCanvas.pixelFaktorFuer).
 */
fun erkenneForm(punkte: List<Offset>, pixelFaktor: Float): List<Offset>? {
    if (punkte.size < 5) return null
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    var weg = 0f
    for (i in punkte.indices) {
        val p = punkte[i]
        minX = min(minX, p.x); minY = min(minY, p.y); maxX = max(maxX, p.x); maxY = max(maxY, p.y)
        if (i > 0) weg += hypot(p.x - punkte[i - 1].x, p.y - punkte[i - 1].y)
    }
    val breite = maxX - minX
    val hoehe = maxY - minY
    val diagonale = hypot(breite, hoehe)
    if (diagonale < 40f * pixelFaktor) return null
    val schritt = 3f * pixelFaktor
    val start = punkte.first()
    val ende = punkte.last()

    // Gerade: alle Punkte nah an der Verbindung Anfang–Ende.
    val gerade = hypot(ende.x - start.x, ende.y - start.y)
    if (gerade > 0.75f * weg) {
        val abweichung = punkte.maxOf { abstandZuGerade(it, start, ende) }
        if (abweichung < max(0.07f * gerade, 6f * pixelFaktor)) return verdichte(listOf(start, ende), schritt)
    }

    // Alles andere muss (fast) geschlossen sein.
    if (gerade > max(0.22f * diagonale, 0.15f * weg)) return null
    val ring = punkte + start

    // Kreis/Ellipse: gleichmäßiger "Radius" relativ zum Begrenzungsrechteck.
    val cx = (minX + maxX) / 2
    val cy = (minY + maxY) / 2
    val rx = max(breite / 2, 1f)
    val ry = max(hoehe / 2, 1f)
    val radien = punkte.map { hypot((it.x - cx) / rx, (it.y - cy) / ry) }
    val mittel = radien.average().toFloat()
    val streuung = sqrt(radien.map { (it - mittel) * (it - mittel) }.average()).toFloat()
    val ecken = vereinfache(ring, 0.07f * diagonale).dropLast(1)

    val wirktRund = streuung < 0.09f || (streuung < 0.13f && ecken.size >= 6)
    if (wirktRund) {
        val kreis = breite / hoehe in 0.8f..1.25f
        val ax = if (kreis) (rx + ry) / 2 else rx
        val ay = if (kreis) (rx + ry) / 2 else ry
        val n = max(48, (Math.PI * (ax + ay) / schritt).toInt())
        return (0..n).map { i ->
            val w = 2 * Math.PI * i / n - Math.PI / 2
            Offset(cx + ax * cos(w).toFloat(), cy + ay * sin(w).toFloat())
        }
    }

    return when (ecken.size) {
        3 -> verdichte(ecken + ecken.first(), schritt)
        4 -> {
            val winkel = ecken.indices.map { i -> innenwinkel(ecken[(i + 3) % 4], ecken[i], ecken[(i + 1) % 4]) }
            val rechtwinklig = winkel.all { abs(it - 90) < 18 }
            // Kanten fast waagerecht/senkrecht: als achsenparalleles Rechteck geraderücken.
            val achsenparallel = (0 until 4).all { i ->
                val a = ecken[i]
                val b = ecken[(i + 1) % 4]
                val dx = abs(b.x - a.x)
                val dy = abs(b.y - a.y)
                min(dx, dy) < 0.2f * max(dx, dy)
            }
            if (rechtwinklig && achsenparallel) {
                verdichte(
                    listOf(Offset(minX, minY), Offset(maxX, minY), Offset(maxX, maxY), Offset(minX, maxY), Offset(minX, minY)),
                    schritt
                )
            } else {
                verdichte(ecken + ecken.first(), schritt)
            }
        }
        in 5..8 -> verdichte(ecken + ecken.first(), schritt)
        else -> null
    }
}
