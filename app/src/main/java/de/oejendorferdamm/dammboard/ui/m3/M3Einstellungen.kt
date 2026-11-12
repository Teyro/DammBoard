package de.oejendorferdamm.dammboard.ui.m3

// Gemeinsame Bausteine der Einstellungen aller Damm-Apps (DammBoard, DammTable, DammZeit, DammZahlen – DammLauncher baut
// dasselbe mit Views nach). Material You / Material 3: ab Android 12 Farben aus dem Hintergrundbild, davor eine feste
// Palette. Diese Datei ist in allen Apps gleich – Änderungen bitte überall übernehmen.

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Feste Palette (Material-3-Tonpalette aus Petrol #00687A) für Boards vor Android 12 */
private val FestePalette = lightColorScheme(
    primary = Color(0xFF00687A), onPrimary = Color.White,
    primaryContainer = Color(0xFFACEDFF), onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF4B6268), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE7EE), onSecondaryContainer = Color(0xFF061F24),
    tertiary = Color(0xFF565D7E), tertiaryContainer = Color(0xFFDDE1FF),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFF5FAFC), onBackground = Color(0xFF171C1E),
    surface = Color(0xFFF5FAFC), onSurface = Color(0xFF171C1E),
    surfaceVariant = Color(0xFFDBE4E7), onSurfaceVariant = Color(0xFF3F484B),
    outline = Color(0xFF6F797B), outlineVariant = Color(0xFFBFC8CB),
    surfaceBright = Color(0xFFF5FAFC), surfaceDim = Color(0xFFD5DBDD),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF4F7),
    surfaceContainer = Color(0xFFE9EFF1), surfaceContainerHigh = Color(0xFFE3E9EC),
    surfaceContainerHighest = Color(0xFFDEE3E6),
)

@Composable
fun einstellungenFarben(): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(LocalContext.current) else FestePalette

/** true innerhalb der Einstellungen – app-eigene Bausteine (Karte, Schalter, Knopf) zeichnen sich dann im Material-Stil */
val LokalEinstellungen = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Rahmen für alles, was in den Einstellungen steht: Material-You-Farben (Schalter, Regler, Knöpfe nehmen sie automatisch) */
@Composable
fun EinstellungenThema(inhalt: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LokalEinstellungen provides true) { MaterialTheme(colorScheme = einstellungenFarben(), content = inhalt) }

/** Kopfzeile: ← zurück (nur auf Unterseiten), Titel, ✕ schließen – in allen Apps gleich */
@Composable
fun M3Kopf(titel: String, onZurueck: (() -> Unit)?, onSchliessen: () -> Unit, modifier: Modifier = Modifier) {
    val f = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onZurueck != null) {
            FilledTonalIconButton(onZurueck, Modifier.size(64.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = f.surfaceContainerHighest)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück zur Übersicht", Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Text(titel, color = f.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f).padding(start = if (onZurueck == null) 8.dp else 0.dp), maxLines = 1)
        FilledTonalIconButton(onSchliessen, Modifier.size(64.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = f.surfaceContainerHighest)) {
            Icon(Icons.Filled.Close, "Einstellungen schließen", Modifier.size(30.dp))
        }
    }
}

/**
 * Gruppe wie in den Android-Einstellungen: optionale Überschrift in der Akzentfarbe, darunter die Einträge als
 * zusammenhängende Segmente (außen stark gerundet, dazwischen eine schmale Fuge).
 */
@Composable
fun M3Gruppe(titel: String? = null, modifier: Modifier = Modifier, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        if (titel != null) Text(titel, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(3.dp), content = inhalt)
    }
}

/** Ein Segment mit freiem Inhalt (Auswahl-Chips, Regler, Text) */
@Composable
fun M3Feld(modifier: Modifier = Modifier, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 24.dp, vertical = 20.dp), content = inhalt)
}

/** Erklärender Text in einem Feld */
@Composable
fun M3Hinweis(text: String, modifier: Modifier = Modifier) =
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, lineHeight = 24.sp, modifier = modifier)

/** Eintrag der Übersicht: Symbol im Tonkreis, Titel, Kurzfassung, Pfeil – die ganze Zeile ist der Knopf */
@Composable
fun M3Bereich(symbol: String, farbe: Color, titel: String, text: String, hinweis: Boolean = false, onClick: () -> Unit) {
    val f = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).background(f.surfaceContainerLowest)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(farbe.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Text(symbol, fontSize = 26.sp) }
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Text(titel, color = f.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(text, color = if (hinweis) f.primary else f.onSurfaceVariant, fontSize = 17.sp, maxLines = 2,
                fontWeight = if (hinweis) FontWeight.Medium else FontWeight.Normal)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = f.onSurfaceVariant, modifier = Modifier.size(32.dp))
    }
}

/** Schalter-Zeile: die ganze Zeile schaltet (wie in den Android-Einstellungen); [text] = optionale Erklärung darunter */
@Composable
fun M3Schalter(titel: String, an: Boolean, text: String? = null, onAendern: (Boolean) -> Unit) {
    val f = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(an, role = Role.Switch, onValueChange = onAendern).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(titel, color = f.onSurface, fontSize = 19.sp)
            if (text != null) Text(text, color = f.onSurfaceVariant, fontSize = 16.sp)
        }
        Switch(checked = an, onCheckedChange = null)
    }
}

/** Auswahl wie ein Material-3-Filter-Chip: gewählt = getönt mit Haken, sonst umrandet */
@Composable
fun M3Auswahl(text: String, gewaehlt: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, vorne: (@Composable () -> Unit)? = null) {
    val f = MaterialTheme.colorScheme
    Row(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
            .background(if (gewaehlt) f.secondaryContainer else f.surfaceContainerLowest)
            .then(if (gewaehlt) Modifier else Modifier.border(1.dp, f.outlineVariant, RoundedCornerShape(12.dp)))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (gewaehlt) { Icon(Icons.Filled.Check, null, tint = f.onSecondaryContainer, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        if (vorne != null) { vorne(); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (gewaehlt) f.onSecondaryContainer else f.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

/** Knopf in den Einstellungen: betont = gefüllt in der Akzentfarbe, sonst getönt */
@Composable
fun M3Knopf(text: String, modifier: Modifier = Modifier, betont: Boolean = false, onClick: () -> Unit) {
    val polster = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 16.dp)
    if (betont) androidx.compose.material3.Button(onClick, modifier.heightIn(min = 60.dp), contentPadding = polster) { Text(text, fontSize = 19.sp) }
    else androidx.compose.material3.FilledTonalButton(onClick, modifier.heightIn(min = 60.dp), contentPadding = polster) { Text(text, fontSize = 19.sp) }
}
