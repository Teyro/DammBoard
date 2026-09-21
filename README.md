# DammBoard

Eine digitale Tafel-App für Android – entwickelt für die **Schule Öjendorfer Damm**.

DammBoard verwandelt ein Android-Tablet in eine interaktive Kreidetafel
mit einer vollständigen Werkzeugleiste: Stift (zwei Stiftarten,
Farbpalette, Verlaufs-Farbwähler), Formen (2D-Formengitter mit Rand-
und Füllfarbe), Radierer, Lasso- und rechteckige Auswahl mit
Verschieben/Löschen, Geometrie-Werkzeuge (Lineal, Winkeldreieck,
Winkelmesser, Zirkel u. a. mit drehbarer Führung und Längenanzeige)
sowie ein Werkzeugkasten für Hintergrund, geteilte Ansicht,
Bildschirmfoto und Lupe.

## Funktionen (v0.3.0)

- Freihand-Zeichnen mit Finger/Stift, mehrere Seiten mit eigener
  Undo/Redo-Historie
- Formen, Geometrie-Werkzeuge, Lasso-/Rechteck-Auswahl mit
  Verschieben und Löschen
- Hintergrund-Vorlagen: vier Farben, dazu liniert/kariert/gepunktet
- Zwei Darstellungsmodi: **Normal** (sanfte Übergänge) und
  **Performance** (ganz ohne Animationen), einstellbar im
  Einstellungsmenü (☰)
- **IServ-Anbindung**: Tafelbild direkt in den schuleigenen
  IServ-WebDAV-Speicher hochladen – "Schnell speichern" in den
  Hauptordner oder gezielt in einen selbst durchblätterten
  Unterordner. Zugangsdaten (Web-Adresse, Benutzername, Passwort)
  werden im Einstellungsmenü hinterlegt.
- Tafelbild als PNG in die Galerie speichern oder über die
  Systemfreigabe teilen

## Technik

- Kotlin + [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Minimale Android-Version: Android 10 (API 29)
- Netzwerkzugriff nur für die IServ-Anbindung (WebDAV über
  [OkHttp](https://square.github.io/okhttp/)); alles andere läuft
  lokal auf dem Gerät
- Einstellungen liegen lokal in DataStore Preferences – **das
  IServ-Passwort wird dabei unverschlüsselt gespeichert**, das ist
  für ein von der Schule verwaltetes Tablet vorgesehen, nicht für ein
  privates Gerät mit sensiblen Zugangsdaten Dritter

## Bauen

```bash
./gradlew assembleDebug
```

Die fertige APK liegt danach unter `app/build/outputs/apk/debug/`.

## Status

DammBoard wird schrittweise nach Wünschen aus dem Schulalltag erweitert.
