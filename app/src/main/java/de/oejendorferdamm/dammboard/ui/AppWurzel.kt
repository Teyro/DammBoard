package de.oejendorferdamm.dammboard.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.oejendorferdamm.dammboard.BuildConfig
import de.oejendorferdamm.dammboard.data.EinstellungenSpeicher
import de.oejendorferdamm.dammboard.data.UpdateClient
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.model.SymbolGroesse
import de.oejendorferdamm.dammboard.model.UpdateInfo
import de.oejendorferdamm.dammboard.model.istNeuereVersion
import de.oejendorferdamm.dammboard.ui.filemanager.DateiManagerScreen
import de.oejendorferdamm.dammboard.ui.settings.EinstellungenScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Bildschirm {
    data object Brett : Bildschirm
    data object Einstellungen : Bildschirm
    data class Dateimanager(val bild: Bitmap) : Bildschirm
}

/** Wie lange nach dem App-Start die Update-Prüfung im Hintergrund läuft. */
private const val UPDATE_PRUEFUNG_VERZOEGERUNG_MS = 10_000L

/** Wurzel der App: schaltet zwischen Tafel, Einstellungen und IServ-Dateimanager um und hält die geteilten Zustände. */
@Composable
fun AppWurzel(onAppSchliessen: () -> Unit) {
    val context = LocalContext.current
    val speicher = remember { EinstellungenSpeicher(context) }
    val updateClient = remember { UpdateClient() }
    val scope = rememberCoroutineScope()

    val iservZugang by speicher.iservZugang.collectAsState(initial = IServZugang())
    val animationsModus by speicher.animationsModus.collectAsState(initial = AnimationsModus.NORMAL)
    val symbolGroesse by speicher.symbolGroesse.collectAsState(initial = SymbolGroesse.STANDARD)

    val tafelState = rememberTafelState()
    var bildschirm by remember { mutableStateOf<Bildschirm>(Bildschirm.Brett) }

    // Läuft einmal pro App-Start im Hintergrund; das Ergebnis zeigt sich nur als kleiner roter
    // Punkt am Einstellungen-Symbol, nie als Dialog – man kann ungestört weiterarbeiten.
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        delay(UPDATE_PRUEFUNG_VERZOEGERUNG_MS)
        updateClient.neuesteVersionAbrufen().onSuccess { info ->
            if (istNeuereVersion(BuildConfig.VERSION_NAME, info.version)) {
                updateInfo = info
            }
        }
    }

    when (val aktuell = bildschirm) {
        is Bildschirm.Brett -> TafelScreen(
            state = tafelState,
            animationsModus = animationsModus,
            symbolSkalierung = symbolGroesse.skalierung,
            zeigeUpdatePunkt = updateInfo != null,
            onSchliessenApp = onAppSchliessen,
            onOeffneEinstellungen = { bildschirm = Bildschirm.Einstellungen },
            onIServAnfrage = { bitmap -> bildschirm = Bildschirm.Dateimanager(bitmap) }
        )
        is Bildschirm.Einstellungen -> EinstellungenScreen(
            aktuellerZugang = iservZugang,
            aktuellerModus = animationsModus,
            aktuelleSymbolGroesse = symbolGroesse,
            aktuelleVersion = BuildConfig.VERSION_NAME,
            updateInfo = updateInfo,
            onZugangSpeichern = { neu -> scope.launch { speicher.speichereIServZugang(neu) } },
            onModusGeaendert = { neu -> scope.launch { speicher.speichereAnimationsModus(neu) } },
            onSymbolGroesseGeaendert = { neu -> scope.launch { speicher.speichereSymbolGroesse(neu) } },
            onZurueck = { bildschirm = Bildschirm.Brett }
        )
        is Bildschirm.Dateimanager -> DateiManagerScreen(
            zugang = iservZugang,
            bild = aktuell.bild,
            onFertig = { bildschirm = Bildschirm.Brett }
        )
    }
}
