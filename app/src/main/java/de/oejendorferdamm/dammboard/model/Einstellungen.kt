package de.oejendorferdamm.dammboard.model

/** Steuert, ob die Oberfläche mit sanften Übergängen (NORMAL) oder ohne jede Animation (PERFORMANCE) läuft. */
enum class AnimationsModus { NORMAL, PERFORMANCE }

/**
 * Größe der Oberfläche (Knöpfe, Abstände, Menüs – inkl. Tippflächen) relativ zum Vorbild, der
 * Tafel-App der CTOUCH-Boards. STANDARD entspricht genau dem Original; die Umrechnung auf den
 * jeweiligen Bildschirm passiert automatisch (siehe ui/Skalierung.kt). Die Namen der Einträge
 * werden gespeichert und dürfen sich deshalb nicht ändern.
 */
enum class SymbolGroesse(val skalierung: Float, val bezeichnung: String) {
    KOMPAKT(0.85f, "Kompakt"),
    STANDARD(1.0f, "Wie Original"),
    GROSS(1.12f, "Groß"),
    SEHR_GROSS(1.25f, "Sehr groß")
}

/** Zugangsdaten für den schuleigenen IServ-WebDAV-Speicher. */
data class IServZugang(
    val serverUrl: String = "",
    val benutzername: String = "",
    val passwort: String = ""
) {
    val istEingerichtet: Boolean
        get() = serverUrl.isNotBlank() && benutzername.isNotBlank() && passwort.isNotBlank()
}

/** Ein Eintrag (Ordner oder Datei) aus einer IServ-WebDAV-Verzeichnisliste. */
data class IServEintrag(
    val name: String,
    val pfad: String,
    val istOrdner: Boolean
)
