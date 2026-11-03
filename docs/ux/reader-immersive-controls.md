# Reader immersive controls

Implemented in the existing ReaderScreen on `feature/community-epub-catalog`, 2026-11-03. This uses Oryn's existing icons, palette, type, ReaderSheet and preferences. It is not a separate reader or experimental route. The supplied description is an interaction reference; no screenshot rendering or pixel comparison is claimed.

## Interaction contract

`ReaderControlsState` is the sole application chrome state: HIDDEN, COMPACT, EXPANDED. ReaderSheet's former independent expansion flag is removed. Material's sheet animation/gesture state remains internal to that standard component.

| Input | HIDDEN | COMPACT | EXPANDED |
| --- | --- | --- | --- |
| Reading-surface tap | Reveal compact controls | Hide controls | Sheet's modal surface handles outside dismissal |
| Menu | Unavailable | Open the existing full ReaderSheet | No additional panel |
| Android Back | Leave reader | Hide controls | Dismiss to compact |
| Sheet swipe, scrim, close handle | — | — | Return to compact |

The floating top bar provides navigation, real chapter title (book title if no chapter is known), real remaining chapter pages when available, session timer and the existing book-bookmark toggle. The progress pill labels **book** and **chapter** progress separately. The shortcut pill opens contents, annotations, layout, typography and the full sheet. Layout/typography focus their existing sections; all settings remain available in Essentials/Appearance. Search, seeking, previous/next chapter, PDF reflow, theme, accent, brightness, font, spacing, alignment, margins and scrolling remain wired to their original callbacks.

Single taps anywhere on a non-interactive reading surface now control chrome; edge taps no longer silently turn pages. Swipe/curl, vertical paging, links, annotation hit testing, long-press selection, seeking and chapter navigation remain available. Toolbar/pill pointer handlers consume their own regions; a backdrop catches taps in the reserved gutters without intercepting document gestures; sheet and auxiliary dialogs do not stack duplicate reader panels.

The bookmark action reuses Oryn's existing **book favorite/bookmark**, not a newly invented per-page bookmark schema. Annotations opens the existing list; long-press text selection still starts highlights/notes. Unsupported format actions remain disabled rather than pretending to work. No Room schema, DataStore key, persisted enum, Gutendex, community Auth, network gate or transfer path is changed.

## Reading data and geometry

- Chapter metadata uses the actual visible page start, independently of the existing persisted book-progress fraction. Chapter percentage describes position within chapter character boundaries; it is not a time estimate.
- EPUB/TXT chapter pages are calculated from the current real pagination and TOC. PDF chapter pages use actual page/outline boundaries. The label explicitly counts pages **after this page**. Missing or invalid bounds omit the information. The former fixed reading-speed estimate is removed from the sheet.
- The timer measures this foreground book session with elapsedRealtime and a RESUMED lifecycle scope; pause/reset are explicit. It excludes background time, survives saved-state recreation and writes no reading statistics to Room. Timer ticks are read by the timer dialog rather than the whole document composition.
- Compact chrome uses stable, font-scale-aware top/bottom gutters in every chrome state. This deliberately reserves whitespace even while hidden, so showing controls does not change the pagination viewport or recreate the document/selection subtree. System bars use ignoring-visibility insets; cutout and side insets are accounted for separately. HTML also uses stable system insets.
- Short viewports prioritize the reading line over the old duplicate page counter. Width/height passed to pagination are bounded to avoid negative constraints in constrained windows. Extremely small split windows and large fonts still require real-device review; no universal visual guarantee is inferred from compilation.
- Controls are opaque, with Oryn's dark semantic chrome independent of light/dark/sepia page colors. Compact actions and sheet icon targets are at least 48 dp; narrow sheet groups wrap and shortcut overflow can scroll horizontally. Page-theme contrast is checked numerically for all five existing presets.
- Compact fades and the existing tab transition honor reduced-motion settings. Expansion/collapse uses the Material modal sheet, including scrim, Back and downward drag. The compact panel is absent while expanded. No screenshot, device animation timing or performance benchmark is claimed.

## Keyboard and accessibility

