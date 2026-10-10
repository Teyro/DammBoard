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
import de.oejendorferdamm.dammboard.data.OrdnerSync
import kotlinx.coroutines.sync.withLock
import de.oejendorferdamm.dammboard.model.abbild
import de.oejendorferdamm.dammboard.ui.filemanager.DateiModus
import de.oejendorferdamm.dammboard.ui.filemanager.bitmapZuPng
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface Bildschirm {
    data object Brett : Bildschirm
    data object Einstellungen : Bildschirm
    data object Tafeln : Bildschirm
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
    val syncOrdnerText by speicher.syncOrdner.collectAsState(initial = null)
    val syncOrdner = syncOrdnerText?.let { android.net.Uri.parse(it) }
    // Für Abläufe, die länger leben als ein Bildaufbau (Sicherung, Abgleich): immer den aktuellen Ordner lesen
    val ordnerAktuell = rememberUpdatedState(syncOrdner)
    val ueberraschungen by speicher.ueberraschungen.collectAsState(initial = true)

    val tafelState = rememberTafelState()
    var bildschirm by remember { mutableStateOf<Bildschirm>(Bildschirm.Brett) }

    // Aus Einstellungen/Dateimanager führt die Zurück-Taste zur Tafel – vorher beendete sie die
    // ganze App und der Tafelinhalt war weg.
    BackHandler(enabled = bildschirm != Bildschirm.Brett) { bildschirm = Bildschirm.Brett }

    LaunchedEffect(formErkennung) { tafelState.formErkennung = formErkennung }

    // ---------------------------------------------------------------- Tafeln im gemeinsamen Ordner
    var syncMeldung by remember { mutableStateOf<String?>(null) }
    val syncPrefs = remember { context.applicationContext.getSharedPreferences("sync", android.content.Context.MODE_PRIVATE) }
    fun verbindungMerken(v: VerbundeneTafel?) {
        tafelState.verbundeneTafel = v
        syncPrefs.edit().apply {
            if (v == null) clear() else putString("name", v.name).putString("uri", v.uri).putLong("stand", v.stand)
        }.apply()
    }
    val syncSperre = remember { kotlinx.coroutines.sync.Mutex() }

    /** Eigene Änderungen in die verbundene Datei schreiben (hat ein anderes Board sie inzwischen geändert: als Kopie). */
    suspend fun syncSchreiben() = syncSperre.withLock {
        val v = tafelState.verbundeneTafel ?: return@withLock
        val baum = ordnerAktuell.value ?: return@withLock
        val signatur = tafelState.syncSignatur()
        if (signatur == v.signatur) return@withLock
        val app = context.applicationContext
        try {
            val uri = android.net.Uri.parse(v.uri)
            val dort = OrdnerSync.stand(app, uri)
            val abbilder = tafelState.seiten.map { it.abbild() }
            val aktiv = tafelState.aktiveSeite
            if (dort == null || dort > v.stand + 2000) {
                // Gelöscht oder von einem anderen Board geändert: nichts überschreiben, eigene Fassung als Kopie
                val zeit = java.text.SimpleDateFormat("HH.mm", java.util.Locale.GERMAN).format(java.util.Date())
                val name = if (dort == null) v.name else "${v.name} (Kopie $zeit)"
                val (neu, stand) = OrdnerSync.speichere(app, baum, name, abbilder, aktiv, null)
                verbindungMerken(VerbundeneTafel(name, neu.toString(), stand, signatur))
                syncMeldung = if (dort == null) "„${v.name}“ wurde auf einem anderen Board gelöscht – die Tafel von hier ist wieder im Ordner gespeichert."
                else "„${v.name}“ wurde gleichzeitig auf einem anderen Board geändert – diese Fassung ist als „$name“ gespeichert."
            } else {
                val (neu, stand) = OrdnerSync.speichere(app, baum, v.name, abbilder, aktiv, uri)
                verbindungMerken(v.copy(uri = neu.toString(), stand = stand, signatur = signatur))
            }
        } catch (e: Exception) {
            // Ordner gerade nicht erreichbar (Netz weg …): beim nächsten Mal wieder versuchen.
        }
    }

    /** Geänderte Fassung von einem anderen Board holen – nur wenn hier seit dem letzten Abgleich nichts geändert wurde. */
    suspend fun syncPruefen() = syncSperre.withLock {
        val v = tafelState.verbundeneTafel ?: return@withLock
        val app = context.applicationContext
        try {
            val uri = android.net.Uri.parse(v.uri)
            val dort = OrdnerSync.stand(app, uri)
            if (dort == null) {
                if (tafelState.syncSignatur() == v.signatur) {
                    verbindungMerken(null)
                    syncMeldung = "„${v.name}“ wurde auf einem anderen Board gelöscht. Die Tafel bleibt hier zu sehen, ist aber nicht mehr verbunden."
                }
                return@withLock
            }
            if (dort > v.stand + 2000 && tafelState.syncSignatur() == v.signatur) {
                val (seiten, aktiv) = OrdnerSync.oeffne(app, uri, ::naechsteId)
                // die gerade offene Seite bleibt offen (falls es sie noch gibt)
                tafelState.seitenErsetzen(seiten, tafelState.aktiveSeite.coerceIn(0, seiten.lastIndex))
                verbindungMerken(v.copy(stand = dort, signatur = tafelState.syncSignatur()))
                syncMeldung = "Neue Fassung von „${v.name}“ von einem anderen Board geladen."
            }
        } catch (e: Exception) {
            // nicht erreichbar – später erneut
        }
    }

    val ordnerWahl = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { baum ->
        if (baum != null) {
            try {
                OrdnerSync.berechtigungMerken(context.applicationContext, baum)
                scope.launch { speicher.speichereSyncOrdner(baum.toString()) }
            } catch (e: Exception) {
                syncMeldung = "Auf diesen Ordner darf DammBoard nicht dauerhaft zugreifen – bitte einen anderen wählen."
            }
        }
    }

    val tafelnAktionen = remember {
        object : TafelnAktionen {
            override fun ordnerWaehlen() {
                // Startet im bisher gewählten Ordner; die Testversion kann für den Emulator-Test einen vorgeben
                val start = ordnerAktuell.value?.let { runCatching { android.provider.DocumentsContract.buildDocumentUriUsingTree(it, android.provider.DocumentsContract.getTreeDocumentId(it)) }.getOrNull() }
                    ?: if (de.oejendorferdamm.dammboard.BuildConfig.DEBUG) TestStartordner.uri else null
                try { ordnerWahl.launch(start) } catch (e: Exception) { syncMeldung = "Auf diesem Board gibt es keine Ordnerauswahl." }
            }

            override suspend fun speichernUnter(name: String): String? {
                val baum = ordnerAktuell.value ?: return "Bitte zuerst einen Ordner wählen."
                return try {
                    syncSperre.withLock {
                        val sauber = OrdnerSync.sichererName(name)
                        val vorhanden = OrdnerSync.liste(context.applicationContext, baum).firstOrNull { it.name.equals(sauber, ignoreCase = true) }
                        val (uri, stand) = OrdnerSync.speichere(context.applicationContext, baum, sauber, tafelState.seiten.map { it.abbild() }, tafelState.aktiveSeite, vorhanden?.uri)
                        verbindungMerken(VerbundeneTafel(sauber, uri.toString(), stand, tafelState.syncSignatur()))
                    }
                    null
                } catch (e: Exception) {
                    "Speichern fehlgeschlagen: ${e.message}"
                }
            }

            override suspend fun oeffnen(datei: OrdnerSync.TafelDatei): String? = try {
                syncSperre.withLock {
                    val (seiten, aktiv) = OrdnerSync.oeffne(context.applicationContext, datei.uri, ::naechsteId)
                    tafelState.seitenErsetzen(seiten, aktiv)
                    val stand = OrdnerSync.stand(context.applicationContext, datei.uri) ?: datei.geaendert
                    verbindungMerken(VerbundeneTafel(datei.name, datei.uri.toString(), stand, tafelState.syncSignatur()))
                }
                null
            } catch (e: Exception) {
                "Öffnen fehlgeschlagen: ${e.message}"
            } catch (e: OutOfMemoryError) {
                "Die Tafel ist zu groß für dieses Board."
            }

            override suspend fun loeschen(datei: OrdnerSync.TafelDatei): String? {
                val ok = OrdnerSync.loesche(context.applicationContext, datei.uri)
                if (ok && tafelState.verbundeneTafel?.uri == datei.uri.toString()) verbindungMerken(null)
                return if (ok) null else "Löschen hat nicht geklappt."
            }

            override fun neueTafel() {
                verbindungMerken(null)
                tafelState.seitenErsetzen(listOf(de.oejendorferdamm.dammboard.model.Seite(tafelState.neueSeitenHintergrund)), 0)
            }

            override fun trennen() = verbindungMerken(null)
        }
    }

    // Andere Boards: alle 30 Sekunden (und beim Zurückkommen in die App) nach einer neueren Fassung sehen
    LaunchedEffect(tafelState.verbundeneTafel?.uri) {
        if (tafelState.verbundeneTafel == null) return@LaunchedEffect
        while (true) {
            delay(30_000)
            syncPruefen()
        }
    }
    LaunchedEffect(syncMeldung) {
        if (syncMeldung != null) {
            delay(12_000)
            syncMeldung = null
        }
    }

    // Automatische Sicherung: beim Start wiederherstellen, danach kurz nach jeder Änderung sichern.
    LaunchedEffect(Unit) {
        val sichern = speicher.tafelSichern.first()
        if (sichern) {
            val geladen = TafelSicherung.lade(context.applicationContext, ::naechsteId)
            // Nur übernehmen, wenn in der Zwischenzeit noch nichts gezeichnet wurde.
            val unberuehrt = tafelState.seiten.size == 1 && tafelState.seite.items.isEmpty()
            if (geladen != null && unberuehrt) {
                tafelState.seitenErsetzen(geladen.first, geladen.second)
                // Verbindung zur Tafel im Ordner wiederherstellen – Stand von damals, damit Neues von anderen Boards erkannt wird
                val uri = syncPrefs.getString("uri", null)
                if (uri != null) tafelState.verbundeneTafel = VerbundeneTafel(syncPrefs.getString("name", "Tafel") ?: "Tafel", uri, syncPrefs.getLong("stand", 0L), tafelState.syncSignatur())
            }
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
                // Verbundene Tafel im gemeinsamen Ordner mitschreiben
                syncSchreiben()
            }
    }
    LaunchedEffect(tafelSichern) { if (!tafelSichern) TafelSicherung.loeschen(context.applicationContext) }

    // Sofort sichern, sobald die App in den Hintergrund geht (Board wird ausgeschaltet, andere App).
    val lebenszyklus = LocalLifecycleOwner.current
    val sichernAktuell by rememberUpdatedState(tafelSichern)
    DisposableEffect(lebenszyklus) {
        val beobachter = LifecycleEventObserver { _, ereignis ->
            if (ereignis == Lifecycle.Event.ON_START) scope.launch { syncPruefen() }
            if (ereignis == Lifecycle.Event.ON_STOP) scope.launch { syncSchreiben() }
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
            neuigkeiten = "DammBoard ${BuildConfig.VERSION_NAME} ist installiert – neu: Werkzeugkasten → Extras → „Tafeln teilen“ " +
                "(Tafeln über einen gemeinsamen Ordner mit anderen Boards abgleichen) und eine kleine Emoji-Überraschung für die Kinder " +
                "(Menü → Einstellungen, dort auch abschaltbar). " +
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
            neuigkeiten = neuigkeiten ?: syncMeldung,
            onNeuigkeitenGelesen = { neuigkeiten = null; syncMeldung = null },
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
            onTafeln = { bildschirm = Bildschirm.Tafeln },
            ueberraschungen = ueberraschungen,
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
                ueberraschungen = ueberraschungen,
                onUeberraschungenGeaendert = { neu -> scope.launch { speicher.speichereUeberraschungen(neu) } },
                onUeberraschungZeigen = {
                    tafelState.ueberraschungJetzt = true
                    bildschirm = Bildschirm.Brett
                },
                onZurueck = { bildschirm = Bildschirm.Brett }
            )
        }
        is Bildschirm.Tafeln -> VorbildRaster(faktor = symbolGroesse.skalierung * MENUE_FAKTOR) {
            TafelnScreen(
                ordner = syncOrdner?.takeIf { OrdnerSync.hatZugriff(context, it) },
                verbunden = tafelState.verbundeneTafel,
                aktionen = tafelnAktionen,
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
