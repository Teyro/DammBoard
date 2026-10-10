package de.oejendorferdamm.dammboard.ui.spiel

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/** Emojis, die es schon auf Android 8 gibt. */
private val PARTY = listOf("😂", "😜", "🙈", "🐸", "🦄", "🍌", "🚀", "🎈", "🌈", "🐙", "🐵", "🍕", "🎉", "🐷", "🐔", "🤡", "🤣", "🦆", "🐳", "🍩", "⭐", "👻", "🐶", "🐱", "🦁", "🍦")
private val GUCKER = listOf("🙈", "🐸", "🦄", "🐙", "🐵", "👻")

private enum class Phase { AUS, GUCKT, PARTY }

/**
 * Kleine Überraschung für die Kinder: Ab und zu (nur, wenn eine Weile nicht gemalt wurde) schaut
 * ein Emoji vom Rand herein und wackelt. Wer es antippt, löst 10 Sekunden Emoji-Party aus – die
 * Emojis fliegen durchs Bild und zerplatzen beim Antippen. Danach ist alles wie vorher; die
 * Tafel darunter wird nie verändert.
 *
 * [letzteAenderung] = Zeitpunkt der letzten Änderung an der Tafel (ms), [jetztZeigen] erzwingt
 * sofort einen Gucker (Einstellungen → „Jetzt ausprobieren“).
 */
@Composable
fun EmojiUeberraschung(aktiv: Boolean, letzteAenderung: () -> Long, jetztZeigen: Boolean, onGezeigt: () -> Unit) {
    var phase by remember { mutableStateOf(Phase.AUS) }
    var gucker by remember { mutableStateOf(GUCKER.first()) }
    var rand by remember { mutableStateOf(0) } // 0 links, 1 rechts, 2 unten
    var lage by remember { mutableStateOf(0.5f) }

    fun guckerZeigen() {
        gucker = GUCKER.random()
        rand = Random.nextInt(3)
        lage = Random.nextFloat() * 0.6f + 0.2f
        phase = Phase.GUCKT
    }

    // Planer: alle 20–40 Minuten, wenn mindestens eine Minute niemand gemalt hat
    LaunchedEffect(aktiv) {
        if (!aktiv) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(20 * 60_000L, 40 * 60_000L))
            while (System.currentTimeMillis() - letzteAenderung() < 60_000L) delay(15_000)
            if (phase == Phase.AUS) guckerZeigen()
        }
    }
    LaunchedEffect(jetztZeigen) {
        if (jetztZeigen) {
            guckerZeigen()
            onGezeigt()
        }
    }
    // Nicht angetippt? Nach 20 Sekunden wieder weg.
    LaunchedEffect(phase) {
        if (phase == Phase.GUCKT) {
            delay(20_000)
            if (phase == Phase.GUCKT) phase = Phase.AUS
        }
    }

    when (phase) {
        Phase.AUS -> Unit
        Phase.GUCKT -> Gucker(gucker, rand, lage) { phase = Phase.PARTY }
        Phase.PARTY -> Party { phase = Phase.AUS }
    }
}

@Composable
private fun Gucker(emoji: String, rand: Int, lage: Float, onAntippen: () -> Unit) {
    val wackeln = rememberInfiniteTransition(label = "wackeln")
    val winkel by wackeln.animateFloat(-14f, 14f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "winkel")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dichte = LocalDensity.current
        val breite = with(dichte) { maxWidth.toPx() }
        val hoehe = with(dichte) { maxHeight.toPx() }
        val groesse = min(breite, hoehe) * 0.12f
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
        // Halb hinter dem Rand versteckt
        val x = when (rand) { 0 -> -groesse * 0.35f; 1 -> breite - groesse * 0.65f; else -> breite * lage - groesse / 2 }
        val y = when (rand) { 2 -> hoehe - groesse * 0.65f; else -> hoehe * lage - groesse / 2 }
        Canvas(
            Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .size(with(dichte) { groesse.toDp() })
                .graphicsLayer { rotationZ = winkel }
                .semantics { contentDescription = "Überraschung" }
                .pointerInput(Unit) { detectTapGestures { onAntippen() } }
        ) {
            paint.textSize = size.height * 0.82f
            drawContext.canvas.nativeCanvas.drawText(emoji, size.width / 2, size.height * 0.8f, paint)
        }
    }
}

