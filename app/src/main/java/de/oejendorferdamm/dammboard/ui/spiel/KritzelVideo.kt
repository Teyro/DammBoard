package de.oejendorferdamm.dammboard.ui.spiel

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Das Überraschungsvideo liegt nur auf dem Board – nie in der App (fremdes Material).
 * Woher: einmal in den Einstellungen gewählt oder als „ueberraschung.mp4“ im Tafeln-Ordner.
 */
object UeberraschungsVideo {
    const val ORDNER_NAME = "ueberraschung.mp4"
    private const val MAX_GROESSE = 300L * 1024 * 1024

    fun datei(context: Context) = File(context.filesDir, "ueberraschung.mp4")

    fun vorhanden(context: Context) = datei(context).let { it.exists() && it.length() > 0 }

    /** Gewähltes Video in den App-Speicher kopieren (die Freigabe der Dateiauswahl hält nicht ewig). */
    suspend fun uebernehmen(context: Context, quelle: Uri): Boolean = withContext(Dispatchers.IO) {
        val ziel = datei(context)
        val tmp = File(ziel.parentFile, ziel.name + ".tmp")
        try {
            context.contentResolver.openInputStream(quelle)?.use { ein ->
                tmp.outputStream().use { aus ->
                    val puffer = ByteArray(256 * 1024)
                    var gesamt = 0L
                    while (true) {
                        val n = ein.read(puffer)
                        if (n < 0) break
                        gesamt += n
                        if (gesamt > MAX_GROESSE) throw IllegalStateException("Video ist zu groß")
                        aus.write(puffer, 0, n)
                    }
                }
            } ?: return@withContext false
            // Nur übernehmen, was Android als Video abspielen kann
            val ok = try {
                MediaMetadataRetriever().run {
                    try { setDataSource(tmp.absolutePath); extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" } finally { release() }
                }
            } catch (_: Exception) { false }
            if (ok) tmp.renameTo(ziel) else tmp.delete()
            ok
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    /** Liegt im Tafeln-Ordner ein „ueberraschung.mp4“, das wir noch nicht (so) haben? Dann holen. */
    suspend fun ausOrdner(context: Context, baum: Uri?) {
        if (baum == null) return
        withContext(Dispatchers.IO) {
            try {
                val kinder = DocumentsContract.buildChildDocumentsUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum))
                val spalten = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE)
                context.contentResolver.query(kinder, spalten, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        if (!c.getString(1).orEmpty().equals(ORDNER_NAME, ignoreCase = true)) continue
                        val groesse = if (c.isNull(2)) -1L else c.getLong(2)
                        if (vorhanden(context) && datei(context).length() == groesse) return@use
                        uebernehmen(context, DocumentsContract.buildDocumentUriUsingTree(baum, c.getString(0)))
                        return@use
                    }
                }
            } catch (_: Exception) {
                // Ordner gerade nicht erreichbar – dann eben das vorhandene Video (oder keins)
            }
        }
    }

    fun entfernen(context: Context) = datei(context).delete()
}

private enum class KritzelPhase { KRITZELN, PAPIER, VIDEO, WEG }

/**
 * Easter Egg: Eine fremde Hand kritzelt mit dickem Wachsmalstift ein Kästchen auf die Tafel, darin
 * erscheint ein herausgerissenes Stück Papier, auf dem das Überraschungsvideo läuft. Im Modus
 * „Qualität“ wandert und schaukelt das Kästchen dabei über die Tafel, im Modus „Performance“
 * bleibt es stehen. Ist das Video zu Ende (oder tippt jemand darauf), ist alles wieder weg –
 * die Tafel selbst wird nie verändert.
 */
