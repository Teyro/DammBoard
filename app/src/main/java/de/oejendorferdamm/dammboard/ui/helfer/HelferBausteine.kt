package de.oejendorferdamm.dammboard.ui.helfer

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import java.io.File

/* Gemeinsame Bausteine der schwebenden Helfer (Maße in Vorbild-Pixeln, siehe ui/Skalierung.kt). */

internal val HelferFlaeche = Color(0xFFF4F4F2)
internal val HelferText = Color(0xFF2B2B2B)
internal val HelferTextSchwach = Color(0xFF6E6E6E)
internal val HelferAkzent = Color(0xFF3A5C4A)
internal val HelferKnopf = Color(0xFFE2E2DE)

/**
 * Karte eines Helfers: Titelzeile zum Verschieben und ein Schließen-Knopf. Fängt alle
 * Berührungen ab, damit nichts auf die Tafel darunter durchgeht.
 */
@Composable
internal fun HelferKarte(
    titel: String,
    onVerschieben: (Offset) -> Unit,
    onSchliessen: () -> Unit,
    modifier: Modifier = Modifier,
    hintergrund: Color = HelferFlaeche,
    inhalt: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .shadow(14.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(hintergrund)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        Row(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, ziehen ->
                        change.consume()
                        onVerschieben(ziehen)
                    }
                }
                .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Griff-Punkte zeigen, dass man die Karte ziehen kann.
            Canvas(Modifier.padding(end = 12.dp).size(width = 14.dp, height = 22.dp)) {
                for (x in 0..1) for (y in 0..2) {
                    drawCircle(HelferTextSchwach, radius = size.width * 0.14f, center = Offset(size.width * (0.25f + x * 0.5f), size.height * (0.2f + y * 0.3f)))
                }
            }
            Text(titel, color = HelferText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onSchliessen)
                    .semantics { contentDescription = "$titel schließen" },
                contentAlignment = Alignment.Center
            ) {
                AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(22.dp), HelferText)
            }
        }
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 6.dp)) { inhalt() }
    }
}

@Composable
internal fun HelferTaste(
    text: String,
    modifier: Modifier = Modifier,
    hervorgehoben: Boolean = false,
    aktiviert: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(40.dp))
            .background(if (hervorgehoben) HelferAkzent else HelferKnopf)
            .clickable(enabled = aktiviert, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                !aktiviert -> HelferTextSchwach
                hervorgehoben -> Color.White
                else -> HelferText
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** − Zahl + */
@Composable
internal fun ZahlWahl(wert: Int, bereich: IntRange, beschriftung: String, onWert: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(beschriftung, color = HelferTextSchwach, fontSize = 18.sp)
        HelferTaste("−", aktiviert = wert > bereich.first) { onWert(wert - 1) }
        Text("$wert", color = HelferText, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
        HelferTaste("+", aktiviert = wert < bereich.last) { onWert(wert + 1) }
    }
}

@Composable
internal fun Abstand(hoehe: Int = 12) = Spacer(Modifier.size(hoehe.dp))

/**
 * Die Namensliste der Klasse für Zufallsname und Gruppen – eine Datei im App-Ordner, damit sie
 * auch nach einem Neustart noch da ist. Nur auf diesem Board gespeichert.
 */
internal object Klassenliste {
    val namen: SnapshotStateList<String> = mutableStateListOf()
    private var geladen = false

    private fun datei(context: Context) = File(context.filesDir, "namen.txt")

    fun lade(context: Context) {
        if (geladen) return
        geladen = true
        val f = datei(context)
        if (f.exists()) namen.addAll(f.readLines().map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun speichere(context: Context, text: String) {
        val neu = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        namen.clear()
        namen.addAll(neu)
        datei(context).writeText(neu.joinToString("\n"))
    }
}
