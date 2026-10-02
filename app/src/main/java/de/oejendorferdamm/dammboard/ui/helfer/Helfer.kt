package de.oejendorferdamm.dammboard.ui.helfer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.oejendorferdamm.dammboard.ui.HelferArt
import de.oejendorferdamm.dammboard.ui.TafelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Alle offenen Helfer über der Tafel. Jeder lässt sich an seiner Titelzeile verschieben und
 * über das X schließen; die Tafel darunter bleibt unverändert.
 */
@Composable
fun HelferEbene(state: TafelState) {
    val dichte = LocalDensity.current
    val positionen = remember { mutableStateMapOf<HelferArt, Offset>() }
    Box(Modifier.fillMaxSize()) {
        state.offeneHelfer.forEachIndexed { index, art ->
            key(art) {
                val position = positionen.getOrPut(art) {
                    with(dichte) { Offset((140 + 70 * index).dp.toPx(), (70 + 50 * index).dp.toPx()) }
                }
                val verschieben: (Offset) -> Unit = { delta -> positionen[art] = (positionen[art] ?: Offset.Zero) + delta }
                val schliessen: () -> Unit = { state.offeneHelfer.remove(art) }
                val modifier = Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                when (art) {
                    HelferArt.TIMER -> TimerHelfer(verschieben, schliessen, modifier)
                    HelferArt.WUERFEL -> WuerfelHelfer(verschieben, schliessen, modifier)
                    HelferArt.ZUFALLSNAME -> ZufallsnameHelfer(verschieben, schliessen, modifier)
                    HelferArt.GRUPPEN -> GruppenHelfer(verschieben, schliessen, modifier)
                    HelferArt.LAUTSTAERKE -> LautstaerkeHelfer(verschieben, schliessen, modifier)
                    HelferArt.LERNUHR -> LernuhrHelfer(verschieben, schliessen, modifier)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Timer

private val TimerRot = Color(0xFFE33B3B)

@Composable
private fun TimerHelfer(onVerschieben: (Offset) -> Unit, onSchliessen: () -> Unit, modifier: Modifier) {
    var gesamt by remember { mutableIntStateOf(5 * 60) }
    var rest by remember { mutableIntStateOf(5 * 60) }
    var laeuft by remember { mutableStateOf(false) }
    var abgelaufen by remember { mutableStateOf(false) }
    var ende by remember { mutableLongStateOf(0L) }

    LaunchedEffect(laeuft) {
        if (!laeuft) return@LaunchedEffect
        ende = System.currentTimeMillis() + rest * 1000L
        while (isActive && laeuft) {
            val uebrig = ((ende - System.currentTimeMillis() + 999) / 1000).toInt()
            rest = uebrig.coerceAtLeast(0)
            if (rest <= 0) {
                abgelaufen = true
                laeuft = false
                break
            }
            delay(200)
        }
    }
    // Abgelaufen: Karte blinkt, bis jemand tippt.
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(abgelaufen) {
        if (abgelaufen) launch { spieleSignal() }
        while (abgelaufen) {
            blink = !blink
            delay(500)
        }
        blink = false
    }

    fun setze(sekunden: Int) {
        laeuft = false
        abgelaufen = false
        gesamt = sekunden.coerceIn(10, 99 * 60)
        rest = gesamt
    }

    HelferKarte(
        "Timer", onVerschieben, onSchliessen, modifier,
        hintergrund = if (blink) Color(0xFFFFD6D6) else HelferFlaeche
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(420.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp).clickable { abgelaufen = false }) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    drawCircle(Color.White, radius = r)
                    // Rote Scheibe wie beim Time Timer: zeigt die restliche Zeit.
                    val anteil = if (gesamt > 0) rest.toFloat() / gesamt else 0f
                    drawArc(TimerRot, -90f, 360f * anteil, useCenter = true, topLeft = Offset(r * 0.08f, r * 0.08f), size = Size(r * 1.84f, r * 1.84f))
                    drawCircle(HelferText, radius = r, style = Stroke(width = r * 0.03f))
                }
                Text(
                    "%d:%02d".format(rest / 60, rest % 60),
                    color = HelferText, fontSize = 56.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 12.dp)
                )
            }
            Abstand(14)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 5, 10, 15).forEach { minuten -> HelferTaste("$minuten'") { setze(minuten * 60) } }
            }
            Abstand(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HelferTaste("−1 min", aktiviert = !laeuft) { setze(rest - 60) }
                HelferTaste("+1 min", aktiviert = !laeuft) { setze(rest + 60) }
                HelferTaste(if (laeuft) "Pause" else "Start", hervorgehoben = true, aktiviert = rest > 0) {
                    abgelaufen = false
                    laeuft = !laeuft
                }
                HelferTaste("↺") { setze(gesamt) }
            }
        }
    }
}

