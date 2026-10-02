package de.oejendorferdamm.dammboard.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import kotlinx.coroutines.flow.collectLatest
import de.oejendorferdamm.dammboard.data.TafelSicherung
import de.oejendorferdamm.dammboard.model.abbild
import de.oejendorferdamm.dammboard.ui.filemanager.DateiModus
import de.oejendorferdamm.dammboard.ui.filemanager.bitmapZuPng
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface Bildschirm {
    data object Brett : Bildschirm
    data object Einstellungen : Bildschirm
    data class Dateimanager(val modus: DateiModus) : Bildschirm
}

/** Wie lange nach dem App-Start die automatische Update-Prüfung im Hintergrund läuft. */
private const val UPDATE_PRUEFUNG_VERZOEGERUNG_MS = 10_000L

/** So lange nach der letzten Änderung wird automatisch gesichert. */
private const val SICHERUNG_VERZOEGERUNG_MS = 1500L

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
    val radierenStueckweise by speicher.radierenStueckweise.collectAsState(initial = true)
    val gespeicherterHintergrund by speicher.gespeicherterHintergrund.collectAsState(initial = null)
    val tafelSichern by speicher.tafelSichern.collectAsState(initial = true)
    val formErkennung by speicher.formErkennung.collectAsState(initial = true)

    val tafelState = rememberTafelState()
    var bildschirm by remember { mutableStateOf<Bildschirm>(Bildschirm.Brett) }

    // Aus Einstellungen/Dateimanager führt die Zurück-Taste zur Tafel – vorher beendete sie die
    // ganze App und der Tafelinhalt war weg.
    BackHandler(enabled = bildschirm != Bildschirm.Brett) { bildschirm = Bildschirm.Brett }

    LaunchedEffect(formErkennung) { tafelState.formErkennung = formErkennung }

    // Automatische Sicherung: beim Start wiederherstellen, danach kurz nach jeder Änderung sichern.
    LaunchedEffect(Unit) {
        val sichern = speicher.tafelSichern.first()
        if (sichern) {
            val geladen = TafelSicherung.lade(context.applicationContext, ::naechsteId)
            // Nur übernehmen, wenn in der Zwischenzeit noch nichts gezeichnet wurde.
            val unberuehrt = tafelState.seiten.size == 1 && tafelState.seite.items.isEmpty()
            if (geladen != null && unberuehrt) tafelState.seitenErsetzen(geladen.first, geladen.second)
        } else {
            TafelSicherung.loeschen(context.applicationContext)
        }
        snapshotFlow {
            // Alles, was eine Sicherung auslösen soll
            tafelState.aktiveSeite to tafelState.seiten.map {
                listOf(it.versionsZaehler, it.hintergrund.value, it.hintergrundBild.value, it.geteilteAnsicht.value)
            }
        }
            .drop(1)
            .collectLatest {
                delay(SICHERUNG_VERZOEGERUNG_MS)
                if (speicher.tafelSichern.first()) {
                    val abbilder = tafelState.seiten.map { it.abbild() }
                    try {
                        TafelSicherung.speichere(context.applicationContext, abbilder, tafelState.aktiveSeite)
                    } catch (e: Exception) {
                        // Voller Speicher o. Ä.: beim nächsten Mal wieder versuchen.
                    }
                }
            }
    }
    LaunchedEffect(tafelSichern) { if (!tafelSichern) TafelSicherung.loeschen(context.applicationContext) }

    // Sofort sichern, sobald die App in den Hintergrund geht (Board wird ausgeschaltet, andere App).
    val lebenszyklus = LocalLifecycleOwner.current
    val sichernAktuell by rememberUpdatedState(tafelSichern)
    DisposableEffect(lebenszyklus) {
        val beobachter = LifecycleEventObserver { _, ereignis ->
            if (ereignis == Lifecycle.Event.ON_STOP && sichernAktuell) {
                val abbilder = tafelState.seiten.map { it.abbild() }
                val aktiv = tafelState.aktiveSeite
                scope.launch {
                    try {
                        TafelSicherung.speichere(context.applicationContext, abbilder, aktiv)
                    } catch (e: Exception) {
                        // nächster Versuch bei der nächsten Änderung
                    }
                }
            }
        }
        lebenszyklus.lifecycle.addObserver(beobachter)
        onDispose { lebenszyklus.lifecycle.removeObserver(beobachter) }
    }

    // Nach einem Update einmal kurz zeigen, dass die neue Version läuft.
    var neuigkeiten by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val vorher = speicher.zuletztGestarteteVersion.first()
        if (vorher != BuildConfig.VERSION_NAME) {
            neuigkeiten = "DammBoard ${BuildConfig.VERSION_NAME} ist installiert – neu: Werkzeugkasten → Extras (PDF, " +
                "Arbeitsblatt, Abdecken, Timer, Würfel, Zufallsname, Gruppen, Lautstärke, Lernuhr), Stempel & Text bei den Formen, " +
                "Textmarker, Formerkennung (Stift kurz halten), Seitenübersicht (Seitenzahl antippen) und automatische Sicherung. " +
                "(Antippen zum Schließen)"
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
            radierenStueckweise = radierenStueckweise,
            zeigeUpdatePunkt = updateInfo != null,
            neuigkeiten = neuigkeiten,
            onNeuigkeitenGelesen = { neuigkeiten = null },
            onSchliessenApp = onAppSchliessen,
            onOeffneEinstellungen = { bildschirm = Bildschirm.Einstellungen },
            onIServAnfrage = { bitmap ->
                bildschirm = Bildschirm.Dateimanager(
                    DateiModus.Speichern("DammBoard_${System.currentTimeMillis()}.png", "image/png") { bitmapZuPng(bitmap) }
                )
            },
            tafelWirdGesichert = tafelSichern,
            onIServPdf = { datei ->
                bildschirm = Bildschirm.Dateimanager(DateiModus.Speichern(datei.name, "application/pdf") { datei.readBytes() })
            },
            onIServOeffnen = {
                bildschirm = Bildschirm.Dateimanager(
                    DateiModus.Oeffnen { name, bytes ->
                        try {
                            val namen = Arbeitsblaetter.importiere(context.applicationContext, name) { bytes.inputStream() }
                            tafelState.arbeitsblaetterEinfuegen(namen)
                            null
                        } catch (e: Exception) {
                            e.message ?: "Datei konnte nicht geöffnet werden"
                        } catch (e: OutOfMemoryError) {
                            "Die Datei ist zu groß für dieses Board"
                        }
                    }
                )
            }
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
                radierenStueckweise = radierenStueckweise,
                tafelSichern = tafelSichern,
                formErkennung = formErkennung,
                onTafelSichernGeaendert = { neu -> scope.launch { speicher.speichereTafelSichern(neu) } },
                onFormErkennungGeaendert = { neu -> scope.launch { speicher.speichereFormErkennung(neu) } },
                onZugangSpeichern = { neu -> scope.launch { speicher.speichereIServZugang(neu) } },
                onModusGeaendert = { neu -> scope.launch { speicher.speichereAnimationsModus(neu) } },
                onSymbolGroesseGeaendert = { neu -> scope.launch { speicher.speichereSymbolGroesse(neu) } },
                onAutoUpdateGeaendert = { neu -> scope.launch { speicher.speichereAutoUpdatePruefung(neu) } },
                onUpdatePruefungAnfordern = { scope.launch { pruefeAufUpdate() } },
                onZeichenPraezisionGeaendert = { neu -> scope.launch { speicher.speichereZeichenPraezision(neu) } },
                onHintergrundMerkenGeaendert = { neu -> scope.launch { speicher.speichereHintergrundMerken(neu) } },
                onHandballenRadierenGeaendert = { neu -> scope.launch { speicher.speichereHandballenRadieren(neu) } },
                onHandballenEmpfindlichkeitGeaendert = { neu -> scope.launch { speicher.speichereHandballenEmpfindlichkeit(neu) } },
                onRadierenStueckweiseGeaendert = { neu -> scope.launch { speicher.speichereRadierenStueckweise(neu) } },
                onZurueck = { bildschirm = Bildschirm.Brett }
            )
        }
        is Bildschirm.Dateimanager -> VorbildRaster(faktor = symbolGroesse.skalierung * MENUE_FAKTOR) {
            DateiManagerScreen(
                zugang = iservZugang,
                modus = aktuell.modus,
                onFertig = { bildschirm = Bildschirm.Brett }
            )
        }
    }
}
