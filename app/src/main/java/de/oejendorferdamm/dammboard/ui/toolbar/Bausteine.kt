package de.oejendorferdamm.dammboard.ui.toolbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Gemeinsame Bausteine und Farben der Bedienoberfläche. Alle Maße sind Vorbild-Pixel (als dp
 * geschrieben, siehe ui/Skalierung.kt) und an den Screenshots der Original-Tafel-App
 * ausgemessen: runde, hellgraue Knöpfe ohne Rahmen, das aktive Werkzeug dunkler grau hinterlegt.
 */

internal val KnopfFarbe = Color(0xFFEBEBEB)
internal val KnopfAktiv = Color(0xFFA7A7A7)
internal val SymbolFarbe = Color(0xFF3F3F3F)
internal val SymbolFarbeSchwach = Color(0xFFB0B0B0)
internal val TextFarbe = Color(0xFF333333)
internal val TextFarbeSchwach = Color(0xFF777777)
internal val PanelFarbe = Color(0xFFEDEDED)
internal val PanelAuswahl = Color(0xFFBDBDBD)
internal val PanelLinie = Color(0xFFCFCFCF)
internal val SchliessenRot = Color(0xFFC62828)
internal val HinweisRot = Color(0xFFE0402E)

/** Knopfdurchmesser und Abstand zwischen zwei Knöpfen im Original (1920×1080). */
internal val KnopfDurchmesser = 67.dp
internal val KnopfAbstand = 18.dp

/** Symbolgröße in den Knöpfen der unteren Leiste. */
internal val LeistenSymbol = 36.dp

/** Runder Knopf der unteren Leiste. */
@Composable
internal fun RundKnopf(
    beschreibung: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ausgewaehlt: Boolean = false,
    aktiviert: Boolean = true,
    inhalt: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(KnopfDurchmesser)
            .clip(CircleShape)
            .background(if (ausgewaehlt) KnopfAktiv else KnopfFarbe)
            .clickable(enabled = aktiviert, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
        content = inhalt
    )
}

/** Runde Auswahlfläche in den Panels: grauer Kreis hinter dem gewählten Eintrag, wie im Original. */
@Composable
internal fun AuswahlKreis(
    ausgewaehlt: Boolean,
    onClick: () -> Unit,
    groesse: Dp,
    beschreibung: String? = null,
    inhalt: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(groesse)
            .clip(CircleShape)
            .background(if (ausgewaehlt) PanelAuswahl else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier),
        contentAlignment = Alignment.Center,
        content = inhalt
    )
}

/**
 * Hintergrund eines Panels. Fängt alle Berührungen darauf ab: sonst würde ein Tippen auf eine
 * freie Stelle im Panel auf der Tafel darunter landen (Punkt zeichnen, Panel schließen).
 */
@Composable
internal fun PanelRahmen(modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(PanelFarbe)
            .faengtBeruehrungen()
            .padding(18.dp)
    ) {
        inhalt()
    }
}

/** Kleines Dreieck unter dem Panel, das auf den zugehörigen Knopf zeigt. */
@Composable
internal fun PanelZeiger(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 30.dp, height = 15.dp)) {
        val pfad = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(pfad, PanelFarbe)
    }
}

/** Nimmt Berührungen entgegen (ohne sie zu verbrauchen), damit sie nicht zur Tafel durchgehen. */
internal fun Modifier.faengtBeruehrungen(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}
