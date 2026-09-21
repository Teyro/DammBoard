package de.oejendorferdamm.dammtafel.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import de.oejendorferdamm.dammtafel.model.BoardItem
import de.oejendorferdamm.dammtafel.model.FormItem
import de.oejendorferdamm.dammtafel.model.FormTyp
import de.oejendorferdamm.dammtafel.model.GeometrieWerkzeug
import de.oejendorferdamm.dammtafel.model.LaengenEtikett
import de.oejendorferdamm.dammtafel.model.StrichItem
import de.oejendorferdamm.dammtafel.model.Werkzeug
import de.oejendorferdamm.dammtafel.model.begrenzendesRechteck
import de.oejendorferdamm.dammtafel.ui.AufnahmeZweck
import de.oejendorferdamm.dammtafel.ui.TafelState
import de.oejendorferdamm.dammtafel.ui.naechsteId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val markierungsFarbe = Color(0xFF2F80FF)
private val gestricheltEffekt = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

private fun punktInPolygon(punkt: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var innen = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]; val pj = polygon[j]
        if ((pi.y > punkt.y) != (pj.y > punkt.y)) {
            val x = (pj.x - pi.x) * (punkt.y - pi.y) / (pj.y - pi.y) + pi.x
            if (punkt.x < x) innen = !innen
        }
        j = i
    }
    return innen
}

private fun mitte(a: Offset, b: Offset) = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)

private fun pxNachCm(px: Float, density: Density): Float = px / density.density / 160f * 2.54f

