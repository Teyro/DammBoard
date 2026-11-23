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

/** Untis-Sicherung (echter Ausschnitt 2C, Montag 23.11.2026): Hauptfach je Stunde, Förderung/SBL zählen nicht */
class KlassenplaeneUntisTest {
    private val text = "{\"faecher\":[{\"name\":\"D\",\"lang\":\"Deutsch\"},{\"name\":\"E\",\"lang\":\"Englisch\"},{\"name\":\"F\",\"lang\":\"Förderung\"},{\"name\":\"Ma\",\"lang\":\"Mathe\"},{\"name\":\"Maxi\",\"lang\":\"Maxistunde\"},{\"name\":\"Sach\",\"lang\":\"Sachunterricht\"},{\"name\":\"SBL\",\"lang\":\"SBL\"},{\"name\":\"SF m\",\"lang\":\"Sprachförderung morgens\"}],\"plaene\":{\"klassen\":{\"2C\":[{\"datum\":\"2026-11-23\",\"von\":\"08:30\",\"bis\":\"09:15\",\"faecher\":[\"D\"]},{\"datum\":\"2026-11-23\",\"von\":\"09:15\",\"bis\":\"10:00\",\"faecher\":[\"D\"]},{\"datum\":\"2026-11-23\",\"von\":\"12:20\",\"bis\":\"13:05\",\"faecher\":[\"E\"]},{\"datum\":\"2026-11-23\",\"von\":\"09:15\",\"bis\":\"10:00\",\"faecher\":[\"F\"]},{\"datum\":\"2026-11-23\",\"von\":\"10:25\",\"bis\":\"11:10\",\"faecher\":[\"F\"]},{\"datum\":\"2026-11-23\",\"von\":\"11:10\",\"bis\":\"11:55\",\"faecher\":[\"F\"]},{\"datum\":\"2026-11-23\",\"von\":\"10:25\",\"bis\":\"11:10\",\"faecher\":[\"Ma\"]},{\"datum\":\"2026-11-23\",\"von\":\"13:05\",\"bis\":\"13:30\",\"faecher\":[\"Maxi\"]},{\"datum\":\"2026-11-23\",\"von\":\"11:10\",\"bis\":\"11:55\",\"faecher\":[\"Sach\"]},{\"datum\":\"2026-11-23\",\"von\":\"08:00\",\"bis\":\"08:30\",\"faecher\":[\"SF m\"]},{\"datum\":\"2026-11-23\",\"von\":\"11:10\",\"bis\":\"11:55\",\"faecher\":[\"SBL\"]},{\"datum\":\"2026-11-23\",\"von\":\"12:20\",\"bis\":\"13:05\",\"faecher\":[\"SBL\"]}]}}}"
    @org.junit.Test fun hauptfachJeStunde() {
        val t = Klassenplaene.ausUntis(text, "2c", LocalDate.of(2026, 11, 23))!!
        assertEquals(listOf("Deutsch", "Deutsch", "Mathe", "Sachunterricht", "Englisch", "Maxistunde"), t.stunden.map { it.fach })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), t.stunden.map { it.stunde })
        // → Ordner der Nextcloud
        assertEquals("Deutsch", de.oejendorferdamm.dammboard.data.Nextcloud.fachOrdner(t.stunden[0].fach, listOf("Deutsch", "Englisch", "Mathematik", "Musik", "Sachunterricht", "Sport", "Tafel")))
        assertEquals("Mathematik", de.oejendorferdamm.dammboard.data.Nextcloud.fachOrdner(t.stunden[2].fach, listOf("Deutsch", "Mathematik")))
        assertNull(Klassenplaene.ausUntis(text, "9z", LocalDate.of(2026, 11, 23)))
    }
}
