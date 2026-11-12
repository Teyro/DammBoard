package de.oejendorferdamm.dammboard

import de.oejendorferdamm.dammboard.data.AlteTafeln
import de.oejendorferdamm.dammboard.data.Nextcloud
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class AlteTafelnTest {
    @Test
    fun findetUbmx2AuchTiefUndNichtVersteckt() {
        val w = Files.createTempDirectory("tafeln").toFile()
        java.io.File(w, "a/b/c").mkdirs(); java.io.File(w, ".versteckt").mkdirs()
        java.io.File(w, "a/1.ubmx2").writeText("x")
        java.io.File(w, "a/b/c/2.UBMX2").writeText("x")
        java.io.File(w, "a/b/notiz.txt").writeText("x")
        java.io.File(w, ".versteckt/3.ubmx2").writeText("x")
        java.io.File(w, "a/leer.ubmx2").createNewFile()
        assertEquals(setOf("1.ubmx2", "2.UBMX2"), AlteTafeln.suche(listOf(w)).map { it.name }.toSet())
        w.deleteRecursively()
    }

    @Test
    fun nextcloudAdresse() {
        assertEquals("https://cloud.schule.de/remote.php/dav/files/board%201", Nextcloud.davAdresse("cloud.schule.de/", "board 1"))
        assertEquals("https://c.de/remote.php/dav/files/u", Nextcloud.davAdresse("https://c.de/index.php/apps/files", "u"))
        assertEquals("https://c.de/remote.php/dav/files/u/x", Nextcloud.davAdresse("https://c.de/remote.php/dav/files/u/x", "u"))
    }
}
