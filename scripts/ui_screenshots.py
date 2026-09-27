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


def tippe_mitte_von(beschreibung: str) -> bool:
    adb("shell", "uiautomator", "dump", "/sdcard/dump.xml")
    adb("pull", "/sdcard/dump.xml", "dump.xml")
    baum = ET.parse("dump.xml")
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


def main():
    os.makedirs(AUSGABE_ORDNER, exist_ok=True)
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(5)

    if not laeuft_noch():
        print("FEHLER: App ist nach dem Start nicht mehr am Laufen (Absturz?)", file=sys.stderr)
        sichere_logcat()
        sys.exit(1)

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

    # Seiten: neue Seite anlegen (Pille zeigt 2/2), dann zurückblättern
    if tippe_mitte_von("Seite hinzufügen"):
        time.sleep(0.8)
        screenshot("10_neue_seite.png")
        tippe_mitte_von("Vorherige Seite")
        time.sleep(0.8)

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
