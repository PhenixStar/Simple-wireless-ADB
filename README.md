# RootADB Pro

**Persistent Wireless ADB with Remote Access & P2P Connections**

A powerful Android utility that enables wireless ADB debugging with multiple remote access options including Tailscale VPN, Warpgate SSH tunnels, and P2P token-based connections. Features a modern Material 3 UI with theme customization.

[![CI](https://github.com/PhenixStar/Simple-wireless-ADB/actions/workflows/ci.yml/badge.svg)](https://github.com/PhenixStar/Simple-wireless-ADB/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/PhenixStar/Simple-wireless-ADB)](https://github.com/PhenixStar/Simple-wireless-ADB/releases)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)

## Features

### Dashboard (v1.2.0)
- **Quick Status** - 4 status indicators (Local/Tailscale/Warpgate/P2P)
- **Quick Toggles** - Enable Local ADB, Tailscale Relay, or Warpgate with one tap
- **Settings** - Boot persistence, notification hiding
- **Trusted Devices** - View count and manage trusted connections

### Local ADB (WiFi)
- Enable/disable wireless ADB with one tap
- Custom port configuration (default: 5555)
- Auto-enable on device boot
- Copy `adb connect` command to clipboard
- Persistent notification when active

### Remote Access Options

| Method | Description | Use Case |
|--------|-------------|----------|
| **Tailscale Direct** | Connect via Tailscale IP on port 5555 | Same Tailscale network |
| **Tailscale Relay** | Built-in relay server on port 5556 | Device authentication |
| **Warpgate** | SSH tunnel via bastion server | Corporate/secure access |
| **P2P Token** | Direct device-to-device connection | No relay needed |

### P2P Token System (v1.2.0)
- Generate secure connection tokens
- Token expires after 30 minutes
- Mark devices as trusted for persistent access
- No relay server required
- STUN-based NAT traversal

### ADB Auto-Pairing (Android 11+)
- Pair wirelessly without PC setup
- SPAKE2 + TLS 1.3 security
- One-time pairing, persistent access

### Shizuku Support
- Works without root using Shizuku
- Proper UserService integration
- Shell or root-level access depending on Shizuku mode

### Theme Customization
- Light / Dark / System modes
- 6 accent colors: Blue, Teal, Purple, Orange, Pink, Green
- Material 3 design language

## Requirements

| Feature | Requirement |
|---------|-------------|
| Basic ADB | Root access OR Shizuku |
| ADB Pairing | Android 11+ (API 30) |
| Tailscale Relay | Tailscale app installed |
| Warpgate | SSH access to bastion server |
| P2P Token | Internet connection |

**Minimum:** Android 8.0 (API 26)

## Installation

1. Download the latest APK from [Releases](https://github.com/PhenixStar/Simple-wireless-ADB/releases)
2. Install on your device
3. Grant root/Shizuku permissions when prompted
4. Enable wireless ADB from the Dashboard

## Usage

### Local Connection (WiFi)

```bash
# On your PC (same WiFi network)
adb connect <DEVICE_IP>:5555

# Example
adb connect 192.168.1.100:5555
```

### Remote Connection (Tailscale)

```bash
# On your PC (connected to same Tailscale network)
adb connect <TAILSCALE_IP>:5556

# Example
adb connect 100.99.87.44:5556
```

### P2P Token Connection

1. Generate token on device A (Dashboard → P2P Token)
2. Share the token with device B
3. Device B connects using the token
4. Approve on device A (or mark as trusted)

### Warpgate SSH Tunnel

1. Configure Warpgate host, credentials, and target in Remote Relay tab
2. Enable Warpgate toggle
3. Connect via the forwarded local port

## Architecture

```
LOCAL ADB:
[PC on WiFi] ────────────> [Android:5555] ───> [ADB Daemon]
                                │
                           RootADB Pro
                           (enables ADB)

TAILSCALE RELAY:
[Remote PC] ──Tailscale──> [Android:5556] ──relay──> [ADB:5555]
    │                           │                        │
100.x.x.x                  RootADB Pro              Local Daemon
                          (authenticates)

WARPGATE:
[Remote PC] ──SSH Tunnel──> [Bastion:2222] ──forward──> [Android:5555]
                                │
                        Warpgate Server

P2P TOKEN:
[Device A] <──STUN/Direct──> [Device B]
     │                            │
 Generates Token            Uses Token
```

## Security

| Layer | Protection |
|-------|------------|
| Network | Tailscale relay only accepts 100.x.x.x range |
| Device Auth | First connection requires approval |
| P2P Tokens | Expire after 30 minutes, device-bound |
| ADB Pairing | SPAKE2 + TLS 1.3 encryption |
| Warpgate | SSH tunnel encryption |
| ADB Auth | Standard RSA key authentication |

## Privacy

RootADB Pro does **NOT** collect any personal data. All settings are stored locally on your device. See our [Privacy Policy](https://danz17.github.io/Simple-wireless-ADB/privacy-policy.html).

## Build

```bash
# Clone the repo
git clone https://github.com/PhenixStar/Simple-wireless-ADB.git
cd Simple-wireless-ADB

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/com/phenix/wirelessadb/
├── AdbManager.kt              # Core ADB logic
├── AdbService.kt              # Foreground service
├── BootReceiver.kt            # Auto-enable on boot
├── MainActivity.kt            # Main activity
├── PrefsManager.kt            # Settings storage
├── model/                     # Data models
│   ├── ConnectionMode.kt
│   ├── P2PToken.kt
│   ├── TrustedDevice.kt
│   └── StatusIndicators.kt
├── p2p/                       # P2P Token System
│   ├── P2PManager.kt
│   └── TokenGenerator.kt
├── pairing/                   # ADB Pairing (Android 11+)
│   ├── AdbPairingClient.kt
│   ├── AdbPairingManager.kt
│   └── TrustAllManager.kt
├── relay/                     # TCP Relay
│   ├── AdbRelayServer.kt
│   ├── ConnectionProxy.kt
│   ├── DeviceAuthManager.kt
│   └── TailscaleHelper.kt
├── shell/                     # Shell Execution
│   ├── ShellExecutor.kt
│   ├── ShizukuExecutor.kt
│   └── ShizukuServiceManager.kt
├── theme/                     # Theme System
│   ├── ThemeManager.kt
│   ├── ThemeMode.kt
│   └── AccentColor.kt
├── ui/                        # UI Fragments
│   ├── DashboardFragment.kt
│   ├── RemoteRelayFragment.kt
│   ├── HelpFragment.kt
│   └── AdbPairingDialog.kt
├── viewmodel/
│   └── AdbViewModel.kt
└── warpgate/                  # Warpgate SSH
    ├── WarpgateManager.kt
    └── WarpgateConfig.kt
```

## Changelog

### v1.2.0 (Current)
- **Dashboard Tab** - Unified control center
- **Theme System** - Light/Dark modes + 6 accent colors
- **P2P Token** - Device-to-device connections
- **ADB Auto-Pairing** - Android 11+ wireless pairing
- **Shizuku Enhancement** - Proper UserService integration
- **Warpgate** - SSH tunnel support
- **Help Downloads** - Quick links to tools

### v1.1.0
- Status indicators for connection modes
- Warpgate SSH tunnel configuration
- Connection mode selector
- Developer notification hiding

### v1.0.0
- Enable/disable wireless ADB
- Custom port configuration
- Auto-enable on boot
- Tailscale relay server
- Device authentication
- Material 3 UI with dark mode

## License

MIT License - See [LICENSE](LICENSE) for details.

## Author

**Phenix** (Alaa Qweider)
Email: alaa@nulled.ai
Package: `com.phenix.wirelessadb`

---

**No ads. No analytics. No bloat.** Just wireless ADB that works.
