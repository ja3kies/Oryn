"""Exercise the installed app on the clean CI emulator and retain actual screen captures."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from tempfile import TemporaryDirectory

PACKAGE = "com.example.oryn.reader"
OUT = Path("build/device-captures")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=30)


def wait_for(*labels):
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        adb("shell", "uiautomator", "dump", "/sdcard/oryn-window.xml")
        tree = ET.fromstring(adb("shell", "cat", "/sdcard/oryn-window.xml"))
        for node in tree.iter("node"):
            if any(node.get(key) in labels for key in ("text", "content-desc", "resource-id")):
                return node
    (OUT / "missing-control.xml").write_bytes(ET.tostring(tree))
    capture("missing-control")
    raise RuntimeError(f"Missing control: {labels}")


def tap(*labels):
    node = wait_for(*labels)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def capture(name):
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))


adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
wait_for("Passer", "Skip")
capture("01-onboarding")
tap("Passer", "Skip")
wait_for("Conseil de lecture", "Reading tip")
capture("02-home")
tap("Conseil de lecture", "Reading tip")
wait_for("Ajoute ton premier livre avec le bouton +", "Add your first book with the + button")
capture("03-reading-tip")
tap("Bibliothèque", "Library")
wait_for("Bibliothèque", "Library")
capture("04-library")
tap("Découvrir", "Discover")
wait_for("Découvrir", "Discover")
capture("05-discover")

# Use Android's real document picker and a uniquely numbered local text.
# The same visible passage after a cold start checks restoration independently of Room.
title = "Carnet du soir"
filename = title + ".txt"
with TemporaryDirectory() as directory:
    source = Path(directory) / filename
    source.write_text("\n\n".join(
        f"Passage {number:03}. Sous le pont, le reflet des fenêtres tremble sur la rivière. "
        "Le dernier train traverse la ville et le quai retrouve son silence."
        for number in range(1, 181)
    ), encoding="utf-8")
    adb("push", str(source), "/sdcard/Download/" + filename)

tap("Bibliothèque", "Library")
tap("Importer un livre", "Import a book", "Ajouter un document", "Add a document")
tap("Show roots", "com.google.android.documentsui:id/roots_toolbar", "Afficher les racines", "Afficher les emplacements")
tap("Downloads", "Téléchargements")
tap(filename)
wait_for(title, "Open " + title, "Ouvrir " + title)
capture("06-imported-book")
tap(title, "Open " + title, "Ouvrir " + title)


def visible_passage(previous=None):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        adb("shell", "uiautomator", "dump", "/sdcard/oryn-window.xml")
        nodes = ET.fromstring(adb("shell", "cat", "/sdcard/oryn-window.xml")).iter("node")
        for node in nodes:
            text = node.get("text", "")
            if re.search(r"Passage \d{3}", text) and text != previous:
                return text
    capture("reader-stalled")
    (OUT / "reader-stalled.xml").write_bytes(adb("shell", "cat", "/sdcard/oryn-window.xml"))
    raise RuntimeError("Reader did not show the expected passage")


opening = visible_passage()
display = adb("shell", "wm", "size").decode()
w, h = map(int, re.findall(r"(\d+)x(\d+)", display)[-1])
adb("shell", "input", "swipe", str(w * 4 // 5), str(h // 2),
    str(w // 5), str(h // 2), "350")
later = visible_passage(previous=opening)
capture("07-reading")
adb("shell", "input", "keyevent", "BACK")
wait_for(title, "Open " + title, "Ouvrir " + title)
adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
tap("Bibliothèque", "Library")
tap(title, "Open " + title, "Ouvrir " + title)
assert visible_passage() == later, "Reader reopened at a different passage"
capture("08-restored-passage")
