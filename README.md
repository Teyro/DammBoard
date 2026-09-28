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

## Funktionen (v0.7.0)

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
- **Oberfläche wie die Original-Tafel-App der CTOUCH-Boards**: runde
  Knöpfe über die ganze Breite verteilt, Panels direkt über dem
  jeweiligen Werkzeug. Alle Maße stammen aus Screenshots des Originals
  (1920×1080) und werden proportional auf den Bildschirm übertragen –
  unabhängig davon, welche Pixeldichte ein Board meldet. Die Größe ist
  zusätzlich einstellbar ("Kompakt" bis "Sehr groß")
- **Tafelspiel Fußball** (Würfel-Knopf unten links): Spielfeld über
  die ganze Tafel, Ball zum Werfen oder Anschießen per Tippen, Tore
  werden automatisch gezählt, Punkte links/rechts auch von Hand
- Die Zurück-Taste des Boards schließt erst Panels und fragt vor dem
  Beenden nach, statt den Tafelinhalt sofort zu verwerfen

## Technik

- Kotlin + [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Minimale Android-Version: Android 8.0 (API 26) – eine einzige APK
  deckt Android 8.0 bis zur jeweils aktuellen Version ab, keine
  getrennten Versionen. Auf Android 9 und älter fragt die App beim
  ersten Speichern einmalig die Speicherberechtigung ab (Scoped
  Storage gibt es erst ab Android 10, siehe `ui/Speichern.kt`)
- Netzwerkzugriff nur für die IServ-Anbindung (WebDAV über
  [OkHttp](https://square.github.io/okhttp/)); alles andere läuft
  lokal auf dem Gerät
- Einstellungen liegen lokal in DataStore Preferences; das
  IServ-Passwort wird mit einem Schlüssel aus dem Android-Keystore
  verschlüsselt (AES-GCM). IServ nur über https, ohne Weiterleitung
  auf unverschlüsselte Adressen
- Updates kommen ausschließlich aus den Releases dieses Repositories;
  vor der Installation prüft die App Paketname, Signatur und Größe

## Hilfe bei Problemen

- **Neue Version lässt sich nicht installieren („App nicht
  installiert")** oder im Einstellungsmenü steht unter „Installiert"
  weiterhin eine alte Versionsnummer: Auf dem Gerät ist noch eine
  sehr alte Testversion mit anderer Signatur. Einmal DammBoard
  deinstallieren und die aktuelle `DammBoard.apk` von der
  [Release-Seite](https://github.com/Teyro/DammBoard/releases/latest)
  neu installieren – danach funktionieren Updates wieder direkt aus
  der App.
- **Alles wirkt zu klein oder zu groß**: Im Einstellungsmenü unter
  „Symbolgröße" anpassen („Wie Original" = genau wie die Tafel-App
  des Boards). Die Zeile darunter zeigt, welche Auflösung und
  Pixeldichte das Gerät meldet – hilfreich bei Rückfragen.
- **Welche Version läuft?** Nach jedem Update erscheint oben kurz ein
  Hinweis mit der Versionsnummer; dauerhaft steht sie im Menü (☰)
  unter „Installiert".

## Bauen

```bash
./gradlew assembleDebug
```

Die fertige APK liegt danach unter `app/build/outputs/apk/debug/`.

## Status

DammBoard wird schrittweise nach Wünschen aus dem Schulalltag erweitert.
