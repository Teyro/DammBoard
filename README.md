# DammTafel

Eine digitale Tafel-App für Android – entwickelt für die **Schule Öjendorfer Damm**.

DammTafel verwandelt ein Android-Tablet in eine einfache, interaktive
Kreidetafel: mit dem Finger oder Stift schreiben und zeichnen, mit
mehreren Kreidefarben, einem Radiergummi, "Rückgängig" für den letzten
Strich und einer "Wischen"-Funktion, die die ganze Tafel auf einmal
leert – genau wie im echten Klassenzimmer. Der aktuelle Tafelstand lässt
sich außerdem als Bild speichern (Galerie-Ordner `Pictures/DammTafel`),
bevor er gelöscht wird.

## Funktionen (v0.1.0)

- Freihand-Zeichnen mit Finger/Stift auf grünem Tafel-Hintergrund
- Mehrere Kreidefarben (Weiß, Gelb, Hellblau, Rosa, Hellgrün)
- Einstellbare Strichbreite
- Radiergummi
- Rückgängig (letzter Strich)
- Wischen (ganze Tafel leeren)
- Tafelbild als PNG speichern

## Technik

- Kotlin + [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Minimale Android-Version: Android 10 (API 29)
- Kein Backend, keine Internetverbindung nötig – alles läuft lokal auf
  dem Gerät

## Bauen

```bash
./gradlew assembleDebug
```

Die fertige APK liegt danach unter `app/build/outputs/apk/debug/`.

## Status

Frühes Grundgerüst (v0.1.0) – als Ausgangspunkt gedacht, um Wünsche aus
dem Schulalltag (z. B. mehrere Tafel-Seiten, Formen/Lineal, Speichern
mehrerer Tafeln) nach und nach zu ergänzen.
