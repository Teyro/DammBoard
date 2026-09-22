package de.oejendorferdamm.dammboard.ui.toolbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.model.KreidePalette

/** Errechnet die Farbe aus einer Position im Verlaufsquadrat (x = Farbton, y = Helligkeit/Sättigung). */
fun hsvVerlaufsFarbe(fractionX: Float, fractionY: Float): Color {
    val hue = fractionX.coerceIn(0f, 1f) * 360f
    val fy = fractionY.coerceIn(0f, 1f)
    val (saturation, value) = if (fy <= 0.5f) {
        (fy / 0.5f) to 1f
    } else {
        1f to (1f - (fy - 0.5f) / 0.5f)
    }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    return Color(argb)
}

private val SwatchGroesse = 44.dp
private val SwatchAbstand = 4.dp

/** Zeilen/Spalten-Aufteilung wie im Original: Stift-Panel 3 Spalten (breiter Verlauf daneben),
 *  Formen-Panel 4 Spalten (schmalere „Rand"-Leiste daneben) – bei gleicher Farbanzahl. */
@Composable
fun FarbGitterUndVerlauf(
    ausgewaehlt: Color,
    onFarbe: (Color) -> Unit,
    modifier: Modifier = Modifier,
    zeigeVerlauf: Boolean = true,
    spalten: Int = 4
) {
    val zeilen = (KreidePalette.size + spalten - 1) / spalten
    val gitterBreite = SwatchGroesse * spalten + SwatchAbstand * (spalten - 1)
    val gitterHoehe = SwatchGroesse * zeilen + SwatchAbstand * (zeilen - 1)

    Row(modifier = modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(spalten),
            modifier = Modifier.width(gitterBreite).height(gitterHoehe),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SwatchAbstand),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SwatchAbstand)
        ) {
            items(KreidePalette) { farbe ->
                FarbFeld(farbe = farbe, ausgewaehlt = farbe == ausgewaehlt, onClick = { onFarbe(farbe) })
            }
        }

        if (zeigeVerlauf) {
            VerlaufsQuadrat(
                modifier = Modifier.width(gitterHoehe).height(gitterHoehe),
                onFarbe = onFarbe
            )
        }
    }
}

@Composable
private fun FarbFeld(farbe: Color, ausgewaehlt: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(SwatchGroesse)
            .clip(RoundedCornerShape(4.dp))
            .background(farbe)
            .border(
                width = if (farbe == Color.White || farbe.luminanz() > 0.9f) 1.dp else 0.dp,
                color = Color(0xFFBBBBBB),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (ausgewaehlt) {
            val hakenFarbe = if (farbe.luminanz() > 0.55f) Color.Black else Color.White
            Text("✓", color = hakenFarbe, fontSize = 20.sp)
        }
    }
}

private fun Color.luminanz(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun VerlaufsQuadrat(modifier: Modifier = Modifier, onFarbe: (Color) -> Unit) {
    var knopf by remember { mutableStateOf<Offset?>(null) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        knopf = start
                        onFarbe(hsvVerlaufsFarbe(start.x / size.width, start.y / size.height))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val begrenzt = Offset(
                            change.position.x.coerceIn(0f, size.width.toFloat()),
                            change.position.y.coerceIn(0f, size.height.toFloat())
                        )
                        knopf = begrenzt
                        onFarbe(hsvVerlaufsFarbe(begrenzt.x / size.width, begrenzt.y / size.height))
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    knopf = position
                    onFarbe(hsvVerlaufsFarbe(position.x / size.width, position.y / size.height))
                }
            }
    ) {
        val regenbogen = Brush.horizontalGradient(
            listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(regenbogen)
                .background(Brush.verticalGradient(listOf(Color.White, Color.Transparent, Color.Transparent, Color.Black)))
        )
        knopf?.let { pos ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White,
                    radius = 7f,
                    center = pos,
                    style = Stroke(width = 2.4f)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = 9f,
                    center = pos,
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}
