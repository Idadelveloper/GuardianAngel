# Design system

Split out of `AGENTS.md`, which is kept under Codex's 32 KiB file cap. Everything here is
still binding — it is here because missing a spacing token is a cosmetic bug, while
missing something in `AGENTS.md` is a safety one, so this is what gets moved when the
budget runs out.

Theme lives in `ui/theme/`. Two accessors, no third:

- `MaterialTheme.colorScheme` / `.typography` / `.shapes` for standard Material roles
- `GuardianTheme.colors` / `.spacing` / `.shapes` / `.type` / `.windowSizeClass` for brand
  tokens Material has no slot for

**Never hard-code a hex value, dp spacing or font in a component.** If a token is missing,
add it to the theme.

### Paired colours

Accent tokens invert between light and dark; always use the paired content colour —
`accentSoft`/`onAccentSoft`, `accentWarm`/`onAccentWarm`, `activeContainer`/
`onActiveContainer`, `safeContainer`/`onSafeContainer`, and Material's own pairs.
Borrowing an unrelated `on*` role looks fine in light mode and renders at ~1.3:1 in dark.
`ColorContrastTest` catches it — run the unit tests after any colour change.

### Accessibility floors

4.5:1 for text, 3:1 for any non-text element that carries meaning. Three values from the
original design document fail and have accessible siblings: `colors.focusRing` for focus,
`colors.borderControl` for control outlines, `colors.iconMuted` for inactive nav icons.
`borderDefault`/`borderEmphasis` are decorative dividers only.

Colour is never the only signal — pair every status with a label and a distinct glyph.

### Other conventions

- Icons: `GuardianIcons`, stroke-based 24×24, round caps. Add there rather than pulling
  in Material's filled glyphs.
- Shapes: `pill` for buttons and chips, `.lg` (16 dp) cards, `.xl` (24 dp) sheets and
  heroes, `.md` (12 dp) inputs. Buttons 52 dp; in-card pill actions 44 dp.
- Elevation is ambient glow plus tonal layering (`guardianCardElevation`,
  `guardianFloatingElevation`, `focalHalo`, `ambientGlow`) — **not** Material tonal
  elevation, which double-tints the surface.
- No dynamic colour. The palette is a safety signal; wallpaper must not repaint it.
- Inside a vertically scrolling column, build grids from chunked `Row`s. A
  `LazyVerticalGrid` nested in a same-orientation scroll will crash.
- Window insets go **outside** the scroll modifier, or the padding scrolls away.
