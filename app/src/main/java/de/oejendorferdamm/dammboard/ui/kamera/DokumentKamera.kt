package de.oejendorferdamm.dammboard.ui.kamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.oejendorferdamm.dammboard.ui.Arbeitsblaetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dokumentenkamera im Vollbild: Live-Bild der per USB angeschlossenen Kamera. Ist eine USB-Videokamera (UVC) eingesteckt,
 * wird sie direkt angesprochen (Bibliothek UVCAndroid) – viele Boards reichen sie nicht als „externe Kamera“ an Apps weiter.
 * Sonst die Kameras des Systems (Camera2); kommt dort nach 4 s kein Bild, wird auf USB gewechselt. „Aufnahme“ friert ein Standbild ein, „Auf die Tafel“ legt es als Bild auf die
 * aktuelle Seite. Die Tafel bleibt solange im Hintergrund. Gibt es mehrere Kameras, steht die externe vorn.
 */
@Composable
fun DokumentKamera(onAufDieTafel: (datei: String, breite: Int, hoehe: Int) -> Unit, onSchliessen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var erlaubt by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var abgelehnt by remember { mutableStateOf(false) }
    val anfrage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> erlaubt = ok; abgelehnt = !ok }
    LaunchedEffect(Unit) { if (!erlaubt) anfrage.launch(Manifest.permission.CAMERA) }

    val kameras = remember { mutableStateListOf<String>() }
    var gewaehlt by remember { mutableStateOf<String?>(null) }
    var textur by remember { mutableStateOf<TextureView?>(null) }
    var oberflaecheBereit by remember { mutableStateOf(false) }
    var verhaeltnis by remember { mutableFloatStateOf(16f / 9f) }
    var vorschauGroesse by remember { mutableStateOf<Size?>(null) }
    var drehung by remember { mutableIntStateOf(0) }
    var standbild by remember { mutableStateOf<Bitmap?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var speichert by remember { mutableStateOf(false) }
    /** Ecken beim Zuschneiden (null = nicht im Zuschneiden) */
    var zuschnitt by remember { mutableStateOf<List<Punkt>?>(null) }
    var hinweis by remember { mutableStateOf<String?>(null) }
    // Direkt per USB (UVC) statt über das System
    var usb by remember { mutableStateOf(usbKamera(context) != null) }
    var letztesBild by remember { mutableStateOf(0L) }
    // Bildqualität der USB-Kamera (gemerkt): reißt das Bild, eine Stufe niedriger
    val kameraPrefs = remember { context.getSharedPreferences("kamera", Context.MODE_PRIVATE) }
    var stufe by remember { mutableIntStateOf(kameraPrefs.getInt("stufe", 1)) }

    // Kameras auflisten – auch wenn die Dokumentenkamera erst jetzt eingesteckt wird
    DisposableEffect(erlaubt) {
        if (!erlaubt) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        fun neuLesen() {
            val liste = try { sortiereKameras(manager) } catch (e: Exception) { emptyList() }
            kameras.clear()
            kameras.addAll(liste)
            if (gewaehlt !in liste) gewaehlt = liste.firstOrNull()
        }
        val rueckruf = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) = neuLesen()
            override fun onCameraUnavailable(cameraId: String) { /* belegt (z. B. von uns) – Liste bleibt */ }
        }
        neuLesen()
        manager.registerAvailabilityCallback(rueckruf, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterAvailabilityCallback(rueckruf) }
    }

    // Kamera öffnen, sobald Kamera und Fläche da sind (nicht, solange ein Standbild gezeigt wird)
    val id = gewaehlt
    val tv = textur
    // Bei USB nicht an der System-Kamera hängen: Öffnet DammBoard die USB-Kamera, meldet Android das als Änderung der
    // Kameraliste – die Sitzung würde sonst ständig neu starten (Bild flackert und blitzt).
    val schluessel: String? = if (usb) "usb$stufe" else id
    DisposableEffect(schluessel, tv, oberflaecheBereit, erlaubt) {
        if (!erlaubt || tv == null || !oberflaecheBereit) return@DisposableEffect onDispose { }
        val groesse = { g: Size -> vorschauGroesse = g; verhaeltnis = g.width.toFloat() / g.height }
        if (usb) {
            fehler = null
            val s = UsbSitzung(context, tv, stufe, onGroesse = groesse, onFehler = { fehler = it })
            s.starte()
            return@DisposableEffect onDispose { s.schliessen() }
        }
        if (id == null) return@DisposableEffect onDispose { }
        val sitzung = KameraSitzung(context, id, tv, onGroesse = groesse, onFehler = { fehler = it })
        sitzung.starte()
        letztesBild = 0L
        // Kein Bild nach 4 s, aber eine USB-Kamera steckt? Dann direkt per USB.
        val wache = Handler(Looper.getMainLooper())
        val pruefen = Runnable { if (letztesBild == 0L && usbKamera(context) != null) usb = true }
        wache.postDelayed(pruefen, 4000)
        onDispose { wache.removeCallbacks(pruefen); sitzung.schliessen() }
    }

    // USB-Kamera eingesteckt oder abgezogen, während die Kamera offen ist
    DisposableEffect(Unit) {
        val empfaenger = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: android.content.Intent) {
                val da = usbKamera(context) != null
                if (da != usb) { usb = da; fehler = null }
            }
        }
        val filter = android.content.IntentFilter().apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        ContextCompat.registerReceiver(context, empfaenger, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { try { context.unregisterReceiver(empfaenger) } catch (_: Exception) {} }
    }

    fun aufnehmen(): Bitmap? {
        val t = textur ?: return null
        val g = vorschauGroesse
        val roh = (if (g != null) t.getBitmap(g.width, g.height) else t.bitmap) ?: return null
        if (drehung == 0) return roh
        val m = Matrix().apply { postRotate(drehung.toFloat()) }
        return Bitmap.createBitmap(roh, 0, 0, roh.width, roh.height, m, true).also { if (it !== roh) roh.recycle() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        // --- Bild
        Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 130.dp), contentAlignment = Alignment.Center) {
            val bild = standbild
            when {
                !erlaubt -> Hinweis(if (abgelehnt) "Ohne Kamera-Erlaubnis geht es nicht. Bitte in den Android-Einstellungen für DammBoard „Kamera“ erlauben." else "Kamera-Erlaubnis wird angefragt …")
                fehler != null -> Hinweis(fehler!!)
                !usb && kameras.isEmpty() -> Hinweis("Keine Kamera gefunden. Ist die Dokumentenkamera per USB eingesteckt? Manche Boards reichen USB-Kameras nicht an Apps weiter – dann geht es nur mit der Kamera-App des Boards.")
                bild != null && zuschnitt != null -> ZuschneideAnsicht(bild, zuschnitt!!) { zuschnitt = it }
                bild != null -> Image(bild.asImageBitmap(), contentDescription = "Aufnahme", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else -> {
                    val gedreht = drehung % 180 != 0
                    Box(Modifier.aspectRatio(verhaeltnis), contentAlignment = Alignment.Center) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).apply {
                                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) { oberflaecheBereit = true }
                                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean { oberflaecheBereit = false; return true }
                                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) { if (letztesBild == 0L) letztesBild = System.currentTimeMillis() }
                                    }
                                    if (isAvailable) oberflaecheBereit = true
                                    textur = this
                                }
                            },
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                rotationZ = drehung.toFloat()
                                val s = if (gedreht) minOf(verhaeltnis, 1f / verhaeltnis) else 1f
                                scaleX = s; scaleY = s
                            }
                        )
                    }
                }
            }
        }

        // --- Schließen oben rechts
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(20.dp).size(64.dp).clip(CircleShape)
                .background(Color(0xFFEBEBEB)).clickable(role = Role.Button, onClick = onSchliessen),
            contentAlignment = Alignment.Center
        ) { Text("✕", fontSize = 28.sp, color = Color(0xFF2B2B2B)) }

        // --- kurzer Hinweis (z. B. Zauberstab fand kein Blatt)
        hinweis?.let { h ->
            LaunchedEffect(h) { kotlinx.coroutines.delay(4000); hinweis = null }
            Text(
                h, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        // --- Knöpfe unten
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically
        ) {
          val still = standbild
          if (still != null && zuschnitt != null) {
            // Zuschneiden: Ecken ziehen, dann übernehmen (zieht das Bild auch gerade)
            KameraKnopf("✕", "Abbrechen") { zuschnitt = null }
            KameraKnopf("🪄", "Blatt suchen") {
                scope.launch {
                    val e = withContext(Dispatchers.Default) { try { BlattErkennung.finde(still) } catch (_: Exception) { null } }
                    if (e != null) zuschnitt = e else hinweis = "Kein Blatt erkannt – bitte die Ecken von Hand ziehen."
                }
            }
            KameraKnopf("✓", "Übernehmen", hervorgehoben = true) {
                val e = zuschnitt!!
                scope.launch {
                    standbild = withContext(Dispatchers.Default) { try { BlattErkennung.geradeZiehen(still, e) } catch (_: Exception) { still } }
                    zuschnitt = null
                }
            }
          } else {
            if (!usb && kameras.size > 1 && standbild == null) {
                KameraKnopf("🔁", "Kamera wechseln") {
                    val i = kameras.indexOf(gewaehlt)
                    gewaehlt = kameras[(i + 1) % kameras.size]
                }
            }
            // Drehen: im Standbild das Foto selbst, sonst die Vorschau
            if (usb && still == null) {
                KameraKnopf("◐", "Qualität: " + when (stufe) { 2 -> "hoch"; 0 -> "niedrig"; else -> "mittel" }) {
                    stufe = (stufe + 2) % 3 // mittel → niedrig → hoch → mittel
                    kameraPrefs.edit().putInt("stufe", stufe).apply()
                    hinweis = "Reißt das Bild, „niedrig“ wählen – dann braucht die Kamera weniger USB-Bandbreite."
                }
            }
            KameraKnopf("⟳", "Drehen") {
                if (still != null) scope.launch { standbild = withContext(Dispatchers.Default) { BlattErkennung.drehe(still) } }
                else drehung = (drehung + 90) % 360
            }
            if (still != null) {
                KameraKnopf("✂", "Zuschneiden") {
                    val r = 0.06f
                    zuschnitt = listOf(
                        Punkt(still.width * r, still.height * r), Punkt(still.width * (1 - r), still.height * r),
                        Punkt(still.width * (1 - r), still.height * (1 - r)), Punkt(still.width * r, still.height * (1 - r))
                    )
                }
                // Zauberstab: Blatt finden, ausschneiden, gerade ziehen (nahe A4 → genau A4)
                KameraKnopf("🪄", "Zauberstab") {
                    scope.launch {
                        val neu = withContext(Dispatchers.Default) {
                            try { BlattErkennung.finde(still)?.let { BlattErkennung.geradeZiehen(still, it) } } catch (_: Exception) { null }
                        }
                        if (neu != null) standbild = neu else hinweis = "Kein Blatt erkannt – mit „Zuschneiden“ geht es von Hand."
                    }
                }
            }
            if (standbild == null) {
                KameraKnopf("📸", "Aufnahme", hervorgehoben = true, aktiv = erlaubt && (usb || kameras.isNotEmpty()) && fehler == null) {
                    standbild = try { aufnehmen() } catch (e: Exception) { null }
                }
            } else {
                KameraKnopf("↺", "Neu aufnehmen") { standbild = null }
            }
            KameraKnopf("📋", if (speichert) "Wird eingefügt …" else "Auf die Tafel", hervorgehoben = standbild != null, aktiv = !speichert && erlaubt && (usb || kameras.isNotEmpty())) {
                val bild = standbild ?: try { aufnehmen() } catch (e: Exception) { null }
                if (bild != null) {
                    speichert = true
                    scope.launch {
                        val name = try { withContext(Dispatchers.IO) { Arbeitsblaetter.speichereBild(context, bild) } } catch (e: Exception) { null }
                        speichert = false
                        if (name != null) onAufDieTafel(name, bild.width, bild.height) else fehler = "Das Bild konnte nicht gespeichert werden."
                    }
                }
            }
          }
        }
    }
}

