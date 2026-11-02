#!/usr/bin/env python3
"""Check built APKs without installing, executing them or contacting a backend.

Run after assembleDebug/assembleRelease with ANDROID_HOME set. Requires Python 3
and the SDK's aapt2. This is a regression guard, not a full security audit.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MARKERS = (
    "DevelopmentCommunitySource", "EPUB de démonstration ",
    "Demonstration EPUB ", "Auteur de démonstration", "Demonstration author",
    "Fiche simulée pour vérifier le catalogue.",
    "Simulated details for testing the catalog.",
)


def require(condition, message):
    if not condition:
        raise SystemExit("FAIL: " + message)


def markers_in(apk):
    found = set()
    with zipfile.ZipFile(apk) as archive:
        dex_names = [name for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)]
        require(dex_names, "APK contains no DEX; cannot validate fixture exclusion")
        for name in dex_names:
            data = archive.read(name)
            require(data.startswith(b"dex\n"), "Unsupported DEX format")
            found.update(marker for marker in MARKERS if marker.encode("utf-8") in data)
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-apk", type=Path, default=ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk")
    parser.add_argument("--debug-apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--aapt2", type=Path)
    args = parser.parse_args()
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    candidates = sorted((Path(sdk) / "build-tools").glob("*/aapt2")) if sdk else []
    aapt2 = args.aapt2 or (candidates[-1] if candidates else None)
    require(aapt2 is not None and aapt2.is_file(), "Set ANDROID_HOME or --aapt2")
    require(args.release_apk.is_file() and args.debug_apk.is_file(), "Build both APKs first")
    require(not markers_in(args.release_apk), "Development fixture marker packaged in release")
    # Positive control: the guard must actually detect fixture data in this non-minified build.
    require(set(MARKERS).issubset(markers_in(args.debug_apk)), "Debug positive control missing fixture markers")
    manifest = subprocess.check_output([str(aapt2), "dump", "xmltree", str(args.release_apk),
                                        "--file", "AndroidManifest.xml"], text=True)
    manifest = manifest.replace("http://schemas.android.com/apk/res/android:", "android:")
    permissions = re.findall(r'E: uses-permission[^\n]*\n\s*A: android:name[^\n]*="([^"]+)"', manifest)
    require(set(permissions) == {"android.permission.INTERNET",
        "com.example.oryn.reader.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}, "Unexpected release Android permissions")
    # AndroidX adds this app-scoped signature permission; it grants no storage/device capability.
    declarations = re.split(r'(?m)^\s*E: permission \(', manifest)[1:]
    require(len(declarations) == 1 and re.search(r'android:protectionLevel[^\n]*=0x00000002',
        declarations[0].split("E: uses-permission")[0]), "App receiver permission must remain signature-only")
    require(re.search(r'android:usesCleartextTraffic[^\n]*=false', manifest), "Cleartext traffic permitted")
    require(not re.search(r'android:debuggable[^\n]*=true', manifest), "Release APK is debuggable")
    activities = re.split(r'(?m)^\s*E: activity \(', manifest)[1:]
    callbacks = [part for part in activities if "CommunityAuthCallbackActivity" in part.split("E: intent-filter")[0]]
    require(len(callbacks) == 1, "Expected one dedicated callback activity")
    callback = callbacks[0]
    require(re.search(r'android:enabled[^\n]*=false', callback), "Release Auth callback enabled")
    require("E: intent-filter" not in callback, "Release Auth callback must expose no link filter")
    print("PASS: release excludes fixtures; debug positive control, permissions and disabled callback without link filter verified")


if __name__ == "__main__":
    main()
