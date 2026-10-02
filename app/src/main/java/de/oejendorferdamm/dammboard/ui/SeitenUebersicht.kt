package de.oejendorferdamm.dammboard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.model.Seite
import de.oejendorferdamm.dammboard.model.abbild
import de.oejendorferdamm.dammboard.ui.canvas.rendereSeite
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import de.oejendorferdamm.dammboard.ui.toolbar.faengtBeruehrungen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val VORSCHAU_BREITE = 340f

/**
 * Übersicht aller Seiten als kleine Vorschaubilder (Antippen der Seitenzahl unten rechts):
 * Seite öffnen, verschieben, duplizieren, löschen – oder mit einer leeren Tafel neu anfangen.
 */
@Composable
internal fun SeitenUebersicht(state: TafelState) {
    if (!state.zeigeSeitenUebersicht) return
    val context = LocalContext.current
    val vorschau = remember { mutableStateMapOf<Seite, Pair<Int, ImageBitmap>>() }
    var neuAnfangenFragen by remember { mutableStateOf(false) }
    var loeschenFragen by remember { mutableStateOf<Seite?>(null) }
    val breite = state.brettBreite.coerceAtLeast(1)
    val hoehe = state.brettHoehe.coerceAtLeast(1)

    // Vorschaubilder im Hintergrund (neu nur für Seiten, die sich geändert haben).
    val seitenListe = state.seiten.toList()
    val staende = seitenListe.map { it.versionsZaehler + it.hintergrund.value.hashCode() + (it.hintergrundBild.value?.hashCode() ?: 0) }
    LaunchedEffect(seitenListe, staende) {
        seitenListe.forEachIndexed { i, seite ->
            val stand = staende[i]
            if (vorschau[seite]?.first == stand) return@forEachIndexed
            val abbild = seite.abbild()
            val bild = withContext(Dispatchers.Default) {
                val blatt = abbild.bild?.let { Arbeitsblaetter.lade(context, it) }
                rendereSeite(abbild, breite, hoehe, VORSCHAU_BREITE / breite, blatt).asImageBitmap()
            }
            vorschau[seite] = stand to bild
        }
        vorschau.keys.filter { it !in seitenListe }.forEach { vorschau.remove(it) }
    }

    fun schliessen() {
        state.zeigeSeitenUebersicht = false
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).faengtBeruehrungen(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 60.dp, vertical = 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF2F1ED))
                .padding(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Seiten (${seitenListe.size})", color = Color(0xFF2B2B2B), fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (neuAnfangenFragen) {
                    Text("Alle Seiten löschen?", color = Color(0xFFC62828), fontSize = 21.sp, modifier = Modifier.padding(end = 12.dp))
                    RundTaste("Ja, neue Tafel", warnung = true) {
                        state.neueTafel()
                        neuAnfangenFragen = false
                        schliessen()
                    }
                    Spacer(Modifier.width(8.dp))
                    RundTaste("Nein") { neuAnfangenFragen = false }
                } else {
                    RundTaste("Neue leere Tafel") { neuAnfangenFragen = true }
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(role = Role.Button, onClick = ::schliessen)
                        .semantics { contentDescription = "Seitenübersicht schließen" },
                    contentAlignment = Alignment.Center
                ) { AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(22.dp), Color(0xFF2B2B2B)) }
            }
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                (seitenListe.indices.toList() + listOf(-1)).chunked(4).forEach { zeile ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        zeile.forEach { index -> Box(Modifier.weight(1f)) {
                            if (index == -1) {
                                // Kachel "neue Seite"
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(breite.toFloat() / hoehe)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(3.dp, Color(0xFFBDBDBD), RoundedCornerShape(12.dp))
                                        .clickable(role = Role.Button) {
                                            state.neueSeite()
                                            schliessen()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+ Neue Seite", color = Color(0xFF6E6E6E), fontSize = 24.sp)
                                }
                            } else {
                                val seite = seitenListe[index]
                                SeitenKachel(
                                    nummer = index + 1,
                                    bild = vorschau[seite]?.second,
                                    seitenverhaeltnis = breite.toFloat() / hoehe,
                                    aktiv = index == state.aktiveSeite,
                                    loeschenFragen = loeschenFragen === seite,
                                    kannLinks = index > 0,
                                    kannRechts = index < seitenListe.lastIndex,
                                    onOeffnen = { state.geheZuSeite(index); schliessen() },
                                    onLinks = { state.seiteVerschieben(index, index - 1) },
                                    onRechts = { state.seiteVerschieben(index, index + 1) },
                                    onDuplizieren = { state.seiteDuplizieren(index) },
                                    onLoeschen = {
                                        if (loeschenFragen === seite || (seite.items.isEmpty() && seite.hintergrundBild.value == null)) {
                                            state.seiteLoeschen(index)
                                            loeschenFragen = null
                                        } else {
                                            loeschenFragen = seite
                                        }
                                    }
                                )
                            }
                        } }
                        repeat(4 - zeile.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeitenKachel(
    nummer: Int,
    bild: ImageBitmap?,
    seitenverhaeltnis: Float,
    aktiv: Boolean,
    loeschenFragen: Boolean,
    kannLinks: Boolean,
    kannRechts: Boolean,
    onOeffnen: () -> Unit,
    onLinks: () -> Unit,
    onRechts: () -> Unit,
    onDuplizieren: () -> Unit,
    onLoeschen: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(seitenverhaeltnis)
                .clip(RoundedCornerShape(12.dp))
                .border(if (aktiv) 5.dp else 1.dp, if (aktiv) Color(0xFF2F80FF) else Color(0xFFCFCFCF), RoundedCornerShape(12.dp))
                .background(Color(0xFFDADADA))
                .clickable(role = Role.Button, onClick = onOeffnen)
                .semantics { contentDescription = "Seite $nummer öffnen" }
        ) {
            if (bild != null) {
                Image(bild, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Text(
                "$nummer",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            KleineTaste("‹", kannLinks, "Nach vorne", onLinks)
            KleineTaste("›", kannRechts, "Nach hinten", onRechts)
            KleineTaste("Kopie", true, "Seite duplizieren", onDuplizieren)
            KleineTaste(if (loeschenFragen) "Wirklich?" else "Löschen", true, "Seite löschen", onLoeschen, warnung = loeschenFragen)
        }
    }
}

@Composable
private fun KleineTaste(text: String, aktiviert: Boolean, beschreibung: String, onClick: () -> Unit, warnung: Boolean = false) {
    Box(
        Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(if (warnung) Color(0xFFC62828) else Color.White)
            .clickable(enabled = aktiviert, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = beschreibung }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                warnung -> Color.White
                aktiviert -> Color(0xFF2B2B2B)
                else -> Color(0xFFBDBDBD)
            },
            fontSize = if (text.length == 1) 30.sp else 19.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
