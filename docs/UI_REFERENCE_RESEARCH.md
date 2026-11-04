# Oryn UI — références, décisions et premier lot de réalisation

**04 novembre 2026.** UI-only development from `feat/oryn-reader-150`. Screenshots referenced below were supplied by the owner in chat; this document does not imply ownership of unrelated third-party designs. Inspired patterns are recreated using native Compose rather than copied screen assets.

## Agreed art direction

- **App light canvas:** warm soft white `#FAFAF8` with white `#FFFFFF` cards and neutral `#F1F2F0` tonal containers. No full-screen `#FEFFDE` ivory cast.
- **Editorial reader:** keep the user's selected paper/sepia/dark/OLED presets and text colors independent from app chrome.
- **Brand:** pink/ivory folded book-leaf motif on dark plum. The approved image was a composite reference; the launcher mark is a **vector rendition**, not a bit-exact recovered original asset. Keep adaptive safe area and monochrome silhouette.
- **App hierarchy:** reading/cover first; a clear primary action; restrained typography; generous whitespace, compact contextual actions; native Android behavior.
- **Dark UI:** dark warm-neutral contextual selection surfaces; do not introduce global glass effects or permanent reader chrome.
- **Unchanged:** three existing floating navigation tabs, import action, routes, application data, EPUB/PDF engines and community security gates.

## Owner-provided visual references

| Reference image | Pattern to borrow | Oryn implementation priority |
|---|---|---|
| Cream surface with deep charcoal pill CTA | Single readable primary action and quiet margins | Shared buttons/reading-resume actions; do not hardcode English |
| "Oryn Reader — Consensus Final" logo sheet | Folded ivory/pink book-leaf brand, strong small-icon recognition, monochrome alternative | Adaptive icon foreground/background/monochrome + home mark |
| Dark "Custom background / Custom font" preference cards | Readable setting title/subtitle, round preview chip, spacious cards | Reader settings; first inspect existing support before adding controls |
| "Reading Now" white library | Oversized featured book, smaller recently-opened cover shelf, readable progress | Home/Library redesign follow-up, preserve three-tab nav |
| Minimal white book detail page | Hero cover, author/title, concise metadata, one CTA, sections | Existing book detail screen follow-up |
| Highlighted passage with context menu | Contextual selection actions, copy/highlight/note/translation | Reader selection toolbar and viewport positioning |
| Dark immersive reader with floating pill tools | Hidden reading chrome, anchored progress controls, calm typography | Reader controls follow-up, keep curl and pagination working |
| Dark library/collections sorting | Organized search, sort and collection affordances | Library follow-up, no new information architecture without permission |

## Verified external references

### Android engineering / interaction

- [Compose text user interactions](https://developer.android.com/develop/ui/compose/text/user-interactions) — plain text is not selectable by default; `SelectionContainer` supplies system selection semantics.
- [Compose August 2026 release](https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html) — `SelectionState` gives programmatic selection in **newer** Compose. Oryn pins BOM **2024.11.00**; **do not directly adopt new APIs without deliberate dependency upgrade and compatibility tests**.
- [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) — 108dp layers, centered 66dp safe zone, distinct monochrome layer.
- [Material 3 Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) — semantic surface roles, accessibility and composable customization.
- [Now in Android (GitHub)](https://github.com/android/nowinandroid) and its [Material 3 case study/Figma files](https://goo.gle/nia-figma) — Compose architecture, dynamic-color and adaptive layouts.
- [Google Material Symbols (GitHub)](https://github.com/google/material-design-icons) — icon consistency reference; existing `OrynIcons`/`HugeIcons` remain preferred, avoiding an additional heavyweight pack.

### UI concepts / community feedback

- [Booki UI case study (Behance)](https://www.behance.net/gallery/242274183/Booki-Mobile-Reading-App-UIUX-Case-Study) — book-cover-first, quiet interface hierarchy.
- [Ebook Reader Mobile Design (Dribbble)](https://dribbble.com/shots/21035903-Ebook-Reader-Mobile-Design) — reader settings microinteractions and dark/light transitions. Use as inspiration, not an exact template.
- [Material You in Jetpack Compose (Android Developers video)](https://www.youtube.com/watch?v=jrfuHyMlehc) — official theming explanation.
- [Text Selection in Jetpack Compose (video)](https://www.youtube.com/watch?v=NRB2j6To0SI) — basic selection container example; **2021 material, not a current selection/gesture API reference**.
- [Reddit: Compose WordIterator & CJK](https://www.reddit.com/r/androiddev/comments/1vmzy3p/composes_worditerator_makes_a_long_press_select/) — community report (not independently reproduced) that CJK word boundaries can select whole clauses; add CJK physical tests.
- [Reddit: Compose selection in lazy text](https://www.reddit.com/r/Kotlin/comments/18fvj45) — user reports selection limitations with offscreen lazy items; treat as investigation lead, not proof about Oryn.

## Current Oryn text-selection code audit

Confirmed by repository inspection at the start of this UI branch:

- `ReaderPager.kt` implements book-global source offsets, `detectTapGestures`, `awaitLongPressOrCancellation`, page-curl gesture arbitration, word/sentence bounds and selection anchors.
- `ReaderAnnotations.kt` implements 48dp selection handles, a contextual selection menu and persistent highlight/note actions.
- This custom design is **not interchangeable** with a trivial `SelectionContainer`: annotations and navigation use original offsets across page composition.
- **UI issue identified:** the former 272dp menu top row combined four 44dp targets, spacing and a 60dp note action; its content could exceed the available width, while color targets were under the project 48dp minimum. A two-row action layout and 48dp targets are included in the initial patch.
- **Gesture issue identified:** drag offset was fixed in physical pixels (`30f`), not density-aware dp. The initial patch uses dp converted to pixels.
- **Positioning issue identified:** action menu Y had no viewport clamp. The initial patch bounds it on-screen.
- **Not verified yet:** physical long-press, selection handles over page curl, auto-scroll, CJK/RTL/bidi, emoji/graphemes, reduced motion and TalkBack traversal. No claim of successful Android device testing.

## Gate and rollback

Run Gradle JVM regression tests, lint and debug/release builds in CI. After green CI, review branch PR and only then consider merge into `feat/oryn-reader-150`. Do **not** merge into `main`, remove stable data, deploy community backend or publish a release. Test the APK on-device only when the planned manual-test milestone is reached.

To roll back the UI batch, keep the development branch at its previous SHA and close the UI PR; do not reset shared branches or uninstall installed copies.
