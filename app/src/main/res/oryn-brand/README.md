# Oryn — Logo Kit

Production kit for the Oryn mark (arch, four-point star, two-tone leaves) and the wordmark **Oryn** with its tagline *« Des histoires à votre rythme. »*.
The geometry of the symbol was **measured on the supplied logo** (`source/reference-raster.png`) and rebuilt as clean Bézier outlines (overlap with the reference about 97–99 % per shape, tips included); the concept and proportions are unchanged. What changed is the **colour system**: a warmer, higher-contrast palette with no blue and no violet (see below).

## Contents

| Folder | What is inside |
|---|---|
| `source/` | Layered master (`oryn-symbol-master.svg`: leaf-l-base, leaf-r-base, leaf-l, leaf-r, arch, star + hidden guides), master logo, `geometry.json` (fitted parameters), the reference raster |
| `svg/symbol/` | Symbol on light / on dark, simplified (≤ 32 px), monochrome black / white / 3 greys, simplified monochrome |
| `svg/app-icon/` | App-icon tile (forest), simplified tile, light tile, 3 monochrome tiles |
| `svg/wordmark/` | Wordmark in brand ink, cream, black, white, 3 greys |
| `svg/logo/` | Primary logo (tile + wordmark + tagline), stacked, stacked + tagline, horizontal — on light, on dark, monochrome × 5 |
| `svg/themes/<direction>/` | 5 colour directions × light / dark × symbol / horizontal / stacked, plus the app icon of each direction |
| `svg/android/` | Adaptive layers on a 108 dp canvas: foreground, background, monochrome |
| `png/` | Transparent exports (symbol, app-icon incl. `favicon.ico`, wordmark, logo, themes, android) |
| `previews/` | 4 presentation boards (**not production files**) |
| `tokens/` | `oryn-brand-tokens.json`, `.css`, `.xml` (Android colour resources), `OrynBrandColors.kt` |
| `android-assets/` | Drop-in resources: vector drawables (foreground, monochrome, notification), `mipmap-anydpi-v26` adaptive XML, legacy mipmaps hdpi → xxxhdpi, Play Store icon |

Inventory: **87 SVG**, **205 PNG** (+ 17 Android resources). No gradients, shadows or effects anywhere: every file is flat colour, plain paths, no external dependency, no live text (wordmark and tagline are outlines).

## Colour system — five static brand directions (no blue, no violet)

Reading app, stories, growth: the original deep green was kept as a **family** but made richer and warmer. Dull teal-green and blue-ish sage became *emerald-night*, *cream* and *fresh sage-green*, with a *saffron* star as the single bright accent. Four alternative directions share the same structure. All values below are exact.

| Direction | App-icon tile | On tile: arch · leaf top · leaf lower · star | Dark surface | Light surface · arch · leaf top · leaf lower · star | Wordmark light / dark |
|---|---|---|---|---|---|
| **Forêt** (default) | `#0B2C25` | `#FFF4DC` `#EFE8CC` `#7EC2A0` `#FFB933` | `#0B2C25` | `#FBF6EA` · `#0F3D33` `#1D6A54` `#3D9670` `#C97A00` | `#0B2C25` / `#FFF4DC` |
| **Terracotta** | `#A9402A` | `#FFF1E2` `#F8DCC6` `#F2B592` `#FFD36B` | `#2A120C` | `#FFF7EF` · `#7B2A18` `#B4452A` `#D2693C` `#B87500` | `#3A1710` / `#FFF1E2` |
| **Safran** | `#F4B52E` | `#0E352C` `#0E352C` `#1F6B54` `#0E352C` | `#1D1707` | `#FFFAEC` · `#0E352C` `#165A47` `#C27A00` `#B87500` | `#10281F` / `#FFF1CF` |
| **Menthe** | `#BFE5CD` | `#0E3B30` `#1F6B54` `#2A8A64` `#B86400` | `#0E211B` | `#F5FBF6` · `#0E3B30` `#1F6B54` `#3E9C74` `#C06A00` | `#0A2B23` / `#E6F6EC` |
| **Charbon** | `#1C1E1C` | `#F5F0E4` `#DAD5C6` `#8F958B` `#FFB933` | `#161816` | `#F6F3EC` · `#1C1E1C` `#4A4E48` `#7E847A` `#B87500` | `#1C1E1C` / `#F5F0E4` |

