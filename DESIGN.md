# Oryn — design system

**Version:** 2.0 · **Updated:** 30 October 2026  
**Source of truth:** this document, `ui/theme/Theme.kt`, `OrynColorScheme.kt`, and `OrynDesignTokens.kt`.

Oryn is a personal reading space. The interface should feel calm, editorial, tactile and unmistakably book-focused. It borrows Apple's design discipline—clarity, purpose, agency, spatial consistency, restrained motion and careful typography—without copying iOS screens or replacing Android conventions.

## 1. Design principles

1. **Purpose:** every visual element earns its place. Book content is the hero.
2. **Agency:** users control their library, reading preferences, sources and sharing choices.
3. **Clarity:** one clear primary action per screen; labels describe the action.
4. **Familiarity:** use Android interaction conventions and predictable navigation.
5. **Flexibility:** respect font scaling, screen sizes, light/dark mode and reduced motion.
6. **Simplicity:** remove noise, not useful context. Keep common actions visible and advanced controls one level deeper.
7. **Craft:** alignments, typography, cover rendering, touch feedback and transitions must feel deliberate.
8. **Delight:** comes from a coherent, responsive reading experience—not decorative animation.

## 2. Brand palette

The five colours below are the approved Oryn palette. Light Yellow is an approximate value sampled from the supplied reference; preserve the chosen value unless the original source asset is provided.

| Token | Hex | Intended role |
|---|---|---|
| Deep Teal | `#0F3A3A` | Core identity, primary text and actions in light mode |
| Light Yellow | `#FEFFDE` | Warm canvas, negative space and inverse text |
| Cherry Rose | `#B5185F` | Signature accent and occasional expressive moments |
| Lime Cream | `#D0E86E` | Reading progress, selection and primary accent surfaces |
| Strong Cyan | `#35C1C2` | Interactive feedback and secondary emphasis |

### Colour hierarchy

- Deep Teal + Light Yellow define the app.
- Lime Cream carries progress and selection.
- Strong Cyan communicates interactive/secondary states.
- Cherry Rose is rare and deliberate.
- Do not put all three accents at full strength on one screen.
- The black background used to present the reference swatches is not an Oryn brand token.

### Contrast and semantic use

- Deep Teal on Light Yellow: primary text and controls.
- Deep Teal on Lime Cream: readable progress/selection labels.
- Light Yellow on Cherry Rose: readable text on a Cherry Rose surface.
- Deep Teal on Strong Cyan: readable labels on a cyan surface.
- Never use Cherry Rose or Strong Cyan as small text on Light Yellow.
- Never use colour alone for reading state, source type, errors, or selection.
- Verify actual foreground/background pairs after alpha, overlays and gradients. Target WCAG AA: 4.5:1 for normal text, 3:1 for large text and meaningful UI graphics.

## 3. Semantic theme

Use `MaterialTheme.colorScheme` for standard UI and `OrynColors.editorial` for brand-specific moments. Do not hardcode palette values inside screens.

### Light mode

| Semantic token | Value |
|---|---|
| Background | Light Yellow `#FEFFDE` |
| Surface | Warm near-white `#FFFFEE` |
| Primary / onPrimary | Deep Teal / Light Yellow |
| Secondary / onSecondary | Strong Cyan / Deep Teal |
| Tertiary / onTertiary | Cherry Rose / Light Yellow |
| Progress / selected surface | Lime Cream / Deep Teal |

### Dark mode

| Semantic token | Value |
|---|---|
| Background | Deep Teal-dark `#081C1C` |
| Surface | Teal card `#0D2727` |
| Primary / onPrimary | Light Yellow / Deep Teal |
| Secondary / onSecondary | Strong Cyan / Deep Teal |
| Tertiary | Brightened Cherry Rose for dark surfaces |
| Progress / selected surface | Lime Cream / Deep Teal |

Dark values are supporting theme values, not additional brand colours. Keep reader-specific page themes independent from app chrome.

### Accent preference compatibility

Existing DataStore enum names `ORANGE`, `FUCHSIA`, `GREEN`, `BLUE`, and `DYNAMIC` are persisted. Never rename or remove them. Their displayed labels and mapped colours may be aligned to the approved palette, but stored values must remain readable.

## 4. Typography

- Use the Android platform sans-serif for app chrome and controls. It is native, ships with Android, and respects system font scaling.
- Use size-specific hierarchy: large titles have tighter tracking; body text stays near normal tracking with comfortable leading.
- Suggested scale: page title 28–34 sp, section 20–22 sp, card title 17–19 sp, body 14–16 sp, metadata 11–13 sp.
- Prefer weight and spacing over all-caps labels. Uppercase is for short metadata only, never decorative headings.
- Reader typography is independent and user-configurable.
- OpenDyslexic remains an optional reader font, not the default app UI font.
- Do not claim Hanken Grotesk is bundled until the font files are actually present in `res/font`. No font may be fetched at runtime.

## 5. Layout, spacing and shape