/** Standbild mit vier ziehbaren Ecken; außerhalb des Vierecks abgedunkelt. */
@Composable
internal fun ZuschneideAnsicht(bild: Bitmap, ecken: List<Punkt>, onEcken: (List<Punkt>) -> Unit) {
    val bildIb = remember(bild) { bild.asImageBitmap() }
    val aktuell by androidx.compose.runtime.rememberUpdatedState(ecken)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val dichte = androidx.compose.ui.platform.LocalDensity.current
        val bPx = with(dichte) { maxWidth.toPx() }
        val hPx = with(dichte) { maxHeight.toPx() }
        val s = minOf(bPx / bild.width, hPx / bild.height)
        val ox = (bPx - bild.width * s) / 2f
        val oy = (hPx - bild.height * s) / 2f
        var gezogen by remember { mutableIntStateOf(-1) }
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize().pointerInput(bild) {
                detectDragGestures(
                    onDragStart = { p ->
                        // nächste Ecke (großzügig: auf dem Board mit dem Finger)
                        val i = aktuell.indices.minByOrNull { k -> val e = aktuell[k]; kotlin.math.hypot(ox + e.x * s - p.x, oy + e.y * s - p.y) } ?: -1
                        gezogen = if (i >= 0 && kotlin.math.hypot(ox + aktuell[i].x * s - p.x, oy + aktuell[i].y * s - p.y) < 160f) i else -1
                    },
                    onDragEnd = { gezogen = -1 },
                    onDragCancel = { gezogen = -1 }
                ) { change, d ->
                    if (gezogen < 0) return@detectDragGestures
                    change.consume()
                    val e = aktuell[gezogen]
                    val neu = Punkt((e.x + d.x / s).coerceIn(0f, bild.width.toFloat()), (e.y + d.y / s).coerceIn(0f, bild.height.toFloat()))
                    onEcken(aktuell.toMutableList().also { it[gezogen] = neu })
                }
            }
        ) {
            drawImage(
                bildIb, dstOffset = androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize((bild.width * s).toInt(), (bild.height * s).toInt())
            )
            val pfad = androidx.compose.ui.graphics.Path().apply {
                ecken.forEachIndexed { i, e -> if (i == 0) moveTo(ox + e.x * s, oy + e.y * s) else lineTo(ox + e.x * s, oy + e.y * s) }
                close()
            }
            clipPath(pfad, androidx.compose.ui.graphics.ClipOp.Difference) {
                drawRect(Color(0x99000000))
            }
            drawPath(pfad, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
            ecken.forEachIndexed { i, e ->
                val m = androidx.compose.ui.geometry.Offset(ox + e.x * s, oy + e.y * s)
                drawCircle(Color.White, 26.dp.toPx(), m)
                drawCircle(if (i == gezogen) Color(0xFFE01E3C) else Color(0xFF1F2124), 18.dp.toPx(), m)
            }
        }
    }
}

