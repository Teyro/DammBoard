package de.oejendorferdamm.dammboard.data

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.HintergrundStil
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.model.MusterTyp
import de.oejendorferdamm.dammboard.model.SymbolGroesse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.einstellungenDataStore by preferencesDataStore(name = "dammboard_einstellungen")

private object Schluessel {
    val SERVER_URL = stringPreferencesKey("iserv_server_url")
    val BENUTZERNAME = stringPreferencesKey("iserv_benutzername")
    val PASSWORT = stringPreferencesKey("iserv_passwort")
    val ANIMATIONSMODUS = stringPreferencesKey("animationsmodus")
    val SYMBOLGROESSE = stringPreferencesKey("symbolgroesse")
    val AUTO_UPDATE_PRUEFUNG = booleanPreferencesKey("auto_update_pruefung")
    val ZEICHEN_PRAEZISION = floatPreferencesKey("zeichen_praezision")
    val HINTERGRUND_MERKEN = booleanPreferencesKey("hintergrund_merken")
    val HANDBALLEN_RADIEREN = booleanPreferencesKey("handballen_radieren")
    val HANDBALLEN_EMPFINDLICHKEIT = floatPreferencesKey("handballen_empfindlichkeit")
    val HINTERGRUND_FARBE = intPreferencesKey("hintergrund_farbe")
    val HINTERGRUND_MUSTER = stringPreferencesKey("hintergrund_muster")
    val ZULETZT_GESTARTETE_VERSION = stringPreferencesKey("zuletzt_gestartete_version")
}

/**
 * Persistiert alle Einstellungen lokal auf dem Gerät (DataStore Preferences). Das IServ-Passwort
 * wird dabei mit einem Schlüssel aus dem Android-Keystore verschlüsselt (siehe Tresor.kt);
 * ältere, noch unverschlüsselt gespeicherte Passwörter werden beim nächsten Speichern umgestellt.
 */
class EinstellungenSpeicher(private val context: Context) {

    val iservZugang: Flow<IServZugang> = context.einstellungenDataStore.data
        .map { prefs ->
            Triple(
                prefs[Schluessel.SERVER_URL] ?: "",
                prefs[Schluessel.BENUTZERNAME] ?: "",
                prefs[Schluessel.PASSWORT] ?: ""
            )
        }
        // Nur neu entschlüsseln, wenn sich der Zugang wirklich geändert hat – DataStore meldet
        // sich bei JEDER gespeicherten Einstellung, auch z. B. beim Hintergrund.
        .distinctUntilChanged()
        .map { (url, benutzer, passwort) -> IServZugang(url, benutzer, Tresor.entschluesseln(passwort)) }
        .flowOn(Dispatchers.Default)

