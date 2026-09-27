package de.oejendorferdamm.dammboard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.ui.canvas.TafelCanvas
import de.oejendorferdamm.dammboard.ui.toolbar.SeitenLeiste
import de.oejendorferdamm.dammboard.ui.toolbar.TafelBedienung
import de.oejendorferdamm.dammboard.ui.toolbar.faengtBeruehrungen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Wie lange der Hinweis "neue Version installiert" höchstens stehen bleibt. */
private const val NEUIGKEITEN_ANZEIGE_MS = 12_000L

/**
 * Bildschirm der Tafel: Zeichenfläche plus komplette Bedienung darüber. Die Zeichenfläche
 * rechnet in echten Bildschirmpixeln; alles darüber wird im Maßstab der Original-App
 * dargestellt (siehe Skalierung.kt).
 */
@Composable
fun TafelScreen(
    state: TafelState,
    animationsModus: AnimationsModus,
    oberflaechenFaktor: Float,
    zeichenPraezision: Float,
    zeigeUpdatePunkt: Boolean,
    neuigkeiten: String?,
    onNeuigkeitenGelesen: () -> Unit,
    onSchliessenApp: () -> Unit,
    onOeffneEinstellungen: () -> Unit,
    onIServAnfrage: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zeigeBeendenAbfrage by remember { mutableStateOf(false) }

    // Die Zurück-Taste (viele Boards haben sie am Rahmen) schließt erst Panel/Lupe und fragt dann
    // nach – vorher beendete sie die App sofort, und der ganze Tafelinhalt war weg.
    BackHandler {
        when {
            zeigeBeendenAbfrage -> zeigeBeendenAbfrage = false
            state.offenesPanel != null -> state.schliessePanel()
            state.lupeAktiv -> state.lupeAktiv = false
            else -> zeigeBeendenAbfrage = true
        }
    }

    // Nur auf Android 9 und älter gebraucht: ab Android 10 übernimmt Scoped Storage das Speichern
    // ohne Berechtigungsdialog (siehe Speichern.kt).
    var ausstehendeSpeicherung by remember { mutableStateOf<Pair<AufnahmeZweck, Bitmap>?>(null) }
    val speicherErlaubnisLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { gewaehrt ->
        val anstehend = ausstehendeSpeicherung
        ausstehendeSpeicherung = null
        if (gewaehrt && anstehend != null) {
            fuehreSpeicherungAus(anstehend.first, anstehend.second, context, scope)
        } else if (!gewaehrt) {
            Toast.makeText(context, "Ohne Speicherberechtigung kann das Bild nicht gespeichert werden", Toast.LENGTH_LONG).show()
        }
    }

    fun starteSpeicherung(zweck: AufnahmeZweck, bitmap: Bitmap) {
        val brauchtErlaubnis = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (brauchtErlaubnis) {
            ausstehendeSpeicherung = zweck to bitmap
            speicherErlaubnisLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            fuehreSpeicherungAus(zweck, bitmap, context, scope)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        TafelCanvas(
            state = state,
            modifier = Modifier.fillMaxSize(),
            zeichenPraezision = zeichenPraezision
        ) { zweck, bitmap ->
            when (zweck) {
                AufnahmeZweck.SPEICHERN, AufnahmeZweck.TEILEN -> starteSpeicherung(zweck, bitmap)
                AufnahmeZweck.ISERV -> onIServAnfrage(bitmap)
            }
        }

        VorbildRaster(faktor = oberflaechenFaktor, leisteMussPassen = true) {
            Box(Modifier.fillMaxSize()) {
                TafelBedienung(
                    state = state,
                    animationsModus = animationsModus,
                    zeigeUpdatePunkt = zeigeUpdatePunkt,
                    onSchliessen = { zeigeBeendenAbfrage = true },
                    onMenu = onOeffneEinstellungen,
                    onTeilen = { state.aufnahmeAnfrage = AufnahmeZweck.TEILEN },
                    onIServ = { state.aufnahmeAnfrage = AufnahmeZweck.ISERV }
                )

                SeitenLeiste(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                )

                if (neuigkeiten != null) {
                    NeuigkeitenHinweis(
                        text = neuigkeiten,
                        onSchliessen = onNeuigkeitenGelesen,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                if (zeigeBeendenAbfrage) {
                    BeendenAbfrage(
                        onAbbrechen = { zeigeBeendenAbfrage = false },
                        onBeenden = {
                            zeigeBeendenAbfrage = false
                            onSchliessenApp()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Sicherheitsabfrage vor dem Beenden. Bewusst kein System-Dialog: der würde je nach
 * Android-Version anders aussehen und nicht mit der Oberfläche mitwachsen.
 */
@Composable
private fun BeendenAbfrage(onAbbrechen: () -> Unit, onBeenden: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onAbbrechen() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .faengtBeruehrungen()
                .padding(36.dp)
        ) {
            Text("DammBoard beenden?", color = Color(0xFF2B2B2B), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(
                "Willst du das Programm wirklich beenden? Nicht gespeicherte Tafelbilder gehen dabei verloren.",
                color = Color(0xFF555555), fontSize = 24.sp, lineHeight = 32.sp
            )
            Spacer(Modifier.height(30.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                DialogKnopf("Abbrechen", hervorgehoben = false, onClick = onAbbrechen)
                DialogKnopf("Beenden", hervorgehoben = true, onClick = onBeenden)
            }
        }
    }
}

@Composable
private fun DialogKnopf(text: String, hervorgehoben: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(if (hervorgehoben) Color(0xFFC62828) else Color(0xFFEBEBEB))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 34.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (hervorgehoben) Color.White else Color(0xFF333333),
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Kurzer Hinweis oben nach einem Update: zeigt, dass die neue Version wirklich läuft. Schließt
 * sich beim Antippen oder nach ein paar Sekunden von selbst.
 */
@Composable
private fun NeuigkeitenHinweis(text: String, onSchliessen: () -> Unit, modifier: Modifier = Modifier) {
    val aktuellesSchliessen by rememberUpdatedState(onSchliessen)
    LaunchedEffect(text) {
        delay(NEUIGKEITEN_ANZEIGE_MS)
        aktuellesSchliessen()
    }
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 24.dp)
            .widthIn(max = 1100.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(role = Role.Button, onClick = onSchliessen)
            .padding(horizontal = 30.dp, vertical = 18.dp)
    ) {
        Text(text, color = Color(0xFF2B2B2B), fontSize = 22.sp, lineHeight = 30.sp)
    }
}

private fun fuehreSpeicherungAus(zweck: AufnahmeZweck, bitmap: Bitmap, context: Context, scope: CoroutineScope) {
    scope.launch {
        val uri = speichereBildUndGibUriZurueck(context, bitmap)
        when (zweck) {
            AufnahmeZweck.SPEICHERN -> Toast.makeText(
                context,
                if (uri != null) "Tafelbild gespeichert" else "Speichern fehlgeschlagen",
                Toast.LENGTH_SHORT
            ).show()
            AufnahmeZweck.TEILEN -> if (uri != null) {
                teileBild(context, uri)
            } else {
                Toast.makeText(context, "Teilen fehlgeschlagen", Toast.LENGTH_SHORT).show()
            }
            AufnahmeZweck.ISERV -> Unit
        }
    }
}
