package de.oejendorferdamm.dammboard.ui.kamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BlattErkennungTest {
    private fun bild(b: Int, h: Int, hell: (Int, Int) -> Boolean) = IntArray(b * h) { if (hell(it % b, it / b)) 230 else 40 }

    @Test
    fun geradesBlattWirdGefunden() {
        val e = BlattErkennung.ecken(bild(200, 150) { x, y -> x in 40..159 && y in 20..129 }, 200, 150)
        assertNotNull(e)
        val (ol, or, ur, ul) = e!!
        assertTrue(abs(ol.x - 40) <= 1 && abs(ol.y - 20) <= 1)
        assertTrue(abs(or.x - 159) <= 1 && abs(or.y - 20) <= 1)
        assertTrue(abs(ur.x - 159) <= 1 && abs(ur.y - 129) <= 1)
        assertTrue(abs(ul.x - 40) <= 1 && abs(ul.y - 129) <= 1)
    }

    @Test
    fun schiefesBlattGibtVierEcken() {
        // leicht gedrehtes Viereck: Ecken (60,20) (170,40) (150,140) (40,120)
        val p = listOf(Punkt(60f, 20f), Punkt(170f, 40f), Punkt(150f, 140f), Punkt(40f, 120f))
        fun innen(x: Int, y: Int): Boolean {
            for (i in 0..3) {
                val a = p[i]; val c = p[(i + 1) % 4]
                if ((c.x - a.x) * (y - a.y) - (c.y - a.y) * (x - a.x) < 0) return false
            }
            return true
        }
        val e = BlattErkennung.ecken(bild(200, 160, ::innen), 200, 160)!!
        for (i in 0..3) assertTrue("Ecke $i: ${e[i]}", abs(e[i].x - p[i].x) <= 3 && abs(e[i].y - p[i].y) <= 3)
    }

    @Test
    fun ohneBlattNichts() {
        assertNull(BlattErkennung.ecken(IntArray(100 * 100) { 128 }, 100, 100))
        assertNull(BlattErkennung.ecken(bild(100, 100) { x, y -> x < 5 && y < 5 }, 100, 100))
    }

    @Test
    fun beinaheA4WirdGenauA4() {
        val (w, h) = BlattErkennung.zielGroesse(listOf(Punkt(0f, 0f), Punkt(1000f, 0f), Punkt(1000f, 1350f), Punkt(0f, 1350f)))
        assertEquals(1000, w)
        assertEquals(1414, h)
        val (w2, h2) = BlattErkennung.zielGroesse(listOf(Punkt(0f, 0f), Punkt(1000f, 0f), Punkt(1000f, 1000f), Punkt(0f, 1000f)))
        assertEquals(1000, w2); assertEquals(1000, h2)
    }
}
