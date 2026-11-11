package de.oejendorferdamm.dammboard.ui.kamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.hypot

/** Punkt in Bildpixeln (eigene Klasse, damit die Erkennung ohne Android testbar bleibt). */
data class Punkt(val x: Float, val y: Float)

/**
 * „Zauberstab“: findet das helle Blatt im Kamerabild und liefert seine vier Ecken
 * (oben links, oben rechts, unten rechts, unten links). Ohne Zusatzbibliothek:
 * verkleinern → Graustufen → Otsu-Schwelle → größte helle Fläche → äußerste Punkte.
 */
object BlattErkennung {

    /** Ecken im verkleinerten Graubild [grau] (Breite [b], Höhe [h]) – oder null, wenn kein Blatt zu sehen ist. */
    fun ecken(grau: IntArray, b: Int, h: Int): List<Punkt>? {
        if (b < 8 || h < 8 || grau.size != b * h) return null
        val schwelle = otsu(grau)
        val hell = BooleanArray(grau.size) { grau[it] > schwelle }
        // größte zusammenhängende helle Fläche (das Blatt)
        val marke = IntArray(grau.size)
        var besteMarke = 0
        var besteGroesse = 0
        var naechste = 0
        val stapel = IntArray(grau.size)
        for (start in grau.indices) {
            if (!hell[start] || marke[start] != 0) continue
            naechste++
            var oben = 0
            stapel[oben++] = start
            marke[start] = naechste
            var groesse = 0
            while (oben > 0) {
                val i = stapel[--oben]
                groesse++
                val x = i % b
                val y = i / b
                if (x > 0) nimm(i - 1, hell, marke, naechste, stapel, oben)?.let { oben = it }
                if (x < b - 1) nimm(i + 1, hell, marke, naechste, stapel, oben)?.let { oben = it }
                if (y > 0) nimm(i - b, hell, marke, naechste, stapel, oben)?.let { oben = it }
                if (y < h - 1) nimm(i + b, hell, marke, naechste, stapel, oben)?.let { oben = it }
            }
            if (groesse > besteGroesse) { besteGroesse = groesse; besteMarke = naechste }
        }
        // zu klein = kein Blatt; fast alles hell = das Blatt füllt schon das Bild (nichts zu tun)
        if (besteGroesse < grau.size * 0.12f || besteGroesse > grau.size * 0.97f) return null
        var olS = Float.MAX_VALUE; var urS = -Float.MAX_VALUE; var orD = -Float.MAX_VALUE; var ulD = Float.MAX_VALUE
        var ol = Punkt(0f, 0f); var or = ol; var ur = ol; var ul = ol
        for (i in grau.indices) {
            if (marke[i] != besteMarke) continue
            val x = (i % b).toFloat(); val y = (i / b).toFloat()
            val s = x + y; val d = x - y
            if (s < olS) { olS = s; ol = Punkt(x, y) }
            if (s > urS) { urS = s; ur = Punkt(x, y) }
            if (d > orD) { orD = d; or = Punkt(x, y) }
            if (d < ulD) { ulD = d; ul = Punkt(x, y) }
        }
        val ecken = listOf(ol, or, ur, ul)
        // entartet (z. B. nur ein Streifen)? dann lieber nichts tun
        if (flaeche(ecken) < grau.size * 0.1f) return null
        return ecken
    }

    private fun nimm(j: Int, hell: BooleanArray, marke: IntArray, m: Int, stapel: IntArray, oben: Int): Int? {
        if (!hell[j] || marke[j] != 0) return null
        marke[j] = m
        stapel[oben] = j
        return oben + 1
    }

    /** Schwelle nach Otsu: trennt helles Blatt und dunklen Untergrund bestmöglich. */
    fun otsu(grau: IntArray): Int {
        val hist = IntArray(256)
        grau.forEach { hist[it.coerceIn(0, 255)]++ }
        val n = grau.size.toDouble()
        var summe = 0.0
        for (i in 0..255) summe += i * hist[i].toDouble()
        var sB = 0.0; var wB = 0.0; var bestes = 0.0; var schwelle = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = n - wB
            if (wF == 0.0) break
            sB += t * hist[t].toDouble()
            val mB = sB / wB
            val mF = (summe - sB) / wF
            val zwischen = wB * wF * (mB - mF) * (mB - mF)
            if (zwischen > bestes) { bestes = zwischen; schwelle = t }
        }
        return schwelle
    }

    /** Fläche des Vierecks (Gaußsche Trapezformel) */
    fun flaeche(e: List<Punkt>): Float {
        var s = 0f
        for (i in e.indices) { val a = e[i]; val c = e[(i + 1) % e.size]; s += a.x * c.y - c.x * a.y }
        return abs(s) / 2f
    }

    /**
     * Zielgröße des geraden Bildes: mittlere Kantenlängen; liegt das Verhältnis nahe an A4 (√2),
     * wird es genau A4 – so wird ein schief fotografiertes Blatt wieder ein sauberes Blatt.
     */
    fun zielGroesse(e: List<Punkt>): Pair<Int, Int> {
        fun l(a: Punkt, c: Punkt) = hypot(a.x - c.x, a.y - c.y)
        var w = (l(e[0], e[1]) + l(e[3], e[2])) / 2f
        var h = (l(e[0], e[3]) + l(e[1], e[2])) / 2f
        val a4 = 1.4142f
        val v = maxOf(w, h) / maxOf(1f, minOf(w, h))
        if (abs(v - a4) / a4 < 0.12f) { if (h >= w) h = w * a4 else w = h * a4 }
        return w.toInt().coerceIn(16, 6000) to h.toInt().coerceIn(16, 6000)
    }

    /** Ecken im Kamerabild suchen (verkleinert auf etwa 240 px) und auf volle Größe umrechnen */
    fun finde(bild: Bitmap): List<Punkt>? {
        val f = 240f / maxOf(bild.width, bild.height)
        val b = maxOf(8, (bild.width * f).toInt()); val h = maxOf(8, (bild.height * f).toInt())
        val klein = Bitmap.createScaledBitmap(bild, b, h, true)
        val px = IntArray(b * h)
        klein.getPixels(px, 0, b, 0, 0, b, h)
        if (klein !== bild) klein.recycle()
        val grau = IntArray(px.size) { val c = px[it]; ((c shr 16 and 255) * 30 + (c shr 8 and 255) * 59 + (c and 255) * 11) / 100 }
        val sx = bild.width.toFloat() / b; val sy = bild.height.toFloat() / h
        return ecken(grau, b, h)?.map { Punkt((it.x + 0.5f) * sx, (it.y + 0.5f) * sy) }
    }

    /** Viereck gerade ziehen (Perspektive) – ergibt ein neues Bild */
    fun geradeZiehen(bild: Bitmap, e: List<Punkt>): Bitmap {
        val (w, h) = zielGroesse(e)
        val m = Matrix()
        m.setPolyToPoly(
            floatArrayOf(e[0].x, e[0].y, e[1].x, e[1].y, e[2].x, e[2].y, e[3].x, e[3].y), 0,
            floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat()), 0, 4
        )
        val ziel = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(ziel).drawBitmap(bild, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return ziel
    }

    /** 90° im Uhrzeigersinn drehen */
    fun drehe(bild: Bitmap): Bitmap = Bitmap.createBitmap(bild, 0, 0, bild.width, bild.height, Matrix().apply { postRotate(90f) }, true)
}
