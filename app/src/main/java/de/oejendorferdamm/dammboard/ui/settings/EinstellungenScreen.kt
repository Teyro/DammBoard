package de.oejendorferdamm.dammboard.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.data.UpdateClient
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.model.SymbolGroesse
import de.oejendorferdamm.dammboard.model.UpdateInfo
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import de.oejendorferdamm.dammboard.ui.update.ApkPruefung
import de.oejendorferdamm.dammboard.ui.update.installationsIntentFuer
import de.oejendorferdamm.dammboard.ui.update.pruefeUpdateApk
import de.oejendorferdamm.dammboard.ui.update.kannUnbekannteQuellenInstallieren
import de.oejendorferdamm.dammboard.ui.update.oeffneUnbekannteQuellenEinstellungen
import de.oejendorferdamm.dammboard.ui.m3.EinstellungenThema
import de.oejendorferdamm.dammboard.ui.m3.M3Auswahl
import de.oejendorferdamm.dammboard.ui.m3.M3Bereich
import de.oejendorferdamm.dammboard.ui.m3.M3Feld
import de.oejendorferdamm.dammboard.ui.m3.M3Gruppe
import de.oejendorferdamm.dammboard.ui.m3.M3Hinweis
import de.oejendorferdamm.dammboard.ui.m3.M3Knopf
import de.oejendorferdamm.dammboard.ui.m3.M3Kopf
import de.oejendorferdamm.dammboard.ui.m3.M3Schalter
import kotlinx.coroutines.launch
import java.io.File


