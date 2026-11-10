package de.oejendorferdamm.dammboard

import androidx.compose.ui.graphics.Color
import de.oejendorferdamm.dammboard.data.UbmxImport
import de.oejendorferdamm.dammboard.model.FormItem
import de.oejendorferdamm.dammboard.model.FormTyp
import de.oejendorferdamm.dammboard.model.StrichItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Import des alten Tafelformats (.ubmx2) – Beispiel: eine Tafel mit Wellenlinie und Kreis auf Grün */
class UbmxImportTest {
    private var id = 0L
    private fun neu() = ++id

    @Test
    fun beispielDatei() {
        val daten = javaClass.classLoader!!.getResourceAsStream("beispiel.ubmx2")!!.readBytes()
        // Board mit halber Auflösung des alten (3840 × 2160 → 1920 × 1080)
        val e = UbmxImport.ausZip(daten, 1920f, 1080f, ::neu)
        assertEquals(1, e.seiten.size)
        val s = e.seiten[0]
        assertEquals(Color(0xFF005A4E), s.hintergrund.farbe) // bgColor="-16754098"
        val striche = s.items.filterIsInstance<StrichItem>()
        val kreise = s.items.filterIsInstance<FormItem>().filter { it.typ == FormTyp.KREIS }
        assertEquals(1, striche.size)
        assertEquals(362, striche[0].punkte.size)
        assertEquals(Color.Black, striche[0].farbe)
        assertEquals(4f, striche[0].breite, 0.01f) // 8 px → halbe Größe
        assertEquals(1, kreise.size)
        val k = kreise[0]
        assertNull(k.fuellFarbe)
        // Mitte 1159,1042 / 2 ≈ 579,521; Radius 275,9 / 2 ≈ 138
        assertEquals(579.7f, (k.start.x + k.ende.x) / 2, 1f)
        assertEquals(138f, (k.ende.x - k.start.x) / 2, 1f)
        assertTrue(e.unbekannt.isEmpty())
    }

    @Test
    fun andereFormenUndSeitenverhaeltnis() {
        val xml = """<ubmx2><screenSize screenWidth="1000" screenHeight="1000" /><pages>
            <page index="0" bgColor="-1" imagePath=""><line startPoint="0,0" endPoint="100,0" strokeWidth="2" borderColor="-65536"/>
            <rect left="10" top="10" right="20" bottom="30" strokeWidth="2" borderColor="-16777216" isFill="true" fillColor="-16711936"/><zauber foo="1"/></page>
            <page index="1" bgColor="-16777216" imagePath="" /></pages></ubmx2>"""
        // quadratisches Alt-Format auf 2000 × 1000: Faktor 1, mittig (500 px Rand links)
        val e = UbmxImport.ausXml(xml, 2000f, 1000f, ::neu)
        assertEquals(2, e.seiten.size)
        val linie = e.seiten[0].items.filterIsInstance<FormItem>().first { it.typ == FormTyp.LINIE }
        assertEquals(500f, linie.start.x, 0.01f)
        assertEquals(Color.Red, linie.randFarbe)
        val quadrat = e.seiten[0].items.filterIsInstance<FormItem>().first { it.typ == FormTyp.QUADRAT }
        assertEquals(Color.Green, quadrat.fuellFarbe)
        assertEquals(1, e.unbekannt["zauber"])
        assertEquals(Color.Black, e.seiten[1].hintergrund.farbe)
    }
}
