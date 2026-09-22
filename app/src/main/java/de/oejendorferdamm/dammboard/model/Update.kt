package de.oejendorferdamm.dammboard.model

/** Informationen zur neuesten verfügbaren Version, wie von der GitHub-Releases-API geliefert. */
data class UpdateInfo(
    val version: String,
    val herunterladenUrl: String,
    val changelog: String,
    val dateigroesseBytes: Long
)

/** Einfacher Semver-Vergleich (x.y.z, fehlende Teile zählen als 0). */
fun istNeuereVersion(aktuell: String, remote: String): Boolean {
    val a = aktuell.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val r = remote.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val laenge = maxOf(a.size, r.size)
    for (i in 0 until laenge) {
        val av = a.getOrElse(i) { 0 }
        val rv = r.getOrElse(i) { 0 }
        if (rv != av) return rv > av
    }
    return false
}
