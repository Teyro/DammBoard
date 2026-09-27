package de.oejendorferdamm.dammboard.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/*
 * Maßstab der Oberfläche.
 *
 * Vorbild ist die Tafel-App der CTOUCH-Boards. Deren Oberfläche ist für 1920×1080 Pixel
 * gestaltet: runde Knöpfe mit 67 px Durchmesser und 18 px Abstand, über die ganze Breite
 * verteilt (ausgemessen an den Screenshots der Original-App). DammBoard beschreibt seine
 * komplette Oberfläche in genau diesen "Vorbild-Pixeln" – im Code als dp geschrieben – und
 * überträgt sie proportional auf den echten Bildschirm. Ein Knopf ist dadurch immer 3,5 % der
 * Bildschirmbreite groß, auf einem Full-HD- genauso wie auf einem 4K-Board.
 *
 * Bis 0.5.0 hing die Größe an der Pixeldichte, die das Gerät an Android meldet. Viele Boards
 * melden dort einen unpassenden Wert (z. B. 4K-Bild bei 240 dpi) – deshalb blieben Knöpfe und
 * Abstände dort viel kleiner als im Original, und auch die Einstellung "Symbolgröße" konnte
 * das nicht ausgleichen.
 */

/** Breite bzw. Höhe des Vorbilds in Vorbild-Pixeln. */
const val VORBILD_BREITE = 1920f
const val VORBILD_HOEHE = 1080f

/**
 * Platzbedarf der unteren Leiste in Vorbild-Pixeln bei Größe 100 %: drei Knopfgruppen plus
 * Mindestabstände dazwischen. Größer als hier Platz ist, wird die Oberfläche nie gemacht.
 */
const val LEISTE_MINDESTBREITE = 1500f

/** Echte Bildschirmpixel pro Vorbild-Pixel. Das Gerät wird immer quer betrachtet. */
fun pixelProVorbildPixel(breitePx: Int, hoehePx: Int): Float {
    val lang = maxOf(breitePx, hoehePx).toFloat()
    val kurz = minOf(breitePx, hoehePx).toFloat()
    if (kurz <= 0f) return 1f
    return minOf(lang / VORBILD_BREITE, kurz / VORBILD_HOEHE)
}

/** Größter Faktor, bei dem die untere Leiste noch in eine Zeile passt. */
fun groessterLeistenFaktor(breitePx: Int, hoehePx: Int): Float {
    val einheit = pixelProVorbildPixel(breitePx, hoehePx)
    val breiteInVorbildPixeln = maxOf(breitePx, hoehePx) / einheit
    return breiteInVorbildPixeln / LEISTE_MINDESTBREITE
}

/**
 * Stellt für [inhalt] eine Dichte bereit, bei der 1 dp = 1 Vorbild-Pixel × [faktor] ist. Größe,
 * Abstände, Schrift UND Tippflächen wachsen dadurch gemeinsam. Die Schrift folgt bewusst nicht
 * der System-Schriftgröße, sonst würden Beschriftungen aus Knöpfen und Leisten ragen.
 *
 * [leisteMussPassen]: den Faktor so weit begrenzen, dass die untere Leiste in eine Zeile passt.
 */
@Composable
fun VorbildRaster(faktor: Float, leisteMussPassen: Boolean = false, inhalt: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val breite = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val hoehe = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val einheit = pixelProVorbildPixel(breite, hoehe)
        val wirksam = if (leisteMussPassen && breite > 0 && hoehe > 0) {
            minOf(faktor, groessterLeistenFaktor(breite, hoehe))
        } else {
            faktor
        }
        val dichte = remember(einheit, wirksam) { Density(density = einheit * wirksam, fontScale = 1f) }
        CompositionLocalProvider(LocalDensity provides dichte, content = inhalt)
    }
}
