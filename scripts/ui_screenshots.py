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

    if not laeuft_noch():
        print("FEHLER: App ist während der Bedienung abgestürzt", file=sys.stderr)
        sichere_logcat()
        sys.exit(1)

    sichere_logcat()


if __name__ == "__main__":
    main()
