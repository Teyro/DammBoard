package de.oejendorferdamm.dammboard.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.oejendorferdamm.dammboard.model.AnimationsModus
import de.oejendorferdamm.dammboard.ui.canvas.TafelCanvas
import de.oejendorferdamm.dammboard.ui.toolbar.TafelWerkzeugleiste
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

    Box(modifier = modifier.fillMaxSize()) {
        TafelCanvas(
            state = state,
            modifier = Modifier.fillMaxSize()
        ) { zweck, bitmap ->
            when (zweck) {
                AufnahmeZweck.SPEICHERN -> scope.launch {
                    val uri = speichereBildUndGibUriZurueck(context, bitmap)
                    Toast.makeText(
                        context,
                        if (uri != null) "Tafelbild gespeichert" else "Speichern fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                AufnahmeZweck.TEILEN -> scope.launch {
                    val uri = speichereBildUndGibUriZurueck(context, bitmap)
                    uri?.let { teileBild(context, it) }
                }
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
                .padding(top = 4.dp)
        )
    }
}
