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
import androidx.compose.runtime.mutableIntStateOf
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
import de.oejendorferdamm.dammboard.model.SchulOrdner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface Bildschirm {
    data object Brett : Bildschirm
    data object Einstellungen : Bildschirm
    data object Tafeln : Bildschirm
    data object Ablage : Bildschirm
    data class Dateimanager(val modus: DateiModus, val schulOrdner: Boolean = false) : Bildschirm
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
    val autoUpdatePruefung by speicher.autoUpdatePruefung.collectAsState(initial = false)
    val zeichenPraezision by speicher.zeichenPraezision.collectAsState(initial = 0.7f)
    val hintergrundMerken by speicher.hintergrundMerken.collectAsState(initial = false)
    val handballenRadieren by speicher.handballenRadieren.collectAsState(initial = true)
    val handballenEmpfindlichkeit by speicher.handballenEmpfindlichkeit.collectAsState(initial = 0.5f)
    val radierenStueckweise by speicher.radierenStueckweise.collectAsState(initial = true)
    val gespeicherterHintergrund by speicher.gespeicherterHintergrund.collectAsState(initial = null)
    val tafelSichern by speicher.tafelSichern.collectAsState(initial = true)
    val formErkennung by speicher.formErkennung.collectAsState(initial = true)
    val kameraKnopf by speicher.kameraKnopf.collectAsState(initial = de.oejendorferdamm.dammboard.data.kameraStandard)
    val syncOrdnerText by speicher.syncOrdner.collectAsState(initial = null)
    val syncOrdner = syncOrdnerText?.let { android.net.Uri.parse(it) }
    // Für Abläufe, die länger leben als ein Bildaufbau (Sicherung, Abgleich): immer den aktuellen Ordner lesen
    val ordnerAktuell = rememberUpdatedState(syncOrdner)
    val ueberraschungen by speicher.ueberraschungen.collectAsState(initial = false)
    val schulPasswort by speicher.schulPasswort.collectAsState(initial = "")
    val ablageOrdner by speicher.ablageOrdner.collectAsState(initial = "Tafel")
    val ablageAutomatisch by speicher.ablageAutomatisch.collectAsState(initial = true)

    val tafelState = rememberTafelState()
    var bildschirm by remember { mutableStateOf<Bildschirm>(Bildschirm.Brett) }

    // Aus Einstellungen/Dateimanager führt die Zurück-Taste zur Tafel – vorher beendete sie die
    // ganze App und der Tafelinhalt war weg.
    BackHandler(enabled = bildschirm != Bildschirm.Brett) { bildschirm = Bildschirm.Brett }

    LaunchedEffect(formErkennung) { tafelState.formErkennung = formErkennung }
    LaunchedEffect(kameraKnopf) { tafelState.kameraKnopf = kameraKnopf }
    LaunchedEffect(ueberraschungen) { tafelState.ueberraschungenAn = ueberraschungen }

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

    // ---------------------------------------------------------------- Ablage nach Fächern (aus dem Stundenplan von DammTable)
    val app = context.applicationContext
    val ablagePrefs = remember { app.getSharedPreferences("ablage", android.content.Context.MODE_PRIVATE) }
    val bildschirmPx = remember { context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) to minOf(it.widthPixels, it.heightPixels) } }
    val ablageOrdnerAktuell = rememberUpdatedState(ablageOrdner)
    val ablageAutoAktuell = rememberUpdatedState(ablageAutomatisch)
    var abgelegtSignatur by remember { mutableStateOf<Any?>(null) }
    var ablageMeldung by remember { mutableStateOf<String?>(null) }
    var ablageArbeitet by remember { mutableStateOf(false) }
    var ablageNeu by remember { mutableIntStateOf(0) }
    val ablageSperre = remember { kotlinx.coroutines.sync.Mutex() }
    var plan by remember { mutableStateOf<de.oejendorferdamm.dammboard.data.Stundenplan.Tag?>(null) }

    fun hatInhalt() = tafelState.seiten.any { it.items.isNotEmpty() || it.hintergrundBild.value != null }

    /** Abschnitt „Datum|Stunde|Fach“ jetzt – ohne Stundenplan einer je Tag unter „Tafel“ */
    fun abschnittJetzt(): String {
        val heute = java.time.LocalDate.now()
        val s = de.oejendorferdamm.dammboard.data.Stundenplan.laufend(plan?.takeIf { it.datum == heute }, java.time.LocalTime.now())
        return "$heute|${s?.stunde ?: ""}|${s?.fach ?: "Tafel"}"
    }

    /** Tafel in den Ordner des Abschnitts ablegen (nur mit Inhalt und wenn seit dem letzten Ablegen etwas geändert wurde) */
    suspend fun ablegen(abschnitt: String, auchUnveraendert: Boolean = false): String? = ablageSperre.withLock {
        if (!hatInhalt()) return@withLock null
        val signatur = tafelState.syncSignatur()
        if (!auchUnveraendert && signatur == abgelegtSignatur) return@withLock null
        val teile = abschnitt.split('|')
        val datum = runCatching { java.time.LocalDate.parse(teile[0]) }.getOrDefault(java.time.LocalDate.now())
        val stunde = teile.getOrNull(1)?.toIntOrNull()
        val fach = teile.getOrNull(2)?.ifBlank { null } ?: "Tafel"
        try {
            de.oejendorferdamm.dammboard.data.Ablage.speichere(app, ablageOrdnerAktuell.value, fach, de.oejendorferdamm.dammboard.data.Ablage.name(fach, datum, stunde),
                tafelState.seiten.map { it.abbild() }, tafelState.aktiveSeite, bildschirmPx.first, bildschirmPx.second)
            abgelegtSignatur = signatur
            ablageNeu++
            null
        } catch (e: Exception) { "Fehler beim Ablegen: ${e.message}" }
    }

    // Beim Stundenwechsel die Tafel in den Ordner der Stunde legen, die gerade zu Ende ist (alle 30 s geprüft)
    LaunchedEffect(Unit) {
        var planStand = 0L
        while (true) {
            if (System.currentTimeMillis() - planStand > 10 * 60_000L) {
                plan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // DammTable zuerst; leere Stunden (oder ganz ohne DammTable) aus den Klassenplänen im Schul-Ordner –
                    // Klasse = Name des Ablage-Ordners (z. B. „2A“). Vorhandene Stunden werden nie überschrieben.
                    val ausDammTable = de.oejendorferdamm.dammboard.data.Stundenplan.heute(app)
                    try { de.oejendorferdamm.dammboard.data.Klassenplaene.aktualisieren(app, schulPasswort) } catch (_: Exception) {}
                    de.oejendorferdamm.dammboard.data.Klassenplaene.ergaenzen(ausDammTable, de.oejendorferdamm.dammboard.data.Klassenplaene.tag(app, ablageOrdnerAktuell.value))
                }
                planStand = System.currentTimeMillis()
            }
            val jetzt = abschnittJetzt()
            val vorher = ablagePrefs.getString("abschnitt", null)
            if (vorher != jetzt) {
                if (vorher != null && ablageAutoAktuell.value) ablegen(vorher)
                ablagePrefs.edit().putString("abschnitt", jetzt).apply()
            }
            delay(30_000)
        }
    }

    // Beim Ausschalten/Verlassen: die laufende Stunde ablegen (falls geändert)
    DisposableEffect(lebenszyklus) {
        val beobachter = LifecycleEventObserver { _, ereignis ->
            if (ereignis == Lifecycle.Event.ON_STOP && ablageAutoAktuell.value) scope.launch { ablegen(abschnittJetzt()) }
        }
        lebenszyklus.lifecycle.addObserver(beobachter)
        onDispose { lebenszyklus.lifecycle.removeObserver(beobachter) }
    }

    // Neuer Tag: die Tafel von gestern ablegen und mit einer leeren Seite beginnen
    LaunchedEffect(Unit) {
        delay(1500) // nach dem Wiederherstellen der Sicherung
        val heute = java.time.LocalDate.now().toString()
        val letzter = ablagePrefs.getString("tag", null)
        if (ablageAutomatisch && letzter != null && letzter != heute && tafelState.verbundeneTafel == null && hatInhalt()) {
            val abschnitt = ablagePrefs.getString("abschnitt", null)?.takeIf { it.startsWith(letzter) } ?: "$letzter||Tafel"
            if (ablegen(abschnitt, auchUnveraendert = true) == null) {
                tafelState.neueTafel()
                abgelegtSignatur = tafelState.syncSignatur()
            }
        }
        ablagePrefs.edit().putString("tag", heute).apply()
    }

    /** Alte Tafeln (.ubmx2) in die Ablage übernehmen; [fach] = Ordner in der Ablage. Gibt (Anzahl, letzte Seiten) zurück. */
    suspend fun importiere(dateien: List<Pair<String, android.net.Uri>>, fach: (String) -> String): Pair<Int, List<de.oejendorferdamm.dammboard.model.Seite>?> {
        var anzahl = 0
        var letzte: List<de.oejendorferdamm.dammboard.model.Seite>? = null
        val unbekannt = HashMap<String, Int>()
        for ((pfad, uri) in dateien) {
            try {
                val daten = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: continue
                val e = de.oejendorferdamm.dammboard.data.UbmxImport.ausZip(daten, bildschirmPx.first.toFloat(), bildschirmPx.second.toFloat(), ::naechsteId) { n ->
                    "imp_" + System.nanoTime().toString(36) + "_" + n.replace(Regex("[^A-Za-z0-9_.-]"), "_").takeLast(60)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { e.bilder.forEach { (n, b) -> java.io.File(Arbeitsblaetter.ordner(app), n).writeBytes(b) } }
                e.unbekannt.forEach { (k, v) -> unbekannt[k] = (unbekannt[k] ?: 0) + v }
                // „2026111011284158“ (Zeitstempel im alten Dateinamen) → „Tafel 2026-11-10 11.28“
                val roh = pfad.substringAfterLast('/').removeSuffix(de.oejendorferdamm.dammboard.data.UbmxImport.ENDUNG).removeSuffix(".UBMX2")
                val m = Regex("^(20\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})").find(roh)
                val name = if (m != null) "Tafel ${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]} ${m.groupValues[4]}.${m.groupValues[5]}" else roh
                val f = de.oejendorferdamm.dammboard.data.Ablage.speichere(app, ablageOrdnerAktuell.value, fach(pfad), name, e.seiten, 0, bildschirmPx.first, bildschirmPx.second)
                if (m != null) runCatching {
                    val t = java.time.LocalDateTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())
                    val ms = t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    f.setLastModified(ms); java.io.File(f.parentFile, f.name.removeSuffix(".dammboard") + ".png").setLastModified(ms)
                }
                letzte = e.seiten.map { a -> de.oejendorferdamm.dammboard.model.Seite(a.hintergrund).apply { hintergrundBild.value = a.bild; setzeInhalt(a.items) } }
                anzahl++
            } catch (e: Exception) {
                ablageMeldung = "Nicht lesbar: ${pfad.substringAfterLast('/')} (${e.message})"
            }
        }
        if (unbekannt.isNotEmpty()) ablageMeldung = "Übersprungen (unbekannt): " + unbekannt.entries.joinToString { "${it.key} ${it.value}×" }
        return anzahl to letzte
    }

    val importWahl = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        ablageArbeitet = true
        scope.launch {
            val liste = uris.map { u -> (android.provider.OpenableColumns.DISPLAY_NAME.let { spalte -> app.contentResolver.query(u, arrayOf(spalte), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } } ?: u.lastPathSegment ?: "Tafel") to u }
            val (n, letzte) = importiere(liste) { "Import" }
            // eine einzelne Tafel gleich öffnen (die aktuelle vorher ablegen)
            if (n == 1 && letzte != null) {
                ablegen(abschnittJetzt())
                verbindungMerken(null)
                tafelState.seitenErsetzen(letzte, 0)
                abgelegtSignatur = tafelState.syncSignatur()
            }
            if (ablageMeldung == null || n > 0) ablageMeldung = if (n == 0) "Nichts importiert." else "$n alte Tafel" + (if (n == 1) " importiert und geöffnet (Ordner „Import“)." else "n importiert (Ordner „Import“).")
            ablageArbeitet = false
            ablageNeu++
        }
    }
    val ordnerImportWahl = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { baum ->
        if (baum == null) return@rememberLauncherForActivityResult
        ablageArbeitet = true
        scope.launch {
            // Dateien im Ordner und eine Ebene darunter; Unterordner werden zu Fächern, sonst der Name des Ordners
            val gefunden = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val r = app.contentResolver
                fun kinder(dokId: String): List<Triple<String, String, String>> = try {
                    r.query(android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(baum, dokId), arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                        buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "")) }
                    } ?: emptyList()
                } catch (_: Exception) { emptyList() }
                val wurzelId = android.provider.DocumentsContract.getTreeDocumentId(baum)
                val wurzelName = OrdnerSync.ordnerName(app, baum)
                val liste = ArrayList<Pair<String, android.net.Uri>>()
                val fachVon = HashMap<String, String>()
                fun nimm(id: String, name: String, fach: String) {
                    if (!name.endsWith(".ubmx2", ignoreCase = true)) return
                    val pfad = "$fach/$name"
                    liste.add(pfad to android.provider.DocumentsContract.buildDocumentUriUsingTree(baum, id)); fachVon[pfad] = fach
                }
                for ((id, name, typ) in kinder(wurzelId)) {
                    if (typ == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) kinder(id).forEach { (i2, n2, _) -> nimm(i2, n2, name) }
                    else nimm(id, name, wurzelName)
                }
                liste to fachVon
            }
            val (liste, fachVon) = gefunden
            val (n, _) = importiere(liste) { p -> fachVon[p] ?: "Import" }
            ablageMeldung = if (n == 0) "Im Ordner liegen keine alten Tafeln (.ubmx2)." else "$n alte Tafeln importiert."
            ablageArbeitet = false
            ablageNeu++
        }
    }
    // Testversion: Ablage auf Wunsch gleich öffnen (Oberflächentest), dabei einmal ablegen, damit ein Fach zu sehen ist
    LaunchedEffect(TestStartordner.ablage.value) {
        if (de.oejendorferdamm.dammboard.BuildConfig.DEBUG && TestStartordner.ablage.value) {
            TestStartordner.ablage.value = false
            ablegen(abschnittJetzt(), auchUnveraendert = true)
            bildschirm = Bildschirm.Ablage
        }
    }
    val ablageAktionen = remember {
        object : AblageAktionen {
            override suspend fun oeffnen(e: de.oejendorferdamm.dammboard.data.Ablage.Eintrag): String? = try {
                ablegen(abschnittJetzt())
                val (seiten, aktiv) = de.oejendorferdamm.dammboard.data.Ablage.oeffne(app, e.datei, ::naechsteId)
                verbindungMerken(null)
                tafelState.seitenErsetzen(seiten, aktiv)
                abgelegtSignatur = tafelState.syncSignatur()
                null
            } catch (x: Exception) { ablageMeldung = "Öffnen fehlgeschlagen: ${x.message}"; ablageMeldung }
            override fun importieren() { try { importWahl.launch(arrayOf("*/*")) } catch (_: Exception) { ablageMeldung = "Auf diesem Board gibt es keine Dateiauswahl." } }
            override fun ordnerImportieren() { try { ordnerImportWahl.launch(null) } catch (_: Exception) { ablageMeldung = "Auf diesem Board gibt es keine Ordnerauswahl." } }
            override fun jetztAblegen() { scope.launch { ablageMeldung = ablegen(abschnittJetzt(), auchUnveraendert = true) ?: if (hatInhalt()) "Abgelegt unter „${abschnittJetzt().split('|')[2]}“." else "Die Tafel ist leer." } }
            override fun tafelnTeilen() { bildschirm = Bildschirm.Tafeln }
        }
    }

    // Nach einem Update einmal kurz zeigen, dass die neue Version läuft.
    var neuigkeiten by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val vorher = speicher.zuletztGestarteteVersion.first()
        if (vorher != BuildConfig.VERSION_NAME) {
            neuigkeiten = "DammBoard ${BuildConfig.VERSION_NAME} ist installiert – Einstellungen neu gegliedert (Übersicht mit Bereichen, ✕ immer oben rechts). " +
                "Den Kamera-Knopf kannst du unter Einstellungen → Darstellung ausblenden."
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
            onTafeln = { ablageMeldung = null; bildschirm = Bildschirm.Ablage },
            ueberraschungen = ueberraschungen,
            videoOrdner = syncOrdner,
            onIServOeffnen = { schulOrdner ->
                bildschirm = Bildschirm.Dateimanager(
                    schulOrdner = schulOrdner,
                    modus = DateiModus.Oeffnen { name, bytes ->
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
                kameraKnopf = kameraKnopf,
                onKameraKnopfGeaendert = { neu -> scope.launch { speicher.speichereKameraKnopf(neu) } },
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
        is Bildschirm.Ablage -> AblageScreen(
            ordnerName = ablageOrdner, automatisch = ablageAutomatisch, meldung = ablageMeldung, arbeitet = ablageArbeitet, neuLaden = ablageNeu,
            aktionen = ablageAktionen,
            onOrdnerName = { n -> scope.launch { speicher.speichereAblageOrdner(n) } },
            onAutomatisch = { an -> scope.launch { speicher.speichereAblageAutomatisch(an) } },
            onZurueck = { bildschirm = Bildschirm.Brett }
        )
        is Bildschirm.Dateimanager -> VorbildRaster(faktor = symbolGroesse.skalierung * MENUE_FAKTOR) {
            DateiManagerScreen(
                zugang = if (aktuell.schulOrdner) SchulOrdner.zugang(schulPasswort) else iservZugang,
                modus = aktuell.modus,
                schulOrdner = aktuell.schulOrdner,
                onSchulPasswort = { neu -> scope.launch { speicher.speichereSchulPasswort(neu) } },
                onFertig = { bildschirm = Bildschirm.Brett }
            )
        }
    }
}
