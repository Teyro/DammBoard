package de.oejendorferdamm.dammboard.model

/** Informationen zur neuesten verfügbaren Version, wie von der GitHub-Releases-API geliefert. */
data class UpdateInfo(
    val version: String,
    val herunterladenUrl: String,
    val changelog: String,
    val dateigroesseBytes: Long
)

/** Einfacher Semver-Vergleich (x.y.z, fehlende Teile zählen als 0). */
fun istNeuereVersion(aktuell: String, remote: String): Boolean = de.oejendorferdamm.core.update.Versionen.istNeuer(remote, aktuell)
