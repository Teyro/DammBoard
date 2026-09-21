package de.oejendorferdamm.dammtafel.ui

import android.app.Activity
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
import de.oejendorferdamm.dammtafel.ui.canvas.TafelCanvas
import de.oejendorferdamm.dammtafel.ui.toolbar.TafelWerkzeugleiste
import kotlinx.coroutines.launch

/** Bildschirm der Tafel: Zeichenfläche plus vollständige Werkzeugleiste, wie im Design vorgegeben. */
@Composable
fun TafelScreen() {
    val state = rememberTafelState()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        TafelCanvas(
            state = state,
            modifier = Modifier.fillMaxSize()
        ) { zweck, bitmap ->
            scope.launch {
                val uri = speichereBildUndGibUriZurueck(context, bitmap)
                when (zweck) {
                    AufnahmeZweck.SPEICHERN -> Toast.makeText(
                        context,
                        if (uri != null) "Tafelbild gespeichert" else "Speichern fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                    AufnahmeZweck.TEILEN -> uri?.let { teileBild(context, it) }
                }
            }
        }

        TafelWerkzeugleiste(
            state = state,
            onSchliessen = { activity?.finish() },
            onMenu = { Toast.makeText(context, "DammTafel · Tafel-App für den Öjendorfer Damm", Toast.LENGTH_SHORT).show() },
            onTeilen = { state.aufnahmeAnfrage = AufnahmeZweck.TEILEN },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 4.dp)
        )
    }
}
