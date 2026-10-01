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
