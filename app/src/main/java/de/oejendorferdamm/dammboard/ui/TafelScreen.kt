package de.oejendorferdamm.dammboard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.ui.canvas.TafelCanvas
import de.oejendorferdamm.dammboard.ui.toolbar.TafelWerkzeugleiste
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Bildschirm der Tafel: Zeichenfläche plus vollständige Werkzeugleiste, wie im Design vorgegeben. */
@Composable
fun TafelScreen(
    state: TafelState,
    animationsModus: AnimationsModus,
    onSchliessenApp: () -> Unit,
    onOeffneEinstellungen: () -> Unit,
    onIServAnfrage: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
            onSchliessen = onSchliessenApp,
            onMenu = onOeffneEinstellungen,
            onTeilen = { state.aufnahmeAnfrage = AufnahmeZweck.TEILEN },
            onIServ = { state.aufnahmeAnfrage = AufnahmeZweck.ISERV },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(top = 4.dp)
        )
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
