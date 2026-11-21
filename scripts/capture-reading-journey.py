"""Exercise the installed app on the clean CI emulator and retain actual screen captures."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

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
            if any(node.get(key) in labels for key in ("text", "content-desc")):
                return node
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
