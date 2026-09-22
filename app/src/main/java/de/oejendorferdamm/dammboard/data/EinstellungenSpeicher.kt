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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    val HINTERGRUND_FARBE = intPreferencesKey("hintergrund_farbe")
    val HINTERGRUND_MUSTER = stringPreferencesKey("hintergrund_muster")
}

/**
 * Persistiert IServ-Zugangsdaten und den Animationsmodus lokal auf dem Gerät (DataStore
 * Preferences, unverschlüsselt). Für ein von der Schule verwaltetes Tablet ausreichend; das
 * Passwort liegt aber im Klartext in der App-Sandbox, nicht in einem verschlüsselten Speicher.
 */
class EinstellungenSpeicher(private val context: Context) {

    val iservZugang: Flow<IServZugang> = context.einstellungenDataStore.data.map { prefs ->
        IServZugang(
            serverUrl = prefs[Schluessel.SERVER_URL] ?: "",
            benutzername = prefs[Schluessel.BENUTZERNAME] ?: "",
            passwort = prefs[Schluessel.PASSWORT] ?: ""
        )
    }

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
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.SERVER_URL] = zugang.serverUrl
            prefs[Schluessel.BENUTZERNAME] = zugang.benutzername
            prefs[Schluessel.PASSWORT] = zugang.passwort
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
