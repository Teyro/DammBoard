package de.oejendorferdamm.dammboard

import de.oejendorferdamm.dammboard.data.Klassenplaene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class KlassenplaeneTest {
    private val text = """{"klassen":{"2A":{"tage":{"1":[{"stunde":1,"beginn":"08:30","ende":"09:15","fach":"Mathe"},{"stunde":2,"beginn":"09:15","ende":"10:00","fach":"Deutsch"}]}}}}"""

    @Test
    fun tagDerKlasse() {
        val mo = LocalDate.of(2026, 11, 9)
        val t = Klassenplaene.tag(text, "2a", mo)!!
        assertEquals(listOf("Mathe", "Deutsch"), t.stunden.map { it.fach })
        assertEquals(8, t.stunden[0].beginn!!.hour)
        assertEquals(listOf("2A"), Klassenplaene.klassen(text))
        assertNull(Klassenplaene.tag(text, "Tafel", mo))
        assertNull(Klassenplaene.tag(text, "2A", LocalDate.of(2026, 11, 14)))   // Samstag
        assertEquals(0, Klassenplaene.tag(text, "2A", LocalDate.of(2026, 11, 10))!!.stunden.size)
    }
}

class KlassenplaeneErgaenzenTest {
    @Test
    fun nieUeberschreiben() {
        val mo = LocalDate.of(2026, 11, 9)
        fun s(n: Int, f: String) = de.oejendorferdamm.dammboard.data.Stundenplan.Stunde(n, null, null, f, "")
        val dt = de.oejendorferdamm.dammboard.data.Stundenplan.Tag(mo, false, listOf(s(1, "Kunst")))
        val untis = de.oejendorferdamm.dammboard.data.Stundenplan.Tag(mo, false, listOf(s(1, "Mathe"), s(2, "Deutsch")))
        assertEquals(listOf("Kunst", "Deutsch"), Klassenplaene.ergaenzen(dt, untis)!!.stunden.map { it.fach })
        assertEquals(listOf("Mathe", "Deutsch"), Klassenplaene.ergaenzen(null, untis)!!.stunden.map { it.fach })
        val frei = dt.copy(frei = true, stunden = emptyList())
        assertEquals(0, Klassenplaene.ergaenzen(frei, untis)!!.stunden.size)
    }
}
