package de.oejendorferdamm.dammboard.ui.canvas

import android.view.MotionEvent

/**
 * Erkennt, ob gerade der Handballen (oder die flache Hand) auf der Tafel liegt – dann wird
 * gewischt statt gezeichnet, wie bei einer echten Tafel und der Original-App der CTOUCH-Boards.
 *
 * Grundlage ist die Größe der Berührungsfläche, die der Touchrahmen zu jedem Kontakt meldet
 * (MotionEvent.getTouchMajor). Wie groß ein Finger in Pixeln ist, hängt stark vom Gerät ab
 * (Tablet oder 86-Zoll-Board, oft falsche dpi-Angaben) – deshalb lernt die Erkennung die übliche
 * Fingergröße aus normalen Berührungen mit und wertet erst ein Vielfaches davon als Hand. Bis
 * genug Fingerberührungen gesehen wurden, gilt eine vorsichtige feste Schwelle.
 *
 * Manche Touchrahmen melden gar keine Fläche (immer 0) – dort zählen ersatzweise drei oder mehr
 * gleichzeitige Berührungspunkte als aufgelegte Hand.
 */
internal class HandballenErkennung {
    /** Größte gemeldete Berührungsfläche (Durchmesser in px) im zuletzt gesehenen Ereignis. */
    var groessteBeruehrungPx = 0f
        private set

    /** Liegt laut zuletzt gesehenem Ereignis eine Hand auf? */
    var handAufgelegt = false
        private set

    /** 0 = unempfindlich (nur eindeutig große Flächen), 1 = sehr empfindlich. */
    var empfindlichkeit = 0.5f
    var bildschirmBreitePx = 1920f
    var xdpi = 160f

    private val fingerProben = ArrayDeque<Float>()
    private var gestenMaximum = 0f
    private var gesteWarHand = false

    fun verarbeite(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            gestenMaximum = 0f
            gesteWarHand = false
        }
        var groesste = 0f
        for (i in 0 until ev.pointerCount) {
            groesste = maxOf(groesste, ev.getTouchMajor(i), ev.getTouchMinor(i))
        }
        groessteBeruehrungPx = groesste
        gestenMaximum = maxOf(gestenMaximum, groesste)

        val gedrueckt = when (ev.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0
            MotionEvent.ACTION_POINTER_UP -> ev.pointerCount - 1
            else -> ev.pointerCount
        }
        // Drei oder mehr Finger zählen nur als Hand, wenn sie dicht beieinander liegen – sonst
        // könnten mehrere Kinder, die gleichzeitig schreiben, aus Versehen etwas wegwischen.
        val vieleFingerBeieinander = gedrueckt >= 3 && run {
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until ev.pointerCount) {
                minX = minOf(minX, ev.getX(i)); maxX = maxOf(maxX, ev.getX(i))
                minY = minOf(minY, ev.getY(i)); maxY = maxOf(maxY, ev.getY(i))
            }
            val grenze = if (bildschirmBreitePx > 0f) bildschirmBreitePx * 0.18f else 400f
            maxX - minX < grenze && maxY - minY < grenze
        }
        val hand = gedrueckt > 0 && ((groesste > 0f && groesste >= schwelle()) || vieleFingerBeieinander)
        if (hand) gesteWarHand = true
        handAufgelegt = hand

        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            // Normale Finger-/Stiftberührung: als Maß für "so groß ist hier ein Finger" merken.
            if (!gesteWarHand && gestenMaximum > 0f) lerneFinger(gestenMaximum)
            handAufgelegt = false
        }
    }

    /** Ab dieser Berührungsgröße (px) gilt ein Kontakt als Hand. */
    fun schwelle(): Float {
        val faktor = 3.5f - 2f * empfindlichkeit.coerceIn(0f, 1f) // 3,5 … 1,5 × Fingergröße
        val untergrenze = maxOf(bildschirmBreitePx * 0.012f, 7f / 25.4f * xdpi)
        val finger = typischerFinger()
        return if (finger != null) {
            maxOf(finger * faktor, untergrenze)
        } else {
            maxOf(bildschirmBreitePx * 0.022f, 14f / 25.4f * xdpi) * (faktor / 2.5f)
        }
    }

    private fun lerneFinger(groesse: Float) {
        fingerProben.addLast(groesse)
        while (fingerProben.size > 15) fingerProben.removeFirst()
    }

    private fun typischerFinger(): Float? {
        if (fingerProben.size < 3) return null
        val sortiert = fingerProben.sorted()
        return sortiert[sortiert.size / 2]
    }
}