private class Flieger(var x: Float, var y: Float, var vx: Float, var vy: Float, var dreh: Float, val drehTempo: Float, val emoji: String, val groesse: Float) {
    var geplatzt = -1L
}

/** 10 Sekunden Emoji-Party, danach kurz ausblenden. Fängt alle Berührungen ab (es wird nichts gemalt). */
@Composable
private fun Party(onEnde: () -> Unit) {
    val flieger = remember { ArrayList<Flieger>() }
    var bild by remember { mutableLongStateOf(0L) }
    var deckkraft by remember { mutableStateOf(1f) }
    var flaeche by remember { mutableStateOf(0f to 0f) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var vorher = start
        var naechster = 0L
        while (true) {
            val jetzt = withFrameNanos { it }
            val dt = ((jetzt - vorher) / 1e9f).coerceAtMost(0.05f)
            vorher = jetzt
            val vergangen = (jetzt - start) / 1_000_000L
            val (b, h) = flaeche
            if (b > 0f) {
                // In den ersten 8 Sekunden immer neue Emojis von unten und von den Seiten
                if (vergangen < 8_000 && vergangen >= naechster && flieger.size < 70) {
                    naechster = vergangen + Random.nextLong(60, 160)
                    val g = min(b, h) * Random.nextFloat() * 0.07f + min(b, h) * 0.06f
                    val vonUnten = Random.nextBoolean()
                    flieger.add(
                        if (vonUnten) Flieger(Random.nextFloat() * b, h + g, Random.nextFloat() * 400f - 200f, -(Random.nextFloat() * 500f + h * 0.9f), 0f, Random.nextFloat() * 360f - 180f, PARTY.random(), g)
                        else {
                            val links = Random.nextBoolean()
                            Flieger(if (links) -g else b + g, Random.nextFloat() * h * 0.6f, (if (links) 1 else -1) * (Random.nextFloat() * 300f + b * 0.25f), -Random.nextFloat() * 300f, 0f, Random.nextFloat() * 360f - 180f, PARTY.random(), g)
                        }
                    )
                }
                val schwere = h * 0.55f
                flieger.forEach { f ->
                    f.vy += schwere * dt
                    f.x += f.vx * dt
                    f.y += f.vy * dt
                    f.dreh += f.drehTempo * dt
                    // an den Seiten abprallen – das bringt die Kinder zum Lachen
                    if (f.x < f.groesse / 2 && f.vx < 0) f.vx = -f.vx * 0.9f
                    if (f.x > b - f.groesse / 2 && f.vx > 0) f.vx = -f.vx * 0.9f
                }
                flieger.removeAll { it.y > h + it.groesse * 2 && it.vy > 0 || (it.geplatzt > 0 && vergangen - it.geplatzt > 350) }
            }
            if (vergangen > 10_000) deckkraft = (1f - (vergangen - 10_000) / 600f).coerceIn(0f, 1f)
            if (vergangen > 10_600) break
            bild = vergangen
        }
        onEnde()
    }

    Box(
        Modifier.fillMaxSize().alpha(deckkraft).pointerInput(Unit) {
            detectTapGestures { p ->
                // Antippen lässt das Emoji zerplatzen
                flieger.lastOrNull { it.geplatzt < 0 && hypot(it.x - p.x, it.y - p.y) < it.groesse * 0.7f }?.let { it.geplatzt = bild }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            flaeche = size.width to size.height
            @Suppress("UNUSED_VARIABLE") val takt = bild // bei jedem Bild neu zeichnen
            val c = drawContext.canvas.nativeCanvas
            flieger.forEach { f ->
                val platzt = f.geplatzt > 0
                val skala = if (platzt) 1f + (bild - f.geplatzt) / 120f else 1f
                paint.textSize = f.groesse * skala
                paint.alpha = if (platzt) (255 * (1f - (bild - f.geplatzt) / 350f)).toInt().coerceIn(0, 255) else 255
                c.save()
                c.rotate(f.dreh, f.x, f.y)
                c.drawText(if (platzt) "💥" else f.emoji, f.x, f.y + paint.textSize * 0.35f, paint)
                c.restore()
            }
        }
    }
}
