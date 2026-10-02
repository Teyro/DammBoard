package de.oejendorferdamm.dammboard.ui.filemanager

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.data.IServClient
import de.oejendorferdamm.dammboard.model.IServEintrag
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import de.oejendorferdamm.dammboard.ui.icons.kontur
import de.oejendorferdamm.dammboard.ui.icons.symbolRaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val Hintergrundfarbe = Color(0xFFF2F1ED)
private val Textfarbe = Color(0xFF2B2B28)
private val TextfarbeSchwach = Color(0xFF8A8880)
private val Akzent = Color(0xFF3A5C4A)
private val Fehlerfarbe = Color(0xFFB3261E)

/** Was der Dateimanager tun soll: eine Datei speichern oder ein Arbeitsblatt zum Öffnen auswählen. */
sealed interface DateiModus {
    class Speichern(val dateiname: String, val mime: String, val daten: suspend () -> ByteArray) : DateiModus

    /** [onDatei] bekommt Name und Inhalt und liefert eine Fehlermeldung oder null bei Erfolg. */
    class Oeffnen(val onDatei: suspend (String, ByteArray) -> String?) : DateiModus
}

/** Dateien, die als Arbeitsblatt geöffnet werden können. */
private fun istArbeitsblatt(name: String): Boolean =
    listOf(".pdf", ".png", ".jpg", ".jpeg", ".webp").any { name.endsWith(it, ignoreCase = true) }

/** Einfacher Dateimanager für den IServ-WebDAV-Speicher: durch Ordner blättern, schnell oder gezielt speichern bzw. öffnen. */
@Composable
fun DateiManagerScreen(zugang: IServZugang, modus: DateiModus, onFertig: () -> Unit) {
    val speichern = modus as? DateiModus.Speichern
    val oeffnen = modus as? DateiModus.Oeffnen
    val scope = rememberCoroutineScope()
    val client = remember(zugang) { IServClient(zugang) }

    var pfad by remember { mutableStateOf("") }
    var eintraege by remember { mutableStateOf<List<IServEintrag>>(emptyList()) }
    var ladend by remember { mutableStateOf(false) }
    var hochladend by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var erfolg by remember { mutableStateOf<String?>(null) }

    fun ladeOrdner(neuerPfad: String) {
        ladend = true
        fehler = null
        scope.launch {
            val ergebnis = client.liste(neuerPfad)
            ladend = false
            ergebnis.onSuccess {
                eintraege = it
                pfad = neuerPfad
            }.onFailure {
                fehler = "Ordner konnte nicht geladen werden: ${it.message}"
            }
        }
    }

    fun oeffneDatei(eintrag: IServEintrag) {
        val ziel = oeffnen ?: return
        hochladend = true
        fehler = null
        erfolg = null
        scope.launch {
            client.herunterladen(eintrag.pfad)
                .onSuccess { bytes ->
                    val problem = ziel.onDatei(eintrag.name, bytes)
                    hochladend = false
                    if (problem == null) onFertig() else fehler = problem
                }
                .onFailure {
                    hochladend = false
                    fehler = "Öffnen fehlgeschlagen: ${it.message}"
                }
        }
    }

    fun hochladenNach(zielPfad: String) {
        val quelle = speichern ?: return
        hochladend = true
        fehler = null
        erfolg = null
        scope.launch {
            // Das Kodieren (PNG/PDF) dauert auf alten Geräten spürbar – nicht auf dem UI-Thread.
            val bytes = withContext(Dispatchers.Default) { quelle.daten() }
            val dateiname = quelle.dateiname
            val ergebnis = client.hochladen(zielPfad, dateiname, bytes, quelle.mime)
            hochladend = false
            ergebnis.onSuccess {
                erfolg = "In IServ gespeichert: $dateiname"
            }.onFailure {
                fehler = "Hochladen fehlgeschlagen: ${it.message}"
            }
        }
    }

    LaunchedEffect(zugang) {
        if (zugang.istEingerichtet) ladeOrdner("") else fehler = "Bitte zuerst IServ-Zugang in den Einstellungen hinterlegen."
    }

    // systemBarsPadding: Die Karte bleibt zwischen Status- und Navigationsleiste, statt auf
    // kleineren Bildschirmen darunter zu verschwinden (die App zeichnet randlos).
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF5E8C6A)).systemBarsPadding()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 480.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Hintergrundfarbe)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(if (oeffnen != null) "Arbeitsblatt aus IServ öffnen" else "In IServ speichern", color = Textfarbe, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White).clickable(onClick = onFertig),
                    contentAlignment = Alignment.Center
                ) {
                    AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(15.dp), Textfarbe)
                }
            }

            if (!zugang.istEingerichtet) {
                Spacer(Modifier.height(16.dp))
                Text("Kein IServ-Zugang hinterlegt. Bitte in den Einstellungen (☰) URL, Benutzername und Passwort eintragen.", color = Fehlerfarbe, fontSize = 13.sp)
                return@Column
            }

            if (speichern != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { hochladenNach("") },
                    enabled = !hochladend,
                    colors = ButtonDefaults.buttonColors(containerColor = Akzent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Schnell speichern (Hauptordner)")
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text("PDF oder Bild antippen – jede PDF-Seite wird eine eigene Tafelseite.", color = TextfarbeSchwach, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pfad.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { ladeOrdner(pfad.substringBeforeLast('/', "")) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Textfarbe),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("↑ Zurück") }
                }
                Text(
                    if (pfad.isEmpty()) "Hauptordner" else pfad,
                    color = TextfarbeSchwach, fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp)) {
                when {
                    ladend -> CircularProgressIndicator(color = Akzent, modifier = Modifier.align(Alignment.Center).size(28.dp))
                    else -> {
                        val sichtbar = eintraege.filter { it.istOrdner || (oeffnen != null && istArbeitsblatt(it.name)) }
                        if (sichtbar.isEmpty()) {
                            Text(if (oeffnen != null) "Keine Ordner oder Arbeitsblätter" else "Keine Unterordner", color = TextfarbeSchwach, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        } else LazyColumn {
                            items(sichtbar) { eintrag ->
                                OrdnerZeile(eintrag = eintrag, onClick = {
                                    if (eintrag.istOrdner) ladeOrdner(eintrag.pfad) else if (!hochladend) oeffneDatei(eintrag)
                                })
                            }
                        }
                    }
                }
            }

            if (speichern != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { hochladenNach(pfad) },
                    enabled = !hochladend,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A5C4A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (hochladend) "Speichert …" else "Hier speichern")
                }
            } else if (hochladend) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Akzent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Wird geöffnet …", color = Textfarbe, fontSize = 13.sp)
                }
            }

            fehler?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Fehlerfarbe, fontSize = 12.sp)
            }
            erfolg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Akzent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OrdnerZeile(eintrag: IServEintrag, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (eintrag.istOrdner) OrdnerSymbol(modifier = Modifier.size(20.dp)) else DateiSymbol(modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(eintrag.name, color = Textfarbe, fontSize = 14.sp)
    }
}