/** Zeichenfläche der Tafel: Rendering aller Seiteninhalte plus vollständige Gesten-Steuerung pro Werkzeug. */
@Composable
fun TafelCanvas(
    state: TafelState,
    modifier: Modifier = Modifier,
    onAufnahme: (AufnahmeZweck, android.graphics.Bitmap) -> Unit
) {
    val seite = state.seite
    val dichte = LocalDensity.current
    val graphicsLayer = rememberGraphicsLayer()

    LaunchedEffect(state.aufnahmeAnfrage) {
        val zweck = state.aufnahmeAnfrage ?: return@LaunchedEffect
        onAufnahme(zweck, graphicsLayer.toImageBitmap().asAndroidBitmap())
        state.aufnahmeAnfrage = null
    }

    var laufenderStrich by remember { mutableStateOf<List<Offset>?>(null) }
    var formVorschau by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var zirkelVorschau by remember { mutableStateOf<Pair<Offset, Float>?>(null) }

    // Zustand einer laufenden Geste, lokal zur pointerInput-Korutine.
    var geometrieModusRotation by remember { mutableStateOf(false) }
    var auswahlModusVerschieben by remember { mutableStateOf(false) }
    var auswahlLetzterPunkt by remember { mutableStateOf<Offset?>(null) }

    val gesteModifier = if (state.lupeAktiv) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { state.lupePosition = it },
                onDrag = { change, _ -> change.consume(); state.lupePosition = change.position },
                onDragEnd = { state.lupePosition = null },
                onDragCancel = { state.lupePosition = null }
            )
        }
    } else {
        Modifier.pointerInput(
            state.werkzeug, seite, state.stiftArt, state.stiftFarbe, state.aktuelleStiftBreite,
            state.formTyp, state.formRandFarbe, state.formFuellFarbe, state.formRandBreite,
            state.radiererGroesse, state.geometrieWerkzeug, state.geometrieGestrichelt, state.zeigeLaenge
        ) {
            val brettGroesse = Size(size.width.toFloat(), size.height.toFloat())
            when (state.werkzeug) {
                Werkzeug.STIFT -> detectDragGestures(
                    onDragStart = { laufenderStrich = listOf(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        laufenderStrich = laufenderStrich.orEmpty() + change.position
                    },
                    onDragEnd = {
                        laufenderStrich?.let { punkte ->
                            if (punkte.size > 1) {
                                seite.hinzufuegen(
                                    StrichItem(naechsteId(), punkte, state.stiftFarbe, state.aktuelleStiftBreite)
                                )
                            }
                        }
                        laufenderStrich = null
                    },
                    onDragCancel = { laufenderStrich = null }
                )

                Werkzeug.RADIERER -> detectDragGestures(
                    onDragStart = { seite.radiereBeruehrte(it, state.radiererGroesse.radius) },
                    onDrag = { change, _ ->
                        change.consume()
                        seite.radiereBeruehrte(change.position, state.radiererGroesse.radius)
                    },
                    onDragEnd = { seite.radierenAbschliessen() },
                    onDragCancel = { seite.radierenAbschliessen() }
                )

                Werkzeug.FORMEN -> detectDragGestures(
                    onDragStart = { formVorschau = it to it },
                    onDrag = { change, _ ->
                        change.consume()
                        formVorschau = formVorschau?.copy(second = change.position)
                    },
                    onDragEnd = {
                        formVorschau?.let { (start, ende) ->
                            if ((start - ende).getDistance() > 4f) {
                                val gestrichelt = state.formTyp in gestrichelteFormen
                                seite.hinzufuegen(
                                    FormItem(
                                        naechsteId(), state.formTyp, start, ende,
                                        state.formRandFarbe, state.formFuellFarbe, state.formRandBreite, gestrichelt
                                    )
                                )
                            }
                        }
                        formVorschau = null
                    },
                    onDragCancel = { formVorschau = null }
                )

                Werkzeug.LASSO -> detectDragGestures(
                    onDragStart = { start ->
                        val (min, max) = ausgewaehlteBegrenzung(seite.items, seite.ausgewaehlteIds)
                        auswahlModusVerschieben = min != null && max != null &&
                            start.x in min.x..max.x && start.y in min.y..max.y
                        auswahlLetzterPunkt = start
                        if (!auswahlModusVerschieben) state.lassoPfad = listOf(start)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (auswahlModusVerschieben) {
                            val letzter = auswahlLetzterPunkt ?: change.position
                            seite.verschiebeAusgewaehlte(change.position - letzter)
                            auswahlLetzterPunkt = change.position
                        } else {
                            state.lassoPfad = state.lassoPfad.orEmpty() + change.position
                        }
                    },
                    onDragEnd = {
                        if (!auswahlModusVerschieben) {
                            val pfad = state.lassoPfad
                            if (pfad != null && pfad.size > 2) {
                                seite.ausgewaehlteIds.clear()
                                seite.items.forEach { item ->
                                    val (min, max) = item.begrenzendesRechteck()
                                    if (punktInPolygon(mitte(min, max), pfad)) seite.ausgewaehlteIds.add(item.id)
                                }
                            } else {
                                seite.ausgewaehlteIds.clear()
                            }
                        }
                        state.lassoPfad = null
                        auswahlModusVerschieben = false
                        auswahlLetzterPunkt = null
                    },
                    onDragCancel = {
                        state.lassoPfad = null
                        auswahlModusVerschieben = false
                        auswahlLetzterPunkt = null
                    }
                )

                Werkzeug.AUSWAHL -> detectDragGestures(
                    onDragStart = { start ->
                        val (min, max) = ausgewaehlteBegrenzung(seite.items, seite.ausgewaehlteIds)
                        auswahlModusVerschieben = min != null && max != null &&
                            start.x in min.x..max.x && start.y in min.y..max.y
                        auswahlLetzterPunkt = start
                        if (!auswahlModusVerschieben) state.auswahlRechteck = start to start
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (auswahlModusVerschieben) {
                            val letzter = auswahlLetzterPunkt ?: change.position
                            seite.verschiebeAusgewaehlte(change.position - letzter)
                            auswahlLetzterPunkt = change.position
                        } else {
                            state.auswahlRechteck = state.auswahlRechteck?.copy(second = change.position)
                        }
                    },
                    onDragEnd = {
                        if (!auswahlModusVerschieben) {
                            state.auswahlRechteck?.let { (a, b) ->
                                val minX = minOf(a.x, b.x); val maxX = maxOf(a.x, b.x)
                                val minY = minOf(a.y, b.y); val maxY = maxOf(a.y, b.y)
                                seite.ausgewaehlteIds.clear()
                                seite.items.forEach { item ->
                                    val (imin, imax) = item.begrenzendesRechteck()
                                    val schneidet = imin.x <= maxX && imax.x >= minX && imin.y <= maxY && imax.y >= minY
                                    if (schneidet) seite.ausgewaehlteIds.add(item.id)
                                }
                            }
                        }
                        state.auswahlRechteck = null
                        auswahlModusVerschieben = false
                        auswahlLetzterPunkt = null
                    },
                    onDragCancel = {
                        state.auswahlRechteck = null
                        auswahlModusVerschieben = false
                        auswahlLetzterPunkt = null
                    }
                )

                Werkzeug.GEOMETRIE -> detectDragGestures(
                    onDragStart = { start ->
                        if (state.geometrieWerkzeug == GeometrieWerkzeug.ZIRKEL) {
                            zirkelVorschau = start to 0f
                        } else {
                            val zentrum = geometrieZentrum(brettGroesse)
                            val griffWelt = geometrieGriffPosition(zentrum, state.geometrieFuehrung.winkelGrad)
                            geometrieModusRotation = hypot(start.x - griffWelt.x, start.y - griffWelt.y) < 34f
                            if (!geometrieModusRotation) formVorschau = start to start
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (state.geometrieWerkzeug == GeometrieWerkzeug.ZIRKEL) {
                            zirkelVorschau?.let { (pivot, _) ->
                                zirkelVorschau = pivot to hypot(change.position.x - pivot.x, change.position.y - pivot.y)
                            }
                        } else if (geometrieModusRotation) {
                            val zentrum = geometrieZentrum(brettGroesse)
                            val winkel = atan2(change.position.y - zentrum.y, change.position.x - zentrum.x)
                            state.geometrieFuehrung.winkelGrad = Math.toDegrees(winkel.toDouble()).toFloat()
                        } else {
                            formVorschau?.let { (start, _) ->
                                val winkelRad = Math.toRadians(state.geometrieFuehrung.winkelGrad.toDouble())
                                val richtung = Offset(cos(winkelRad).toFloat(), sin(winkelRad).toFloat())
                                val delta = change.position - start
                                val projektion = delta.x * richtung.x + delta.y * richtung.y
                                formVorschau = start to (start + richtung * projektion)
                            }
                        }
                    },
                    onDragEnd = {
                        if (state.geometrieWerkzeug == GeometrieWerkzeug.ZIRKEL) {
                            zirkelVorschau?.let { (pivot, radius) ->
                                if (radius > 4f) {
                                    seite.hinzufuegen(
                                        FormItem(
                                            naechsteId(), FormTyp.KREIS,
                                            pivot - Offset(radius, radius), pivot + Offset(radius, radius),
                                            state.stiftFarbe, null, 4f, false
                                        )
                                    )
                                    if (state.zeigeLaenge) {
                                        val text = "%.1f cm".format(pxNachCm(radius, dichte))
                                        seite.hinzufuegen(LaengenEtikett(naechsteId(), pivot + Offset(radius + 8f, 0f), text))
                                    }
                                }
                            }
                            zirkelVorschau = null
                        } else if (!geometrieModusRotation) {
                            formVorschau?.let { (start, ende) ->
                                if ((start - ende).getDistance() > 6f) {
                                    seite.hinzufuegen(
                                        FormItem(
                                            naechsteId(),
                                            if (state.geometrieGestrichelt) FormTyp.LINIE_GESTRICHELT else FormTyp.LINIE,
                                            start, ende, state.stiftFarbe, null, 3.5f, state.geometrieGestrichelt
                                        )
                                    )
                                    if (state.zeigeLaenge) {
                                        val text = "%.1f cm".format(pxNachCm((start - ende).getDistance(), dichte))
                                        seite.hinzufuegen(LaengenEtikett(naechsteId(), mitte(start, ende) + Offset(0f, -14f), text))
                                    }
                                }
                            }
                            formVorschau = null
                        }
                        geometrieModusRotation = false
                    },
                    onDragCancel = {
                        formVorschau = null
                        zirkelVorschau = null
                        geometrieModusRotation = false
                    }
                )

                Werkzeug.WERKZEUGKASTEN -> { /* keine Zeichen-Geste, nur Panel-Aktionen */ }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
                state.lupePosition?.let { pos -> zeichneLupe(graphicsLayer, pos) }
            }
            .then(gesteModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = seite.hintergrund.value)

            if (seite.geteilteAnsicht.value) {
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(size.width / 2, 0f), end = Offset(size.width / 2, size.height),
                    strokeWidth = 2f, pathEffect = gestricheltEffekt
                )
            }

            seite.items.forEach { item -> zeichneItem(item) }

            seite.ausgewaehlteIds.forEach { id ->
                seite.items.firstOrNull { it.id == id }?.let { item -> zeichneMarkierung(item) }
            }

            laufenderStrich?.let { punkte ->
                zeichneItem(StrichItem(-1, punkte, state.stiftFarbe, state.aktuelleStiftBreite))
            }

            formVorschau?.let { (start, ende) ->
                if (state.werkzeug == Werkzeug.FORMEN) {
                    val gestrichelt = state.formTyp in gestrichelteFormen
                    zeichneItem(FormItem(-1, state.formTyp, start, ende, state.formRandFarbe, state.formFuellFarbe, state.formRandBreite, gestrichelt))
                } else if (state.werkzeug == Werkzeug.GEOMETRIE) {
                    zeichneItem(
                        FormItem(
                            -1, FormTyp.LINIE, start, ende, state.stiftFarbe, null, 3.5f, state.geometrieGestrichelt
                        )
                    )
                }
            }

            zirkelVorschau?.let { (pivot, radius) ->
                if (radius > 1f) {
                    drawCircle(state.stiftFarbe, radius = radius, center = pivot, style = Stroke(width = 3.5f))
                }
                drawCircle(state.stiftFarbe.copy(alpha = 0.5f), radius = 4f, center = pivot)
            }

            state.lassoPfad?.let { pfad ->
                if (pfad.size > 1) {
                    val p = Path().apply {
                        moveTo(pfad.first().x, pfad.first().y)
                        pfad.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(p, markierungsFarbe, style = Stroke(width = 2.5f, pathEffect = gestricheltEffekt))
                }
            }

            state.auswahlRechteck?.let { (a, b) ->
                val topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
                val gr = Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y))
                drawRect(markierungsFarbe.copy(alpha = 0.12f), topLeft = topLeft, size = gr)
                drawRect(markierungsFarbe, topLeft = topLeft, size = gr, style = Stroke(width = 2f, pathEffect = gestricheltEffekt))
            }

            if (state.werkzeug == Werkzeug.GEOMETRIE && state.geometrieWerkzeug != GeometrieWerkzeug.ZIRKEL) {
                zeichneGeometrieFuehrung(state.geometrieWerkzeug, geometrieZentrum(size), state.geometrieFuehrung.winkelGrad)
            }
        }
    }
}