private suspend fun spieleSignal() = withContext(Dispatchers.Default) {
    val ton = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (e: RuntimeException) {
        // Kein Ton möglich (z. B. Audio belegt) – die blinkende Karte reicht dann.
        return@withContext
    }
    try {
        repeat(3) {
            ton.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450)
            delay(700)
        }
    } finally {
        ton.release()
    }
}

// ------------------------------------------------------------------ Würfel

@Composable
private fun WuerfelHelfer(onVerschieben: (Offset) -> Unit, onSchliessen: () -> Unit, modifier: Modifier) {
    var anzahl by remember { mutableIntStateOf(1) }
    val werte = remember { mutableStateListOf(6, 6, 6) }
    var wuerfelt by remember { mutableIntStateOf(0) }

    LaunchedEffect(wuerfelt) {
        if (wuerfelt == 0) return@LaunchedEffect
        repeat(9) {
            for (i in 0 until 3) werte[i] = Random.nextInt(1, 7)
            delay(55L + it * 12L)
        }
    }

    HelferKarte("Würfel", onVerschieben, onSchliessen, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.clickable(role = Role.Button) { wuerfelt++ }
            ) {
                for (i in 0 until anzahl) Wuerfel(werte[i], Modifier.size(140.dp))
            }
            if (anzahl > 1) {
                Abstand(8)
                Text("Summe: ${werte.take(anzahl).sum()}", color = HelferText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            Abstand(14)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ZahlWahl(anzahl, 1..3, "Anzahl") { anzahl = it }
                HelferTaste("Würfeln", hervorgehoben = true) { wuerfelt++ }
            }
        }
    }
}

@Composable
private fun Wuerfel(wert: Int, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        drawRoundRect(Color.White, cornerRadius = CornerRadius(s * 0.16f))
        drawRoundRect(HelferText, cornerRadius = CornerRadius(s * 0.16f), style = Stroke(width = s * 0.03f))
        val a = s * 0.25f
        val m = s * 0.5f
        val e = s * 0.75f
        val punkte = when (wert) {
            1 -> listOf(m to m)
            2 -> listOf(a to a, e to e)
            3 -> listOf(a to a, m to m, e to e)
            4 -> listOf(a to a, e to a, a to e, e to e)
            5 -> listOf(a to a, e to a, m to m, a to e, e to e)
            else -> listOf(a to a, e to a, a to m, e to m, a to e, e to e)
        }
        punkte.forEach { (x, y) -> drawCircle(HelferText, radius = s * 0.085f, center = Offset(x, y)) }
    }
}

// ------------------------------------------------------------------ Namensliste

@Composable
private fun NamenBearbeiten(onFertig: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(Klassenliste.namen.joinToString("\n")) }
    Column(Modifier.width(460.dp)) {
        Text("Ein Name pro Zeile. Die Liste bleibt nur auf diesem Board gespeichert.", color = HelferTextSchwach, fontSize = 17.sp)
        Abstand(8)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.width(460.dp).height(320.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, color = HelferText),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HelferAkzent, cursorColor = HelferAkzent)
        )
        Abstand(10)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HelferTaste("Abbrechen", onClick = onFertig)
            HelferTaste("Speichern", hervorgehoben = true) {
                Klassenliste.speichere(context, text)
                onFertig()
            }
        }
    }
}