@Composable
private fun OrdnerSymbol(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { symbolRaster { w, h ->
        drawRoundRect(
            color = Akzent,
            topLeft = Offset(w * 0.06f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = kontur(2f)
        )
        drawLine(
            color = Akzent,
            start = Offset(w * 0.06f, h * 0.3f), end = Offset(w * 0.4f, h * 0.3f),
            strokeWidth = 2f
        )
        drawLine(
            color = Akzent,
            start = Offset(w * 0.4f, h * 0.3f), end = Offset(w * 0.5f, h * 0.16f),
            strokeWidth = 2f
        )
        drawLine(
            color = Akzent,
            start = Offset(w * 0.5f, h * 0.16f), end = Offset(w * 0.78f, h * 0.16f),
            strokeWidth = 2f
        )
        drawLine(
            color = Akzent,
            start = Offset(w * 0.78f, h * 0.16f), end = Offset(w * 0.86f, h * 0.28f),
            strokeWidth = 2f
        )
    } }
}

@Composable
private fun DateiSymbol(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { symbolRaster { w, h ->
        drawRect(
            color = Akzent,
            topLeft = Offset(w * 0.2f, h * 0.1f),
            size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.8f),
            style = kontur(2f)
        )
        drawLine(color = Akzent, start = Offset(w * 0.32f, h * 0.38f), end = Offset(w * 0.68f, h * 0.38f), strokeWidth = 1.6f)
        drawLine(color = Akzent, start = Offset(w * 0.32f, h * 0.55f), end = Offset(w * 0.68f, h * 0.55f), strokeWidth = 1.6f)
        drawLine(color = Akzent, start = Offset(w * 0.32f, h * 0.72f), end = Offset(w * 0.55f, h * 0.72f), strokeWidth = 1.6f)
    } }
}

/** PNG eines Tafelbilds (für "In IServ speichern"). */
fun bitmapZuPng(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}
