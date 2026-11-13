package de.oejendorferdamm.dammboard.data

import java.time.LocalTime

/**
 * Wann ein automatisches Update gesucht und angezeigt werden darf – nie mitten in der Stunde:
 * in den Pausen 10:00–10:15 und 12:00–12:15, nach 13:30 und vor 8:00. Von Hand („Jetzt suchen“) geht es immer.
 * In allen Damm-Apps gleich.
 */
object UpdateZeit {
    fun erlaubt(t: LocalTime = LocalTime.now()): Boolean {
        val m = t.hour * 60 + t.minute
        return m < 8 * 60 || m in 10 * 60 until 10 * 60 + 15 || m in 12 * 60 until 12 * 60 + 15 || m >= 13 * 60 + 30
    }
}
