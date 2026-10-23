package de.oejendorferdamm.dammboard.ui.helfer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/*
 * Wörter raten (kindgerechtes „Galgenmännchen“): Statt eines Galgens verliert eine Blume bei
 * jedem falschen Buchstaben ein Blütenblatt. Das Wort tippt die Lehrkraft verdeckt ein oder
 * nimmt ein Zufallswort aus einer Liste einfacher Wörter.
 */

private const val BLAETTER = 8

/** Einfache Nomen (ohne ß), wie sie in der Grundschule früh vorkommen. */
private val WORTLISTE = listOf(
    "APFEL", "BALL", "BAUM", "BLUME", "BROT", "BUCH", "FISCH", "HAUS", "HUND", "KATZE", "MAUS", "MOND", "SONNE",
    "SCHULE", "TAFEL", "VOGEL", "WOLKE", "ZEBRA", "HASE", "IGEL", "PFERD", "REGEN", "SCHNEE", "STERN", "TISCH",
    "STUHL", "LAMPE", "NASE", "HAND", "AUTO", "ENTE", "ESEL", "INSEL", "KERZE", "BANANE", "BIRNE", "TOMATE",
    "GARTEN", "WALD", "WIESE", "BLATT", "HERZ", "FARBE", "PINSEL", "HEFT", "STIFT", "LINEAL", "SCHERE",
    "RANZEN", "PAUSE", "FREUND", "DRACHE", "PIRAT", "KRONE", "BURG", "RITTER", "RAKETE", "TIGER", "LÖWE",
    "BÄR", "KÄSE", "MÖHRE", "MÜTZE", "SCHLÜSSEL", "BRÜCKE", "KÜCHE", "SCHAF", "ZIEGE", "BIENE", "KÄFER",
)

private val TASTEN = listOf("ABCDEFGHI", "JKLMNOPQR", "STUVWXYZ", "ÄÖÜ")

private val BlattFarbe = Color(0xFFF2A0C0)
private val BlattRand = Color(0xFFD9779F)
private val Mitte = Color(0xFFF5C542)
private val Stiel = Color(0xFF4E9A4A)
private val Falsch = Color(0xFFE0E0DC)

@Composable
internal fun WoerterRatenHelfer(onVerschieben: (Offset) -> Unit, onSchliessen: () -> Unit, modifier: Modifier) {
    var wort by remember { mutableStateOf("") }               // in Großbuchstaben
    var eingabe by remember { mutableStateOf("") }
    val geraten = remember { mutableStateListOf<Char>() }
    var aufgeloest by remember { mutableStateOf(false) }

    val buchstaben = wort.filter { it.isLetter() }.toSet()
    val falsch = geraten.count { it !in buchstaben }
    val gewonnen = wort.isNotEmpty() && buchstaben.all { it in geraten }
    val verloren = wort.isNotEmpty() && falsch >= BLAETTER
    val ende = gewonnen || verloren || aufgeloest

    fun starte(neu: String) {
        wort = neu.trim().uppercase().replace(Regex("[^A-ZÄÖÜ -]"), "")
        geraten.clear(); aufgeloest = false; eingabe = ""
    }

    HelferKarte("Wörter raten", onVerschieben, onSchliessen, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(700.dp)) {
            if (wort.isEmpty()) {
                // Wort verdeckt eingeben oder Zufallswort
                Text("Wort eintippen (die Kinder sehen nur Punkte) oder ein Zufallswort nehmen.",
                    color = HelferTextSchwach, fontSize = 18.sp)
                Abstand(10)
                OutlinedTextField(
                    value = eingabe, onValueChange = { eingabe = it.take(20) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (eingabe.isNotBlank()) starte(eingabe) }),
                    placeholder = { Text("Geheimes Wort", fontSize = 20.sp) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HelferAkzent),
                    modifier = Modifier.width(420.dp)
                )
                Abstand(12)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HelferTaste("Los", hervorgehoben = true, aktiviert = eingabe.any { it.isLetter() }) { starte(eingabe) }
                    HelferTaste("Zufallswort") { starte(WORTLISTE[Random.nextInt(WORTLISTE.size)]) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Blume(BLAETTER - falsch, verloren, Modifier.size(170.dp))
                    Abstand(16)
                    Column {
                        // Das Wort: erratene Buchstaben, sonst Lücken
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            wort.forEach { c ->
                                val zeigen = !c.isLetter() || c in geraten || ende
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                                    Text(
                                        if (zeigen) "$c" else " ",
                                        color = if (zeigen && c !in geraten && c.isLetter()) Color(0xFFC0392B) else HelferText,
                                        fontSize = 38.sp, fontWeight = FontWeight.Bold
                                    )
                                    if (c.isLetter()) Box(Modifier.width(36.dp).size(width = 36.dp, height = 4.dp).background(HelferText))
                                }
                            }
                        }
                        Abstand(10)
                        Text(
                            when {
                                gewonnen -> "🎉 Geschafft!"
                                verloren -> "Oh nein – die Blume hat keine Blätter mehr."
                                aufgeloest -> "Aufgelöst."
                                else -> "Noch ${BLAETTER - falsch} Blütenblätter"
                            },
                            color = if (gewonnen) HelferAkzent else HelferTextSchwach, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Abstand(14)
                // Buchstaben-Tasten
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TASTEN.forEach { zeile ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            zeile.forEach { c ->
                                val schon = c in geraten
                                val farbe = when {
                                    !schon -> HelferKnopf
                                    c in buchstaben -> HelferAkzent
                                    else -> Falsch
                                }
                                Box(
                                    Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(farbe)
                                        .clickable(enabled = !schon && !ende, role = Role.Button) { geraten.add(c) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$c", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                        color = when { !schon -> HelferText; c in buchstaben -> Color.White; else -> HelferTextSchwach }
                                    )
                                }
                            }
                        }
                    }
                }
                Abstand(14)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HelferTaste("Neues Wort", hervorgehoben = ende) { wort = ""; geraten.clear(); aufgeloest = false }
                    HelferTaste("Zufallswort") { starte(WORTLISTE[Random.nextInt(WORTLISTE.size)]) }
                    HelferTaste("Auflösen", aktiviert = !ende) { aufgeloest = true }
                }
            }
        }
    }
}

/** Blume mit [blaetter] von 8 Blütenblättern; ohne Blätter lässt sie den Kopf hängen. */
@Composable
private fun Blume(blaetter: Int, welk: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val kopf = Offset(w * 0.5f, h * 0.36f)
        // Stiel und Blatt
        drawLine(Stiel, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.98f), strokeWidth = w * 0.05f)
        drawOval(Stiel, Offset(w * 0.5f, h * 0.66f), Size(w * 0.22f, h * 0.09f))
        rotate(if (welk) 35f else 0f, Offset(w * 0.5f, h * 0.55f)) {
            for (i in 0 until BLAETTER) {
                val winkel = i * 360f / BLAETTER
                rotate(winkel, kopf) {
                    val oben = Offset(kopf.x - w * 0.09f, kopf.y - w * 0.33f)
                    if (i < blaetter) {
                        drawOval(BlattFarbe, oben, Size(w * 0.18f, w * 0.26f))
                        drawOval(BlattRand, oben, Size(w * 0.18f, w * 0.26f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.012f))
                    }
                }
            }
            drawCircle(if (welk) Color(0xFFB59A3A) else Mitte, w * 0.12f, kopf)
        }
    }
}
