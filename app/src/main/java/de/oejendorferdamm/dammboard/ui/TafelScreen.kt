package de.oejendorferdamm.dammboard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.ui.canvas.TafelCanvas
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import de.oejendorferdamm.dammboard.ui.toolbar.TafelWerkzeugleiste
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Bildschirm der Tafel: Zeichenfläche plus vollständige Werkzeugleiste, wie im Design vorgegeben. */
@Composable
fun TafelScreen(
    state: TafelState,
    animationsModus: AnimationsModus,
    symbolSkalierung: Float,
    zeigeUpdatePunkt: Boolean,
    onSchliessenApp: () -> Unit,
    onOeffneEinstellungen: () -> Unit,
    onIServAnfrage: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zeigeBeendenDialog by remember { mutableStateOf(false) }

    // Nur auf Android 9 und älter gebraucht: ab Android 10 übernimmt Scoped Storage das Speichern
    // ohne Berechtigungsdialog (siehe speichereBildUndGibUriZurueck in Speichern.kt).
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
            modifier = Modifier.fillMaxSize()
        ) { zweck, bitmap ->
            when (zweck) {
                AufnahmeZweck.SPEICHERN, AufnahmeZweck.TEILEN -> starteSpeicherung(zweck, bitmap)
                AufnahmeZweck.ISERV -> onIServAnfrage(bitmap)
            }
        }

        TafelWerkzeugleiste(
            state = state,
            animationsModus = animationsModus,
            symbolSkalierung = symbolSkalierung,
            zeigeUpdatePunkt = zeigeUpdatePunkt,
            onMenu = onOeffneEinstellungen,
            onTeilen = { state.aufnahmeAnfrage = AufnahmeZweck.TEILEN },
            onIServ = { state.aufnahmeAnfrage = AufnahmeZweck.ISERV },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(top = 4.dp)
        )

        // Immer sichtbar (unabhängig vom Werkzeugkasten-Panel) am rechten Bildschirmrand, wie im
        // Original-Design: zeigt aktuelle/Gesamtzahl der Seiten und lässt sich zusätzlich durch
        // vertikales Ziehen/Scrollen blättern.
        SeitenNavigator(
            aktuelleSeite = state.aktiveSeite,
            seitenAnzahl = state.seiten.size,
            aufVorherige = { state.vorherigeSeite() },
            aufNaechste = { state.naechsteSeite() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        )

        // Bewusst abseits der Werkzeuggruppe, ganz unten links – damit man beim Arbeiten in der
        // Mitte/rechts nicht versehentlich die App beendet.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 14.dp)
                .size(40.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { zeigeBeendenDialog = true },
            contentAlignment = Alignment.Center
        ) {
            AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(16.dp), Color(0xFFE0402E))
        }
    }

    if (zeigeBeendenDialog) {
        AlertDialog(
            onDismissRequest = { zeigeBeendenDialog = false },
            title = { Text("DammBoard beenden?") },
            text = { Text("Willst du das Programm wirklich beenden?") },
            confirmButton = {
                TextButton(onClick = {
                    zeigeBeendenDialog = false
                    onSchliessenApp()
                }) { Text("Beenden") }
            },
            dismissButton = {
                TextButton(onClick = { zeigeBeendenDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

private val SeitenFarbe = Color(0xFF2B2B28)
private val SeitenFarbeSchwach = Color(0xFF8A8880)

/** Ständig sichtbare Seitenanzeige am rechten Rand: Pfeile zum Blättern plus vertikales
 *  Ziehen über die gesamte Fläche schaltet ebenfalls eine Seite weiter/zurück. */
@Composable
private fun SeitenNavigator(
    aktuelleSeite: Int,
    seitenAnzahl: Int,
    aufVorherige: () -> Unit,
    aufNaechste: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zugSumme by remember { mutableFloatStateOf(0f) }
    val schwelle = 56f

    Column(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .pointerInput(seitenAnzahl) {
                detectVerticalDragGestures(
                    onDragStart = { zugSumme = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        zugSumme += dragAmount
                        while (zugSumme <= -schwelle) {
                            aufNaechste()
                            zugSumme += schwelle
                        }
                        while (zugSumme >= schwelle) {
                            aufVorherige()
                            zugSumme -= schwelle
                        }
                    },
                    onDragEnd = { zugSumme = 0f },
                    onDragCancel = { zugSumme = 0f }
                )
            }
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .then(if (aktuelleSeite > 0) Modifier.clickable(onClick = aufVorherige) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            AllgemeinSymbol(
                AllgemeinesSymbol.PFEIL_LINKS,
                Modifier.size(16.dp).rotate(90f),
                if (aktuelleSeite > 0) SeitenFarbe else SeitenFarbeSchwach
            )
        }
        Text("${aktuelleSeite + 1}", color = SeitenFarbe, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.width(16.dp).height(1.dp).background(SeitenFarbeSchwach))
        Text("$seitenAnzahl", color = SeitenFarbeSchwach, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .then(if (aktuelleSeite < seitenAnzahl - 1) Modifier.clickable(onClick = aufNaechste) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            AllgemeinSymbol(
                AllgemeinesSymbol.PFEIL_RECHTS,
                Modifier.size(16.dp).rotate(90f),
                if (aktuelleSeite < seitenAnzahl - 1) SeitenFarbe else SeitenFarbeSchwach
            )
        }
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
            AufnahmeZweck.TEILEN -> uri?.let { teileBild(context, it) }
            AufnahmeZweck.ISERV -> Unit
        }
    }
}
