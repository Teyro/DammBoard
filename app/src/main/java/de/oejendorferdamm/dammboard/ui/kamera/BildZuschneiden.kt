package de.oejendorferdamm.dammboard.ui.kamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.ui.Arbeitsblaetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ein Bild, das schon auf der Tafel liegt, nachträglich zuschneiden, gerade ziehen oder drehen – dieselben Werkzeuge
 * wie bei der Dokumentenkamera. Ergebnis: neue Bilddatei (das Original bleibt, „Rückgängig“ geht).
 */
@Composable
fun BildZuschneiden(datei: String, onFertig: (neueDatei: String, breite: Int, hoehe: Int) -> Unit, onAbbrechen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bild by remember { mutableStateOf<Bitmap?>(null) }
    var ecken by remember { mutableStateOf<List<Punkt>?>(null) }
    var arbeitet by remember { mutableStateOf(false) }
    var hinweis by remember { mutableStateOf<String?>(null) }
    fun rahmen(b: Bitmap) = listOf(Punkt(b.width * 0.04f, b.height * 0.04f), Punkt(b.width * 0.96f, b.height * 0.04f), Punkt(b.width * 0.96f, b.height * 0.96f), Punkt(b.width * 0.04f, b.height * 0.96f))

    LaunchedEffect(datei) {
        val b = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(File(Arbeitsblaetter.ordner(context), datei).absolutePath) }.getOrNull() }
        if (b == null) { hinweis = "Das Bild ließ sich nicht laden."; return@LaunchedEffect }
        bild = b; ecken = rahmen(b)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF111111)).clickable(remember { MutableInteractionSource() }, null) {}) {
        val b = bild; val e = ecken
        Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 130.dp), contentAlignment = Alignment.Center) {
            if (b != null && e != null) ZuschneideAnsicht(b, e) { ecken = it }
            else CircularProgressIndicator(color = Color.White)
        }
        hinweis?.let { h ->
            LaunchedEffect(h) { kotlinx.coroutines.delay(4000); hinweis = null }
            Text(h, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(horizontal = 24.dp, vertical = 12.dp))
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            KameraKnopf("✕", "Abbrechen", onClick = onAbbrechen)
            KameraKnopf("⟳", "Drehen", aktiv = b != null && !arbeitet) {
                val alt = b ?: return@KameraKnopf
                scope.launch { val neu = withContext(Dispatchers.Default) { BlattErkennung.drehe(alt) }; bild = neu; ecken = rahmen(neu) }
            }
            KameraKnopf("🪄", "Blatt suchen", aktiv = b != null && !arbeitet) {
                val alt = b ?: return@KameraKnopf
                scope.launch {
                    val f = withContext(Dispatchers.Default) { runCatching { BlattErkennung.finde(alt) }.getOrNull() }
                    if (f != null) ecken = f else hinweis = "Kein Blatt erkannt – bitte die Ecken von Hand ziehen."
                }
            }
            KameraKnopf("✓", if (arbeitet) "Speichert …" else "Übernehmen", hervorgehoben = true, aktiv = b != null && e != null && !arbeitet) {
                val alt = b ?: return@KameraKnopf
                val ziel = e ?: return@KameraKnopf
                arbeitet = true
                scope.launch {
                    val ergebnis = withContext(Dispatchers.IO) {
                        runCatching { val neu = BlattErkennung.geradeZiehen(alt, ziel); Triple(Arbeitsblaetter.speichereBild(context, neu), neu.width, neu.height) }.getOrNull()
                    }
                    arbeitet = false
                    if (ergebnis != null) onFertig(ergebnis.first, ergebnis.second, ergebnis.third) else hinweis = "Speichern hat nicht geklappt."
                }
            }
        }
    }
}
