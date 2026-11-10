package de.oejendorferdamm.dammboard.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import de.oejendorferdamm.dammboard.model.BildItem
import de.oejendorferdamm.dammboard.model.BoardItem
import de.oejendorferdamm.dammboard.model.FormItem
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.HintergrundStil
import de.oejendorferdamm.dammboard.model.SeitenAbbild
import de.oejendorferdamm.dammboard.model.StrichItem
import de.oejendorferdamm.dammboard.model.TextItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Import der alten Tafel-Dateien (.ubmx2 – ZIP mit pages.xml und images/). Aufbau von pages.xml:
 *   <ubmx2><screenSize screenWidth="3840" screenHeight="2160"/><pages>
 *     <page index="0" bgColor="-16754098" imagePath=""> <curve strokeWidth="8.0" borderColor="-16777216" points="x,y x,y …"/>
 *       <circle isFill="false" strokeWidth="8.0" borderColor="…" fillColor="0" centerPoint="x,y" radius="275.9"/> …
 * Farben sind vorzeichenbehaftete ARGB-Ganzzahlen (Android-Color-Int). Koordinaten werden von der alten Bildschirmgröße
 * auf die Größe dieses Boards umgerechnet. Unbekannte Elemente werden gezählt und übersprungen.
 */
object UbmxImport {
    const val ENDUNG = ".ubmx2"

    class Ergebnis(val seiten: List<SeitenAbbild>, val unbekannt: Map<String, Int>, val bilder: Map<String, ByteArray>)