/** Einstellungsmenü im Material-You-Stil (gemeinsame Bausteine ui/m3): Übersicht → Bereiche, ← zurück, ✕ schließen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EinstellungenScreen(
    aktuellerZugang: IServZugang,
    aktuellerModus: AnimationsModus,
    aktuelleSymbolGroesse: SymbolGroesse,
    aktuelleVersion: String,
    bildschirmInfo: String,
    updateInfo: UpdateInfo?,
    autoUpdatePruefung: Boolean,
    updatePruefungLaeuft: Boolean,
    updateBereitsAktuell: Boolean,
    zeichenPraezision: Float,
    hintergrundMerken: Boolean,
    handballenRadieren: Boolean,
    handballenEmpfindlichkeit: Float,
    radierenStueckweise: Boolean,
    tafelSichern: Boolean,
    formErkennung: Boolean,
    onTafelSichernGeaendert: (Boolean) -> Unit,
    onFormErkennungGeaendert: (Boolean) -> Unit,
    kameraKnopf: Boolean = true,
    onKameraKnopfGeaendert: (Boolean) -> Unit = {},
    ueberraschungen: Boolean = true,
    onUeberraschungenGeaendert: (Boolean) -> Unit = {},
    onUeberraschungZeigen: () -> Unit = {},
    onZugangSpeichern: (IServZugang) -> Unit,
    onModusGeaendert: (AnimationsModus) -> Unit,
    onSymbolGroesseGeaendert: (SymbolGroesse) -> Unit,
    onAutoUpdateGeaendert: (Boolean) -> Unit,
    onUpdatePruefungAnfordern: () -> Unit,
    onZeichenPraezisionGeaendert: (Float) -> Unit,
    onHintergrundMerkenGeaendert: (Boolean) -> Unit,
    onHandballenRadierenGeaendert: (Boolean) -> Unit,
    onHandballenEmpfindlichkeitGeaendert: (Float) -> Unit,
    onRadierenStueckweiseGeaendert: (Boolean) -> Unit,
    onZurueck: () -> Unit
) {
    var serverUrl by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.serverUrl) }
    var benutzername by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.benutzername) }
    var passwort by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.passwort) }
    var urlFehler by remember { mutableStateOf<String?>(null) }
    var seite by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    EinstellungenThema {
    val f = MaterialTheme.colorScheme
    // systemBarsPadding: Die Karte bleibt zwischen Status- und Navigationsleiste, statt auf
    // kleineren Bildschirmen darunter zu verschwinden (die App zeichnet randlos).
    Box(modifier = Modifier.fillMaxSize().background(Color(0x66000000)).systemBarsPadding()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .widthIn(max = 760.dp)
                .heightIn(max = 1100.dp)
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(f.surfaceContainer)
        ) {
            val titel = when (seite) {
                "update" -> "Updates"; "darstellung" -> "Aussehen"; "zeichnen" -> "Zeichnen und Radieren"
                "tafel" -> "Tafel"; "spass" -> "Überraschungen"; "iserv" -> "IServ"; "ueber" -> "Über DammBoard"; "kamera" -> "Dokumentenkamera"
                else -> "Einstellungen"
            }
            // Zurück-Taste: erst zur Übersicht, dann wie das ✕
            androidx.activity.compose.BackHandler(enabled = seite != null) { seite = null }
            // Kopfzeile bleibt stehen (✕ immer sichtbar) – gescrollt wird nur der Inhalt darunter
            M3Kopf(titel, if (seite != null) ({ seite = null }) else null, onZurueck)
            androidx.compose.runtime.key(seite) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            val an = { b: Boolean -> if (b) "an" else "aus" }
            when (seite) {
                null -> {
                    // Reihenfolge wie in allen Damm-Apps: erst die eigenen Bereiche, unter „Allgemein“ Updates und Über
                    M3Gruppe {
                        M3Bereich("🎨", Color(0xFF7C3AED), "Aussehen",
                            "${if (aktuellerModus == AnimationsModus.NORMAL) "Normal" else "Performance"} · Symbole ${aktuelleSymbolGroesse.bezeichnung}") { seite = "darstellung" }
                        M3Bereich("✏️", Color(0xFFD97706), "Zeichnen und Radieren",
                            "Formerkennung ${an(formErkennung)} · Radierer ${if (radierenStueckweise) "stückweise" else "ganz"} · Handballen ${an(handballenRadieren)}") { seite = "zeichnen" }
                        M3Bereich("🗂", Color(0xFF0891B2), "Tafel",
                            "Automatisch sichern ${an(tafelSichern)} · Hintergrund merken ${an(hintergrundMerken)}") { seite = "tafel" }
                        M3Bereich("📷", Color(0xFFE01E3C), "Dokumentenkamera",
                            if (kameraKnopf) "an – Knopf unten links in der Leiste" else "aus – kein Kamera-Knopf") { seite = "kamera" }
                        M3Bereich("🎉", Color(0xFFDB2777), "Überraschungen", "Easter Eggs ${an(ueberraschungen)} · Überraschungsvideo") { seite = "spass" }
                        M3Bereich("☁️", Color(0xFF16A34A), "IServ",
                            if (aktuellerZugang.serverUrl.isBlank()) "nicht eingerichtet" else aktuellerZugang.serverUrl.removePrefix("https://").substringBefore("/")) { seite = "iserv" }
                    }
                    M3Gruppe("Allgemein") {
                        M3Bereich("⬆️", Color(0xFF2563EB), "Updates",
                            if (updateInfo != null) "Neue Version ${updateInfo.version} verfügbar" else "Installiert: $aktuelleVersion · automatisch prüfen ${an(autoUpdatePruefung)}",
                            hinweis = updateInfo != null) { seite = "update" }
                        M3Bereich("ℹ️", Color(0xFF6B7280), "Über DammBoard", "Version $aktuelleVersion · Bildschirm") { seite = "ueber" }
                    }
                }
                "update" -> UpdateAbschnitt(
                    aktuelleVersion = aktuelleVersion,
                    info = updateInfo,
                    autoPruefung = autoUpdatePruefung,
                    pruefungLaeuft = updatePruefungLaeuft,
                    bereitsAktuell = updateBereitsAktuell,
                    onAutoGeaendert = onAutoUpdateGeaendert,
                    onJetztPruefen = onUpdatePruefungAnfordern
                )
                "darstellung" -> {
                    M3Gruppe("Animationen") {
                        M3Feld {
                            M3Hinweis("Normalmodus mit sanften Übergängen, oder Performance-Modus ganz ohne Animationen.")
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                M3Auswahl("Normal", aktuellerModus == AnimationsModus.NORMAL, { onModusGeaendert(AnimationsModus.NORMAL) })
                                M3Auswahl("Performance", aktuellerModus == AnimationsModus.PERFORMANCE, { onModusGeaendert(AnimationsModus.PERFORMANCE) })
                            }
                        }
                    }
                    M3Gruppe("Symbolgröße") {
                        M3Feld {
                            M3Hinweis("„Wie Original“ entspricht genau der Tafel-App des Boards – Knöpfe, Abstände und Menüs " +
                                "werden automatisch an die Bildschirmbreite angepasst. Größer oder kleiner verändert alles gemeinsam.")
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SymbolGroesse.entries.forEach { g -> M3Auswahl(g.bezeichnung, aktuelleSymbolGroesse == g, { onSymbolGroesseGeaendert(g) }) }
                            }
                        }
                    }
                }
                "kamera" -> M3Gruppe {
                    M3Feld {
                        M3Schalter("Kamera-Knopf zeigen", kameraKnopf,
                            "Knopf „Dokumentenkamera“ unten links in der Leiste: fotografieren, zuschneiden, drehen, Zauberstab – und als Bild auf die Tafel. " +
                                "Aus, wenn an diesem Board keine Dokumentenkamera hängt (auf den älteren CTOUCH-Boards ist sie von Haus aus aus).",
                            onKameraKnopfGeaendert)
                    }
                }
                "zeichnen", "tafel", "spass" -> ErweiterteEinstellungen(
                    teil = seite!!,
                    zeichenPraezision = zeichenPraezision,
                    hintergrundMerken = hintergrundMerken,
                    handballenRadieren = handballenRadieren,
                    handballenEmpfindlichkeit = handballenEmpfindlichkeit,
                    radierenStueckweise = radierenStueckweise,
                    tafelSichern = tafelSichern,
                    formErkennung = formErkennung,
                    onTafelSichernGeaendert = onTafelSichernGeaendert,
                    onFormErkennungGeaendert = onFormErkennungGeaendert,
                    onZeichenPraezisionGeaendert = onZeichenPraezisionGeaendert,
                    onHintergrundMerkenGeaendert = onHintergrundMerkenGeaendert,
                    onHandballenRadierenGeaendert = onHandballenRadierenGeaendert,
                    onHandballenEmpfindlichkeitGeaendert = onHandballenEmpfindlichkeitGeaendert,
                    onRadierenStueckweiseGeaendert = onRadierenStueckweiseGeaendert,
                    ueberraschungen = ueberraschungen,
                    onUeberraschungenGeaendert = onUeberraschungenGeaendert,
                    onUeberraschungZeigen = onUeberraschungZeigen
                )
                "iserv" -> M3Gruppe("IServ-Speicher") {
                    M3Feld {
                        M3Hinweis("Web-Adresse deines IServ-WebDAV-Speichers, z. B. https://schule.example.de/iserv/webdav")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = serverUrl, onValueChange = { serverUrl = it; urlFehler = null },
                            label = { Text("Web-Adresse") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = benutzername, onValueChange = { benutzername = it },
                            label = { Text("Benutzername") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = passwort, onValueChange = { passwort = it },
                            label = { Text("Passwort") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        M3Hinweis("Hinweis: Das Passwort wird lokal auf diesem Gerät gespeichert, nicht verschlüsselt.")
                        urlFehler?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = f.error, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        M3Knopf("IServ-Zugang speichern", Modifier.fillMaxWidth(), betont = true) {
                            val url = serverUrl.trim()
                            if (url.isNotEmpty() && !url.startsWith("https://")) {
                                urlFehler = "Aus Sicherheitsgründen nur eine https://-Adresse – sonst würden Benutzername und Passwort unverschlüsselt übertragen."
                            } else {
                                onZugangSpeichern(IServZugang(url, benutzername.trim(), passwort))
                            }
                        }
                    }
                }
                else -> M3Gruppe {
                    M3Feld {
                        Text("DammBoard $aktuelleVersion", color = f.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        // Hilft bei der Fehlersuche aus der Ferne: zeigt, was das Gerät Android meldet.
                        M3Hinweis(bildschirmInfo)
                    }
                }
            }
            }
            }
        }
    }
    }
}

@Composable
private fun UpdateAbschnitt(
    aktuelleVersion: String,
    info: UpdateInfo?,
    autoPruefung: Boolean,
    pruefungLaeuft: Boolean,
    bereitsAktuell: Boolean,
    onAutoGeaendert: (Boolean) -> Unit,
    onJetztPruefen: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { UpdateClient() }
    var ladend by remember { mutableStateOf(false) }
    var fortschritt by remember { mutableStateOf(0f) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val f = MaterialTheme.colorScheme

    fun starteHerunterladen(zielInfo: UpdateInfo) {
        if (!kannUnbekannteQuellenInstallieren(context)) {
            Toast.makeText(context, "Bitte DammBoard die Installationserlaubnis erteilen und danach erneut versuchen", Toast.LENGTH_LONG).show()
            oeffneUnbekannteQuellenEinstellungen(context)
            return
        }
        ladend = true
        fehler = null
        scope.launch {
            val ziel = File(context.cacheDir, "dammboard-update.apk")
            val ergebnis = client.apkHerunterladen(zielInfo.herunterladenUrl, ziel, zielInfo.dateigroesseBytes) { fortschritt = it }
            ladend = false
            ergebnis.onSuccess { datei ->
                // Vor dem Installer prüfen, ob das Update überhaupt passt – sonst meldet Android
                // nur ein unverständliches "App nicht installiert".
                when (pruefeUpdateApk(context, datei)) {
                    ApkPruefung.IN_ORDNUNG -> context.startActivity(installationsIntentFuer(context, datei))
                    ApkPruefung.ANDERE_SIGNATUR -> fehler = "Auf diesem Gerät ist noch eine alte Testversion von DammBoard " +
                        "installiert, die anders signiert ist. Bitte DammBoard einmal deinstallieren und die aktuelle " +
                        "DammBoard.apk von github.com/Teyro/DammBoard/releases neu installieren – danach klappen " +
                        "Updates wieder direkt hier."
                    ApkPruefung.UNGUELTIG -> fehler = "Die heruntergeladene Datei ist keine gültige DammBoard-Installationsdatei."
                }
            }.onFailure {
                fehler = "Herunterladen fehlgeschlagen: ${it.message}"
            }
        }
    }

    M3Gruppe {
        M3Feld {
            M3Hinweis("Installiert: $aktuelleVersion")
            M3Schalter("Automatisch nach dem Start prüfen", autoPruefung, onAendern = onAutoGeaendert)
        }
        if (info != null) {
            Column(Modifier.fillMaxWidth().background(f.primaryContainer).padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text("Neue Version verfügbar: ${info.version}", color = f.onPrimaryContainer, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(info.changelog.trim(), color = f.onPrimaryContainer, fontSize = 16.sp,
                        modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()))
                }
                Spacer(Modifier.height(14.dp))
                M3Knopf(if (ladend) "Lädt … ${(fortschritt * 100).toInt()} %" else "Jetzt aktualisieren", Modifier.fillMaxWidth(), betont = true) {
                    if (!ladend) starteHerunterladen(info)
                }
                fehler?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = f.error, fontSize = 16.sp)
                }
            }
        } else {
            M3Feld {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    M3Knopf(if (pruefungLaeuft) "Prüft …" else "Jetzt nach Updates suchen", Modifier.weight(1f), betont = true) {
                        if (!pruefungLaeuft) onJetztPruefen()
                    }
                    if (pruefungLaeuft) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                if (bereitsAktuell && !pruefungLaeuft) {
                    Spacer(Modifier.height(8.dp))
                    M3Hinweis("Du hast bereits die neueste Version.")
                }
            }
        }
        M3Feld { M3Knopf("Damm-Apps in F-Droid hinzufügen", Modifier.fillMaxWidth()) { oeffneFDroidRepo(context) } }
    }
}

/** Öffnet F-Droid mit der Frage „Paketquelle hinzufügen?“ – ohne F-Droid die Seite mit Anleitung und QR-Code. */
private fun oeffneFDroidRepo(context: android.content.Context) {
    val repo = "teyro.github.io/damm-fdroid/repo?fingerprint=db7f158b6eb2c7eb17e79aa4a9ae0e9487c3c2228fe7de60b37a1979408c0604"
    for (adresse in listOf("fdroidrepos://$repo", "https://teyro.github.io/damm-fdroid/")) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(adresse)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: android.content.ActivityNotFoundException) {}
    }
    android.widget.Toast.makeText(context, "Weder F-Droid noch ein Browser ist installiert", android.widget.Toast.LENGTH_LONG).show()
}

