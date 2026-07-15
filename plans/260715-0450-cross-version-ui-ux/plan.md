# Cross-Version UI/UX Improvements (Android 8.0 → 15+)

**Status:** Complete — all phases implemented; `assembleDebug`, `testDebugUnitTest`, `lintDebug` pass (changes uncommitted)
**Branch:** PhenixStar/improve-UI-UX-against-all-android-versions

## Problems found

1. `values-v31/themes.xml` hardcodes `system_neutral1_900` (near-black) background into a DayNight theme + `windowLightStatusBar=true` → broken light mode on Android 12+; also kills accent feature on 31+.
2. Accent color feature is a no-op: pref saved, `recreate()` called, but no theme overlay ever applied.
3. Edge-to-edge half-wired: decor fitting disabled + transparent bars, but no inset listeners; relies on `fitsSystemWindows` (breaks under Android 15 enforced edge-to-edge); fragment scroll bottoms hidden under gesture nav bar.
4. API 26: transparent nav bar with no light-nav-icon support → invisible nav buttons in light mode.
5. No adaptive launcher icon (legacy letterboxed icon on all launchers), no monochrome layer for Android 13+ themed icons.
6. No predictive back opt-in (Android 13/14+).

## Phases

### Phase 1 — Theme correctness
- Delete broken `values-v31/themes.xml` base override.
- Add per-accent `ThemeOverlay` styles (values + values-night, M3 tonal primary/container).
- `ThemeManager.applyAccent(activity)` applies overlay before `setContentView`; new `DYNAMIC` accent uses `DynamicColors.applyToActivityIfAvailable` (Material You, API 31+, restores intent of deleted v31 file, correctly for both modes).
- HelpFragment: wire Dynamic button (hidden below API 31).
- API 26 nav bar scrim set programmatically.

### Phase 2 — Edge-to-edge insets (8.0 → 15+ proof)
- `util/InsetsExtensions.kt`: status-bar top padding, nav-bar/cutout bottom padding helpers via `ViewCompat`.
- Remove `fitsSystemWindows` from `activity_main.xml`; pad AppBarLayout top via listener.
- Fragments: `clipToPadding=false` + bottom inset padding on scroll roots.

### Phase 3 — Launcher icon
- Adaptive icon (`mipmap-anydpi-v26`) with foreground/background split + monochrome (13+ themed icons). Manifest → `@mipmap/ic_launcher`.

### Phase 4 — Platform behaviors
- `android:enableOnBackInvokedCallback="true"` (no custom back handling exists — safe).

### Phase 5 — Verify
- `lintDebug`, `testDebugUnitTest`, `assembleDebug`. Update CHANGELOG.

### Phase 6 — targetSdk 35 (added on user request)
- compileSdk/targetSdk 34 → 35, manifest tools:targetApi 35.
- Fixed SDK 35 nullability change: `Bitmap.getConfig()` now nullable (QrCodeGenerator).
- Behavior review: enforced edge-to-edge covered by Phase 2 insets; FGS specialUse subtype property already present; CONNECTIVITY_ACTION is an exempt system broadcast.
