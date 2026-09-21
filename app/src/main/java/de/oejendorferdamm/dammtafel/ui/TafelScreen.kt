package de.oejendorferdamm.dammtafel.ui

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.OutputStream

private val TafelGruen = Color(0xFF1E3D2F)

private val Kreidefarben = listOf(
    Color(0xFFF5F3EE), // Kreide-Weiß
    Color(0xFFFFE08A), // Gelb
    Color(0xFF8FD3F4), // Hellblau
    Color(0xFFF4A6C6), // Rosa
    Color(0xFF9BE39B), // Hellgrün
)

private data class Strich(
    val punkte: List<Offset>,
    val farbe: Color,
    val breite: Float
)

@Composable
fun TafelScreen() {
    val striche = remember { mutableStateListOf<Strich>() }
    var aktuelleFarbe by remember { mutableStateOf(Kreidefarben.first()) }
    var radiergummiAktiv by remember { mutableStateOf(false) }
    var strichbreite by remember { mutableFloatStateOf(10f) }

    var laufenderStrich by remember { mutableStateOf<List<Offset>?>(null) }

    val graphicsLayer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                }
                .pointerInput(radiergummiAktiv, aktuelleFarbe, strichbreite) {
                    detectDragGestures(
                        onDragStart = { start ->
                            laufenderStrich = listOf(start)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            laufenderStrich = laufenderStrich.orEmpty() + change.position
                        },
                        onDragEnd = {
                            laufenderStrich?.let { punkte ->
                                if (punkte.size > 1) {
                                    striche.add(
                                        Strich(
                                            punkte = punkte,
                                            farbe = if (radiergummiAktiv) TafelGruen else aktuelleFarbe,
                                            breite = if (radiergummiAktiv) strichbreite * 3f else strichbreite
                                        )
                                    )
                                }
                            }
                            laufenderStrich = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = TafelGruen)
                striche.forEach { strich -> zeichneStrich(strich) }
                laufenderStrich?.let { punkte ->
                    zeichneStrich(
                        Strich(
                            punkte = punkte,
                            farbe = if (radiergummiAktiv) TafelGruen else aktuelleFarbe,
                            breite = if (radiergummiAktiv) strichbreite * 3f else strichbreite
                        )
                    )
                }
            }
        }

        Werkzeugleiste(
            farben = Kreidefarben,
            aktuelleFarbe = aktuelleFarbe,
            radiergummiAktiv = radiergummiAktiv,
            strichbreite = strichbreite,
            kannRueckgaengig = striche.isNotEmpty(),
            onFarbeGewaehlt = { farbe ->
                aktuelleFarbe = farbe
                radiergummiAktiv = false
            },
            onRadiergummi = { radiergummiAktiv = !radiergummiAktiv },
            onStrichbreiteGeaendert = { strichbreite = it },
            onRueckgaengig = { if (striche.isNotEmpty()) striche.removeAt(striche.lastIndex) },
            onLoeschen = { striche.clear() },
            onSpeichern = {
                scope.launch {
                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                    val gespeichert = speichereAlsBild(context, bitmap)
                    Toast.makeText(
                        context,
                        if (gespeichert) "Tafelbild gespeichert" else "Speichern fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.zeichneStrich(strich: Strich) {
    if (strich.punkte.size < 2) return
    for (i in 0 until strich.punkte.size - 1) {
        drawLine(
            color = strich.farbe,
            start = strich.punkte[i],
            end = strich.punkte[i + 1],
            strokeWidth = strich.breite,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
private fun Werkzeugleiste(
    farben: List<Color>,
    aktuelleFarbe: Color,
    radiergummiAktiv: Boolean,
    strichbreite: Float,
    kannRueckgaengig: Boolean,
    onFarbeGewaehlt: (Color) -> Unit,
    onRadiergummi: () -> Unit,
    onStrichbreiteGeaendert: (Float) -> Unit,
    onRueckgaengig: () -> Unit,
    onLoeschen: () -> Unit,
    onSpeichern: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        farben.forEach { farbe ->
            FarbKreis(
                farbe = farbe,
                ausgewaehlt = !radiergummiAktiv && farbe == aktuelleFarbe,
                onClick = { onFarbeGewaehlt(farbe) }
            )
        }

        Button(
            onClick = onRadiergummi,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (radiergummiAktiv) Color(0xFFF5F3EE) else Color(0xFF3A5C4A)
            )
        ) {
            Text("Radierer", color = if (radiergummiAktiv) TafelGruen else Color(0xFFF5F3EE))
        }

        Text("Breite", color = Color.Gray)
        Slider(
            value = strichbreite,
            onValueChange = onStrichbreiteGeaendert,
            valueRange = 4f..32f,
            modifier = Modifier
                .weight(1f)
        )

        Button(onClick = onRueckgaengig, enabled = kannRueckgaengig) {
            Text("Rückgängig")
        }

        Button(onClick = onLoeschen) {
            Text("Wischen")
        }

        Button(onClick = onSpeichern) {
            Text("Speichern")
        }
    }
}

@Composable
private fun RowScope.FarbKreis(farbe: Color, ausgewaehlt: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (ausgewaehlt) 36.dp else 28.dp)
            .clip(CircleShape)
            .padding(2.dp)
    ) {
        IconButton(onClick = onClick) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = farbe)
                }
            }
        }
    }
}

private fun speichereAlsBild(context: android.content.Context, bitmap: android.graphics.Bitmap): Boolean {
    return try {
        val name = "DammTafel_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DammTafel")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        val stream: OutputStream? = context.contentResolver.openOutputStream(uri)
        stream?.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (e: Exception) {
        false
    }
}
