package de.oejendorferdamm.dammboard.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.data.OrdnerSync
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Hintergrund = Color(0xFFF2F1ED)
private val Text1 = Color(0xFF2B2B28)
private val Text2 = Color(0xFF8A8880)
private val Akzent = Color(0xFF3A5C4A)
private val Fehler = Color(0xFFB3261E)

/** Was der Tafeln-Bildschirm auslöst; die Ergebnisse sind Fehlertexte (null = geklappt). */
interface TafelnAktionen {
    fun ordnerWaehlen()
    suspend fun speichernUnter(name: String): String?
    suspend fun oeffnen(datei: OrdnerSync.TafelDatei): String?
    suspend fun loeschen(datei: OrdnerSync.TafelDatei): String?
    fun neueTafel()
    fun trennen()
}

/**
 * Tafeln im gemeinsamen Ordner: speichern, öffnen, löschen. Eine geöffnete Tafel bleibt mit ihrer
 * Datei verbunden und wird beim Arbeiten automatisch zurückgeschrieben.
 */
@Composable
fun TafelnScreen(ordner: Uri?, verbunden: VerbundeneTafel?, aktionen: TafelnAktionen, onZurueck: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dateien by remember { mutableStateOf<List<OrdnerSync.TafelDatei>>(emptyList()) }
    var laedt by remember { mutableStateOf(false) }
    var arbeitet by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var neuLaden by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(verbunden?.name ?: "") }
    var loeschenFrage by remember { mutableStateOf<OrdnerSync.TafelDatei?>(null) }
    var oeffnenFrage by remember { mutableStateOf<OrdnerSync.TafelDatei?>(null) }
    val zeit = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN) }

    LaunchedEffect(ordner, neuLaden) {
        if (ordner == null) return@LaunchedEffect
        laedt = true
        fehler = null
        dateien = try {
            OrdnerSync.liste(context, ordner)
        } catch (e: Exception) {
            fehler = e.message ?: "Ordner nicht lesbar"
            emptyList()
        }
        laedt = false
    }

    fun ausfuehren(arbeit: suspend () -> String?) {
        arbeitet = true
        fehler = null
        scope.launch {
            fehler = arbeit()
            arbeitet = false
            neuLaden++
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF5E8C6A)).systemBarsPadding()) {
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 560.dp).heightIn(max = 620.dp)
                .clip(RoundedCornerShape(18.dp)).background(Hintergrund).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Tafeln im gemeinsamen Ordner", color = Text1, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White).clickable(onClick = onZurueck).semantics { contentDescription = "Schließen" }, contentAlignment = Alignment.Center) {
                    AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(15.dp), Text1)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (ordner == null) {
                Text(
                    "Wähle einmal einen Ordner, den alle Boards sehen – z. B. den Ordner einer Cloud-App (Nextcloud, Synology Drive), " +
                        "ein Netzlaufwerk oder einen USB-Stick. Tafeln, die du dort speicherst oder löschst, sind dann auch auf den anderen Boards " +
                        "gespeichert bzw. gelöscht, sobald der Ordner abgeglichen ist.",
                    color = Text1, fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = aktionen::ordnerWaehlen, colors = ButtonDefaults.buttonColors(containerColor = Akzent), modifier = Modifier.fillMaxWidth()) { Text("Ordner wählen …") }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ordner: " + OrdnerSync.ordnerName(context, ordner), color = Text2, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = aktionen::ordnerWaehlen) { Text("Ändern", fontSize = 12.sp) }
            }
            if (verbunden != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFDCE9E0)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Offen: „${verbunden.name}“ – Änderungen werden automatisch in den Ordner geschrieben.", color = Text1, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = aktionen::trennen) { Text("Trennen", fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name der Tafel") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { ausfuehren { aktionen.speichernUnter(name) } },
                    enabled = !arbeitet && name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Akzent)
                ) { Text("Speichern") }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { aktionen.neueTafel(); onZurueck() }) { Text("Neue leere Tafel", fontSize = 12.sp) }
            fehler?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Fehler, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (laedt || arbeitet) CircularProgressIndicator(color = Akzent, modifier = Modifier.size(28.dp))
            if (!laedt && dateien.isEmpty()) Text("Noch keine Tafeln im Ordner.", color = Text2, fontSize = 13.sp)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(dateien, key = { it.uri.toString() }) { d ->
                    val offen = verbunden?.uri == d.uri.toString()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (offen) Color(0xFFDCE9E0) else Color.White).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, color = Text1, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (d.geaendert > 0) "geändert " + zeit.format(Date(d.geaendert)) else "", color = Text2, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { oeffnenFrage = d },
                            enabled = !arbeitet,
                            colors = ButtonDefaults.buttonColors(containerColor = Akzent)
                        ) { Text(if (offen) "Neu laden" else "Öffnen", fontSize = 12.sp) }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { loeschenFrage = d }, enabled = !arbeitet) { Text("Löschen", color = Fehler, fontSize = 12.sp) }
                    }
                }
            }
        }

        // Rückfragen
        loeschenFrage?.let { d ->
            Frage(
                "„${d.name}“ löschen? Sie verschwindet auch auf den anderen Boards.", "Löschen", Fehler,
                onJa = { loeschenFrage = null; ausfuehren { aktionen.loeschen(d) } }, onNein = { loeschenFrage = null }
            )
        }
        oeffnenFrage?.let { d ->
            Frage(
                "„${d.name}“ öffnen? Die Tafel, die gerade zu sehen ist, wird dabei ersetzt" +
                    (if (verbunden == null) " (vorher speichern, falls du sie noch brauchst)." else "."),
                "Öffnen", Akzent,
                onJa = {
                    oeffnenFrage = null
                    ausfuehren { aktionen.oeffnen(d).also { if (it == null) onZurueck() } }
                },
                onNein = { oeffnenFrage = null }
            )
        }
    }
}

@Composable
private fun Frage(text: String, ja: String, farbe: Color, onJa: () -> Unit, onNein: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable(onClick = onNein), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(16.dp)).background(Hintergrund).clickable(enabled = false) {}.padding(20.dp)) {
            Text(text, color = Text1, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onNein) { Text("Abbrechen") }
                Button(onClick = onJa, colors = ButtonDefaults.buttonColors(containerColor = farbe)) { Text(ja) }
            }
        }
    }
}