@Composable
private fun ErweiterteEinstellungen(
    teil: String,
    zeichenPraezision: Float,
    hintergrundMerken: Boolean,
    handballenRadieren: Boolean,
    handballenEmpfindlichkeit: Float,
    radierenStueckweise: Boolean,
    tafelSichern: Boolean,
    formErkennung: Boolean,
    onTafelSichernGeaendert: (Boolean) -> Unit,
    onFormErkennungGeaendert: (Boolean) -> Unit,
    onZeichenPraezisionGeaendert: (Float) -> Unit,
    onHintergrundMerkenGeaendert: (Boolean) -> Unit,
    onHandballenRadierenGeaendert: (Boolean) -> Unit,
    onHandballenEmpfindlichkeitGeaendert: (Float) -> Unit,
    onRadierenStueckweiseGeaendert: (Boolean) -> Unit,
    ueberraschungen: Boolean = true,
    onUeberraschungenGeaendert: (Boolean) -> Unit = {},
    onUeberraschungZeigen: () -> Unit = {}
) {
    if (teil == "zeichnen") {
        M3Gruppe("Zeichen-Genauigkeit") {
            M3Feld {
                M3Hinweis("Wie fein Freihandlinien erfasst werden. Weiter rechts = glattere Kurven und besser " +
                    "lesbare kleine Schrift, kostet aber mehr Leistung. Auf älteren Tafel-Geräten eher links lassen.")
                // Erst beim Loslassen speichern, nicht bei jedem Zwischenschritt des Reglers.
                var praezision by remember(zeichenPraezision) { mutableFloatStateOf(zeichenPraezision) }
                ReglerZeile("Performance", "Fein", praezision, { praezision = it }) { onZeichenPraezisionGeaendert(praezision) }
            }
            M3Feld {
                M3Schalter("Formerkennung", formErkennung,
                    "Stift am Ende eines Strichs kurz still halten: aus Kreis, Linie, Dreieck oder Rechteck wird eine saubere Form.", onFormErkennungGeaendert)
            }
        }
        M3Gruppe("Radierer und Wischen") {
            M3Feld {
                M3Hinweis("Stückweise: nur das, worüber gewischt wird, verschwindet – wie mit einem Schwamm, auch bei " +
                    "Formen. Ganz: eine berührte Linie oder Form verschwindet komplett.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    M3Auswahl("Stückweise", radierenStueckweise, { onRadierenStueckweiseGeaendert(true) })
                    M3Auswahl("Ganze Linie / Form", !radierenStueckweise, { onRadierenStueckweiseGeaendert(false) })
                }
            }
            M3Feld {
                M3Schalter("Mit dem Handballen wischen", handballenRadieren,
                    "Wie an einer echten Tafel: Handballen oder flache Hand auflegen und wischen – das " +
                        "Darunterliegende wird weggewischt, egal welches Werkzeug gerade gewählt ist.", onHandballenRadierenGeaendert)
                if (handballenRadieren) {
                    Spacer(Modifier.height(8.dp))
                    M3Hinweis("Empfindlichkeit – weiter links, falls beim normalen Schreiben aus Versehen gewischt " +
                        "wird; weiter rechts, falls die Hand nicht erkannt wird.")
                    var empfindlichkeit by remember(handballenEmpfindlichkeit) { mutableFloatStateOf(handballenEmpfindlichkeit) }
                    ReglerZeile("Nur große Hand", "Empfindlich", empfindlichkeit, { empfindlichkeit = it }) { onHandballenEmpfindlichkeitGeaendert(empfindlichkeit) }
                }
            }
        }
    }

    if (teil == "tafel") {
        M3Gruppe {
            M3Feld {
                M3Schalter("Letzten Hintergrund merken", hintergrundMerken,
                    "Aus: jede neue Seite startet auf dem bekannten grünen Tafelhintergrund. An: neue " +
                        "Seiten – auch nach dem nächsten Start – übernehmen den zuletzt gewählten Hintergrund.", onHintergrundMerkenGeaendert)
            }
            M3Feld {
                M3Schalter("Tafel automatisch sichern", tafelSichern,
                    "Alle Seiten werden laufend gesichert. Nach einem Absturz oder dem Ausschalten des Boards ist " +
                        "die Tafel beim nächsten Start wieder da. Neu anfangen: Seitenzahl unten rechts antippen → „Neue leere Tafel“.", onTafelSichernGeaendert)
            }
        }
    }

    if (teil == "spass") {
        M3Gruppe {
            M3Feld {
                M3Schalter("Überraschungen (Easter Eggs)", ueberraschungen,
                    "Standardmäßig aus. An: ab und zu schaut ein Emoji vom Rand herein (nur, wenn eine Weile niemand malt) – wer es antippt, " +
                        "löst 10 Sekunden Emoji-Party aus. Außerdem gibt es versteckte Überraschungen beim Würfel und beim Stift.", onUeberraschungenGeaendert)
                Spacer(Modifier.height(8.dp))
                M3Knopf("Jetzt ausprobieren", onClick = onUeberraschungZeigen)
            }
        }
        M3Gruppe("Überraschungsvideo") { M3Feld { UeberraschungsVideoWahl() } }
    }
}