@Composable
private fun ZufallsnameHelfer(onVerschieben: (Offset) -> Unit, onSchliessen: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Klassenliste.lade(context) }
    var bearbeiten by remember { mutableStateOf(false) }
    var ohneWiederholung by remember { mutableStateOf(true) }
    val schonGezogen = remember { mutableStateListOf<String>() }
    var anzeige by remember { mutableStateOf<String?>(null) }
    var ziehen by remember { mutableIntStateOf(0) }

    val namen = Klassenliste.namen
    val verfuegbar = if (ohneWiederholung) namen.filter { it !in schonGezogen } else namen.toList()

    LaunchedEffect(ziehen) {
        if (ziehen == 0) return@LaunchedEffect
        val auswahl = (if (ohneWiederholung) namen.filter { it !in schonGezogen } else namen.toList())
        if (auswahl.isEmpty()) return@LaunchedEffect
        // Kurz "durchrattern" wie bei einer Lostrommel.
        repeat(12) {
            anzeige = namen[Random.nextInt(namen.size)]
            delay(50L + it * 10L)
        }
        val gewaehlt = auswahl[Random.nextInt(auswahl.size)]
        anzeige = gewaehlt
        if (ohneWiederholung) schonGezogen.add(gewaehlt)
    }

    HelferKarte("Zufallsname", onVerschieben, onSchliessen, modifier) {
        if (bearbeiten) {
            NamenBearbeiten { bearbeiten = false }
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(460.dp)) {
            Box(
                Modifier.width(460.dp).height(130.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        namen.isEmpty() -> "Noch keine Namen"
                        anzeige == null -> "?"
                        else -> anzeige!!
                    },
                    color = HelferText, fontSize = if (namen.isEmpty()) 26.sp else 50.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 1
                )
            }
            Abstand(12)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                HelferTaste("Ziehen", hervorgehoben = true, aktiviert = verfuegbar.isNotEmpty()) { ziehen++ }
                HelferTaste("Namen bearbeiten") { bearbeiten = true }
            }
            Abstand(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Jeden nur einmal", color = HelferTextSchwach, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
                Switch(
                    checked = ohneWiederholung,
                    onCheckedChange = { ohneWiederholung = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = HelferAkzent)
                )
                if (ohneWiederholung && namen.isNotEmpty()) {
                    Text("  noch ${verfuegbar.size} von ${namen.size}", color = HelferTextSchwach, fontSize = 18.sp)
                    if (schonGezogen.isNotEmpty()) {
                        Text(
                            "  Neu starten", color = HelferAkzent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { schonGezogen.clear(); anzeige = null }.padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Gruppen

@Composable
private fun GruppenHelfer(onVerschieben: (Offset) -> Unit, onSchliessen: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Klassenliste.lade(context) }
    var bearbeiten by remember { mutableStateOf(false) }
    var anzahl by remember { mutableIntStateOf(4) }
    var gruppen by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    HelferKarte("Gruppen", onVerschieben, onSchliessen, modifier) {
        if (bearbeiten) {
            NamenBearbeiten { bearbeiten = false }
        } else Column(modifier = Modifier.widthIn(min = 460.dp, max = 980.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ZahlWahl(anzahl, 2..10, "Gruppen") { anzahl = it }
                HelferTaste("Einteilen", hervorgehoben = true, aktiviert = Klassenliste.namen.size >= 2) {
                    val gemischt = Klassenliste.namen.shuffled()
                    gruppen = List(anzahl) { g -> gemischt.filterIndexed { i, _ -> i % anzahl == g } }.filter { it.isNotEmpty() }
                }
                HelferTaste("Namen") { bearbeiten = true }
            }
            if (Klassenliste.namen.isEmpty()) {
                Abstand(10)
                Text("Zuerst unter „Namen“ die Klassenliste eintragen.", color = HelferTextSchwach, fontSize = 18.sp)
            }
            if (gruppen.isNotEmpty()) {
                Abstand(14)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                ) {
                    gruppen.withIndex().chunked(5).forEach { zeile ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            zeile.forEach { (nr, mitglieder) ->
                                Column(
                                    Modifier.width(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp)
                                ) {
                                    Text("Gruppe ${nr + 1}", color = HelferAkzent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    mitglieder.forEach { Text(it, color = HelferText, fontSize = 19.sp, maxLines = 1) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Lautstärke-Ampel

private val AmpelRot = Color(0xFFE33B3B)
private val AmpelGelb = Color(0xFFF5B800)
private val AmpelGruen = Color(0xFF3FAE3A)

@Composable
private fun LautstaerkeHelfer(onVerschieben: (Offset) -> Unit, onSchliessen: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var erlaubt by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var gefragt by remember { mutableStateOf(false) }
    val anfrage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt = it }
    LaunchedEffect(Unit) {
        if (!erlaubt && !gefragt) {
            gefragt = true
            anfrage.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var pegel by remember { mutableFloatStateOf(0f) }
    var grenze by remember { mutableFloatStateOf(0.6f) }
    var mikrofonFehlt by remember { mutableStateOf(false) }
    // Ohne Mikrofon: Ampel von Hand schalten (0 grün, 1 gelb, 2 rot).
    var handStufe by remember { mutableIntStateOf(0) }
    var rotSeit by remember { mutableLongStateOf(0L) }

    if (erlaubt) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val rate = 16000
                val puffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (puffer <= 0) {
                    mikrofonFehlt = true
                    return@withContext
                }
                val aufnahme = try {
                    @Suppress("MissingPermission")
                    AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, puffer * 2)
                } catch (e: Exception) {
                    null
                }
                if (aufnahme == null || aufnahme.state != AudioRecord.STATE_INITIALIZED) {
                    aufnahme?.release()
                    mikrofonFehlt = true
                    return@withContext
                }
                val daten = ShortArray(puffer)
                try {
                    aufnahme.startRecording()
                    var geglaettet = 0f
                    while (isActive) {
                        val n = aufnahme.read(daten, 0, daten.size)
                        if (n <= 0) continue
                        var summe = 0.0
                        for (i in 0 until n) summe += daten[i].toDouble() * daten[i]
                        val rms = sqrt(summe / n)
                        val db = if (rms < 1) -90.0 else 20 * log10(rms / 32768.0)
                        // −60 dB (sehr leise) … 0 dB (ganz laut) auf 0…1
                        val wert = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
                        geglaettet = if (wert > geglaettet) geglaettet * 0.5f + wert * 0.5f else geglaettet * 0.9f + wert * 0.1f
                        pegel = geglaettet
                    }
                } finally {
                    try { aufnahme.stop() } catch (_: Exception) {}
                    aufnahme.release()
                }
            }
        }
    }

    val automatisch = erlaubt && !mikrofonFehlt
    val stufeJetzt = when {
        !automatisch -> handStufe
        pegel >= grenze -> 2
        pegel >= grenze * 0.82f -> 1
        else -> 0
    }
    // Rot bleibt kurz stehen, damit die Ampel nicht flackert.
    val jetzt = System.currentTimeMillis()
    if (stufeJetzt == 2) rotSeit = jetzt
    val stufe = if (automatisch && stufeJetzt < 2 && jetzt - rotSeit < 1500) 2 else stufeJetzt

    HelferKarte("Lautstärke", onVerschieben, onSchliessen, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFF2B2B2B)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(AmpelRot to 2, AmpelGelb to 1, AmpelGruen to 0).forEach { (farbe, nr) ->
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (stufe == nr) farbe else farbe.copy(alpha = 0.18f))
                            .clickable(enabled = !automatisch) { handStufe = nr }
                    )
                }
            }
            Column(Modifier.width(280.dp)) {
                if (automatisch) {
                    Text("Lautstärke im Raum", color = HelferTextSchwach, fontSize = 18.sp)
                    Abstand(6)
                    Canvas(Modifier.width(280.dp).height(26.dp)) {
                        drawRoundRect(HelferKnopf, cornerRadius = CornerRadius(size.height / 2))
                        drawRoundRect(
                            when (stufe) { 2 -> AmpelRot; 1 -> AmpelGelb; else -> AmpelGruen },
                            size = Size(size.width * pegel, size.height), cornerRadius = CornerRadius(size.height / 2)
                        )
                        val x = size.width * grenze
                        drawLine(HelferText, Offset(x, -4f), Offset(x, size.height + 4f), strokeWidth = 4f, cap = StrokeCap.Round)
                    }
                    Abstand(16)
                    Text("Erlaubte Lautstärke", color = HelferTextSchwach, fontSize = 18.sp)
                    Slider(
                        value = grenze, onValueChange = { grenze = it }, valueRange = 0.25f..0.95f,
                        colors = SliderDefaults.colors(thumbColor = HelferAkzent, activeTrackColor = HelferAkzent)
                    )
                    Text("Leise ← → Laut", color = HelferTextSchwach, fontSize = 16.sp)
                } else {
                    Text(
                        if (mikrofonFehlt) "Kein Mikrofon gefunden – die Ampel lässt sich von Hand schalten: einfach auf eine Lampe tippen."
                        else "Ohne Mikrofon-Erlaubnis schaltest du die Ampel von Hand: einfach auf eine Lampe tippen.",
                        color = HelferText, fontSize = 19.sp
                    )
                    if (!erlaubt && !mikrofonFehlt) {
                        Abstand(12)
                        HelferTaste("Mikrofon erlauben", hervorgehoben = true) { anfrage.launch(Manifest.permission.RECORD_AUDIO) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Lernuhr

private val uhrPinsel = android.graphics.Paint().apply {
    isAntiAlias = true
    textAlign = android.graphics.Paint.Align.CENTER
    isFakeBoldText = true
}

@Composable
private fun LernuhrHelfer(onVerschieben: (Offset) -> Unit, onSchliessen: () -> Unit, modifier: Modifier) {
    // Minuten seit 0:00 (0…1439). Ziehen am Zifferblatt stellt den Minutenzeiger, der
    // Stundenzeiger läuft wie bei einer echten Uhr mit.
    var minuten by remember {
        mutableIntStateOf(Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) })
    }
    var digital by remember { mutableStateOf(false) }

    HelferKarte("Lernuhr", onVerschieben, onSchliessen, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .size(380.dp)
                    .pointerInput(Unit) {
                        var letzteMinute = -1
                        detectDragGestures(
                            onDragStart = { letzteMinute = -1 },
                            onDrag = { change, _ ->
                                change.consume()
                                val mitte = Offset(size.width / 2f, size.height / 2f)
                                val p = change.position - mitte
                                val winkel = (Math.toDegrees(atan2(p.x.toDouble(), -p.y.toDouble())) + 360) % 360
                                val minute = (winkel / 6).roundToInt() % 60
                                if (letzteMinute >= 0) {
                                    var diff = minute - letzteMinute
                                    if (diff > 30) diff -= 60
                                    if (diff < -30) diff += 60
                                    minuten = ((minuten + diff) % 1440 + 1440) % 1440
                                } else {
                                    // Erster Kontakt: Minutenzeiger springt dorthin.
                                    val stunde = minuten / 60
                                    minuten = stunde * 60 + minute
                                }
                                letzteMinute = minute
                            }
                        )
                    }
            ) {
                val r = size.minDimension / 2
                val m = center
                drawCircle(Color.White, radius = r)
                drawCircle(HelferText, radius = r, style = Stroke(width = r * 0.035f))
                for (i in 0 until 60) {
                    val w = Math.toRadians(i * 6.0)
                    val innen = if (i % 5 == 0) r * 0.86f else r * 0.92f
                    drawLine(
                        HelferText,
                        Offset(m.x + innen * sin(w).toFloat(), m.y - innen * cos(w).toFloat()),
                        Offset(m.x + r * 0.97f * sin(w).toFloat(), m.y - r * 0.97f * cos(w).toFloat()),
                        strokeWidth = if (i % 5 == 0) r * 0.025f else r * 0.01f
                    )
                }
                uhrPinsel.textSize = r * 0.16f
                uhrPinsel.color = HelferText.toArgb()
                for (h in 1..12) {
                    val w = Math.toRadians(h * 30.0)
                    drawContext.canvas.nativeCanvas.drawText(
                        "$h", m.x + r * 0.72f * sin(w).toFloat(), m.y - r * 0.72f * cos(w).toFloat() + uhrPinsel.textSize * 0.36f, uhrPinsel
                    )
                }
                val minWinkel = Math.toRadians((minuten % 60) * 6.0)
                val stdWinkel = Math.toRadians(((minuten / 60) % 12) * 30.0 + (minuten % 60) * 0.5)
                drawLine(HelferText, m, Offset(m.x + r * 0.5f * sin(stdWinkel).toFloat(), m.y - r * 0.5f * cos(stdWinkel).toFloat()), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
                drawLine(Color(0xFF1E3AA8), m, Offset(m.x + r * 0.78f * sin(minWinkel).toFloat(), m.y - r * 0.78f * cos(minWinkel).toFloat()), strokeWidth = r * 0.035f, cap = StrokeCap.Round)
                drawCircle(HelferText, radius = r * 0.05f, center = m)
            }
            Abstand(10)
            Text(
                if (digital) "%d:%02d Uhr".format(minuten / 60, minuten % 60) else " ",
                color = HelferText, fontSize = 34.sp, fontWeight = FontWeight.Bold
            )
            Abstand(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HelferTaste("−1 h") { minuten = (minuten + 1440 - 60) % 1440 }
                HelferTaste("+1 h") { minuten = (minuten + 60) % 1440 }
                HelferTaste(if (digital) "Zeit verdecken" else "Zeit zeigen") { digital = !digital }
                HelferTaste("Jetzt") {
                    minuten = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
                }
            }
        }
    }
}