private fun ausgewaehlteBegrenzung(items: List<BoardItem>, ids: List<Long>): Pair<Offset?, Offset?> {
    if (ids.isEmpty()) return null to null
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    var gefunden = false
    items.forEach { item ->
        if (item.id in ids) {
            gefunden = true
            val (min, max) = item.begrenzendesRechteck()
            minX = minOf(minX, min.x); minY = minOf(minY, min.y)
            maxX = maxOf(maxX, max.x); maxY = maxOf(maxY, max.y)
        }
    }
    return if (gefunden) Offset(minX, minY) to Offset(maxX, maxY) else null to null
}

private fun geometrieZentrum(groesse: Size): Offset = Offset(groesse.width * 0.5f, groesse.height * 0.28f)

private fun geometrieGriffPosition(zentrum: Offset, winkelGrad: Float): Offset {
    val winkelRad = Math.toRadians(winkelGrad.toDouble())
    val abstand = 170f
    return zentrum + Offset(cos(winkelRad).toFloat() * abstand, sin(winkelRad).toFloat() * abstand)
}

private fun DrawScope.zeichneGeometrieFuehrung(werkzeug: GeometrieWerkzeug, zentrum: Offset, winkelGrad: Float) {
    rotate(degrees = winkelGrad, pivot = zentrum) {
        val farbe = Color.White.copy(alpha = 0.55f)
        when (werkzeug) {
            GeometrieWerkzeug.LINEAL -> drawRoundRect(
                farbe, topLeft = zentrum - Offset(150f, 22f), size = Size(300f, 44f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f), style = Stroke(width = 2.5f)
            )
            GeometrieWerkzeug.WINKELMESSER -> drawArc(
                farbe, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = zentrum - Offset(140f, 140f), size = Size(280f, 280f), style = Stroke(width = 2.5f)
            )
            GeometrieWerkzeug.ZIRKEL -> {}
            else -> {
                val p = Path().apply {
                    moveTo(zentrum.x, zentrum.y - 130f)
                    lineTo(zentrum.x + 150f, zentrum.y + 90f)
                    lineTo(zentrum.x - 150f, zentrum.y + 90f)
                    close()
                }
                drawPath(p, farbe, style = Stroke(width = 2.5f))
            }
        }
    }
    val griff = geometrieGriffPosition(zentrum, winkelGrad)
    drawCircle(Color.White, radius = 14f, center = griff, style = Stroke(width = 3f))
    drawCircle(Color.White.copy(alpha = 0.5f), radius = 5f, center = griff)
}

