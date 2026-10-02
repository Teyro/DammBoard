#!/usr/bin/env python3
"""Installiert/startet DammBoard im CI-Emulator, tippt sich per uiautomator-Dump durch
die Werkzeugleiste und sammelt Screenshots. Wird ausschließlich vom manuell gestarteten
Workflow '.github/workflows/screenshots.yml' aufgerufen, nicht Teil des normalen Builds.
"""
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PAKET = "de.oejendorferdamm.dammboard"
AUSGABE_ORDNER = "screenshots"


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True)


def laeuft_noch() -> bool:
    ergebnis = adb("shell", "pidof", PAKET, check=False)
    return ergebnis.returncode == 0 and ergebnis.stdout.strip() != ""


def screenshot(dateiname: str):
    pfad = os.path.join(AUSGABE_ORDNER, dateiname)
    ergebnis = subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True)
    with open(pfad, "wb") as datei:
        datei.write(ergebnis.stdout)
    print(f"Screenshot gespeichert: {pfad}")


def ui_baum():
    """uiautomator-Abbild der Oberfläche; im Emulator schlägt das gelegentlich fehl – dann erneut."""
    for _ in range(4):
        dump = adb("shell", "uiautomator", "dump", "/sdcard/dump.xml", check=False)
        pull = adb("pull", "/sdcard/dump.xml", "dump.xml", check=False)
        if dump.returncode == 0 and pull.returncode == 0:
            try:
                return ET.parse("dump.xml")
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return None


def raeume_systemdialoge_weg():
    """Nach 'wm density' stürzt im Emulator gern der Launcher ab; sein Fehlerdialog verdeckt die
    App. Mit Zurück schließen und DammBoard wieder nach vorne holen."""
    for _ in range(3):
        baum = ui_baum()
        if baum is None:
            break
        texte = " ".join((k.get("text") or "") for k in baum.iter("node"))
        if "has stopped" in texte or "keeps stopping" in texte or "isn't responding" in texte:
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(1.5)
        else:
            break
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(2)


def tippe_mitte_von(beschreibung: str) -> bool:
    baum = ui_baum()
    if baum is None:
        print(f"WARNUNG: Oberfläche nicht lesbar, '{beschreibung}' übersprungen", file=sys.stderr)
        return False
    for knoten in baum.iter("node"):
        if knoten.get("content-desc") == beschreibung:
            grenzen = knoten.get("bounds", "")
            zahlen = [int(z) for z in grenzen.replace("][", ",").strip("[]").split(",")]
            x1, y1, x2, y2 = zahlen
            mitte_x, mitte_y = (x1 + x2) // 2, (y1 + y2) // 2
            adb("shell", "input", "tap", str(mitte_x), str(mitte_y))
            return True
    print(f"WARNUNG: Element '{beschreibung}' nicht in der UI gefunden", file=sys.stderr)
    return False


def tippe_text(text: str) -> bool:
    """Tippt auf das erste Element mit genau diesem sichtbaren Text."""
    baum = ui_baum()
    if baum is None:
        return False
    for knoten in baum.iter("node"):
        if (knoten.get("text") or "") == text:
            zahlen = [int(z) for z in knoten.get("bounds", "").replace("][", ",").strip("[]").split(",")]
            x1, y1, x2, y2 = zahlen
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return True
    print(f"WARNUNG: Text '{text}' nicht in der UI gefunden", file=sys.stderr)
    return False


def zeichne_mit_halten(punkte, halten_s=1.0):
    """Zeichnet einen Strich (Bildschirmpixel) und hält am Ende still – für die Formerkennung."""
    teile = [f"input motionevent DOWN {punkte[0][0]} {punkte[0][1]}"]
    for x, y in punkte[1:]:
        teile.append(f"input motionevent MOVE {x} {y}")
    teile.append(f"sleep {halten_s}")
    teile.append(f"input motionevent UP {punkte[-1][0]} {punkte[-1][1]}")
    adb("shell", " ; ".join(teile), check=False)


