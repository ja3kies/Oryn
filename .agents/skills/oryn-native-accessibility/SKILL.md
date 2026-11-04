---
name: oryn-native-accessibility
description: Audit Jetpack Compose Android UI and fix TalkBack, WCAG contrast, minimum target sizes, focus, dialog layering, navigation, reader sheet/pill and responsive layouts.
---

# Oryn Native Accessibility — production checklist

This local skill adapts **Android-specific** principles from:
- [Platform Design Skills: Android Design Guidelines](https://agenticskills.io/skills/platform-design-skills) (MIT, rules covering Material 3 and WCAG 2.2).
- [UI/UX Pro Max](https://agenticskills.io/skills/ui-ux-pro-max) (MIT, mobile pre-delivery checklist).
- [Android accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) and [Material 3 color accessibility](https://developer.android.com/develop/ui/compose/designsystems/material3).

Do not install web-only React/Tailwind skills or import their CSS into native Compose. This file is original Oryn guidance, not a copy of a third-party skill.

## For each screen and component

1. Inventory clickable controls, hover/long press, touch, gestures, text fields, dialogs, book cover and fallback.
2. Use built-in `Button`, `IconButton`, `IconToggleButton`, `FilterChip`, `Slider`, `ModalBottomSheet` whenever their semantics match; avoid inventing custom controls.
3. Ensure effective interactive hit targets >= 48×48dp; check any `Modifier.size`, `width`, `height`, `clickable`, `selectable` and `pointerInput` combination.
4. Icon-only CTA: descriptive TalkBack name; avoid redundant decorative text announcements. Radio/checkbox/tabs announce current selection. User-controlled ratings must both react to taps and persist.
5. Test text >=4.5:1 and meaningful UI/graphics >=3:1 **after alpha compositing**, including selected, unselected, dark, light, reader parchment, modal overlays. Don't infer from brand hex swatches alone.
6. Complex sheets: no stacked dialogs, escape and Android back, accessible close action, keyboard input, window insets, scroll and font scaling without clipping.
7. Paged reader: do not capture long presses during page turning, and never claim gesture correctness from JVM tests. Full selection needs device qualification.
8. Cropped image warnings: preserve complete book-cover art with ContentScale.Fit; background letterboxing must be neutral (not neon accent) and accessible fallback has text.
9. Book metadata may be missing from EPUB AND Open Library. Label provenance, never fabricate rating/year, expose local editing for year/description. Personal ratings are not community scores.
10. Check same screen in light, dark, large font, TalkBack, portrait/landscape, empty/loading/failed state. Validate screenshot and tests separately.

## Gate

Read `AGENTS.md`, `DESIGN.md`, `docs/design-system.md`, `docs/UI_SCREENSHOT_UX_AUDIT_2026-11-04.md`.
Every patch requires diff review + targeted unit tests + `:app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease`; Android test compilation is not Android execution. Open a draft PR, keep the signed APK reproducible, and protect stable branches.