private val gestrichelteFormen = setOf(
    FormTyp.LINIE_GESTRICHELT, FormTyp.PFEIL_GESTRICHELT,
    FormTyp.DOPPELPFEIL_GESTRICHELT, FormTyp.FREIHANDPFEIL_GESTRICHELT
)

private fun DrawScope.zeichneItem(item: BoardItem) {
    when (item) {
        is StrichItem -> zeichneStrich(item)
        is FormItem -> zeichneForm(item)
        is LaengenEtikett -> drawContext.canvas.nativeCanvas.drawText(
            item.text, item.position.x, item.position.y,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                isAntiAlias = true
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
            }
        )
    }
}

private fun DrawScope.zeichneStrich(strich: StrichItem) {
    if (strich.punkte.size < 2) {
        strich.punkte.firstOrNull()?.let { drawCircle(strich.farbe, radius = strich.breite / 2, center = it) }
        return
    }
    val pfad = Path().apply {
        moveTo(strich.punkte.first().x, strich.punkte.first().y)
        for (i in 1 until strich.punkte.size) lineTo(strich.punkte[i].x, strich.punkte[i].y)
    }
    drawPath(
        pfad, strich.farbe,
        style = Stroke(
            width = strich.breite, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round,
            pathEffect = if (strich.gestrichelt) gestricheltEffekt else null
        )
    )
}

