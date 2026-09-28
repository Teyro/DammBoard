package de.oejendorferdamm.dammboard.ui.spiel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinSymbol
import de.oejendorferdamm.dammboard.ui.icons.AllgemeinesSymbol
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * Tafelspiel "Fußball": ein Spielfeld über die ganze Tafel, ein Ball zum Ziehen und Schießen
 * und ein Punktezähler für die linke und die rechte Mannschaft.
 *
 * Bedienung: Ball anfassen und werfen (er rollt mit Schwung weiter und prallt von den Banden
 * ab) oder irgendwo aufs Feld tippen – dann wird der Ball in diese Richtung geschossen. Rollt
 * er ins Tor, zählt der Punkt automatisch. Die Punkte lassen sich zusätzlich von Hand ändern.
 */

private val Wiese = Color(0xFF2E6B34)
private val RasenHell = Color(0xFF4CA64F)
private val RasenDunkel = Color(0xFF43984A)
private val Linienweiss = Color(0xFFF5F5F0)
private val TeamLinks = Color(0xFF1E6FD9)
private val TeamRechts = Color(0xFFD93A2E)

/** Torbreite als Anteil der Spielfeldhöhe. */
private const val TOR_ANTEIL = 0.30f

/** Spielstand und Ball – Ballposition als Anteil des Spielfelds (0..1), damit sie jede Größe übersteht. */
private class FussballSpiel {
    var ballX by mutableFloatStateOf(0.5f)
    var ballY by mutableFloatStateOf(0.5f)
    var vx = 0f // Spielfeldbreiten pro Sekunde
    var vy = 0f // Spielfeldhöhen pro Sekunde
    var rollt by mutableStateOf(false)
    var punkteLinks by mutableIntStateOf(0)
    var punkteRechts by mutableIntStateOf(0)
    var torMeldung by mutableStateOf<String?>(null)
    var torZaehler by mutableIntStateOf(0)

    fun anstoss() {
        ballX = 0.5f
        ballY = 0.5f
        vx = 0f
        vy = 0f
        rollt = false
    }

    fun neuesSpiel() {
        punkteLinks = 0
        punkteRechts = 0
        torMeldung = null
        anstoss()
    }
}

/** Spielfeld innerhalb der Zeichenfläche: oben Platz für die Anzeige, rundherum Wiese. */
private fun spielfeld(flaeche: Size): Rect {
    val rand = flaeche.width * 0.075f
    return Rect(rand, flaeche.height * 0.17f, flaeche.width - rand, flaeche.height * 0.95f)
}

private fun ballRadius(feld: Rect): Float = min(feld.width, feld.height) * 0.03f

