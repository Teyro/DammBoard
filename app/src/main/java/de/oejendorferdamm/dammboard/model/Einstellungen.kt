package de.oejendorferdamm.dammboard.model

/** Steuert, ob die Oberfläche mit sanften Übergängen (NORMAL) oder ohne jede Animation (PERFORMANCE) läuft. */
enum class AnimationsModus { NORMAL, PERFORMANCE }

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