@Composable
fun KritzelVideo(bewegt: Boolean, videoOrdner: Uri?, onFertig: () -> Unit) {
    val context = LocalContext.current
    val fertig by rememberUpdatedState(onFertig)
    var phase by remember { mutableStateOf(KritzelPhase.KRITZELN) }
    val strich = remember { Animatable(0f) }
    val papier = remember { Animatable(0f) }
    var seitenverhaeltnis by remember { mutableFloatStateOf(16f / 9f) }
    var hatVideo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Video (evtl. aus dem Tafeln-Ordner) vorbereiten, während schon gekritzelt wird
        val vorbereitet = async {
            UeberraschungsVideo.ausOrdner(context.applicationContext, videoOrdner)
            UeberraschungsVideo.vorhanden(context)
        }
        strich.animateTo(1f, tween(1500, easing = LinearEasing))
        hatVideo = vorbereitet.await()
        if (hatVideo) withContext(Dispatchers.IO) {
            try {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(UeberraschungsVideo.datei(context).absolutePath)
                        val b = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 0f
                        val h = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 0f
                        val dreh = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        if (b > 0 && h > 0) seitenverhaeltnis = if (dreh == 90 || dreh == 270) h / b else b / h
                    } finally { release() }
                }
            } catch (_: Exception) {
            }
        }
        phase = KritzelPhase.PAPIER
        papier.animateTo(1f, tween(350))
        phase = KritzelPhase.VIDEO
        if (!hatVideo) {
            delay(4500)
            phase = KritzelPhase.WEG
        }
    }
    LaunchedEffect(phase) {
        if (phase == KritzelPhase.WEG) {
            papier.animateTo(0f, tween(300))
            fertig()
        }
    }

    // Wanderbewegung (nur „Qualität“)
    var zeit by remember { mutableFloatStateOf(0f) }
    if (bewegt) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { zeit = (it - start) / 1_000_000_000f }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { phase = KritzelPhase.WEG }
    ) {
        val dichte = LocalDensity.current
        val bPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        // Kästchen: so groß wie möglich bei ~52 % der Breite, passend zum Video
        var kb = bPx * 0.52f
        var kh = kb / seitenverhaeltnis
        if (kh > hPx * 0.62f) { kh = hPx * 0.62f; kb = kh * seitenverhaeltnis }
        val rand = minOf(kb, kh) * 0.07f
        val dx = if (bewegt) sin(zeit * 0.55f) * bPx * 0.13f + sin(zeit * 1.7f) * bPx * 0.012f else 0f
        val dy = if (bewegt) cos(zeit * 0.73f) * hPx * 0.08f else 0f
        val drehung = if (bewegt) sin(zeit * 1.25f) * 3.5f else -1.5f
        val pfade = remember(kb, kh, rand) { kritzelRahmen(kb + 2 * rand, kh + 2 * rand, rand * 0.5f) }
        val links = (bPx - kb) / 2f + dx
        val oben = (hPx - kh) / 2f + dy

        Box(
            Modifier
                .offset { IntOffset((links - rand).roundToInt(), (oben - rand).roundToInt()) }
                .size(with(dichte) { (kb + 2 * rand).toDp() }, with(dichte) { (kh + 2 * rand).toDp() })
                .graphicsLayer { rotationZ = drehung }
        ) {
            // Herausgerissenes Papier
            if (phase != KritzelPhase.KRITZELN) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = papier.value }) {
                    val p = rissPfad(rand * 0.55f, rand * 0.55f, size.width - rand * 1.1f, size.height - rand * 1.1f, rand * 0.35f)
                    translate(rand * 0.12f, rand * 0.2f) { drawPath(p, Color.Black.copy(alpha = 0.28f)) }
                    drawPath(p, Color(0xFFFDFBF4))
                    // ein paar Karo-Linien wie im Schulheft
                    val schritt = rand * 0.9f
                    var x = rand
                    while (x < size.width - rand) {
                        drawLine(Color(0x223A7BD5), Offset(x, rand * 0.7f), Offset(x, size.height - rand * 0.7f), 1.2f)
                        x += schritt
                    }
                    var y = rand
                    while (y < size.height - rand) {
                        drawLine(Color(0x223A7BD5), Offset(rand * 0.7f, y), Offset(size.width - rand * 0.7f, y), 1.2f)
                        y += schritt
                    }
                }
            }
            // Das Video
            if (phase == KritzelPhase.VIDEO || phase == KritzelPhase.WEG) {
                if (hatVideo) {
                    VideoFlaeche(
                        UeberraschungsVideo.datei(context),
                        Modifier.offset { IntOffset(rand.roundToInt(), rand.roundToInt()) }
                            .size(with(dichte) { kb.toDp() }, with(dichte) { kh.toDp() })
                            .graphicsLayer { alpha = papier.value },
                        onEnde = { phase = KritzelPhase.WEG }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Hier läuft gleich das Überraschungsvideo 🙂\nEinstellungen → Erweitert → Überraschungsvideo",
                            color = Color(0xFF3A3A3A), fontSize = 18.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // Wachsmalstift-Kritzelrahmen mit der „fremden Hand“
            Canvas(Modifier.fillMaxSize()) {
                val dick = minOf(size.width, size.height) * 0.035f
                var spitze: Offset? = null
                pfade.forEachIndexed { i, pfad ->
                    val messung = PathMeasure().apply { setPath(pfad, false) }
                    // zwei Durchgänge nacheinander, der zweite etwas versetzt und heller
                    val anteil = ((strich.value * 2f) - i).coerceIn(0f, 1f)
                    if (anteil <= 0f) return@forEachIndexed
                    val teil = Path()
                    messung.getSegment(0f, messung.length * anteil, teil, true)
                    val farbe = if (i == 0) Color(0xE6222222) else Color(0x99404040)
                    drawPath(teil, farbe, style = Stroke(width = if (i == 0) dick else dick * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (anteil < 1f) spitze = messung.getPosition(messung.length * anteil)
                }
                spitze?.let { s ->
                    val schrift = android.graphics.Paint().apply { textSize = dick * 4.5f }
                    drawContext.canvas.nativeCanvas.drawText("✍️", s.x - dick * 0.6f, s.y + dick * 1.2f, schrift)
                }
            }
        }
    }
}

/** Krakeliger Rahmen aus zwei Durchgängen – wie schnell mit dickem Wachsmalstift gezogen. */
private fun kritzelRahmen(b: Float, h: Float, rand: Float): List<Path> {
    val r = Random(42)
    return List(2) { durchgang ->
        val versatz = if (durchgang == 0) 0f else rand * 0.5f
        val ecken = listOf(
            Offset(rand + versatz, rand), Offset(b - rand, rand + versatz),
            Offset(b - rand - versatz, h - rand), Offset(rand, h - rand - versatz), Offset(rand + versatz * 2, rand * 1.3f)
        )
        Path().apply {
            moveTo(ecken[0].x - rand * 0.6f, ecken[0].y + rand * 0.2f)
            for (k in 1 until ecken.size) {
                val a = ecken[k - 1]
                val z = ecken[k]
                val schritte = 9
                for (s in 1..schritte) {
                    val t = s / schritte.toFloat()
                    val wackeln = rand * 0.35f
                    lineTo(a.x + (z.x - a.x) * t + (r.nextFloat() - 0.5f) * wackeln, a.y + (z.y - a.y) * t + (r.nextFloat() - 0.5f) * wackeln)
                }
            }
            // kleiner Überstrich am Ende, wie beim echten Kritzeln
            lineTo(ecken.last().x + rand * 1.2f, ecken.last().y - rand * 0.4f)
        }
    }
}

/** Zackiger Rand ringsum – sieht aus wie aus dem Heft gerissen. */
private fun rissPfad(x: Float, y: Float, b: Float, h: Float, zacke: Float): Path {
    val r = Random(7)
    fun z() = (r.nextFloat() - 0.5f) * zacke
    return Path().apply {
        moveTo(x + z(), y + z())
        var t = 0f
        while (t < b) { t += zacke * (1.2f + r.nextFloat()); lineTo(x + minOf(t, b), y + z()) }
        t = 0f
        while (t < h) { t += zacke * (1.2f + r.nextFloat()); lineTo(x + b + z(), y + minOf(t, h)) }
        t = b
        while (t > 0f) { t -= zacke * (1.2f + r.nextFloat()); lineTo(x + maxOf(t, 0f), y + h + z()) }
        t = h
        while (t > 0f) { t -= zacke * (1.2f + r.nextFloat()); lineTo(x + z(), y + maxOf(t, 0f)) }
        close()
    }
}

/** Video auf einer TextureView – die lässt sich (anders als SurfaceView) mit drehen und verschieben. */
@Composable
private fun VideoFlaeche(datei: File, modifier: Modifier, onEnde: () -> Unit) {
    val ende by rememberUpdatedState(onEnde)
    val spieler = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            try { spieler.stop() } catch (_: Exception) {}
            spieler.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            TextureView(c).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        try {
                            spieler.setSurface(Surface(st))
                            spieler.setDataSource(datei.absolutePath)
                            spieler.setOnCompletionListener { ende() }
                            spieler.setOnErrorListener { _, _, _ -> ende(); true }
                            spieler.setOnPreparedListener { it.start() }
                            spieler.prepareAsync()
                        } catch (_: Exception) {
                            ende()
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        }
    )
}
