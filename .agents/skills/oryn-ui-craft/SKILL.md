---
name: oryn-ui-craft
description: Design, implement, or audit Oryn's Android Jetpack Compose UI, Material 3 surfaces, branding, reader controls, component hierarchy, typography, or accessibility.
---

# Oryn UI craft

Use for UI changes, not for unrelated repository work.

1. Read `AGENTS.md`, `DESIGN.md`, `docs/design-system.md` and `docs/UI_REFERENCE_RESEARCH.md` before editing. Inspect the actual Compose screen and existing components before introducing new dependencies.
2. Retain app routes, four-tab floating navigation (Home, Library, Discover, Favorites; owner-approved 2026-11-05), separate import action, selection/deletion semantics, FR/EN translations, persisted DataStore enums, offline usage and disabled community networking.
3. Separate **chrome** (`MaterialTheme.colorScheme`) from **editorial reader** (user's chosen page preset). Light chrome background is warm soft white `#FAFAF8`; do not replace the reader's sepia/parchment presets.
4. Translate the supplied reference set into patterns, not pixel-for-pixel iOS cloning: prominent book covers; quiet metadata; a clear resume-reading CTA; native Android back navigation; bottom-sheet reading preferences; contextual text actions.
5. Reuse `OrynSpace`, `OrynShape`, existing `OrynIcons`/`HugeIcons` and Material 3. Only propose new icon packs or component libraries after checking licenses, current dependencies, application size and Android interoperability.
6. All interactive targets >=48dp; verify contrast at least 4.5:1 for normal text, font scaling, TalkBack description, touch/keyboard/back behavior and reduced-motion configuration.
7. No unmotivated shadows, glows, glassmorphism, rainbow gradients, invented labels, looping animations or hidden essential actions. Keep one dominant action per surface.
8. For each batch: identify an observed UI problem, implement the smallest coherent change, inspect the diff, run targeted tests and `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` (or observe exact CI equivalents). Inspect failures; do not claim device validation without device evidence.
9. Commit atomically to a feature branch and keep rollback SHA. Report tested vs untested states separately; don't merge stable branches without approval.

Primary references:
- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/develop/ui/compose/accessibility
- https://github.com/android/nowinandroid
- https://www.behance.net/gallery/242274183/Booki-Mobile-Reading-App-UIUX-Case-Study
