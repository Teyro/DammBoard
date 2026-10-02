package de.oejendorferdamm.dammboard.ui.canvas

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import de.oejendorferdamm.dammboard.model.MusterTyp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Stumme Karten als Tafelhintergrund. Die Umrisse liegen vereinfacht in assets/karten
 * (erzeugt aus Natural Earth, deutschlandGeoJSON und den Bezirksgrenzen der FHH, siehe
 * assets/karten/QUELLEN.txt). Format je Zeile:
 *   A <Seitenverhältnis>        P x y x y …  (Umriss, 0…10000)
 *   L x y Name (Beschriftung)   M x y Name (Markierung, z. B. unsere Schule)
 */

internal class KartenDaten(
    val seitenverhaeltnis: Float,
    val ringe: List<FloatArray>,
    val beschriftungen: List<Pair<Offset, String>>,
    val markierungen: List<Pair<Offset, String>>,
    val quelle: String?
)

internal object Karten {
    private var appContext: Context? = null
    private val cache = HashMap<MusterTyp, KartenDaten?>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun daten(typ: MusterTyp): KartenDaten? = synchronized(cache) {
        cache.getOrPut(typ) { lade(typ) }
    }

    private fun lade(typ: MusterTyp): KartenDaten? {
        val (datei, quelle) = when (typ) {
            MusterTyp.KARTE_DEUTSCHLAND -> "deutschland.txt" to null
            MusterTyp.KARTE_HAMBURG -> "hamburg.txt" to "Bezirksgrenzen: © FHH, LGV (dl-de/by-2.0)"
            MusterTyp.KARTE_WELT -> "welt.txt" to null
            else -> return null
        }
        val context = appContext ?: return null
        return try {
            var verhaeltnis = 1.5f
            val ringe = ArrayList<FloatArray>()
            val beschriftungen = ArrayList<Pair<Offset, String>>()
            val markierungen = ArrayList<Pair<Offset, String>>()
            context.assets.open("karten/$datei").bufferedReader(Charsets.UTF_8).useLines { zeilen ->
                zeilen.forEach { zeile ->
                    when {
                        zeile.startsWith("A ") -> verhaeltnis = zeile.substring(2).trim().toFloat()
                        zeile.startsWith("P ") -> {
                            val teile = zeile.substring(2).trim().split(' ')
                            ringe.add(FloatArray(teile.size) { teile[it].toFloat() / 10000f })
                        }
                        zeile.startsWith("L ") || zeile.startsWith("M ") -> {
                            val teile = zeile.substring(2).split(' ', limit = 3)
                            if (teile.size == 3) {
                                val eintrag = Offset(teile[0].toFloat() / 10000f, teile[1].toFloat() / 10000f) to teile[2]
                                if (zeile[0] == 'L') beschriftungen.add(eintrag) else markierungen.add(eintrag)
                            }
                        }
                    }
                }
            }
            KartenDaten(verhaeltnis, ringe, beschriftungen, markierungen, quelle)
        } catch (e: Exception) {
            null
        }
    }
}

private val kartenPinsel = ThreadLocal.withInitial {
    android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
}

/** Zeichnet eine Karte so groß wie möglich, ohne unter die Werkzeugleiste zu rutschen. */
internal fun DrawScope.zeichneKarte(typ: MusterTyp, linienFarbe: Color, textFarbe: Color, p: Float) {
    val daten = Karten.daten(typ) ?: return
    val randSeite = 60f * p
    val randOben = 40f * p
    val randUnten = 125f * p
    val platzB = size.width - 2 * randSeite
    val platzH = size.height - randOben - randUnten
    if (platzB <= 0f || platzH <= 0f) return
    var b = platzB
    var h = b / daten.seitenverhaeltnis
    if (h > platzH) {
        h = platzH
        b = h * daten.seitenverhaeltnis
    }
    val x0 = (size.width - b) / 2
    val y0 = randOben + (platzH - h) / 2
    fun punkt(x: Float, y: Float) = Offset(x0 + x * b, y0 + y * h)

    val pfad = Path()
    daten.ringe.forEach { ring ->
        if (ring.size < 4) return@forEach
        val start = punkt(ring[0], ring[1])
        pfad.moveTo(start.x, start.y)
        var i = 2
        while (i + 1 < ring.size) {
            val q = punkt(ring[i], ring[i + 1])
            pfad.lineTo(q.x, q.y)
            i += 2
        }
        pfad.close()
    }
    drawPath(pfad, linienFarbe, style = Stroke(width = 2.2f * p, join = StrokeJoin.Round))

    val pinsel = kartenPinsel.get()!!
    pinsel.color = textFarbe.toArgb()
    pinsel.isFakeBoldText = false
    pinsel.textSize = (if (typ == MusterTyp.KARTE_HAMBURG) 26f else 19f) * p
    val leinwand = drawContext.canvas.nativeCanvas
    daten.beschriftungen.forEach { (pos, name) ->
        val q = punkt(pos.x, pos.y)
        leinwand.drawText(name, q.x, q.y + pinsel.textSize / 3, pinsel)
    }
    daten.markierungen.forEach { (pos, name) ->
        val q = punkt(pos.x, pos.y)
        val r = 13f * p
        val stern = Path().apply {
            for (k in 0 until 10) {
                val radius = if (k % 2 == 0) r else r * 0.45f
                val w = -Math.PI / 2 + k * Math.PI / 5
                val x = q.x + radius * cos(w).toFloat()
                val y = q.y + radius * sin(w).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(stern, Color(0xFFFBD936))
        drawPath(stern, Color.Black.copy(alpha = 0.5f), style = Stroke(width = 1.2f * p))
        pinsel.isFakeBoldText = true
        leinwand.drawText(name, q.x, q.y + r + pinsel.textSize, pinsel)
        pinsel.isFakeBoldText = false
    }
    daten.quelle?.let { quelle ->
        pinsel.textSize = 13f * p
        pinsel.textAlign = android.graphics.Paint.Align.LEFT
        leinwand.drawText(quelle, x0, y0 + h + 18f * p, pinsel)
        pinsel.textAlign = android.graphics.Paint.Align.CENTER
    }
}