@Composable
fun TafelFussball(onSchliessen: () -> Unit, modifier: Modifier = Modifier) {
    val spiel = remember { FussballSpiel() }

    // Physik nur solange der Ball rollt – liegt er still, wird auch nichts neu gezeichnet.
    LaunchedEffect(spiel.rollt) {
        if (!spiel.rollt) return@LaunchedEffect
        var letzte = withFrameNanos { it }
        while (spiel.rollt) {
            val jetzt = withFrameNanos { it }
            val dt = ((jetzt - letzte) / 1_000_000_000f).coerceIn(0f, 0.05f)
            letzte = jetzt
            bewege(spiel, dt)
        }
    }
    LaunchedEffect(spiel.torZaehler) {
        if (spiel.torMeldung != null) {
            delay(1800)
            spiel.torMeldung = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Wiese)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Spielfeld" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val unten = awaitFirstDown()
                        val flaeche = Size(size.width.toFloat(), size.height.toFloat())
                        val feld = spielfeld(flaeche)
                        val radius = ballRadius(feld)
                        val ball = Offset(feld.left + spiel.ballX * feld.width, feld.top + spiel.ballY * feld.height)
                        val gegriffen = (unten.position - ball).getDistance() < radius * 2.6f
                        val tracker = VelocityTracker()
                        if (gegriffen) {
                            spiel.rollt = false
                            spiel.vx = 0f
                            spiel.vy = 0f
                            tracker.addPosition(unten.uptimeMillis, unten.position)
                            unten.consume()
                        }
                        var zuletzt = unten.position
                        while (true) {
                            val ereignis = awaitPointerEvent()
                            val aenderung = ereignis.changes.firstOrNull { it.id == unten.id } ?: break
                            zuletzt = aenderung.position
                            if (gegriffen) {
                                tracker.addPosition(aenderung.uptimeMillis, aenderung.position)
                                spiel.ballX = ((aenderung.position.x - feld.left) / feld.width).coerceIn(0f, 1f)
                                spiel.ballY = ((aenderung.position.y - feld.top) / feld.height).coerceIn(0f, 1f)
                                aenderung.consume()
                            }
                            if (!aenderung.pressed) break
                        }
                        val maxTempo = feld.width * 2.2f
                        if (gegriffen) {
                            // Werfen: Schwung der Hand übernehmen.
                            val v = tracker.calculateVelocity()
                            val tempo = hypot(v.x, v.y)
                            val begrenzung = if (tempo > maxTempo) maxTempo / tempo else 1f
                            spiel.vx = v.x * begrenzung / feld.width
                            spiel.vy = v.y * begrenzung / feld.height
                        } else {
                            // Tippen aufs Feld: Schuss in diese Richtung, je weiter weg, desto fester.
                            val ballJetzt = Offset(feld.left + spiel.ballX * feld.width, feld.top + spiel.ballY * feld.height)
                            val richtung = zuletzt - ballJetzt
                            val abstand = richtung.getDistance()
                            if (abstand > 1f) {
                                val tempo = (abstand * 1.8f).coerceAtMost(maxTempo)
                                spiel.vx = richtung.x / abstand * tempo / feld.width
                                spiel.vy = richtung.y / abstand * tempo / feld.height
                            }
                        }
                        if (spiel.vx != 0f || spiel.vy != 0f) spiel.rollt = true
                    }
                }
        ) {
            val feld = spielfeld(size)
            zeichneSpielfeld(feld)
            zeichneBall(Offset(feld.left + spiel.ballX * feld.width, feld.top + spiel.ballY * feld.height), ballRadius(feld))
        }

        // Anzeige oben: Punkte links : rechts, dazu "Neues Spiel" und Schließen.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Punktefeld("Links", spiel.punkteLinks, TeamLinks, onPlus = { spiel.punkteLinks++ }, onMinus = { if (spiel.punkteLinks > 0) spiel.punkteLinks-- })
            Text(":", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Punktefeld("Rechts", spiel.punkteRechts, TeamRechts, onPlus = { spiel.punkteRechts++ }, onMinus = { if (spiel.punkteRechts > 0) spiel.punkteRechts-- })
        }
        SpielKnopf(
            text = "Neues Spiel",
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 24.dp, top = 30.dp),
            onClick = { spiel.neuesSpiel() }
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 24.dp, top = 24.dp)
                .size(67.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFFEBEBEB))
                .clickable(role = Role.Button, onClick = onSchliessen)
                .semantics { contentDescription = "Spiel schließen" },
            contentAlignment = Alignment.Center
        ) {
            AllgemeinSymbol(AllgemeinesSymbol.SCHLIESSEN, Modifier.size(36.dp), Color(0xFFC62828))
        }

        spiel.torMeldung?.let { meldung ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(horizontal = 56.dp, vertical = 26.dp)
            ) {
                Text(meldung, color = Color(0xFF222222), fontSize = 72.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** Ein Rechenschritt: rollen, bremsen, an Banden abprallen, Tore erkennen. */
private fun bewege(spiel: FussballSpiel, dt: Float) {
    var x = spiel.ballX + spiel.vx * dt
    var y = spiel.ballY + spiel.vy * dt
    val bremse = exp(-1.1f * dt)
    spiel.vx *= bremse
    spiel.vy *= bremse
    val torOben = 0.5f - TOR_ANTEIL / 2
    val torUnten = 0.5f + TOR_ANTEIL / 2
    val imTorBereich = y in torOben..torUnten

    // Oben/unten abprallen.
    if (y < 0.02f) { y = 0.02f; spiel.vy = -spiel.vy * 0.75f }
    if (y > 0.98f) { y = 0.98f; spiel.vy = -spiel.vy * 0.75f }

    // Links/rechts: im Tor → Treffer, sonst Bande.
    if (x < 0.01f) {
        if (imTorBereich) {
            if (x < -0.035f) { tor(spiel, fuerLinks = false); return }
        } else {
            x = 0.01f; spiel.vx = -spiel.vx * 0.75f
        }
    }
    if (x > 0.99f) {
        if (imTorBereich) {
            if (x > 1.035f) { tor(spiel, fuerLinks = true); return }
        } else {
            x = 0.99f; spiel.vx = -spiel.vx * 0.75f
        }
    }
    spiel.ballX = x
    spiel.ballY = y
    // Fast still: anhalten, damit keine Bilder mehr berechnet werden.
    if (hypot(spiel.vx, spiel.vy) < 0.01f) {
        spiel.vx = 0f
        spiel.vy = 0f
        spiel.rollt = false
    }
}

private fun tor(spiel: FussballSpiel, fuerLinks: Boolean) {
    if (fuerLinks) spiel.punkteLinks++ else spiel.punkteRechts++
    spiel.torMeldung = if (fuerLinks) "TOR für Links!" else "TOR für Rechts!"
    spiel.torZaehler++
    spiel.anstoss()
}

@Composable
private fun Punktefeld(name: String, punkte: Int, farbe: Color, onPlus: () -> Unit, onMinus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (name == "Rechts") MinusKnopf(name, onMinus)
        Column(
            modifier = Modifier
                .width(170.dp)
                .shadow(6.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(farbe)
                .clickable(role = Role.Button, onClick = onPlus)
                .semantics { contentDescription = "Punkt für $name" }
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("$punkte", color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Bold)
        }
        if (name == "Links") MinusKnopf(name, onMinus)
    }
}

@Composable
private fun MinusKnopf(name: String, onMinus: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.85f))
            .clickable(role = Role.Button, onClick = onMinus)
            .semantics { contentDescription = "Punkt abziehen $name" },
        contentAlignment = Alignment.Center
    ) {
        Text("−", color = Color(0xFF333333), fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpielKnopf(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(34.dp))
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xFFEBEBEB))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp)
    ) {
        Text(text, color = Color(0xFF333333), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun DrawScope.zeichneSpielfeld(feld: Rect) {
    // Gemähte Rasenstreifen.
    val streifen = 12
    val breite = feld.width / streifen
    for (i in 0 until streifen) {
        drawRect(
            if (i % 2 == 0) RasenHell else RasenDunkel,
            topLeft = Offset(feld.left + i * breite, feld.top),
            size = Size(breite + 1f, feld.height)
        )
    }
    val linie = min(feld.width, feld.height) * 0.006f
    val strich = Stroke(width = linie)
    val mitte = feld.center

    drawRect(Linienweiss, topLeft = feld.topLeft, size = feld.size, style = strich)
    drawLine(Linienweiss, Offset(mitte.x, feld.top), Offset(mitte.x, feld.bottom), strokeWidth = linie)
    drawCircle(Linienweiss, radius = feld.height * 0.14f, center = mitte, style = strich)
    drawCircle(Linienweiss, radius = linie * 1.8f, center = mitte)

    // Strafräume, Torräume, Elfmeterpunkte – links und gespiegelt rechts.
    for (links in listOf(true, false)) {
        val richtung = if (links) 1f else -1f
        val linie0 = if (links) feld.left else feld.right
        fun kasten(tiefe: Float, hoehe: Float) {
            val x0 = if (links) linie0 else linie0 - tiefe
            drawRect(
                Linienweiss, topLeft = Offset(x0, mitte.y - hoehe / 2), size = Size(tiefe, hoehe), style = strich
            )
        }
        kasten(feld.width * 0.15f, feld.height * 0.56f)
        kasten(feld.width * 0.055f, feld.height * 0.3f)
        drawCircle(Linienweiss, radius = linie * 1.6f, center = Offset(linie0 + richtung * feld.width * 0.105f, mitte.y))

        // Tor mit Netz außerhalb der Linie.
        val torHoehe = feld.height * TOR_ANTEIL
        val torTiefe = feld.width * 0.035f
        val torX = if (links) linie0 - torTiefe else linie0
        val torOben = mitte.y - torHoehe / 2
        drawRect(Color.White.copy(alpha = 0.18f), topLeft = Offset(torX, torOben), size = Size(torTiefe, torHoehe))
        val netz = torTiefe / 4
        var nx = torX + netz
        while (nx < torX + torTiefe) {
            drawLine(Color.White.copy(alpha = 0.45f), Offset(nx, torOben), Offset(nx, torOben + torHoehe), strokeWidth = linie * 0.4f)
            nx += netz
        }
        var ny = torOben + netz
        while (ny < torOben + torHoehe) {
            drawLine(Color.White.copy(alpha = 0.45f), Offset(torX, ny), Offset(torX + torTiefe, ny), strokeWidth = linie * 0.4f)
            ny += netz
        }
        drawRect(Linienweiss, topLeft = Offset(torX, torOben), size = Size(torTiefe, torHoehe), style = Stroke(width = linie * 1.6f))
    }

    // Eckfahnen-Viertelkreise.
    val eck = feld.height * 0.04f
    listOf(
        Triple(feld.topLeft, 0f, 90f), Triple(feld.topRight, 90f, 90f),
        Triple(feld.bottomRight, 180f, 90f), Triple(feld.bottomLeft, 270f, 90f)
    ).forEach { (punkt, start, schwenk) ->
        drawArc(
            Linienweiss, startAngle = start, sweepAngle = schwenk, useCenter = false,
            topLeft = punkt - Offset(eck, eck), size = Size(eck * 2, eck * 2), style = strich
        )
    }
}

private fun DrawScope.zeichneBall(mitte: Offset, radius: Float) {
    drawOval(
        Color.Black.copy(alpha = 0.25f),
        topLeft = Offset(mitte.x - radius * 0.9f, mitte.y + radius * 0.55f),
        size = Size(radius * 1.9f, radius * 0.8f)
    )
    drawCircle(Color.White, radius = radius, center = mitte)
    // Schwarzes Fünfeck in der Mitte, Nähte zu fünf Randflecken – auf die Ballform beschnitten.
    clipPath(Path().apply { addOval(Rect(mitte, radius)) }) {
        val innen = radius * 0.36f
        val fuenfeck = Path()
        for (i in 0 until 5) {
            val winkel = -Math.PI / 2 + i * 2 * Math.PI / 5
            val p = mitte + Offset(cos(winkel).toFloat() * innen, sin(winkel).toFloat() * innen)
            if (i == 0) fuenfeck.moveTo(p.x, p.y) else fuenfeck.lineTo(p.x, p.y)
            val aussen = mitte + Offset(cos(winkel).toFloat() * radius * 0.82f, sin(winkel).toFloat() * radius * 0.82f)
            drawLine(Color(0xFF333333), p, aussen, strokeWidth = radius * 0.07f)
            drawCircle(Color(0xFF222222), radius = radius * 0.2f, center = mitte + Offset(cos(winkel).toFloat() * radius * 0.95f, sin(winkel).toFloat() * radius * 0.95f))
        }
        fuenfeck.close()
        drawPath(fuenfeck, Color(0xFF222222))
    }
    // Rand nach dem Füllen, damit die Flecken sauber abschließen.
    drawCircle(Color(0xFF555555), radius = radius, center = mitte, style = Stroke(width = radius * 0.08f))
}
