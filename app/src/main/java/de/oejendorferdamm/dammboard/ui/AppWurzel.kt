package de.oejendorferdamm.dammboard.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import de.oejendorferdamm.dammboard.BuildConfig
import de.oejendorferdamm.dammboard.data.EinstellungenSpeicher
import de.oejendorferdamm.dammboard.data.UpdateClient
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.HintergrundStil
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.model.SymbolGroesse
import de.oejendorferdamm.dammboard.model.TafelGruen
import de.oejendorferdamm.dammboard.model.UpdateInfo
import de.oejendorferdamm.dammboard.model.istNeuereVersion
import de.oejendorferdamm.dammboard.ui.filemanager.DateiManagerScreen
import de.oejendorferdamm.dammboard.ui.settings.EinstellungenScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface Bildschirm {
    data object Brett : Bildschirm
    data object Einstellungen : Bildschirm
    data class Dateimanager(val bild: Bitmap) : Bildschirm
}

/** Wie lange nach dem App-Start die automatische Update-Prüfung im Hintergrund läuft. */
private const val UPDATE_PRUEFUNG_VERZOEGERUNG_MS = 10_000L

/** Menüs (Einstellungen, Dateimanager) etwas größer als die Leiste – dort wird mehr gelesen. */
private const val MENUE_FAKTOR = 1.3f

