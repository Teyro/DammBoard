package de.oejendorferdamm.dammboard.model

/** Steuert, ob die Oberfläche mit sanften Übergängen (NORMAL) oder ohne jede Animation (PERFORMANCE) läuft. */
enum class AnimationsModus { NORMAL, PERFORMANCE }

/** Größe der Werkzeugleisten-Symbole. STANDARD ist bereits etwas größer als die ursprüngliche Größe. */
enum class SymbolGroesse(val skalierung: Float) {
    KOMPAKT(1.0f),
    STANDARD(1.15f),
    GROSS(1.35f)
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
