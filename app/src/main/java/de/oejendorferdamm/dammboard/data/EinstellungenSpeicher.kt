package de.oejendorferdamm.dammboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.IServZugang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.einstellungenDataStore by preferencesDataStore(name = "dammboard_einstellungen")

private object Schluessel {
    val SERVER_URL = stringPreferencesKey("iserv_server_url")
    val BENUTZERNAME = stringPreferencesKey("iserv_benutzername")
    val PASSWORT = stringPreferencesKey("iserv_passwort")
    val ANIMATIONSMODUS = stringPreferencesKey("animationsmodus")
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
            else -> AnimationsModus.NORMAL
        }
    }

    suspend fun speichereIServZugang(zugang: IServZugang) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.SERVER_URL] = zugang.serverUrl
            prefs[Schluessel.BENUTZERNAME] = zugang.benutzername
            prefs[Schluessel.PASSWORT] = zugang.passwort
        }
    }

    suspend fun speichereAnimationsModus(modus: AnimationsModus) {
        context.einstellungenDataStore.edit { prefs ->
            prefs[Schluessel.ANIMATIONSMODUS] = modus.name
        }
    }
}