    val animationsModus: Flow<AnimationsModus> = context.einstellungenDataStore.data.map { prefs ->
        when (prefs[Schluessel.ANIMATIONSMODUS]) {
            AnimationsModus.PERFORMANCE.name -> AnimationsModus.PERFORMANCE
            AnimationsModus.NORMAL.name -> AnimationsModus.NORMAL
            // Noch nie gespeichert: sinnvoller Standard je nach Geräte-Alter – Android 8/9 laufen
            // vermutlich auf schwächerer Hardware (Performance-Modus), ab Android 10 (insbesondere
            // die moderneren CleverTouch-Boards mit Android 12+) darf die volle Animation an.
            else -> if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) AnimationsModus.PERFORMANCE else AnimationsModus.NORMAL
        }
    }

    suspend fun speichereIServZugang(zugang: IServZugang) {
        val verschluesselt = withContext(Dispatchers.Default) { Tresor.verschluesseln(zugang.passwort) }
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.SERVER_URL] = zugang.serverUrl
            prefs[Schluessel.BENUTZERNAME] = zugang.benutzername
            prefs[Schluessel.PASSWORT] = verschluesselt
        }
    }

    /** Version, mit der die App zuletzt gestartet wurde – für den Hinweis nach einem Update. */
    val zuletztGestarteteVersion: Flow<String?> = context.einstellungenDataStore.data.map { prefs ->
        prefs[Schluessel.ZULETZT_GESTARTETE_VERSION]
    }

    suspend fun speichereZuletztGestarteteVersion(version: String) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.ZULETZT_GESTARTETE_VERSION] = version
        }
    }

    val symbolGroesse: Flow<SymbolGroesse> = context.einstellungenDataStore.data.map { prefs ->
        SymbolGroesse.entries.find { it.name == prefs[Schluessel.SYMBOLGROESSE] } ?: SymbolGroesse.STANDARD
    }

    suspend fun speichereAnimationsModus(modus: AnimationsModus) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.ANIMATIONSMODUS] = modus.name
        }
    }

    suspend fun speichereSymbolGroesse(groesse: SymbolGroesse) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.SYMBOLGROESSE] = groesse.name
        }
    }

    /** Ob ca. 10 Sekunden nach dem App-Start automatisch (unauffällig, nur roter Punkt) auf
     *  Updates geprüft wird. Manuell prüfen geht im Einstellungsmenü immer, unabhängig davon. */
    val autoUpdatePruefung: Flow<Boolean> = context.einstellungenDataStore.data.map { prefs ->
        prefs[Schluessel.AUTO_UPDATE_PRUEFUNG] ?: true
    }

    suspend fun speichereAutoUpdatePruefung(aktiv: Boolean) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.AUTO_UPDATE_PRUEFUNG] = aktiv
        }
    }

    /** 0 = grob/performant, 1 = maximal fein – Standard liegt bewusst näher an "fein", damit auch
     *  klein Geschriebenes gut lesbar bleibt, ohne alte Touch-Geräte zu überlasten. */
    val zeichenPraezision: Flow<Float> = context.einstellungenDataStore.data.map { prefs ->
        prefs[Schluessel.ZEICHEN_PRAEZISION] ?: 0.7f
    }

    suspend fun speichereZeichenPraezision(wert: Float) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.ZEICHEN_PRAEZISION] = wert.coerceIn(0f, 1f)
        }
    }

    /** Wischen mit dem Handballen (siehe ui/canvas/Handballen.kt) – standardmäßig an. */
    val handballenRadieren: Flow<Boolean> = context.einstellungenDataStore.data.map { prefs ->
        prefs[Schluessel.HANDBALLEN_RADIEREN] ?: true
    }

    suspend fun speichereHandballenRadieren(aktiv: Boolean) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.HANDBALLEN_RADIEREN] = aktiv
        }
    }

    /** 0 = nur eindeutig große Flächen gelten als Hand, 1 = sehr empfindlich. */
    val handballenEmpfindlichkeit: Flow<Float> = context.einstellungenDataStore.data.map { prefs ->
        prefs[Schluessel.HANDBALLEN_EMPFINDLICHKEIT] ?: 0.5f
    }

    suspend fun speichereHandballenEmpfindlichkeit(wert: Float) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.HANDBALLEN_EMPFINDLICHKEIT] = wert.coerceIn(0f, 1f)
        }
    }

    /** Aus: jede neue Seite beginnt wieder auf dem bekannten grünen Tafelhintergrund. An: die
     *  zuletzt gewählte Hintergrundfarbe/-muster wird gemerkt und beim nächsten Start verwendet. */
    val hintergrundMerken: Flow<Boolean> = context.einstellungenDataStore.data.map { prefs ->
        prefs[Schluessel.HINTERGRUND_MERKEN] ?: false
    }

    suspend fun speichereHintergrundMerken(aktiv: Boolean) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.HINTERGRUND_MERKEN] = aktiv
        }
    }

    val gespeicherterHintergrund: Flow<HintergrundStil?> = context.einstellungenDataStore.data.map { prefs ->
        val farbe = prefs[Schluessel.HINTERGRUND_FARBE] ?: return@map null
        val muster = MusterTyp.entries.find { it.name == prefs[Schluessel.HINTERGRUND_MUSTER] } ?: MusterTyp.KEIN
        HintergrundStil(Color(farbe), muster)
    }

    suspend fun speichereHintergrund(stil: HintergrundStil) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.HINTERGRUND_FARBE] = stil.farbe.toArgb()
            prefs[Schluessel.HINTERGRUND_MUSTER] = stil.muster.name
        }
    }
}