The document's semantic action exposes show/hide controls to TalkBack. Enter/Space activates the document only while it owns keyboard focus; child buttons retain their own activation. F10 toggles chrome; Escape collapses/hides without leaving the book. Repeated key-down events are consumed without repeated transitions. Back exits only once chrome is hidden. The full sheet focuses its labelled 48 dp close handle; Tab navigation, modal focus containment, native Back and scrollable settings are retained. Labels are translated FR/EN, including selected bookmark actions and sheet tabs.

## Local validation

Final command:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease --continue --console=plain
```

Final local results (2026-11-03):

| Check | Result |
| --- | --- |
| JVM | 125 discovered: 124 passed, 0 failed, 1 skipped (existing isolated Auth probe) |
| Debug / release APK | Both assembled; release is unsigned |
| Android test APK | Compiled; 9 new reader scenarios plus 5 existing community scenarios |
| Device execution | Not executed: `adb devices -l` returned no devices |
| Debug / release lint | 0 errors; 93 / 85 warnings, unchanged baseline |
| Release community guard | Passed: simulated fixtures excluded, permissions and disabled callback verified |
| Debug APK signature | `apksigner verify` passed |
| Diff and sensitive-token pattern scan | Passed; no matching credentials in changed files, no dependency/manifest/Room/community changes |

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (28,036,006 bytes), SHA-256 `25656d7165a8b8c77b7bb69260c0b571166f5497b9e6fcd7a67e0809e91c0154`.
Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`, SHA-256 `0775f70e2be53d18ccb852e79167e3e647d476d5cc6359e8bb2e9f5a28bc593f`.

Logs remain outside Git at `/tmp/oryn-immersive-*.log`. No lint suppression or weakened checks were introduced. Secret-pattern scanning is a limited static check, not proof that every possible secret format is absent. No measured performance improvement is claimed. The device checklist below remains the acceptance gate for touch, visual and accessibility behavior.

New JVM regressions cover transition/Back sequences, repeated expansion, outside taps, temporary tools, chapter boundaries, missing data, PDF and EPUB remaining pages, stable gutters, measured timer intervals/pause/reset and all page-theme contrast pairs. Existing Auth/catalogue/session tests remain in the global run.

Nine Android scenarios exercise the actual ReflowReader, compact controls and ReaderSheet in a test Activity without Room or a real document: tap reveal/hide with stable page and progress callbacks; edge/button tap isolation; menu/no duplicate panel and Back sequence; swipe-down; layout/typography targeting; light and dark page bounds; sepia at 200% text in a short viewport; keyboard activation and Escape. Bounds checks include TextLayoutResult.hasVisualOverflow. These are prepared tests, **not device results** when ADB is empty.

## Device acceptance checklist — not executed

- [ ] Install the current debug APK on an actual device/emulator. Test EPUB/TXT, native PDF, PDF text mode and HTML; include an illustrated EPUB and a document with no TOC.
- [ ] Tap center and edges: one tap reveals compact, another outside tap hides. Swipe still changes pages; links, image interactions, text selection and annotation handles keep their original gestures.
- [ ] Tap every toolbar action and blank pill area: no page turn or unintended progress write. Verify book bookmark in the existing Favorites collection, annotations/notes, contents, search, seeking and previous/next chapter.
- [ ] Menu opens exactly one full ReaderSheet. Test scrim, swipe-down, close handle, Android Back, predictive Back where available and Escape. Each returns to compact; the next Back hides, then Back exits. Auxiliary dialogs must return appropriately without stacked panels.
- [ ] Record reading offset, selection, bookmark and settings before repeated show/hide cycles, dialogs, rotation and font/layout changes. Check no unintended changes and no reload on control taps.
- [ ] Light, dark and beige/sepia; narrow widths, landscape, gesture/three-button navigation, cutouts, large fonts and small split windows. Check full glyphs, page footer fallback, progress labels, all hit targets, horizontal shortcut overflow and sheet scrolling.
- [ ] TalkBack traversal/labels, keyboard Tab/Shift-Tab and Enter/Space on both document and focused controls. Verify reduced motion on-device; no duplicate reachable control panel.
- [ ] Timer pause/resume/reset, rotation, background/foreground and process restoration. Confirm it remains measured session time rather than remaining-time prediction.
- [ ] Confirm community Auth/transfers remain disabled; this reader work enables no backend feature.