- Base grid: 4 dp. Use `OrynSpace` tokens (4, 8, 12, 16, 24, 32, 40, 48 dp).
- Default screen inset: 16 dp.
- Separate major sections by 16–24 dp.
- Shapes: control 12 dp, medium 20 dp, card 24 dp, large 28 dp, sheet 32 dp, pill 100 dp. Use `OrynShape`.
- Minimum touch target: 48 × 48 dp.
- Prefer whitespace and tonal surfaces to decorative separators.
- Keep covers at their original aspect ratio. Avoid cropping meaningful cover artwork.
- Layout must survive narrow screens, landscape where supported, and Android font scaling.

## 6. Surfaces and depth

- Prefer opaque tonal surfaces. Do not make glassmorphism the default card treatment.
- Avoid coloured outlines, glow, excessive blur and stacked translucent surfaces.
- Use subtle elevation only when it clarifies hierarchy; no shadows in dark mode.
- A cover can have a quiet physical lift from its surface, but the surrounding card should not compete with the cover.
- Floating navigation stays visually distinct from scrolling content and retains its current structure.

## 7. Navigation and screen roles

Do not change the existing navigation structure without explicit approval:
- exactly three tabs in the floating navigation bar;
- separate import (+) action;
- Home, Library, Bookmarks & Notes, Settings, reader and catalogue flows remain available through their current routes/entry points.

Screen priorities:
- **Home:** resume reading first; then recent/curated shelves and lightweight personal context.
- **Library:** fast search, sort/filter and predictable grid/list presentation. Covers lead; metadata supports.
- **Book details:** cover, title, author, reading state and one obvious primary action.
- **Reader:** text first; controls appear when needed and stay quiet.
- **Bookmarks & Notes:** make saved passages and annotations easy to scan and reopen.
- **Settings:** grouped by task, not by implementation detail.

## 8. Motion and feedback

Motion should clarify cause and effect, not decorate the screen.

- Respond immediately to touch-down with restrained pressed feedback.
- Use Compose springs for interactions that can be interrupted or redirected.
- Default to critically damped/no-bounce motion. Use a little bounce only when the user's gesture carries momentum.
- A sheet or panel must enter and exit along the same spatial path.
- Never block input merely because an animation is running.
- Use short transitions (roughly 150–300 ms) for simple state changes; spring settling is not a fixed-duration animation.
- Haptics are reserved for meaningful events (selection, completion, destructive confirmation), not every tap.
- Respect Android's animator duration scale and Oryn's `MotionPreferences`. Reduced motion should keep feedback but avoid large movement, parallax and overshoot.
- No animation triggered only by scrolling. No looping decorative motion.

## 9. Accessibility and interaction

- Touch targets at least 48 dp.
- TalkBack descriptions for icon-only controls; logical traversal order.
- Support system font scaling and sufficient contrast.
- Selected, disabled, loading, error and progress states must be distinguishable without colour alone.
- Destructive actions need clear labels and a recovery/confirmation strategy appropriate to their impact.
- Avoid hidden gesture-only actions; provide visible alternatives for important operations.

## 10. Component guidance

- **Primary button:** Deep Teal pill in light mode / Light Yellow pill in dark mode, with contrasting text.
- **Secondary button:** quiet tonal surface; no bright outline.
- **Selection:** Lime Cream surface with Deep Teal content unless the selected accent preference changes the accent.
- **Progress:** Lime Cream fill with a clearly visible track and percentage/status label where useful.
- **Book cover:** preserve aspect ratio, render crisply, use `coverFrame()` when appropriate.
- **Cards:** warm solid surface in light mode, teal surface in dark mode; 20–24 dp radius.
- **Icons:** continue using the existing rounded Tabler/Lucide-derived `OrynIcons` / `HugeIcons` set. Do not replace the logo or protected navigation icons.
- **Mascot:** retain as a brand character, but never let it obscure reading actions or content.

## 11. Do / don't

### Do
- Let the book cover be the most expressive object on a screen.
- Use the palette with clear semantic roles.
- Make the next reading action obvious.
- Use motion to communicate hierarchy and state.
- Keep reading surfaces distraction-free.
- Preserve existing user data and preference keys.

### Don't
- Revert to monochrome chartreuse-only styling.
- Use gradients as text, neon glow, glass cards everywhere or decorative uppercase labels.
- Introduce unnecessary dependencies or a new design framework.
- Add animation without a user-visible purpose.
- Change the logo, floating navigation structure or protected icons without permission.
- Hardcode new UI strings; add French and English entries through `i18n/I18n.kt`.

## 12. Implementation checklist

- [ ] All brand colours are centralized in theme tokens.
- [ ] Light and dark themes use the approved palette.
- [ ] Existing DataStore enum names and keys remain unchanged.
- [ ] Contrast is measured for text, controls, selection and progress.
- [ ] Screens work with system font scaling and narrow widths.
- [ ] Touch targets and TalkBack descriptions are present.
- [ ] Reduced-motion preferences are respected.
- [ ] Reader themes remain independent of app chrome.
- [ ] Local, external and community catalogue items remain distinguishable.
- [ ] No new dependency is added without a concrete need.
- [ ] `./gradlew assembleDebug` passes before delivery.
- [ ] Review on a real Android device before merging.

---

## Reference palette

- Deep Teal — `#0F3A3A`
- Light Yellow — `#FEFFDE` (approximate reference value)
- Cherry Rose — `#B5185F`
- Lime Cream — `#D0E86E`
- Strong Cyan — `#35C1C2`