@Composable
private fun Hinweis(text: String) {
    Text(text, color = Color.White, fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(40.dp))
}

@Composable
internal fun KameraKnopf(zeichen: String, titel: String, hervorgehoben: Boolean = false, aktiv: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (hervorgehoben) Color(0xFFE01E3C) else Color.White)
            .clickable(enabled = aktiv, role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp)
            .graphicsLayer { alpha = if (aktiv) 1f else 0.45f },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(zeichen, fontSize = 30.sp, color = if (hervorgehoben) Color.White else Color(0xFF2B2B2B))
        Text(titel, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (hervorgehoben) Color.White else Color(0xFF2B2B2B))
    }
}

/** Eingesteckte USB-Videokamera (UVC: Schnittstelle der Klasse „Video“), sonst null */
internal fun usbKamera(context: Context): UsbDevice? {
    val m = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
    return try {
        m.deviceList.values.firstOrNull { d -> (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO } }
    } catch (_: Exception) { null }
}

/**
 * Vorschau direkt über USB (UVCAndroid): Gerät wählen → Android fragt nach dem USB-Zugriff → Kamera öffnen →
 * Vorschau auf die TextureView. Standbild wie bei Camera2 über TextureView.getBitmap.
 */
private class UsbSitzung(
    private val context: Context,
    private val textur: TextureView,
    /** 0 = niedrig, 1 = mittel, 2 = hoch */
    private val stufe: Int,
    private val onGroesse: (Size) -> Unit,
    private val onFehler: (String) -> Unit
) {
    private var helfer: com.herohan.uvcapp.CameraHelper? = null
    private var gewaehlt = false
    private val haupt = Handler(Looper.getMainLooper())

    fun starte() {
        try {
            com.serenegiant.utils.UVCUtils.init(context.applicationContext as android.app.Application)
            val h = com.herohan.uvcapp.CameraHelper()
            helfer = h
            h.setStateCallback(object : com.herohan.uvcapp.ICameraHelper.StateCallback {
                override fun onAttach(device: UsbDevice) { waehle(device) }
                override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
                    val hh = helfer ?: return
                    // MJPEG bis zur gewählten Stufe (Hoch: Full HD, Mittel: 1280×720, Niedrig: 640×480). Mit „Bandbreite festlegen“:
                    // sonst fordert die Kamera mehr USB-Bandbreite an, als das Board (Touchrahmen am selben Bus) hergibt –
                    // das Bild steht kurz und reißt dann in Streifen.
                    val grenze = when (stufe) { 2 -> 1920 * 1080; 0 -> 640 * 480; else -> 1280 * 720 }
                    val liste = try { hh.supportedSizeList.orEmpty() } catch (_: Exception) { emptyList() }
                    val beste = liste.filter { it.type == com.serenegiant.usb.UVCCamera.UVC_VS_FRAME_MJPEG && it.width * it.height <= grenze }.maxByOrNull { it.width * it.height }
                        ?: liste.filter { it.width * it.height <= 640 * 480 }.maxByOrNull { it.width * it.height } // unkomprimiert nur klein
                    val quirk = com.serenegiant.usb.UVCCamera.UVC_QUIRK_FIX_BANDWIDTH
                    try {
                        if (beste != null) hh.openCamera(com.serenegiant.usb.UVCParam(beste, quirk)) else hh.openCamera()
                    } catch (_: Throwable) {
                        if (beste != null) hh.openCamera(beste) else hh.openCamera()
                    }
                }
                override fun onCameraOpen(device: UsbDevice) {
                    val hh = helfer ?: return
                    hh.startPreview()
                    hh.previewSize?.let { s -> haupt.post { onGroesse(Size(s.width, s.height)) } }
                    textur.surfaceTexture?.let { hh.addSurface(it, false) }
                }
                override fun onCameraClose(device: UsbDevice) { textur.surfaceTexture?.let { try { helfer?.removeSurface(it) } catch (_: Exception) {} } }
                override fun onDeviceClose(device: UsbDevice) {}
                override fun onDetach(device: UsbDevice) { gewaehlt = false; haupt.post { onFehler("Die Kamera wurde getrennt. Bitte wieder einstecken.") } }
                override fun onCancel(device: UsbDevice) { gewaehlt = false; haupt.post { onFehler("Ohne USB-Zugriff geht es nicht. Bitte die Kamera abziehen, wieder einstecken und beim Nachfragen „OK“ tippen (am besten mit Haken bei „Immer“).") } }
                override fun onError(device: UsbDevice, e: com.herohan.uvcapp.CameraException) { haupt.post { onFehler("Die USB-Kamera meldet einen Fehler: ${e.message ?: "unbekannt"}") } }
            })
            // schon eingesteckt? Dann gleich wählen (onAttach kommt nicht auf jedem Gerät für bereits angeschlossene Kameras)
            haupt.postDelayed({ usbKamera(context)?.let { waehle(it) } }, 300)
        } catch (e: Throwable) {
            onFehler("Die USB-Kamera lässt sich nicht starten: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun waehle(d: UsbDevice) {
        if (gewaehlt) return
        if ((0 until d.interfaceCount).none { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }) return
        gewaehlt = true
        try { helfer?.selectDevice(d) } catch (e: Exception) { gewaehlt = false; onFehler("Die USB-Kamera lässt sich nicht wählen: ${e.message}") }
    }

    fun schliessen() {
        try { helfer?.release() } catch (_: Throwable) {}
        helfer = null
    }
}

/** Externe (USB) Kameras zuerst, dann die übrigen. */
private fun sortiereKameras(manager: CameraManager): List<String> =
    manager.cameraIdList.sortedBy { id ->
        when (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_EXTERNAL -> 0
            CameraCharacteristics.LENS_FACING_BACK -> 1
            else -> 2
        }
    }

/** Eine laufende Vorschau mit Camera2 (ohne Zusatzbibliothek). */
private class KameraSitzung(
    private val context: Context,
    private val id: String,
    private val textur: TextureView,
    private val onGroesse: (Size) -> Unit,
    private val onFehler: (String) -> Unit
) {
    private val haupt = Handler(Looper.getMainLooper())
    private var geraet: CameraDevice? = null
    private var sitzung: CameraCaptureSession? = null
    private var geschlossen = false

    @SuppressLint("MissingPermission")
    fun starte() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val groesse = try {
            val karte = manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val alle = karte?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            // möglichst Full HD (Dokumentenkameras liefern oft mehr – das bremst nur), sonst die größte
            alle.filter { it.width * it.height <= 1920 * 1080 }.maxByOrNull { it.width * it.height } ?: alle.maxByOrNull { it.width * it.height }
        } catch (e: Exception) { null } ?: Size(1280, 720)
        onGroesse(groesse)
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (geschlossen) { camera.close(); return }
                    geraet = camera
                    vorschau(camera, groesse)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); geraet = null; if (!geschlossen) onFehler("Die Kamera wurde getrennt. Bitte wieder einstecken.") }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); geraet = null; if (!geschlossen) onFehler("Die Kamera lässt sich nicht öffnen (Fehler $error). Ist sie in einer anderen App offen?") }
            }, haupt)
        } catch (e: Exception) {
            onFehler("Die Kamera lässt sich nicht öffnen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    @Suppress("DEPRECATION")
    private fun vorschau(camera: CameraDevice, groesse: Size) {
        val st = textur.surfaceTexture ?: return
        st.setDefaultBufferSize(groesse.width, groesse.height)
        val flaeche = Surface(st)
        try {
            camera.createCaptureSession(listOf(flaeche), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (geschlossen) { s.close(); return }
                    sitzung = s
                    try {
                        // Autofokus nur, wenn die Kamera ihn kann – manche USB-Kameras liefern sonst gar kein Bild
                        val afModi = try {
                            (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(id).get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                        } catch (_: Exception) { null }
                        val anfrage = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(flaeche)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            if (afModi?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true) set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        s.setRepeatingRequest(anfrage.build(), null, haupt)
                    } catch (e: Exception) {
                        onFehler("Die Vorschau startet nicht: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { onFehler("Die Kamera unterstützt diese Vorschau nicht.") }
            }, haupt)
        } catch (e: Exception) {
            onFehler("Die Vorschau startet nicht: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun schliessen() {
        geschlossen = true
        try { sitzung?.close() } catch (_: Exception) {}
        try { geraet?.close() } catch (_: Exception) {}
        sitzung = null
        geraet = null
    }
}