/** Regler mit Beschriftung links und rechts; gespeichert wird erst beim Loslassen */
@Composable
private fun ReglerZeile(links: String, rechts: String, wert: Float, onWert: (Float) -> Unit, onFertig: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        M3Hinweis(links)
        Slider(value = wert, onValueChange = onWert, onValueChangeFinished = onFertig, valueRange = 0f..1f, steps = 9,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        M3Hinweis(rechts)
    }
}

/**
 * Überraschungsvideo (Easter Egg) wählen. Das Video bleibt auf diesem Board – es wird nie mit der
 * App verteilt. Alternativ holt sich jedes Board „ueberraschung.mp4“ aus dem Tafeln-Ordner.
 */
@Composable
private fun UeberraschungsVideoWahl() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var vorhanden by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(de.oejendorferdamm.dammboard.ui.spiel.UeberraschungsVideo.vorhanden(context)) }
    var meldung by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val wahl = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        meldung = "Wird übernommen …"
        scope.launch {
            val ok = de.oejendorferdamm.dammboard.ui.spiel.UeberraschungsVideo.uebernehmen(context.applicationContext, uri)
            vorhanden = de.oejendorferdamm.dammboard.ui.spiel.UeberraschungsVideo.vorhanden(context)
            meldung = if (ok) "Gespeichert." else "Das Video lässt sich nicht abspielen."
        }
    }
    M3Hinweis(
        (if (vorhanden) "Ein Video ist gewählt. " else "Noch kein Video gewählt. ") +
            "Es bleibt nur auf diesem Board. Alternativ als „ueberraschung.mp4“ in den Tafeln-Ordner legen – dann holt es sich jedes Board selbst. Wie man es auslöst, bleibt geheim 😉"
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        M3Knopf(if (vorhanden) "Anderes Video wählen …" else "Video wählen …") {
            try { wahl.launch(arrayOf("video/*")) } catch (_: Exception) { meldung = "Auf diesem Board gibt es keine Dateiauswahl." }
        }
        if (vorhanden) M3Knopf("Entfernen") {
            de.oejendorferdamm.dammboard.ui.spiel.UeberraschungsVideo.entfernen(context)
            vorhanden = false
            meldung = "Entfernt."
        }
    }
    meldung?.let { M3Hinweis(it, Modifier.padding(top = 8.dp)) }
}
