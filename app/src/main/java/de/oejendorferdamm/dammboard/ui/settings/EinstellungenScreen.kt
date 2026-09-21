package de.oejendorferdamm.dammboard.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol

private val Hintergrundfarbe = Color(0xFFF2F1ED)
private val Textfarbe = Color(0xFF2B2B28)
private val TextfarbeSchwach = Color(0xFF8A8880)
private val Akzent = Color(0xFF3A5C4A)

/** Einstellungsmenü: IServ-Zugangsdaten (Web-Adresse, Benutzername, Passwort) und Darstellungsmodus. */
@Composable
fun EinstellungenScreen(
    aktuellerZugang: IServZugang,
    aktuellerModus: AnimationsModus,
    onZugangSpeichern: (IServZugang) -> Unit,
    onModusGeaendert: (AnimationsModus) -> Unit,
    onZurueck: () -> Unit
) {
    var serverUrl by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.serverUrl) }
    var benutzername by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.benutzername) }
    var passwort by remember(aktuellerZugang) { mutableStateOf(aktuellerZugang.passwort) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF5E8C6A))) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Hintergrundfarbe)
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

            Spacer(Modifier.height(20.dp))
            Text("IServ-Speicher", color = Textfarbe, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Web-Adresse deines IServ-WebDAV-Speichers, z. B. https://schule.example.de/iserv/webdav",
                color = TextfarbeSchwach, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            OutlinedTextField(
                value = serverUrl, onValueChange = { serverUrl = it },
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

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onZugangSpeichern(IServZugang(serverUrl.trim(), benutzername.trim(), passwort)) },
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
