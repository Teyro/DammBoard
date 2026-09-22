package de.oejendorferdamm.dammboard.ui.settings

import android.widget.Toast
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import de.oejendorferdamm.dammboard.ui.update.installationsIntentFuer
import de.oejendorferdamm.dammboard.ui.update.kannUnbekannteQuellenInstallieren
import de.oejendorferdamm.dammboard.ui.update.oeffneUnbekannteQuellenEinstellungen
import kotlinx.coroutines.launch
import java.io.File

private val Hintergrundfarbe = Color(0xFFF2F1ED)
private val Textfarbe = Color(0xFF2B2B28)
private val TextfarbeSchwach = Color(0xFF8A8880)
private val Akzent = Color(0xFF3A5C4A)
private val Fehlerfarbe = Color(0xFFB3261E)

/** Einstellungsmenü: IServ-Zugangsdaten, Darstellungsmodus und – falls vorhanden – ein Update-Hinweis. */
@Composable
fun EinstellungenScreen(
    aktuellerZugang: IServZugang,
    aktuellerModus: AnimationsModus,
    aktuelleSymbolGroesse: SymbolGroesse,
    aktuelleVersion: String,
    updateInfo: UpdateInfo?,
    onZugangSpeichern: (IServZugang) -> Unit,
    onModusGeaendert: (AnimationsModus) -> Unit,
    onSymbolGroesseGeaendert: (SymbolGroesse) -> Unit,
    onZurueck: () -> Unit
) {
    var serverUrl by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.serverUrl) }
    var benutzername by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.benutzername) }
    var passwort by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.passwort) }
    var urlFehler by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF5E8C6A))) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 480.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Hintergrundfarbe)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Einstellungen", color = Textfarbe, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White).clickable(onClick = onZurueck),
                    contentAlignment = Alignment.Center
                ) {
                    AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(16.dp), Textfarbe)
                }
            }

            if (updateInfo != null) {
                Spacer(Modifier.height(18.dp))
                UpdateAbschnitt(aktuelleVersion = aktuelleVersion, info = updateInfo)
            }

            Spacer(Modifier.height(20.dp))
            Text("IServ-Speicher", color = Textfarbe, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Web-Adresse deines IServ-WebDAV-Speichers, z. B. https://schule.example.de/iserv/webdav",
                color = TextfarbeSchwach, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            OutlinedTextField(
                value = serverUrl, onValueChange = { serverUrl = it; urlFehler = null },
                label = { Text("Web-Adresse") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = feldFarben(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = benutzername, onValueChange = { benutzername = it },
                label = { Text("Benutzername") },
                singleLine = true,
                colors = feldFarben(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = passwort, onValueChange = { passwort = it },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = feldFarben(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Hinweis: Das Passwort wird lokal auf diesem Gerät gespeichert, nicht verschlüsselt.",
                color = TextfarbeSchwach, fontSize = 10.sp
            )
            urlFehler?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Fehlerfarbe, fontSize = 11.sp)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val url = serverUrl.trim()
                    if (url.isNotEmpty() && !url.startsWith("https://")) {
                        urlFehler = "Aus Sicherheitsgründen nur eine https://-Adresse – sonst würden Benutzername und Passwort unverschlüsselt übertragen."
                    } else {
                        onZugangSpeichern(IServZugang(url, benutzername.trim(), passwort))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Akzent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("IServ-Zugang speichern")
            }

            Spacer(Modifier.height(26.dp))
            Text("Darstellung", color = Textfarbe, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Normalmodus mit sanften Übergängen, oder Performance-Modus ganz ohne Animationen.",
                color = TextfarbeSchwach, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModusKnopf("Normal", ausgewaehlt = aktuellerModus == AnimationsModus.NORMAL, modifier = Modifier.weight(1f)) {
                    onModusGeaendert(AnimationsModus.NORMAL)
                }
                ModusKnopf("Performance", ausgewaehlt = aktuellerModus == AnimationsModus.PERFORMANCE, modifier = Modifier.weight(1f)) {
                    onModusGeaendert(AnimationsModus.PERFORMANCE)
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Symbolgröße", color = Textfarbe, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Größe der Werkzeugleisten-Symbole – wirkt sich auch auf die Tippflächen aus.",
                color = TextfarbeSchwach, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModusKnopf("Kompakt", ausgewaehlt = aktuelleSymbolGroesse == SymbolGroesse.KOMPAKT, modifier = Modifier.weight(1f)) {
                    onSymbolGroesseGeaendert(SymbolGroesse.KOMPAKT)
                }
                ModusKnopf("Standard", ausgewaehlt = aktuelleSymbolGroesse == SymbolGroesse.STANDARD, modifier = Modifier.weight(1f)) {
                    onSymbolGroesseGeaendert(SymbolGroesse.STANDARD)
                }
                ModusKnopf("Groß", ausgewaehlt = aktuelleSymbolGroesse == SymbolGroesse.GROSS, modifier = Modifier.weight(1f)) {
                    onSymbolGroesseGeaendert(SymbolGroesse.GROSS)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("DammBoard $aktuelleVersion", color = TextfarbeSchwach, fontSize = 11.sp)
        }
    }
}

@Composable
private fun UpdateAbschnitt(aktuelleVersion: String, info: UpdateInfo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { UpdateClient() }
    var ladend by remember { mutableStateOf(false) }
    var fortschritt by remember { mutableStateOf(0f) }
    var fehler by remember { mutableStateOf<String?>(null) }

    fun starteHerunterladen() {
        if (!kannUnbekannteQuellenInstallieren(context)) {
            Toast.makeText(context, "Bitte DammBoard die Installationserlaubnis erteilen und danach erneut versuchen", Toast.LENGTH_LONG).show()
            oeffneUnbekannteQuellenEinstellungen(context)
            return
        }
        ladend = true
        fehler = null
        scope.launch {
            val ziel = File(context.cacheDir, "dammboard-update.apk")
            val ergebnis = client.apkHerunterladen(info.herunterladenUrl, ziel) { fortschritt = it }
            ladend = false
            ergebnis.onSuccess {
                context.startActivity(installationsIntentFuer(context, it))
            }.onFailure {
                fehler = "Herunterladen fehlgeschlagen: ${it.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Akzent)
            .padding(16.dp)
    ) {
        Text("Update verfügbar: Version ${info.version}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Installiert: $aktuelleVersion", color = Color(0xFFD7DFD4), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        if (info.changelog.isNotBlank()) {
            Text(
                info.changelog.trim(),
                color = Color(0xFFE9EEE7), fontSize = 11.sp,
                modifier = Modifier.heightIn(max = 90.dp).verticalScroll(rememberScrollState())
            )
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = { starteHerunterladen() },
            enabled = !ladend,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Akzent),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (ladend) {
                CircularProgressIndicator(color = Akzent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lädt … ${(fortschritt * 100).toInt()}%")
            } else {
                Text("Jetzt aktualisieren")
            }
        }
        fehler?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
private fun feldFarben() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Akzent,
    unfocusedBorderColor = Color(0xFFCFCDC6),
    focusedLabelColor = Akzent,
    cursorColor = Akzent
)

@Composable
private fun ModusKnopf(label: String, ausgewaehlt: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(50))
            .background(if (ausgewaehlt) Akzent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (ausgewaehlt) Color.White else Textfarbe, fontSize = 13.sp)
    }
}