/** Wurzel der App: schaltet zwischen Tafel, Einstellungen und IServ-Dateimanager um und hält die geteilten Zustände. */
@Composable
fun AppWurzel(onAppSchliessen: () -> Unit) {
    val context = LocalContext.current
    val speicher = remember { EinstellungenSpeicher(context.applicationContext) }
    val updateClient = remember { UpdateClient() }
    val scope = rememberCoroutineScope()

    val iservZugang by speicher.iservZugang.collectAsState(initial = IServZugang())
    val animationsModus by speicher.animationsModus.collectAsState(initial = AnimationsModus.NORMAL)
    val symbolGroesse by speicher.symbolGroesse.collectAsState(initial = SymbolGroesse.STANDARD)
    val autoUpdatePruefung by speicher.autoUpdatePruefung.collectAsState(initial = true)
    val zeichenPraezision by speicher.zeichenPraezision.collectAsState(initial = 0.7f)
    val hintergrundMerken by speicher.hintergrundMerken.collectAsState(initial = false)
    val handballenRadieren by speicher.handballenRadieren.collectAsState(initial = true)
    val handballenEmpfindlichkeit by speicher.handballenEmpfindlichkeit.collectAsState(initial = 0.5f)
    val gespeicherterHintergrund by speicher.gespeicherterHintergrund.collectAsState(initial = null)

    val tafelState = rememberTafelState()
    var bildschirm by remember { mutableStateOf<Bildschirm>(Bildschirm.Brett) }

    // Aus Einstellungen/Dateimanager führt die Zurück-Taste zur Tafel – vorher beendete sie die
    // ganze App und der Tafelinhalt war weg.
    BackHandler(enabled = bildschirm != Bildschirm.Brett) { bildschirm = Bildschirm.Brett }

    // Nach einem Update einmal kurz zeigen, dass die neue Version läuft.
    var neuigkeiten by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val vorher = speicher.zuletztGestarteteVersion.first()
        if (vorher != BuildConfig.VERSION_NAME) {
            neuigkeiten = "DammBoard ${BuildConfig.VERSION_NAME} ist installiert – gestochen scharfe Symbole auch auf " +
                "älteren Boards und neu: Tafelfußball über den Würfel-Knopf unten links. (Antippen zum Schließen)"
            speicher.speichereZuletztGestarteteVersion(BuildConfig.VERSION_NAME)
        }
    }

    // Beim allerersten Laden der gespeicherten Werte (falls "Hintergrund merken" aktiv ist)
    // einmalig den zuletzt genutzten Hintergrund übernehmen – danach merkt sich jede weitere
    // Änderung automatisch selbst (siehe LaunchedEffect weiter unten).
    var anfangsHintergrundGesetzt by remember { mutableStateOf(false) }
    LaunchedEffect(hintergrundMerken, gespeicherterHintergrund) {
        val stil = gespeicherterHintergrund
        // Auch jede neu angelegte Seite startet dann mit dem zuletzt gewählten Hintergrund.
        tafelState.neueSeitenHintergrund = if (hintergrundMerken && stil != null) stil else HintergrundStil(TafelGruen)
        if (!anfangsHintergrundGesetzt && hintergrundMerken && stil != null) {
            tafelState.seite.hintergrund.value = stil
            anfangsHintergrundGesetzt = true
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { tafelState.seite.hintergrund.value }
            .drop(1)
            .collect { stil -> if (hintergrundMerken) speicher.speichereHintergrund(stil) }
    }

    // Automatisch (unauffällig, nur roter Punkt) oder manuell über den Knopf im
    // Einstellungsmenü – beides läuft über dieselbe Prüfung.
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updatePruefungLaeuft by remember { mutableStateOf(false) }
    var updateBereitsAktuell by remember { mutableStateOf(false) }

    suspend fun pruefeAufUpdate() {
        updatePruefungLaeuft = true
        updateBereitsAktuell = false
        updateClient.neuesteVersionAbrufen()
            .onSuccess { info ->
                if (istNeuereVersion(BuildConfig.VERSION_NAME, info.version)) {
                    updateInfo = info
                } else {
                    updateBereitsAktuell = true
                }
            }
        updatePruefungLaeuft = false
    }

    LaunchedEffect(autoUpdatePruefung) {
        if (autoUpdatePruefung) {
            delay(UPDATE_PRUEFUNG_VERZOEGERUNG_MS)
            pruefeAufUpdate()
        }
    }

    // Für die Fehlersuche aus der Ferne: was das Gerät meldet und wie groß die Oberfläche wird.
    val anzeige = context.resources.displayMetrics
    val einheit = pixelProVorbildPixel(anzeige.widthPixels, anzeige.heightPixels)
    val wirksameGroesse = minOf(symbolGroesse.skalierung, groessterLeistenFaktor(anzeige.widthPixels, anzeige.heightPixels))
    val bildschirmInfo = "Bildschirm: ${anzeige.widthPixels}×${anzeige.heightPixels} px, ${anzeige.densityDpi} dpi · " +
        "1 Original-Pixel = ${"%.2f".format(einheit)} px · Größe ${(wirksameGroesse * 100).roundToInt()} %"

    when (val aktuell = bildschirm) {
        is Bildschirm.Brett -> TafelScreen(
            state = tafelState,
            animationsModus = animationsModus,
            oberflaechenFaktor = symbolGroesse.skalierung,
            zeichenPraezision = zeichenPraezision,
            handballenRadieren = handballenRadieren,
            handballenEmpfindlichkeit = handballenEmpfindlichkeit,
            zeigeUpdatePunkt = updateInfo != null,
            neuigkeiten = neuigkeiten,
            onNeuigkeitenGelesen = { neuigkeiten = null },
            onSchliessenApp = onAppSchliessen,
            onOeffneEinstellungen = { bildschirm = Bildschirm.Einstellungen },
            onIServAnfrage = { bitmap -> bildschirm = Bildschirm.Dateimanager(bitmap) }
        )
        is Bildschirm.Einstellungen -> VorbildRaster(faktor = symbolGroesse.skalierung * MENUE_FAKTOR) {
            EinstellungenScreen(
                aktuellerZugang = iservZugang,
                aktuellerModus = animationsModus,
                aktuelleSymbolGroesse = symbolGroesse,
                aktuelleVersion = BuildConfig.VERSION_NAME,
                bildschirmInfo = bildschirmInfo,
                updateInfo = updateInfo,
                autoUpdatePruefung = autoUpdatePruefung,
                updatePruefungLaeuft = updatePruefungLaeuft,
                updateBereitsAktuell = updateBereitsAktuell,
                zeichenPraezision = zeichenPraezision,
                hintergrundMerken = hintergrundMerken,
                handballenRadieren = handballenRadieren,
                handballenEmpfindlichkeit = handballenEmpfindlichkeit,
                onZugangSpeichern = { neu -> scope.launch { speicher.speichereIServZugang(neu) } },
                onModusGeaendert = { neu -> scope.launch { speicher.speichereAnimationsModus(neu) } },
                onSymbolGroesseGeaendert = { neu -> scope.launch { speicher.speichereSymbolGroesse(neu) } },
                onAutoUpdateGeaendert = { neu -> scope.launch { speicher.speichereAutoUpdatePruefung(neu) } },
                onUpdatePruefungAnfordern = { scope.launch { pruefeAufUpdate() } },
                onZeichenPraezisionGeaendert = { neu -> scope.launch { speicher.speichereZeichenPraezision(neu) } },
                onHintergrundMerkenGeaendert = { neu -> scope.launch { speicher.speichereHintergrundMerken(neu) } },
                onHandballenRadierenGeaendert = { neu -> scope.launch { speicher.speichereHandballenRadieren(neu) } },
                onHandballenEmpfindlichkeitGeaendert = { neu -> scope.launch { speicher.speichereHandballenEmpfindlichkeit(neu) } },
                onZurueck = { bildschirm = Bildschirm.Brett }
            )
        }
        is Bildschirm.Dateimanager -> VorbildRaster(faktor = symbolGroesse.skalierung * MENUE_FAKTOR) {
            DateiManagerScreen(
                zugang = iservZugang,
                bild = aktuell.bild,
                onFertig = { bildschirm = Bildschirm.Brett }
            )
        }
    }
}