private fun DrawScope.zeichneMarkierung(item: BoardItem) {
    val (min, max) = item.begrenzendesRechteck()
    val polster = 10f
    drawRoundRect(
        markierungsFarbe,
        topLeft = min - Offset(polster, polster),
        size = Size((max.x - min.x) + polster * 2, (max.y - min.y) + polster * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
        style = Stroke(width = 2.5f, pathEffect = gestricheltEffekt)
    )
}

private fun DrawScope.zeichneForm(form: FormItem) {
    val topLeft = Offset(minOf(form.start.x, form.ende.x), minOf(form.start.y, form.ende.y))
    val w = kotlin.math.abs(form.ende.x - form.start.x)
    val h = kotlin.math.abs(form.ende.y - form.start.y)
    val randStil = Stroke(
        width = form.randBreite, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathEffect = if (form.gestrichelt) gestricheltEffekt else null
    )

    fun fuelleUndZeichne(pfad: Path) {
        form.fuellFarbe?.let { drawPath(pfad, it) }
        drawPath(pfad, form.randFarbe, style = randStil)
    }

    when (form.typ) {
        FormTyp.KREIS -> {
            val r = minOf(w, h) / 2
            val mittelpunkt = Offset(topLeft.x + w / 2, topLeft.y + h / 2)
            form.fuellFarbe?.let { drawCircle(it, radius = r, center = mittelpunkt) }
            drawCircle(form.randFarbe, radius = r, center = mittelpunkt, style = randStil)
        }
        FormTyp.ELLIPSE -> {
            form.fuellFarbe?.let { drawOval(it, topLeft = topLeft, size = Size(w, h)) }
            drawOval(form.randFarbe, topLeft = topLeft, size = Size(w, h), style = randStil)
        }
        FormTyp.QUADRAT -> {
            form.fuellFarbe?.let { drawRect(it, topLeft = topLeft, size = Size(w, h)) }
            drawRect(form.randFarbe, topLeft = topLeft, size = Size(w, h), style = randStil)
        }
        FormTyp.ABGERUNDET -> {
            val rund = androidx.compose.ui.geometry.CornerRadius(minOf(w, h) * 0.18f)
            form.fuellFarbe?.let { drawRoundRect(it, topLeft = topLeft, size = Size(w, h), cornerRadius = rund) }
            drawRoundRect(form.randFarbe, topLeft = topLeft, size = Size(w, h), cornerRadius = rund, style = randStil)
        }
        FormTyp.DREIECK_RECHTS -> fuelleUndZeichne(Path().apply {
            moveTo(topLeft.x, topLeft.y + h); lineTo(topLeft.x, topLeft.y); lineTo(topLeft.x + w, topLeft.y + h); close()
        })
        FormTyp.DREIECK -> fuelleUndZeichne(Path().apply {
            moveTo(topLeft.x + w / 2, topLeft.y); lineTo(topLeft.x + w, topLeft.y + h); lineTo(topLeft.x, topLeft.y + h); close()
        })
        FormTyp.SECHSECK -> fuelleUndZeichne(vieleckPfad(topLeft, w, h, 6))
        FormTyp.FUENFECK -> fuelleUndZeichne(vieleckPfad(topLeft, w, h, 5))
        FormTyp.STERN -> fuelleUndZeichne(sternPfad(topLeft, w, h))
        FormTyp.WELLE -> drawPath(wellenPfad(topLeft, w, h), form.randFarbe, style = randStil)
        FormTyp.LINIE, FormTyp.LINIE_GESTRICHELT -> drawLine(form.randFarbe, form.start, form.ende, strokeWidth = form.randBreite, cap = StrokeCap.Round, pathEffect = randStil.pathEffect)
        FormTyp.PFEIL, FormTyp.PFEIL_GESTRICHELT -> zeichnePfeil(form.start, form.ende, form.randFarbe, form.randBreite, false, randStil)
        FormTyp.DOPPELPFEIL, FormTyp.DOPPELPFEIL_GESTRICHELT -> zeichnePfeil(form.start, form.ende, form.randFarbe, form.randBreite, true, randStil)
        FormTyp.FREIHANDPFEIL, FormTyp.FREIHANDPFEIL_GESTRICHELT -> {
            val kontrolle = mitte(form.start, form.ende) + Offset(-(form.ende.y - form.start.y), (form.ende.x - form.start.x)) * 0.2f
            val pfad = Path().apply {
                moveTo(form.start.x, form.start.y)
                quadraticTo(kontrolle.x, kontrolle.y, form.ende.x, form.ende.y)
            }
            drawPath(pfad, form.randFarbe, style = randStil)
            zeichnePfeilspitze(form.ende, kontrolle, form.randFarbe, form.randBreite)
        }
    }
}

private fun DrawScope.zeichnePfeil(start: Offset, ende: Offset, farbe: Color, breite: Float, doppelt: Boolean, stil: Stroke) {
    drawLine(farbe, start, ende, strokeWidth = breite, cap = StrokeCap.Round, pathEffect = stil.pathEffect)
    zeichnePfeilspitze(ende, start, farbe, breite)
    if (doppelt) zeichnePfeilspitze(start, ende, farbe, breite)
}

private fun DrawScope.zeichnePfeilspitze(spitze: Offset, ansatz: Offset, farbe: Color, breite: Float) {
    val laenge = 10f + breite
    val winkel = atan2(spitze.y - ansatz.y, spitze.x - ansatz.x)
    val a1 = winkel + Math.PI.toFloat() * 0.78f
    val a2 = winkel - Math.PI.toFloat() * 0.78f
    drawLine(farbe, spitze, spitze + Offset(cos(a1) * laenge, sin(a1) * laenge), strokeWidth = breite, cap = StrokeCap.Round)
    drawLine(farbe, spitze, spitze + Offset(cos(a2) * laenge, sin(a2) * laenge), strokeWidth = breite, cap = StrokeCap.Round)
}

private fun vieleckPfad(topLeft: Offset, w: Float, h: Float, ecken: Int): Path {
    val cx = topLeft.x + w / 2; val cy = topLeft.y + h / 2; val r = minOf(w, h) / 2
    return Path().apply {
        for (i in 0 until ecken) {
            val winkel = -Math.PI / 2 + i * (2 * Math.PI / ecken)
            val x = cx + r * cos(winkel).toFloat(); val y = cy + r * sin(winkel).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private fun sternPfad(topLeft: Offset, w: Float, h: Float): Path {
    val cx = topLeft.x + w / 2; val cy = topLeft.y + h / 2
    val rAussen = minOf(w, h) / 2; val rInnen = rAussen * 0.42f
    return Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) rAussen else rInnen
            val winkel = -Math.PI / 2 + i * (Math.PI / 5)
            val x = cx + r * cos(winkel).toFloat(); val y = cy + r * sin(winkel).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private fun wellenPfad(topLeft: Offset, w: Float, h: Float): Path {
    val y = topLeft.y + h / 2
    return Path().apply {
        moveTo(topLeft.x, y)
        cubicTo(topLeft.x + w * 0.2f, topLeft.y, topLeft.x + w * 0.3f, topLeft.y + h, topLeft.x + w * 0.5f, y)
        cubicTo(topLeft.x + w * 0.7f, topLeft.y, topLeft.x + w * 0.8f, topLeft.y + h, topLeft.x + w, y)
    }
}

private fun DrawScope.zeichneLupe(graphicsLayer: GraphicsLayer, position: Offset) {
    val radius = 90f
    clipPath(Path().apply { addOval(Rect(center = position, radius = radius)) }) {
        scale(2.2f, pivot = position) {
            drawLayer(graphicsLayer)
        }
    }
    drawCircle(Color.White, radius = radius, center = position, style = Stroke(width = 5f))
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius, center = position, style = Stroke(width = 1.5f))
}