def zwei_finger_zeichnen():
    """Zwei Finger gleichzeitig (zwei Kinder an der geteilten Tafel): je eine Welle links und rechts."""
    import math
    info = touch_geraet()
    if not info:
        print("WARNUNG: kein Touchscreen für sendevent gefunden")
        return
    geraet, b = info
    mx, my = b["ABS_MT_POSITION_X"], b["ABS_MT_POSITION_Y"]
    roh = touch_abbildung()
    befehle = []
    def ev(typ, code, wert):
        befehle.append(f"sendevent {geraet} {typ} {code} {wert}")
    schritte = 24
    for s in range(schritte + 1):
        t = s / schritte
        for i, x0 in enumerate((0.1, 0.6)):
            rx, ry = roh(x0 + 0.3 * t, 0.3 + 0.08 * math.sin(t * 6.28), mx, my)
            ev(3, 47, i)
            if s == 0:
                ev(3, 57, 200 + i)
            ev(3, 53, rx)
            ev(3, 54, ry)
            if "ABS_MT_PRESSURE" in b:
                ev(3, 58, max(1, b["ABS_MT_PRESSURE"] // 2))
        if s == 0:
            ev(1, 330, 1)
        ev(0, 0, 0)
    for i in range(2):
        ev(3, 47, i)
        ev(3, 57, 4294967295)
    ev(1, 330, 0)
    ev(0, 0, 0)
    adb("shell", " ; ".join(befehle), check=False)


# Kleines Test-PDF mit zwei Seiten (Arbeitsblatt öffnen)
def test_pdf() -> bytes:
    objekte = []
    seiten_ids = [3, 5]
    objekte.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objekte.append(b"<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>")
    for nr, text in ((1, b"Arbeitsblatt Seite 1: 3 + 4 = ___"), (2, b"Arbeitsblatt Seite 2: 12 - 5 = ___")):
        inhalt = b"BT /F1 28 Tf 60 700 Td (" + text + b") Tj ET 50 100 m 545 100 l S"
        objekte.append(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents %d 0 R /Resources << /Font << /F1 7 0 R >> >> >>" % (len(objekte) + 2))
        objekte.append(b"<< /Length %d >>\nstream\n" % len(inhalt) + inhalt + b"\nendstream")
    objekte.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    ausgabe = bytearray(b"%PDF-1.4\n")
    positionen = []
    for i, obj in enumerate(objekte, start=1):
        positionen.append(len(ausgabe))
        ausgabe += b"%d 0 obj\n" % i + obj + b"\nendobj\n"
    xref = len(ausgabe)
    ausgabe += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objekte) + 1)
    for pos in positionen:
        ausgabe += b"%010d 00000 n \n" % pos
    ausgabe += b"trailer << /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objekte) + 1, xref)
    return bytes(ausgabe)


def neue_funktionen(breite, hoehe):
    """Version 0.9: Textmarker, Formerkennung, Stempel, Text, Kopieren, Vorlagen, Karten, Helfer,
    Abdecken, PDF, Seitenübersicht, geteilte Tafel mit zwei Fingern, Arbeitsblatt, Sicherung."""
    # Neue leere Seite für die Tests
    tippe_mitte_von("Seite hinzufügen")
    time.sleep(0.8)

    # Textmarker über einen Strich
    if tippe_mitte_von("Stift"):
        time.sleep(0.8)
        tippe_mitte_von("Stift fein")
        tippe_mitte_von("Stift")
        time.sleep(0.5)
        adb("shell", "input", "swipe", str(int(breite * 0.1)), str(int(hoehe * 0.15)), str(int(breite * 0.45)), str(int(hoehe * 0.15)), "400")
        time.sleep(0.3)
        tippe_mitte_von("Stift")
        time.sleep(0.8)
        tippe_mitte_von("Textmarker")
        time.sleep(0.4)
        screenshot("20a_stift_panel_textmarker.png")
        tippe_mitte_von("Stift")
        time.sleep(0.5)
        adb("shell", "input", "swipe", str(int(breite * 0.08)), str(int(hoehe * 0.16)), str(int(breite * 0.4)), str(int(hoehe * 0.16)), "400")
        time.sleep(0.5)
        tippe_mitte_von("Stift")
        time.sleep(0.8)
        tippe_mitte_von("Stift fein")
        tippe_mitte_von("Stift")
        time.sleep(0.5)

    # Formerkennung: krakeliger Kreis, Dreieck, Linie – jeweils am Ende kurz halten
    import math
    kreis = [(int(breite * 0.62 + math.cos(w / 20 * 6.4) * breite * 0.07 * (1 + 0.08 * math.sin(w))),
              int(hoehe * 0.3 + math.sin(w / 20 * 6.4) * breite * 0.07)) for w in range(21)]
    zeichne_mit_halten(kreis)
    dreieck_ecken = [(0.75, 0.45), (0.88, 0.45), (0.8, 0.25), (0.752, 0.44)]
    dreieck = []
    for (ax, ay), (bx, by) in zip(dreieck_ecken, dreieck_ecken[1:]):
        for k in range(6):
            dreieck.append((int(breite * (ax + (bx - ax) * k / 6) + (k % 2) * 4), int(hoehe * (ay + (by - ay) * k / 6))))
    zeichne_mit_halten(dreieck)
    linie = [(int(breite * (0.1 + 0.03 * k)), int(hoehe * 0.42 + (k % 3) * 3)) for k in range(12)]
    zeichne_mit_halten(linie)
    time.sleep(0.6)
    screenshot("20b_textmarker_formerkennung.png")

    # Stempel und Text
    if tippe_mitte_von("Formen"):
        time.sleep(0.8)
        tippe_text("Stempel")
        time.sleep(0.6)
        screenshot("21a_stempel_panel.png")
        tippe_mitte_von("Haken")
        tippe_mitte_von("Formen")
        time.sleep(0.4)
        adb("shell", "input", "tap", str(int(breite * 0.12)), str(int(hoehe * 0.6)))
        time.sleep(0.3)
        tippe_mitte_von("Formen")
        time.sleep(0.8)
        tippe_mitte_von("Super")
        tippe_mitte_von("Formen")
        time.sleep(0.4)
        adb("shell", "input", "tap", str(int(breite * 0.25)), str(int(hoehe * 0.6)))
        time.sleep(0.3)
        tippe_mitte_von("Formen")
        time.sleep(0.8)
        tippe_mitte_von("Lachendes Gesicht")
        tippe_mitte_von("Formen")
        time.sleep(0.4)
        adb("shell", "input", "tap", str(int(breite * 0.38)), str(int(hoehe * 0.6)))
        time.sleep(0.3)
        tippe_mitte_von("Formen")
        time.sleep(0.8)
        tippe_mitte_von("Textfeld")
        tippe_mitte_von("Formen")
        time.sleep(0.4)
        adb("shell", "input", "tap", str(int(breite * 0.1)), str(int(hoehe * 0.72)))
        time.sleep(1.5)
        adb("shell", "input", "text", "Hallo%sKlasse%s2b")
        time.sleep(0.8)
        screenshot("21b_text_dialog.png")
        tippe_text("Fertig")
        time.sleep(1)
        screenshot("21c_stempel_text.png")

    # Lasso: Stempel auswählen, Leiste mit Kopieren/Duplizieren, duplizieren
    if tippe_mitte_von("Auswahl"):
        time.sleep(0.5)
        adb("shell", "input", "swipe", str(int(breite * 0.05)), str(int(hoehe * 0.5)), str(int(breite * 0.45)), str(int(hoehe * 0.68)), "500")
        time.sleep(0.8)
        screenshot("22a_auswahl_leiste.png")
        tippe_text("Duplizieren")
        time.sleep(0.8)
        tippe_text("Kopieren")
        time.sleep(0.5)
        screenshot("22b_dupliziert.png")
        tippe_mitte_von("Stift")
        time.sleep(0.5)
        tippe_mitte_von("Stift")
        time.sleep(0.5)

    # Hintergründe: Lineatur, Hundertertafel, Zahlenstrahl, Karten
    for nr, name in enumerate(["Lineatur 1", "Lineatur 3", "100er-Tafel", "Zahlenstrahl", "Deutschland", "Hamburg", "Welt"]):
        if tippe_mitte_von("Werkzeugkasten"):
            time.sleep(0.8)
            tippe_mitte_von("Hintergrund")
            time.sleep(0.6)
            if nr == 0:
                screenshot("23_hintergrund_auswahl.png")
            tippe_text(name)
            time.sleep(0.3)
            tippe_mitte_von("Werkzeugkasten")
            time.sleep(0.8)
            screenshot(f"23{chr(97 + nr)}_{name.replace(' ', '_').replace('-', '')}.png")
    if tippe_mitte_von("Werkzeugkasten"):
        time.sleep(0.8)
        tippe_mitte_von("Hintergrund")
        time.sleep(0.6)
        tippe_text("Einfarbig")
        tippe_mitte_von("Werkzeugkasten")
        time.sleep(0.5)

    # Extras: Helfer öffnen
    def extra(name, warten=1.0):
        if tippe_mitte_von("Werkzeugkasten"):
            time.sleep(0.8)
            tippe_mitte_von("Extras")
            time.sleep(0.6)
            tippe_mitte_von(name)
            time.sleep(warten)

    if tippe_mitte_von("Werkzeugkasten"):
        time.sleep(0.8)
        tippe_mitte_von("Extras")
        time.sleep(0.6)
        screenshot("24_extras.png")
        tippe_mitte_von("Werkzeugkasten")
        time.sleep(0.5)
    extra("Timer")
    tippe_text("1'")
    tippe_text("Start")
    time.sleep(1)
    screenshot("24a_timer.png")
    tippe_mitte_von("Timer schließen")
    extra("Würfel")
    tippe_text("Würfeln")
    time.sleep(1.5)
    screenshot("24b_wuerfel.png")
    tippe_mitte_von("Würfel schließen")
    extra("Lernuhr")
    tippe_text("Zeit zeigen")
    time.sleep(0.5)
    screenshot("24c_lernuhr.png")
    tippe_mitte_von("Lernuhr schließen")
    extra("Zufallsname")
    tippe_text("Namen bearbeiten")
    time.sleep(1.5)
    adb("shell", "input", "text", "Ali")
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    adb("shell", "input", "text", "Berta")
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    adb("shell", "input", "text", "Can")
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    adb("shell", "input", "text", "Dilara")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")  # Tastatur zu
    time.sleep(0.6)
    tippe_text("Speichern")
    time.sleep(0.6)
    tippe_text("Ziehen")
    time.sleep(2)
    screenshot("24d_zufallsname.png")
    tippe_mitte_von("Zufallsname schließen")
    extra("Gruppen")
    tippe_text("Einteilen")
    time.sleep(0.8)
    screenshot("24e_gruppen.png")
    tippe_mitte_von("Gruppen schließen")
    extra("Lautstärke", 2.0)
    screenshot("24f_lautstaerke_frage.png")
    for knopf in ("While using the app", "Allow", "Only this time", "ALLOW"):
        if tippe_text(knopf):
            break
    time.sleep(2)
    screenshot("24g_lautstaerke.png")
    tippe_mitte_von("Lautstärke schließen")

    # Abdecken
    extra("Abdecken")
    screenshot("25a_abdecken.png")
    adb("shell", "input", "swipe", str(breite // 2), str(int(hoehe * 0.17)), str(breite // 2), str(int(hoehe * 0.55)), "600")
    time.sleep(0.8)
    screenshot("25b_aufgedeckt.png")
    tippe_mitte_von("Abdecken beenden")
    time.sleep(0.5)

    # PDF aller Seiten
    extra("PDF", 5.0)
    screenshot("26_pdf_fertig.png")
    tippe_text("Abbrechen")
    time.sleep(0.5)

    # Geteilte Tafel: zwei Finger gleichzeitig
    tippe_mitte_von("Seite hinzufügen")
    time.sleep(0.6)
    if tippe_mitte_von("Werkzeugkasten"):
        time.sleep(0.8)
        tippe_mitte_von("Bild teilen")
        tippe_mitte_von("Werkzeugkasten")
        time.sleep(0.5)
    tippe_mitte_von("Stift")
    time.sleep(0.4)
    tippe_mitte_von("Stift")
    time.sleep(0.4)
    zwei_finger_zeichnen()
    time.sleep(0.8)
    screenshot("27_zwei_finger.png")

    # Arbeitsblatt (PDF) über die Android-Dateiauswahl öffnen
    with open("arbeitsblatt_test.pdf", "wb") as datei:
        datei.write(test_pdf())
    adb("push", "arbeitsblatt_test.pdf", "/sdcard/Download/arbeitsblatt_test.pdf", check=False)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file:///sdcard/Download/arbeitsblatt_test.pdf", check=False)
    tippe_mitte_von("Seite hinzufügen")
    time.sleep(0.6)
    extra("Arbeitsblatt")
    screenshot("28a_arbeitsblatt_frage.png")
    tippe_text("Vom Board oder USB-Stick")
    time.sleep(3)
    screenshot("28b_dateiauswahl.png")
    if not tippe_text("arbeitsblatt_test.pdf"):
        # Dateiauswahl zeigt erst "Zuletzt verwendet": Seitenmenü öffnen, dann Downloads
        tippe_mitte_von("Show roots") or tippe_mitte_von("Open navigation drawer")
        time.sleep(1.5)
        tippe_text("Downloads") or tippe_text("Download")
        time.sleep(2.5)
        tippe_text("arbeitsblatt_test.pdf")
    time.sleep(5)
    screenshot("28c_arbeitsblatt.png")
    adb("shell", "input", "swipe", str(int(breite * 0.35)), str(int(hoehe * 0.3)), str(int(breite * 0.6)), str(int(hoehe * 0.32)), "400")
    time.sleep(0.5)
    screenshot("28d_arbeitsblatt_beschriftet.png")

    # Seitenübersicht
    if tippe_mitte_von("Seitenübersicht"):
        time.sleep(3)
        screenshot("29_seitenuebersicht.png")
        tippe_mitte_von("Seitenübersicht schließen")
        time.sleep(0.6)

    # Automatische Sicherung: App hart beenden und neu starten – die Tafel muss wieder da sein
    time.sleep(3)
    adb("shell", "am", "force-stop", PAKET)
    time.sleep(1)
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(5)
    screenshot("30_nach_neustart.png")
    if tippe_mitte_von("Seitenübersicht"):
        time.sleep(3)
        screenshot("31_uebersicht_nach_neustart.png")
        tippe_mitte_von("Seitenübersicht schließen")
        time.sleep(0.6)


def bildschirm_groesse():
    """Aktuelle (ggf. per 'wm size' simulierte) Bildschirmgröße in Pixeln, quer ausgerichtet."""
    ausgabe = adb("shell", "wm", "size").stdout
    zeilen = [z for z in ausgabe.splitlines() if ":" in z]
    breite, hoehe = (int(z) for z in zeilen[-1].split(":")[1].strip().split("x"))
    return max(breite, hoehe), min(breite, hoehe)


def sichere_logcat():
    with open("logcat.txt", "w") as datei:
        subprocess.run(["adb", "logcat", "-d"], stdout=datei)


def touch_geraet():
    """Findet den (virtuellen) Touchscreen und seine Wertebereiche über getevent -pl."""
    ausgabe = adb("shell", "getevent", "-pl", check=False).stdout
    geraet, bereiche, ergebnis = None, {}, None
    for zeile in ausgabe.splitlines():
        if zeile.startswith("add device"):
            if geraet and "ABS_MT_POSITION_X" in bereiche:
                ergebnis = ergebnis or (geraet, dict(bereiche))
            geraet, bereiche = zeile.split(":", 1)[1].strip(), {}
        for achse in ("ABS_MT_POSITION_X", "ABS_MT_POSITION_Y", "ABS_MT_TOUCH_MAJOR", "ABS_MT_PRESSURE"):
            if achse in zeile and "max" in zeile:
                teile = zeile.replace(",", " ").split()
                bereiche[achse] = int(teile[teile.index("max") + 1])
    if geraet and "ABS_MT_POSITION_X" in bereiche:
        ergebnis = ergebnis or (geraet, dict(bereiche))
    return ergebnis


def touch_abbildung():
    """Rechnet Bildschirm-Anteile (0..1 der sichtbaren Oberfläche) in Rohwerte des Touchscreens um.
    Nötig, weil 'wm size' die Oberfläche in einen Ausschnitt des physischen Displays legt."""
    import re
    diag = adb("shell", "dumpsys", "input", check=False).stdout
    sw = int(re.search(r"SurfaceWidth: (\d+)px", diag).group(1))
    sh = int(re.search(r"SurfaceHeight: (\d+)px", diag).group(1))
    m = re.search(r"physicalFrame=\[(-?\d+), (-?\d+), (-?\d+), (-?\d+)\], deviceSize=\[(\d+), (\d+)\]", diag)
    links, oben, rechts, unten, dw, dh = (int(g) for g in m.groups())
    if dw <= 0 or dh <= 0 or rechts <= links:
        # Ohne 'wm size' meldet Android 11 hier Nullen: Touchscreen = ganze Oberfläche.
        links, oben, rechts, unten, dw, dh = 0, 0, sw, sh, sw, sh
    print(f"Touch-Abbildung: surface {sw}x{sh}, frame {links},{oben},{rechts},{unten}, device {dw}x{dh}")
    def roh(xa, ya, mx, my):
        px = (links + xa * (rechts - links)) * sw / dw
        py = (oben + ya * (unten - oben)) * sh / dh
        return int(px / sw * mx), int(py / sh * my)
    return roh


def handballen_wischen(x_anteil, y_von, y_bis):
    """Legt eine "Hand" auf und wischt senkrecht. Der Emulator meldet keine Kontaktfläche
    (SizeScale 0) – deshalb drei Finger nebeneinander, der Ersatzweg der Erkennung."""
    info = touch_geraet()
    if not info:
        print("WARNUNG: kein Touchscreen für sendevent gefunden")
        return
    geraet, b = info
    mx, my = b["ABS_MT_POSITION_X"], b["ABS_MT_POSITION_Y"]
    roh = touch_abbildung()
    finger = [-0.02, 0.0, 0.02]
    befehle = []
    def ev(typ, code, wert):
        befehle.append(f"sendevent {geraet} {typ} {code} {wert}")
    schritte = 12
    for s in range(schritte + 1):
        y = y_von + (y_bis - y_von) * s / schritte
        for i, dx in enumerate(finger):
            rx, ry = roh(x_anteil + dx, y, mx, my)
            ev(3, 47, i)
            if s == 0:
                ev(3, 57, 100 + i)
            ev(3, 53, rx)
            ev(3, 54, ry)
            if "ABS_MT_PRESSURE" in b:
                ev(3, 58, max(1, b["ABS_MT_PRESSURE"] // 2))
            if "ABS_MT_TOUCH_MAJOR" in b:
                ev(3, 48, 50)
        if s == 0:
            ev(1, 330, 1)
        ev(0, 0, 0)
    for i, _ in enumerate(finger):
        ev(3, 47, i)
        ev(3, 57, 4294967295)
    ev(1, 330, 0)
    ev(0, 0, 0)
    r = adb("shell", " ; ".join(befehle), check=False)
    print("sendevent:", len(befehle), "Befehle, Fehler:", (r.stderr or r.stdout or "")[:200])


def main():
    os.makedirs(AUSGABE_ORDNER, exist_ok=True)
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(5)

    if not laeuft_noch():
        print("FEHLER: App ist nach dem Start nicht mehr am Laufen (Absturz?)", file=sys.stderr)
        sichere_logcat()
        sys.exit(1)

    raeume_systemdialoge_weg()
    screenshot("01_start.png")

    ablauf = [
        ("Stift", "02_stift.png"),
        ("Stift", None),
        ("Formen", "03_formen.png"),
        ("Formen", None),
        ("Radierer", "04_radierer.png"),
        ("Radierer", None),
        ("Geometrie", "05_geometrie.png"),
        ("Geometrie", None),
        ("Werkzeugkasten", "06_werkzeugkasten.png"),
    ]
    for beschreibung, datei in ablauf:
        if tippe_mitte_von(beschreibung):
            time.sleep(1)
            if datei:
                screenshot(datei)
    # Werkzeugkasten wieder schließen
    tippe_mitte_von("Werkzeugkasten")
    time.sleep(1)

    # Zeichenfläche: Stift wählen, ein paar Striche ziehen, einen davon wegradieren.
    breite, hoehe = bildschirm_groesse()
    if tippe_mitte_von("Stift"):
        time.sleep(0.5)
        tippe_mitte_von("Stift")  # Panel wieder zu
        time.sleep(0.5)
        for i in range(3):
            y = int(hoehe * (0.2 + 0.1 * i))
            adb("shell", "input", "swipe", str(int(breite * 0.15)), str(y), str(int(breite * 0.6)), str(y + int(hoehe * 0.05)), "600")
            time.sleep(0.3)
        screenshot("07_striche.png")
    if tippe_mitte_von("Radierer"):
        time.sleep(0.5)
        tippe_mitte_von("Radierer")
        time.sleep(0.5)
        x = int(breite * 0.35)
        adb("shell", "input", "swipe", str(x), str(int(hoehe * 0.15)), str(x), str(int(hoehe * 0.28)), "500")
        time.sleep(0.5)
        screenshot("08_radiert.png")
    if tippe_mitte_von("Rückgängig"):
        time.sleep(0.5)
        screenshot("09_rueckgaengig.png")

    # Mit dem Handballen wischen – bei gewähltem Stift, es darf NICHT gezeichnet werden.
    if tippe_mitte_von("Stift"):
        time.sleep(0.5)
        tippe_mitte_von("Stift")
        time.sleep(0.5)
    handballen_wischen(0.45, 0.12, 0.5)
    time.sleep(0.8)
    screenshot("09c_handballen.png")
    if tippe_mitte_von("Rückgängig"):
        time.sleep(0.5)
        screenshot("09d_handballen_rueckgaengig.png")

    # Gestrichelte Linie und gestrichelter Pfeil (auf Android 8 früher durchgezogen)
    if tippe_mitte_von("Formen"):
        time.sleep(0.8)
        if tippe_mitte_von("Linie gestrichelt"):
            tippe_mitte_von("Formen")  # Panel schließen
            time.sleep(0.5)
            y = int(hoehe * 0.55)
            adb("shell", "input", "swipe", str(int(breite * 0.15)), str(y), str(int(breite * 0.55)), str(y), "500")
            time.sleep(0.5)
        if tippe_mitte_von("Formen"):
            time.sleep(0.8)
            if tippe_mitte_von("Pfeil gestrichelt"):
                tippe_mitte_von("Formen")
                time.sleep(0.5)
                y = int(hoehe * 0.65)
                adb("shell", "input", "swipe", str(int(breite * 0.15)), str(y), str(int(breite * 0.55)), str(y), "500")
                time.sleep(0.8)
        screenshot("09b_gestrichelt.png")
        # Ein Kreis dazu, dann quer durch Linie, Pfeil und Kreis wischen: stückweise weg.
        if tippe_mitte_von("Formen"):
            time.sleep(0.8)
            if tippe_mitte_von("Kreis"):
                tippe_mitte_von("Formen")
                time.sleep(0.5)
                adb("shell", "input", "swipe", str(int(breite * 0.62)), str(int(hoehe * 0.3)), str(int(breite * 0.82)), str(int(hoehe * 0.65)), "500")
                time.sleep(0.8)
        handballen_wischen(0.35, 0.48, 0.75)
        time.sleep(0.5)
        handballen_wischen(0.72, 0.25, 0.75)
        time.sleep(0.8)
        screenshot("09e_formen_stueckweise.png")

    # Seiten: neue Seite anlegen (Pille zeigt 2/2), dann zurückblättern
    if tippe_mitte_von("Seite hinzufügen"):
        time.sleep(0.8)
        screenshot("10_neue_seite.png")
        tippe_mitte_von("Vorherige Seite")
        time.sleep(0.8)

    # Tafelspiel: Fußballfeld öffnen, Ball Richtung rechtes Tor werfen, Punkt von Hand
    if tippe_mitte_von("Tafelspiel"):
        time.sleep(1.5)
        screenshot("11a_spiel.png")
        mitte_x, mitte_y = int(breite * 0.5), int(hoehe * 0.56)
        adb("shell", "input", "swipe", str(mitte_x), str(mitte_y), str(int(breite * 0.8)), str(mitte_y), "120")
        time.sleep(0.4)
        screenshot("11b_spiel_wurf.png")
        time.sleep(2.5)
        tippe_mitte_von("Punkt für Links")
        time.sleep(0.8)
        screenshot("11c_spiel_punkte.png")
        tippe_mitte_von("Spiel schließen")
        time.sleep(1)

    neue_funktionen(breite, hoehe)
    if not laeuft_noch():
        print("FEHLER: App ist bei den neuen Funktionen abgestürzt", file=sys.stderr)
        sichere_logcat()
        sys.exit(1)

    # Zurück-Taste: darf die App nicht sofort beenden, sondern muss nachfragen
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)
    screenshot("11_zurueck_taste.png")
    if not laeuft_noch():
        print("FEHLER: Zurück-Taste hat die App beendet", file=sys.stderr)
        sichere_logcat()
        sys.exit(1)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)

    # Einstellungen (zeigt auch die gemeldete Auflösung/Dichte und die Oberflächengröße)
    if tippe_mitte_von("Menü"):
        time.sleep(1.5)
        screenshot("12_einstellungen.png")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)
        screenshot("13_zurueck_zur_tafel.png")

    if not laeuft_noch():
        print("FEHLER: App ist während der Bedienung abgestürzt", file=sys.stderr)
        sichere_logcat()
        sys.exit(1)

    sichere_logcat()


if __name__ == "__main__":
    main()
