# Oryn Design System — Hybrid color model

Two layers, one source of truth. Chrome and editorial never mix in a single component.

## 1. Chrome layer — UI controls, navigation, surfaces

Backed by Material 3 `ColorScheme`. Access via `OrynColors.chrome.*` or `MaterialTheme.colorScheme.*`.

- **Light**: `#EFEFEC` + ink `#111111` + user accent (chartreuse `#C8F135` default)
- **Dark**: `#121212` + `#F2F2EF` + lighter accent
- **Rule**: accent = `primaryContainer`. `primary` = text.

## 2. Editorial layer — book identity, progression, favorites, reader

Brand palette from Oryn brief. Access via `OrynColors.editorial.*`.

| Token         | Hex       | Use case                                |
|---------------|-----------|------------------------------------------|
| `teal`        | #0F3A3A   | Book spines, "Nuit" reader text          |
| `cherry`      | #B5185F   | Favorites, bookmarks, highlights         |
| `lime`        | #D0E86E   | Progress on dark, "Nuit" reader links    |
| `cyan`        | #35C1C2   | Sync indicator, multi-book dots          |
| `parchment`   | #FEFFDF   | "Papier" reader page + "Nuit" reader text|
| `darkSurface` | #081C1C   | "Nuit" reader page                       |
| `darkCard`    | #0D2727   | "Nuit" reader cards                      |

## Contrast-safe variants

| Token         | Hex       | On white   | On #121212 |
|---------------|-----------|------------|------------|
| `cherryOnDark`| #E63D83   | —          | 4.83:1 AA  |
| `tealOnDark`  | #4FBDBD   | —          | 8.24:1 AAA |
| `cyanDeep`    | #1A6B6B   | 6.26:1 AA  | —          |
| `limeDeep`    | #5A6B1E   | 5.95:1 AA  | —          |

Cherry on dark = 2.83:1 (fail). Lime on light = 1.18:1 (invisible). Cyan on light = 1.94:1 (fail). Use safe variants there.

## Decision table

| Component                  | Layer     | Token |
|----------------------------|-----------|-------|
| Nav pill (active)          | chrome    | `.accent` |
| Primary CTA                | chrome    | `.primary` |
| Favorite heart (filled)    | editorial | `.cherry` / `.cherryOnDark` |
| Continue-reading progress  | editorial | `.lime` (dark) / `.limeDeep` (light) |
| Book cover spine shadow    | editorial | `.teal` |
| Multi-book sync dot        | editorial | `.cyan` / `.cyanDeep` |
| Reader "Papier"            | editorial | `.parchment` page, `.teal` text |
| Reader "Nuit"              | editorial | `.darkSurface` page, `.parchment` text |

## When in doubt

- Text/icon on neutral surface → **chrome**
- Identity, progression, book accent → **editorial**
- Both needed → split: chrome control, editorial data.

## Legacy

`OrynBrandColors.kt` (in `res/oryn-brand/tokens/`) is NOT wired. Logo palette only. Do not reference from UI code.

## Reader presets

`ReaderThemePreset` (in `ReaderPreferencesRepository.kt`):

| Preset      | Page      | Text        | Links         |
|-------------|-----------|-------------|---------------|
| LIGHT       | #F8F8F5   | #1E1E1E     | #3F5A00       |
| BEIGE       | #F4ECD8   | #3C3024     | #3F5A00       |
| MIDNIGHT    | #1C1C1B   | #E9E9E4     | #CBF53C       |
| OLED        | #000000   | #F0F0F0     | #CBF53C       |
| NUIT        | darkSurface | parchment | lime          |
