---
name: oryn-adaptive-logo
description: Modify Oryn's launcher logo, themed icon, icon layers, in-app wordmark, or brand-color assets without affecting user data.
---

# Android adaptive icon integrity

1. Base artwork on the supplied approved **pink/ivory folded-book-leaf** symbol. Preserve strong figure/ground separation and readable geometry at 24dp.
2. Keep a full-bleed 108x108dp background and a foreground silhouette inside the centered 66x66dp safe area. Avoid embedded corner masks/shadows on the adaptive foreground. The launcher performs the mask.
3. Always maintain `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_background.xml`, and `drawable/ic_launcher_monochrome.xml` together, with the same recognizable silhouette.
4. Check `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` references; Android 24/25 legacy PNG variants are separate and must be checked before a final release.
5. Keep the in-app logo and launch icon consistent while respecting reader-specific preset colors and user-selectable theme. Treat a traced vector from a composite screenshot as a rendition rather than pretending to possess an original SVG.
6. Validate AAPT2 resource linking, Android launch on at least one API >=26 emulator/device, monochrome themed icon and legacy icon before calling the brand final.
7. Do not publish icon assets to a public package/library or claim an exact pixel reproduction without confirming the source graphic.

Primary reference:
- https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
