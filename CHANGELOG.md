# Changelog

All notable changes to RootADB Pro will be documented in this file.

## [1.4.0] - 2026-07-15

### Changed
- Target and compile SDK raised from 34 to 35 (Android 15); Android 15's
  enforced edge-to-edge is handled by the explicit inset listeners added in
  this release

### Added
- Accent color selection now actually themes the app: each accent applies a
  Material 3 tonal overlay (light + dark palettes) at activity creation
- "Dynamic" accent option using Material You wallpaper colors (Android 12+)
- Adaptive launcher icon with monochrome layer for Android 13+ themed icons
- Predictive back gesture opt-in (Android 13+)

### Fixed
- Android 12+ light mode no longer renders a near-black background: removed
  the broken `values-v31` dynamic-color theme that hardcoded
  `system_neutral1_900` into a DayNight theme
- Edge-to-edge insets are now handled explicitly (status bar padding on the
  app bar, navigation-bar padding on scrolling content) instead of relying on
  `fitsSystemWindows`, surviving Android 15's enforced edge-to-edge and
  display cutouts
- Fragment content no longer rests hidden behind the gesture navigation bar
- Android 8.0 (API 26): navigation bar now uses a translucent scrim since the
  platform cannot render dark nav-bar icons, keeping buttons visible on light
  content
- Orange accent uses a dark on-primary color for WCAG-compliant contrast

## [1.3.0] - 2026-07-15

### Security
- Warpgate SSH password is now stored in `EncryptedSharedPreferences`
  (Android Keystore) instead of plaintext prefs; existing stored passwords
  migrate automatically on first read
- SSH host key verification for Warpgate tunnels changed from disabled
  (`StrictHostKeyChecking=no`) to trust-on-first-use (`accept-new`) with a
  persisted known-hosts file, protecting against MITM on later connections
- Persistent P2P token lookup now uses constant-time comparison

### Fixed
- Legacy (v1.0) trusted-device records no longer deserialize with a null
  device ID (Gson bypasses Kotlin null-safety); records are now routed by
  JSON shape and old entries parse correctly instead of crashing on access
- Relay server connection maps are now thread-safe (`ConcurrentHashMap`)
- Approving an already-trusted IP no longer creates a duplicate trusted
  device entry; the existing entry is updated instead
- Denying a pending relay device now closes the connection immediately
  instead of holding it open for the full 60-second approval timeout
- `ShellExecutor.execute()` rejects empty/blank command lists
- Removed machine-specific `org.gradle.java.home` from `gradle.properties`
  that broke builds on any non-Windows machine (including CI)
- Fixed all 81 failing unit tests: Robolectric could not load
  `conscrypt-android`'s JNI on the host JVM (added `conscrypt-openjdk-uber`
  for local unit tests), `android.util.Log` was unmocked in plain JUnit
  suites (enabled `returnDefaultValues`), and `BootReceiver` tests never
  accounted for the real 5-second boot delay (delays now test-overridable)

- Fixed all 15 lint errors (CI lint job was failing):
  - Quick Settings tile long-press crashed on Android 14+
    (`startActivityAndCollapse(Intent)` throws on API 34; now uses the
    `PendingIntent` overload)
  - Home-screen widget layout used a plain `<View>` divider, which is not
    RemoteViews-compatible and could crash launchers (now `FrameLayout`)
  - `Tile.subtitle` guarded behind API 29 check (min SDK is 26)
  - `android:tint` → `app:tint` on in-app AppCompat image views
  - Declared camera as optional hardware (`uses-feature required=false`)
    so camera-less devices are not filtered out
  - Marked `windowLightNavigationBar` usage as API 27+

### CI
- Added a unit-test job to the CI workflow (tests were previously never run
  in CI, which let the failures above go unnoticed)

## [1.0.0] - 2025-01-03

### Initial Public Release

**Local ADB**
- Enable/disable wireless ADB with one tap
- Custom port configuration (default: 5555)
- Auto-enable on device boot
- Copy `adb connect` command to clipboard
- Persistent notification with quick toggle
- Real-time status indicator

**Remote Relay (Tailscale)**
- Built-in TCP relay server for remote ADB access
- Auto-detect Tailscale IP (100.x.x.x range)
- Device authentication with persistent trust
- Approve/deny new device connections on-device
- Relay type selector (SSH Tunnel & Custom Relay coming soon)

**UI/UX**
- Material 3 design language
- Dark mode support (follows system theme)
- Edge-to-edge modern UI
- Tabbed interface: Local ADB / Remote Relay / Help
- Split tunnel setup guide in Help tab

**Technical**
- Fixed: Tailscale IP detection with VPN active (uses ConnectivityManager API)
- Fixed: WiFi IP detection no longer affected by VPN
- Uses Ktor for TCP relay server
- Gson for trusted device persistence

### Requirements
- Android 8.0+ (API 26)
- Root access (Magisk, KernelSU, etc.)
- Tailscale (optional, for remote relay)
