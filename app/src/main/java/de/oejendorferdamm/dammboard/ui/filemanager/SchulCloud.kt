package de.oejendorferdamm.dammboard.ui.filemanager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammboard.data.IServClient
import de.oejendorferdamm.dammboard.data.Nextcloud
import de.oejendorferdamm.dammboard.model.IServEintrag
import de.oejendorferdamm.dammboard.model.IServZugang
import de.oejendorferdamm.dammboard.ui.Arbeitsblaetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Request

private val Grund = Color(0xFFECEEF1)
private val Schrift1 = Color(0xFF1F2124)
private val Schrift2 = Color(0xFF6B7078)
private val Akzent = Color(0xFFE01E3C)
private val BILD = Regex("(?i).+\\.(png|jpe?g|webp)$")

/**
 * Bilder aus der Schul-Cloud öffnen: durch Fächer und Klassen klicken (cleverTouch/<Fach>/Klasse 4c), Vorschaubilder sehen,
 * antippen legt das Bild auf die Tafel. Zugänge kommen aus DammTable (wie beim Ablegen nach jeder Stunde).
 */
@Composable
fun SchulCloud(board: String, onBild: (datei: String, breite: Int, hoehe: Int) -> Unit, onSchliessen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val schule = remember { Nextcloud.schule(context, board) }
    val wurzel = remember(schule) { schule?.pfad?.substringBefore("{fach}")?.trim('/')?.ifBlank { null } ?: "cleverTouch" }
    val client = remember(schule) { schule?.let { IServClient(IServZugang(Nextcloud.davAdresse(it.server, it.benutzer), it.benutzer, it.passwort)) } }
    var pfad by remember { mutableStateOf(wurzel) }
    var eintraege by remember { mutableStateOf<List<IServEintrag>>(emptyList()) }
    var laedt by remember { mutableStateOf(false) }
    var meldung by remember { mutableStateOf<String?>(null) }
    val vorschau = remember { mutableStateMapOf<String, Bitmap?>() }
    val sperre = remember { Semaphore(3) }

    LaunchedEffect(pfad, client) {
        val c = client ?: return@LaunchedEffect
        laedt = true; meldung = null
        val r = c.liste(pfad)
        eintraege = r.getOrNull().orEmpty().filter { it.istOrdner || BILD.matches(it.name) }
            .sortedWith(compareBy<IServEintrag>({ !it.istOrdner }, { if (it.istOrdner) it.name.lowercase() else "" }).thenByDescending { if (it.istOrdner) "" else it.name })
        if (r.isFailure) meldung = "Ordner nicht lesbar: ${r.exceptionOrNull()?.message}"
        laedt = false
    }

    /** Vorschau: aus dem Speicher (einmal am Tag aufgefrischt), sonst klein von Nextcloud (core/preview), sonst das ganze Bild verkleinert */
    suspend fun ladeVorschau(e: IServEintrag): Bitmap? = withContext(Dispatchers.IO) {
        val s = schule ?: return@withContext null
        val rel = "/" + pfad.trim('/') + "/" + e.name
        de.oejendorferdamm.dammboard.data.VorschauCache.lies(context, rel)
            ?: de.oejendorferdamm.dammboard.data.VorschauCache.holen(s, rel)?.let { b ->
                de.oejendorferdamm.dammboard.data.VorschauCache.schreibe(context, rel, b); BitmapFactory.decodeByteArray(b, 0, b.size)
            }
            ?: client?.herunterladen(pfad.trim('/') + "/" + e.name)?.getOrNull()?.let { b ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(b, 0, b.size, o)
            BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = maxOf(1, o.outWidth / 360) })
        }
    }

    fun oeffne(e: IServEintrag) {
        val c = client ?: return
        laedt = true
        scope.launch {
            val ergebnis = withContext(Dispatchers.IO) {
                val b = c.herunterladen(pfad.trim('/') + "/" + e.name).getOrNull() ?: return@withContext null
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(b, 0, b.size, o)
                // höchstens etwa 2560 Pixel breit – größer bringt auf der Tafel nichts und kostet Speicher
                val bild = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = maxOf(1, o.outWidth / 2560) }) ?: return@withContext null
                Triple(Arbeitsblaetter.speichereBild(context, bild), bild.width, bild.height).also { bild.recycle() }
            }
            laedt = false
            if (ergebnis != null) onBild(ergebnis.first, ergebnis.second, ergebnis.third) else meldung = "Das Bild ließ sich nicht laden."
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { onSchliessen() }.systemBarsPadding()) {
        Column(
            Modifier.align(Alignment.Center).fillMaxSize().padding(36.dp).shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(Grund).clickable(remember { MutableInteractionSource() }, null) {}.padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (pfad != wurzel) {
                    Box(Modifier.size(44.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White).clickable { pfad = pfad.substringBeforeLast('/', wurzel) }
                        .semantics { contentDescription = "Zurück" }, contentAlignment = Alignment.Center) { Text("‹", color = Schrift1, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Schul-Cloud", color = Schrift1, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(pfad.removePrefix(wurzel).trim('/').replace("/", " › ").ifBlank { "Fächer" }, color = Schrift2, fontSize = 14.sp)
                }
                if (laedt) CircularProgressIndicator(color = Akzent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(44.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White).clickable(onClick = onSchliessen)
                    .semantics { contentDescription = "Schließen" }, contentAlignment = Alignment.Center) { Text("✕", color = Schrift1, fontSize = 20.sp) }
            }
            Spacer(Modifier.height(14.dp))
            if (client == null) {
                Text("Die Schul-Cloud braucht die Zugänge der Schule aus DammTable (ab Version 2.4) und eine gewählte Klasse.", color = Schrift2, fontSize = 16.sp)
            }
            meldung?.let { Text(it, color = Akzent, fontSize = 14.sp) }
            if (client != null && !laedt && eintraege.isEmpty() && meldung == null) Text("Leer.", color = Schrift2, fontSize = 15.sp)
            LazyVerticalGrid(GridCells.Adaptive(190.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(eintraege, key = { it.pfad }) { e ->
                    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).clickable {
                        if (e.istOrdner) pfad = pfad.trim('/') + "/" + e.name.trimEnd('/') else oeffne(e)
                    }.padding(10.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE1E4E8)), contentAlignment = Alignment.Center) {
                            if (e.istOrdner) Text("📁", fontSize = 46.sp)
                            else {
                                val k = pfad + "/" + e.name
                                val v = vorschau[k]
                                LaunchedEffect(k) { if (!vorschau.containsKey(k)) { vorschau[k] = null; vorschau[k] = sperre.withPermit { ladeVorschau(e) } } }
                                if (v != null) Image(v.asImageBitmap(), contentDescription = e.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else CircularProgressIndicator(color = Schrift2, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(e.name.trimEnd('/').substringBeforeLast(".png").substringBeforeLast(".jpg"), color = Schrift1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