How to read it: the symbol has four colour roles — **arch**, **leaf top** (ivory part), **leaf lower** (sage crescent) and **star**. *On tile* and *dark* use the light-on-dark scheme; *light* uses the dark-on-light scheme (never cream on cream).

Checked contrast (WCAG): arch ≥ 4.5:1 and wordmark ≥ 7:1 on every intended background; every coloured part of the symbol (leaves, star) ≥ 3:1; tagline ≥ 4.5:1. Per-direction ratios are in `tokens/oryn-brand-tokens.json` (`contrast`).

Neutrals for monochrome use: black `#000000`, white `#FFFFFF`, grey dark `#3A3F3C`, grey mid `#6E7772`, grey light `#C6CCC8`.

### Static brand variants vs. Android dynamic colour

The five directions are **fixed brand colourways**; they are *not* a Material You theme. On Android 12+, the UI should take its colours from the system (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) through Material 3 roles (`primary`, `surface`, `onSurface`…), while the **logo itself keeps the brand colours** so it stays recognisable on any wallpaper. `m3_reference_scheme` in the tokens only shows what a TonalSpot scheme seeded by each direction would look like; it is a design reference, not what the device generates. Themed launcher icons (Android 13+) use `ic_launcher_monochrome` and are tinted by the system.

## Variants and when to use them

- **Primary logo** — tile + wordmark + tagline (the composition of the original). Light backgrounds; a dark-surface version exists.
- **Stacked / horizontal** — symbol without tile + wordmark; horizontal for headers and toolbars.
- **Symbol alone** — app icon, avatar, favicon. It stays identifiable without the wordmark.
- **Simplified (≤ 32 px)** — leaves in one tone, star enlarged ×1.28, arch lifted so the gap with the leaves survives small sizes. Use at 16, 24, 32 px (and up to 48 px if you prefer the bolder look).
- **Monochrome** — solid shapes; the leaf split is an *empty line*, so contrast comes from shape, not colour. Single-colour printing, stamps, embossing, watermarks.
- **Android monochrome** — arch, star and two leaves as separate solid shapes, sized inside the 66 dp safe circle.

## Android

- Canvas 108 dp, visible area 72 dp, safe zone = circle of 66 dp. The symbol's enclosing circle is scaled to **30 dp radius** (≤ 33 dp), i.e. about 59 dp wide, centred on the enclosing circle (not on the bounding box).
- `ic_launcher.xml` declares background (`@color/ic_launcher_background` = `#0B2C25`), foreground and monochrome. `ic_notification_oryn.xml` is a 24 dp white silhouette for status-bar use.
- No Android project was provided, so `android-assets/` is **not integrated**: copy `res/` into your module and adjust names / manifest if they differ.

## Typography

Wordmark: **Montserrat SemiBold (600), tracking −0.03 em**, converted to outlines (SIL Open Font Licence 1.1). It is a reconstruction of the wordmark in the supplied image, which matches this typeface closely; spacing was tuned against it. Tagline: Montserrat Regular, letter-spaced to the wordmark's width. If you have the original lettering file, prefer it for final print production.

## Usage

- Clear space around any logo: at least the height of the lowercase **n** of the wordmark.
- Minimum sizes: full-colour symbol 48 px; simplified symbol 16 px; horizontal logo 24 px high; stacked logo 64 px high; stacked + tagline 160 px high (below that, drop the tagline).
- On light surfaces use the *light* scheme; on dark surfaces the *dark* scheme; on a vivid flat colour use the black or white monochrome, whichever contrasts more.

**Don't**: stretch, rotate or re-proportion the symbol · add shadows, glow, outlines or gradients · recolour outside this document's values · place cream parts on a light background · use the full-colour symbol under 32 px, or the simplified one as a large hero · separate the star from the arch or reorder the layers · retype the wordmark with another font · use blue or violet directions (the brand deliberately has none).

## Known limits

- The symbol is fitted from a **1254 px raster**: edges are accurate to well under 1 px of the source, and the sharp tips (leaf bottoms, star points) were reconstructed to compensate for blur. A vector original from the designer would be the final authority.
- No Android build was run (no project): vector drawables were checked for validity and rendered through their SVG twin, not on a device.
- The monochrome leaf split (negative-space line) disappears below ≈ 48 px; use the simplified monochrome there.
- No PDF / EPS / ICO beyond `favicon.ico` (16, 32, 48 px).
