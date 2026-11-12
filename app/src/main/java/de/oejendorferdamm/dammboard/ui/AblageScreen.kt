package de.oejendorferdamm.dammboard.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.data.Ablage
import de.oejendorferdamm.dammboard.data.Stundenplan
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Grund = Color(0xFFECEEF1) // Farben wie in DammZeit
private val Schrift1 = Color(0xFF1F2124)
private val Schrift2 = Color(0xFF6B7078)
private val Akzent = Color(0xFFE01E3C)
private val Rot = Color(0xFFB3261E)

interface AblageAktionen {
    suspend fun oeffnen(e: Ablage.Eintrag): String?
    fun importieren()
    fun ordnerImportieren()
    fun boardDurchsuchen()
    fun pfadDurchsuchen(pfad: String)
    fun jetztAblegen()
    fun tafelnTeilen()
}

/**
 * Datei öffnen wie im Schrank: erst die Fächer (mit dem METACOM-Symbol der Schule aus DammTable), dann die Tafeln des Fachs
 * mit Vorschau – antippen öffnet. Oben: alte Tafeln importieren, Ablage-Einstellungen.
 */
@Composable
fun AblageScreen(
    ordnerName: String,
    automatisch: Boolean,
    meldung: String?,
    arbeitet: Boolean,
    neuLaden: Int,
    aktionen: AblageAktionen,
    onOrdnerName: (String) -> Unit,
    onAutomatisch: (Boolean) -> Unit,
    onZurueck: () -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var faecher by remember { mutableStateOf<List<Ablage.Fach>>(emptyList()) }
    var symbole by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var fach by remember { mutableStateOf<Ablage.Fach?>(null) }
    var eintraege by remember { mutableStateOf<List<Ablage.Eintrag>>(emptyList()) }
    var oeffnenFrage by remember { mutableStateOf<Ablage.Eintrag?>(null) }
    var loeschenFrage by remember { mutableStateOf<Ablage.Eintrag?>(null) }
    var einstellungen by remember { mutableStateOf(false) }
    var name by remember(ordnerName) { mutableStateOf(ordnerName) }
    var lokal by remember { mutableIntStateOf(0) }
    val zeit = remember { SimpleDateFormat("EEE dd.MM. HH:mm", Locale.GERMAN) }

    LaunchedEffect(ordnerName, neuLaden, lokal) {
        faecher = withContext(Dispatchers.IO) { Ablage.faecher(context, ordnerName) }
        // Symbole der Fächer aus DammTable (Name der Karte = Name des Fachs)
        symbole = withContext(Dispatchers.IO) {
            val karten = Stundenplan.karten(context)
            faecher.mapNotNull { f -> karten.firstOrNull { it.second.equals(f.name, ignoreCase = true) }?.let { k -> Stundenplan.symbol(context, k.first)?.let { f.name to it } } }.toMap()
        }
        fach?.let { f -> eintraege = withContext(Dispatchers.IO) { Ablage.eintraege(f.ordner) } }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF5E8C6A)).systemBarsPadding()) {
        Column(Modifier.align(Alignment.Center).fillMaxSize().padding(28.dp).clip(RoundedCornerShape(22.dp)).background(Grund).padding(22.dp)) {
            // Kopf: Titel, Zurück (im Fach), ✕
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (fach != null) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White).clickable { fach = null; eintraege = emptyList() }.semantics { contentDescription = "Zurück zu den Fächern" }, contentAlignment = Alignment.Center) {
                        Text("‹", color = Schrift1, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(fach?.name ?: "Tafel-Ablage · $ordnerName", color = Schrift1, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (fach == null) {
                    OutlinedButton(onClick = aktionen::importieren, enabled = !arbeitet) { Text("Alte Tafel importieren …") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = aktionen::ordnerImportieren, enabled = !arbeitet) { Text("Ordner importieren …") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { einstellungen = !einstellungen }) { Text(if (einstellungen) "Fertig" else "Einstellungen") }
                    Spacer(Modifier.width(12.dp))
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White).clickable(onClick = onZurueck).semantics { contentDescription = "Schließen" }, contentAlignment = Alignment.Center) {
                    AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(16.dp), Schrift1)
                }
            }
            meldung?.let { Spacer(Modifier.height(6.dp)); Text(it, color = if (it.startsWith("Fehler") || it.contains("nicht")) Rot else Akzent, fontSize = 14.sp) }
            if (arbeitet) { Spacer(Modifier.height(6.dp)); CircularProgressIndicator(color = Akzent, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.height(12.dp))

            if (einstellungen && fach == null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name der Ablage (Board oder Klasse, z. B. 2a)") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onOrdnerName(name.trim().ifBlank { "Tafel" }) }, colors = ButtonDefaults.buttonColors(containerColor = Akzent)) { Text("Übernehmen") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onAutomatisch(!automatisch) }) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatisch ablegen", color = Schrift1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Zu jedem Stundenende in den Ordner des Fachs (Stundenplan aus DammTable, sonst Klassenpläne aus dem Schul-Ordner, sonst nach dem Stundenraster der Schule unter „Tafel“), am Ende des Tages und beim Ausschalten – mit Nextcloud auch dorthin. Am nächsten Tag startet die Tafel leer.", color = Schrift2, fontSize = 12.sp)
                        }
                        Switch(checked = automatisch, onCheckedChange = onAutomatisch, colors = SwitchDefaults.colors(checkedTrackColor = Akzent, checkedThumbColor = Color.White))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row {
                        OutlinedButton(onClick = aktionen::jetztAblegen, enabled = !arbeitet) { Text("Tafel jetzt ablegen") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = aktionen::tafelnTeilen) { Text("Tafeln mit anderen Boards teilen …") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Ablage auf dem Board: Android/data/de.oejendorferdamm.dammboard/files/Tafeln/$ordnerName", color = Schrift2, fontSize = 11.sp)

                    // --- Alte Tafeln suchen
                    Spacer(Modifier.height(16.dp))
                    Text("Alte Tafeln (.ubmx2) suchen", color = Schrift1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Durchsucht das ganze Board (und eingesteckte USB-Sticks) – alles Neue kommt in den Ordner „Importiert“. Braucht einmal „Zugriff auf alle Dateien“.", color = Schrift2, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    var pfad by remember { mutableStateOf("/sdcard/") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = aktionen::boardDurchsuchen, enabled = !arbeitet, colors = ButtonDefaults.buttonColors(containerColor = Akzent)) { Text("Ganzes Board durchsuchen") }
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(pfad, { pfad = it.take(200) }, singleLine = true, label = { Text("oder Ordner, z. B. /sdcard/Notes") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { aktionen.pfadDurchsuchen(pfad) }, enabled = !arbeitet && pfad.isNotBlank()) { Text("Durchsuchen") }
                    }

                    // --- Nextcloud
                    Spacer(Modifier.height(16.dp))
                    NextcloudEinstellungen()
                }
                Spacer(Modifier.height(12.dp))
            }

            val f = fach
            if (f == null) {
                if (faecher.isEmpty()) Text("Noch nichts abgelegt. Nach der ersten Stunde (oder mit „Tafel jetzt ablegen“) erscheinen hier die Fächer.", color = Schrift2, fontSize = 15.sp)
                LazyVerticalGrid(GridCells.Adaptive(170.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(faecher, key = { it.name }) { x ->
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).clickable {
                                fach = x; lokal++
                            }.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val s = symbole[x.name]
                            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8EEE9)), contentAlignment = Alignment.Center) {
                                if (s != null) Image(s.asImageBitmap(), contentDescription = x.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                                else Text(x.name.take(2).uppercase(), color = Akzent, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(x.name, color = Schrift1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text("${x.anzahl} Tafel" + (if (x.anzahl == 1) "" else "n"), color = Schrift2, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                if (eintraege.isEmpty()) Text("Leer.", color = Schrift2, fontSize = 15.sp)
                LazyVerticalGrid(GridCells.Adaptive(230.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(eintraege, key = { it.datei.path }) { e ->
                        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { oeffnenFrage = e }.padding(10.dp)) {
                            val vorschau = remember(e.vorschau.path, e.geaendert) { if (e.vorschau.exists()) BitmapFactory.decodeFile(e.vorschau.path) else null }
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2F4A3A)), contentAlignment = Alignment.Center) {
                                if (vorschau != null) Image(vorschau.asImageBitmap(), contentDescription = "Vorschau", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                else Text("ohne Vorschau", color = Color.White, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, color = Schrift1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(zeit.format(Date(e.geaendert)), color = Schrift2, fontSize = 11.sp)
                                }
                                Box(Modifier.size(34.dp).clip(CircleShape).clickable { loeschenFrage = e }.semantics { contentDescription = "Löschen" }, contentAlignment = Alignment.Center) {
                                    Text("🗑", fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        oeffnenFrage?.let { e ->
            AblageFrage("„${e.name}“ öffnen? Die Tafel, die gerade zu sehen ist, wird vorher abgelegt.", "Öffnen", Akzent,
                onJa = { oeffnenFrage = null; scope.launch { if (aktionen.oeffnen(e) == null) onZurueck() } }, onNein = { oeffnenFrage = null })
        }
        loeschenFrage?.let { e ->
            AblageFrage("„${e.name}“ löschen?", "Löschen", Rot,
                onJa = { Ablage.loesche(e); loeschenFrage = null; lokal++ }, onNein = { loeschenFrage = null })
        }
    }
}

@Composable
private fun AblageFrage(text: String, ja: String, farbe: Color, onJa: () -> Unit, onNein: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable(onClick = onNein), contentAlignment = Alignment.Center) {
        Column(Modifier.width(460.dp).clip(RoundedCornerShape(16.dp)).background(Grund).clickable(enabled = false) {}.padding(20.dp)) {
            Text(text, color = Schrift1, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onNein) { Text("Abbrechen") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onJa, colors = ButtonDefaults.buttonColors(containerColor = farbe)) { Text(ja) }
            }
        }
    }
}

/** Nextcloud: Server und Benutzer meist schon vorbelegt (fest oder aus dem Schul-Ordner) – nur das App-Passwort eintippen */
@Composable
private fun NextcloudEinstellungen() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val nc = de.oejendorferdamm.dammboard.data.Nextcloud
    var k by remember { mutableStateOf(nc.lese(context)) }
    var passwort by remember { mutableStateOf("") }
    var mehr by remember { mutableStateOf(k.server.isBlank() || k.benutzer.isBlank()) }
    var meldung by remember { mutableStateOf(nc.status(context).let { if (it.isEmpty()) "" else "Zuletzt hochgeladen: $it" }) }
    fun speichern() { val neu = k.copy(passwort = passwort.ifEmpty { k.passwort }); nc.speichere(context, neu); k = neu; passwort = "" }

    val schule = remember { nc.schule(context, "") }
    Text("Nextcloud", color = Schrift1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    if (schule != null) {
        Text("Zugänge der Schule (über DammTable): Klasse ${schule.klasse} → ${schule.pfad.replace("{klasse}", schule.klasse).replace("{fach}", "<Fach>")} – nach jeder Stunde als PNG. Ohne passendes Fach in „Unsortiert“. Nichts einzutippen.",
            color = Akzent, fontSize = 12.sp)
        val vc = de.oejendorferdamm.dammboard.data.VorschauCache
        var inhalt by remember { mutableStateOf(vc.groesse(context)) }
        val um = vc.zeitpunkt(java.time.LocalDate.now(), schule.klasse, "").let { "%d–%d Uhr".format(it / 60, it / 60 + 1) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Vorschaubilder im Speicher: ${inhalt.first} (${inhalt.second / 1_048_576} MB) – einmal am Tag zwischen $um aufgefrischt" +
                vc.stand(context).let { if (it.isEmpty()) "" else ", zuletzt $it" }, color = Schrift2, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Löschen", color = Akzent, fontSize = 12.sp, modifier = Modifier.clickable { vc.loeschen(context); inhalt = vc.groesse(context) }.padding(8.dp))
        }
    }
    Text(
        if (schule != null) "Eigene Nextcloud (nur ohne Zugänge der Schule):"
        else if (k.server.isNotBlank() && k.benutzer.isNotBlank()) "Nach jeder Stunde geht die Tafel auch nach ${k.server.removePrefix("https://")} (Benutzer ${k.benutzer}, Ordner „${k.ordner}“)."
        else "Server und Benutzer eintragen – oder als versteckte Datei „DammBoard/.nextcloud.json“ in den Schul-Ordner legen, dann füllt sich das von selbst.",
        color = Schrift2, fontSize = 12.sp
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(passwort, { passwort = it }, singleLine = true, modifier = Modifier.weight(1f),
            label = { Text(if (k.passwort.isEmpty()) "App-Passwort" else "App-Passwort (gespeichert – nur zum Ändern)") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Spacer(Modifier.width(8.dp))
        Button(onClick = { speichern(); scope.launch { meldung = "Prüft …"; meldung = nc.testen(nc.lese(context)) } }, colors = ButtonDefaults.buttonColors(containerColor = Akzent)) { Text("Speichern und testen") }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { k = k.copy(an = !k.an); nc.speichere(context, k) }) {
        Text("Nach jeder Stunde hochladen", color = Schrift1, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = k.an, onCheckedChange = { k = k.copy(an = it); nc.speichere(context, k) }, colors = SwitchDefaults.colors(checkedTrackColor = Akzent, checkedThumbColor = Color.White))
    }
    if (mehr) {
        Row {
            OutlinedTextField(k.server, { k = k.copy(server = it) }, singleLine = true, label = { Text("Server, z. B. https://cloud.schule.de") }, modifier = Modifier.weight(1.4f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(k.benutzer, { k = k.copy(benutzer = it) }, singleLine = true, label = { Text("Benutzer") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(k.ordner, { k = k.copy(ordner = it) }, singleLine = true, label = { Text("Ordner") }, modifier = Modifier.weight(1f))
        }
    } else {
        Text("Server, Benutzer oder Ordner ändern", color = Akzent, fontSize = 12.sp, modifier = Modifier.clickable { mehr = true }.padding(vertical = 4.dp))
    }
    if (meldung.isNotEmpty()) Text(meldung, color = if (meldung.startsWith("Keine")) Rot else Schrift2, fontSize = 12.sp)
}
