package de.oejendorferdamm.dammboard.ui.toolbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.oejendorferdamm.dammboard.model.KreidePalette

/** Errechnet die Farbe aus einer Position im Verlaufsfeld (x = Farbton, y = hell → satt → dunkel). */
fun hsvVerlaufsFarbe(fractionX: Float, fractionY: Float): Color {
    val hue = fractionX.coerceIn(0f, 1f) * 360f
    val fy = fractionY.coerceIn(0f, 1f)
    val (saturation, value) = if (fy <= 0.5f) {
        (fy / 0.5f) to 1f
    } else {
        1f to (1f - (fy - 0.5f) / 0.5f)
    }
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
}

/**
 * Die zwölf Kreidefarben als Gitter, auf Wunsch mit freiem Farbverlauf daneben – wie im
 * Original: Stift-Panel 3 Spalten mit breitem Verlauf, Formen-Panel 4 Spalten.
 */
@Composable
internal fun FarbAuswahl(
    ausgewaehlt: Color,
    onFarbe: (Color) -> Unit,
    spalten: Int,
    feld: Dp,
    luecke: Dp,
    zeigeVerlauf: Boolean,
    modifier: Modifier = Modifier
) {
    val zeilen = KreidePalette.chunked(spalten)
    val gitterHoehe = feld * zeilen.size + luecke * (zeilen.size - 1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(luecke)) {
            zeilen.forEach { zeile ->
                Row(horizontalArrangement = Arrangement.spacedBy(luecke)) {
                    zeile.forEach { farbe ->
                        FarbFeld(farbe = farbe, ausgewaehlt = farbe == ausgewaehlt, groesse = feld, onClick = { onFarbe(farbe) })
                    }
                }
            }
        }
        if (zeigeVerlauf) {
            VerlaufsFeld(modifier = Modifier.width(gitterHoehe * 1.15f).height(gitterHoehe), onFarbe = onFarbe)
        }
    }
}

@Composable
private fun FarbFeld(farbe: Color, ausgewaehlt: Boolean, groesse: Dp, onClick: () -> Unit) {
    val hell = farbe.luminanz() > 0.85f
    Box(
        modifier = Modifier
            .size(groesse)
            .clip(RoundedCornerShape(3.dp))
            .background(farbe)
            .border(if (hell || ausgewaehlt) 1.dp else 0.dp, Color(0xFFB5B5B5), RoundedCornerShape(3.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (ausgewaehlt) {
            // Haken selbst zeichnen statt als Schriftzeichen – sieht auf jedem Gerät gleich aus.
            val hakenFarbe = if (farbe.luminanz() > 0.55f) Color.Black else Color.White
            Canvas(Modifier.size(groesse * 0.55f)) {
                val haken = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.55f)
                    lineTo(size.width * 0.4f, size.height * 0.82f)
                    lineTo(size.width * 0.9f, size.height * 0.2f)
                }
                drawPath(
                    haken, hakenFarbe,
                    style = Stroke(width = size.width * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

private fun Color.luminanz(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Freie Farbwahl: waagerecht der Farbton, senkrecht von hell über satt nach dunkel. */
@Composable
private fun VerlaufsFeld(modifier: Modifier = Modifier, onFarbe: (Color) -> Unit) {
    var knopf by remember { mutableStateOf<Offset?>(null) }
    val aktuellerRueckruf by rememberUpdatedState(onFarbe)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                fun waehle(position: Offset) {
                    val begrenzt = Offset(
                        position.x.coerceIn(0f, size.width.toFloat()),
                        position.y.coerceIn(0f, size.height.toFloat())
                    )
                    knopf = begrenzt
                    aktuellerRueckruf(hsvVerlaufsFarbe(begrenzt.x / size.width, begrenzt.y / size.height))
                }
                detectDragGestures(onDragStart = { waehle(it) }) { change, _ ->
                    change.consume()
                    waehle(change.position)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    knopf = position
                    aktuellerRueckruf(hsvVerlaufsFarbe(position.x / size.width, position.y / size.height))
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                    )
                )
                .background(Brush.verticalGradient(listOf(Color.White, Color.Transparent, Color.Transparent, Color.Black)))
        )
        val position = knopf
        if (position != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(Color.White, radius = 8.dp.toPx(), center = position, style = Stroke(width = 2.5.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.45f), radius = 10.dp.toPx(), center = position, style = Stroke(width = 1.dp.toPx()))
            }
        }
    }
}
