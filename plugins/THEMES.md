# Theme plugins

A theme is one `.json` file. Install it in the app: Appearance → Theme plugins → Install from file.
Tap an installed theme to activate it (tap again to deactivate). The palette overrides the current design;
`tune` overrides the sliders from Appearance. See `samples/themes/`.

```json
{
  "id": "sunset", "name": "Sunset", "author": "you",
  "mode": "dark",
  "dark":  { "primary": "#FF8A65", "onPrimary": "#2A0E05", "background": "#1B1210", "surface": "#261A16",
             "onSurface": "#F5E6DF", "container": "#2F211C", "outline": "#5A4038" },
  "light": { "primary": "#C2410C", "background": "#FFF7F2", "surface": "#FFFFFF", "onSurface": "#2A1A12" },
  "tune":  { "uiRadius": 120, "uiSpace": 105, "cardStyle": 1, "swStyle": 1, "startStyle": 1 }
}
```

* `mode`: `auto` (default, follows the app setting), `light`, `dark`.
* Colours: `primary onPrimary primaryContainer onPrimaryContainer secondary background onBackground surface onSurface
  surfaceVariant onSurfaceVariant outline error container`. All optional, `#RGB` or `#RRGGBB`.
* `tune` keys: `uiRadius uiSpace uiFont` (percent), `cardStyle` 0 tonal/1 outlined/2 flat, `swStyle` 0 Material/1 iOS/2 Fluent,
  `startStyle` 0 button/1 switch, plus `mSat mSurf` and the glass sliders (`gBlur` …).