    /** ZIP lesen: pages.xml + Bilder (images/…) */
    fun ausZip(daten: ByteArray, zielBreite: Float, zielHoehe: Float, neueId: () -> Long, bildName: (String) -> String = { it }): Ergebnis {
        var xml: String? = null
        val bilder = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(daten)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val inhalt = lies(zip)
                when {
                    e.name.endsWith("pages.xml") -> xml = String(inhalt, Charsets.UTF_8)
                    e.name.contains("images/") -> bilder[e.name.substringAfterLast('/')] = inhalt
                }
            }
        }
        return ausXml(xml ?: throw IOException("Keine alte Tafel-Datei (pages.xml fehlt)."), zielBreite, zielHoehe, neueId, bilder, bildName)
    }

    fun ausXml(xml: String, zielBreite: Float, zielHoehe: Float, neueId: () -> Long, bilder: Map<String, ByteArray> = emptyMap(), bildName: (String) -> String = { it }): Ergebnis {
        val bildschirm = Regex("<screenSize\\b([^>]*)>").find(xml)?.groupValues?.get(1)
        val altB = attr(bildschirm ?: "", "screenWidth")?.toFloatOrNull() ?: 1920f
        val altH = attr(bildschirm ?: "", "screenHeight")?.toFloatOrNull() ?: 1080f
        // gleichmäßig skalieren und mittig setzen (Seitenverhältnis bleibt)
        val m = minOf(zielBreite / altB, zielHoehe / altH)
        val dx = (zielBreite - altB * m) / 2f
        val dy = (zielHoehe - altH * m) / 2f
        fun p(x: Float, y: Float) = Offset(dx + x * m, dy + y * m)
        val unbekannt = HashMap<String, Int>()
        val genutzt = HashMap<String, ByteArray>()
        val seiten = ArrayList<SeitenAbbild>()
        for (seite in Regex("<page\\b([^>]*?)(/>|>([\\s\\S]*?)</page>)").findAll(xml)) {
            val kopf = seite.groupValues[1]
            val inhalt = seite.groupValues[3]
            val hintergrund = HintergrundStil(farbe(attr(kopf, "bgColor")) ?: Color(0xFF005A4E))
            val items = ArrayList<BoardItem>()
            var seitenBild: String? = null
            attr(kopf, "imagePath")?.takeIf { it.isNotBlank() }?.let { pfad ->
                val name = pfad.substringAfterLast('/').substringAfterLast('\\')
                bilder[name]?.let { b -> val neu = bildName(name); genutzt[neu] = b; seitenBild = neu }
            }
            for (el in Regex("<([a-zA-Z]+)\\b([^>]*?)/?>").findAll(inhalt)) {
                val typ = el.groupValues[1]
                val a = el.groupValues[2]
                if (typ.startsWith("/")) continue
                val breite = (attr(a, "strokeWidth")?.toFloatOrNull() ?: 4f) * m
                val rand = farbe(attr(a, "borderColor")) ?: farbe(attr(a, "color")) ?: Color.White
                val fuellung = if (attr(a, "isFill") == "true") farbe(attr(a, "fillColor"))?.takeIf { it.alpha > 0f } else null
                val punkte = punkte(attr(a, "points")).map { p(it.x, it.y) }
                when (typ.lowercase()) {
                    "curve", "path", "pen", "stroke", "polyline" -> if (punkte.size >= 2) items.add(StrichItem(neueId(), punkte, rand, breite.coerceAtLeast(1f)))
                        else if (punkte.size == 1) items.add(StrichItem(neueId(), listOf(punkte[0], punkte[0] + Offset(0.5f, 0.5f)), rand, breite.coerceAtLeast(1f)))
                    "circle" -> {
                        val mitte = punkte(attr(a, "centerPoint")).firstOrNull()?.let { p(it.x, it.y) }
                        val r = (attr(a, "radius")?.toFloatOrNull() ?: 0f) * m
                        if (mitte != null && r > 0f) items.add(FormItem(neueId(), FormTyp.KREIS, mitte - Offset(r, r), mitte + Offset(r, r), rand, fuellung, breite, false))
                    }
                    "ellipse", "oval" -> rechteck(a, punkte, ::p)?.let { (s, e) -> items.add(FormItem(neueId(), FormTyp.ELLIPSE, s, e, rand, fuellung, breite, false)) }
                    "rect", "rectangle", "square" -> rechteck(a, punkte, ::p)?.let { (s, e) -> items.add(FormItem(neueId(), FormTyp.QUADRAT, s, e, rand, fuellung, breite, false)) }
                    "triangle" -> rechteck(a, punkte, ::p)?.let { (s, e) -> items.add(FormItem(neueId(), FormTyp.DREIECK, s, e, rand, fuellung, breite, false)) }
                    "line", "arrow" -> {
                        val s = punkte(attr(a, "startPoint")).firstOrNull()?.let { p(it.x, it.y) } ?: punkte.firstOrNull()
                        val e = punkte(attr(a, "endPoint")).firstOrNull()?.let { p(it.x, it.y) } ?: punkte.lastOrNull()
                        if (s != null && e != null && s != e) items.add(FormItem(neueId(), if (typ.lowercase() == "arrow") FormTyp.PFEIL else FormTyp.LINIE, s, e, rand, null, breite, false))
                    }
                    "text" -> {
                        val t = entities(attr(a, "text") ?: attr(a, "content") ?: "")
                        val pos = rechteck(a, punkte, ::p)?.first ?: punkte.firstOrNull()
                        val groesse = (attr(a, "textSize")?.toFloatOrNull() ?: attr(a, "fontSize")?.toFloatOrNull() ?: 48f) * m
                        if (t.isNotBlank() && pos != null) items.add(TextItem(neueId(), pos, t, farbe(attr(a, "textColor")) ?: rand, groesse, groesse * 0.6f * t.length.coerceAtMost(60), groesse * 1.3f))
                    }
                    "image", "picture", "bitmap" -> {
                        val name = (attr(a, "imagePath") ?: attr(a, "path") ?: attr(a, "src") ?: "").substringAfterLast('/').substringAfterLast('\\')
                        val (s, e) = rechteck(a, punkte, ::p) ?: (p(0f, 0f) to p(altB, altH))
                        val b = bilder[name]
                        if (b != null) { val neu = bildName(name); genutzt[neu] = b; items.add(BildItem(neueId(), neu, s, e.x - s.x, e.y - s.y)) }
                        else unbekannt[typ] = (unbekannt[typ] ?: 0) + 1
                    }
                    else -> unbekannt[typ] = (unbekannt[typ] ?: 0) + 1
                }
            }
            seiten.add(SeitenAbbild(items, hintergrund, false, seitenBild))
        }
        if (seiten.isEmpty()) throw IOException("In der Datei steht keine Seite.")
        return Ergebnis(seiten, unbekannt, genutzt)
    }

    /** Rechteck aus left/top/right/bottom, x/y/width/height, leftTop/rightBottom oder den ersten zwei Punkten */
    private fun rechteck(a: String, punkte: List<Offset>, p: (Float, Float) -> Offset): Pair<Offset, Offset>? {
        fun f(n: String) = attr(a, n)?.toFloatOrNull()
        val l = f("left"); val t = f("top"); val r = f("right"); val b = f("bottom")
        if (l != null && t != null && r != null && b != null) return p(l, t) to p(r, b)
        val x = f("x"); val y = f("y"); val w = f("width"); val h = f("height")
        if (x != null && y != null && w != null && h != null) return p(x, y) to p(x + w, y + h)
        val lt = punkte(attr(a, "leftTop") ?: attr(a, "startPoint")).firstOrNull()
        val rb = punkte(attr(a, "rightBottom") ?: attr(a, "endPoint")).firstOrNull()
        if (lt != null && rb != null) return p(lt.x, lt.y) to p(rb.x, rb.y)
        if (punkte.size >= 2) {
            val xs = punkte.map { it.x }; val ys = punkte.map { it.y }
            return Offset(xs.min(), ys.min()) to Offset(xs.max(), ys.max())
        }
        return null
    }

    fun attr(s: String, name: String): String? = Regex("\\b$name=\"([^\"]*)\"").find(s)?.groupValues?.get(1)

    /** "x,y x,y …" → Punkte (Leerzeichen am Ende, doppelte Punkte egal) */
    fun punkte(s: String?): List<Offset> = s.orEmpty().trim().split(Regex("\\s+")).mapNotNull { p ->
        val t = p.split(',')
        if (t.size < 2) null else { val x = t[0].toFloatOrNull(); val y = t[1].toFloatOrNull(); if (x == null || y == null) null else Offset(x, y) }
    }

    /** Vorzeichenbehaftete ARGB-Zahl („-16777216“ = Schwarz) → Farbe */
    fun farbe(s: String?): Color? {
        val n = s?.trim()?.toLongOrNull() ?: return null
        return Color((n and 0xFFFFFFFFL).toInt())
    }

    private fun entities(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#10;", "\n").replace("&amp;", "&")

    private fun lies(zip: ZipInputStream): ByteArray {
        val aus = ByteArrayOutputStream()
        val puffer = ByteArray(64 * 1024)
        while (true) {
            val n = zip.read(puffer)
            if (n < 0) break
            aus.write(puffer, 0, n)
            if (aus.size() > 80L * 1024 * 1024) throw IOException("Eintrag zu groß")
        }
        return aus.toByteArray()
    }
}
