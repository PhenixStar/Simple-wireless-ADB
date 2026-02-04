# GitHub Actions CI/CD Setup Guide

## Overview

This project uses GitHub Actions for automated builds and releases.

## Workflows

### CI Workflow (`.github/workflows/ci.yml`)
- **Triggers**: Push to `main`/`develop`, Pull Requests
- **Jobs**:
  - Build Debug APK
  - Run Android Lint
- **Artifacts**: Debug APK + Lint reports (14-day retention)

### Release Workflow (`.github/workflows/release.yml`)
- **Triggers**: Push to `main`/`release` branches
- **Jobs**:
  - Build Release APK (signed)
  - Generate changelog from conventional commits
  - Create GitHub Release
- **Artifacts**: Release APK (90-day retention)

---

## Required GitHub Secrets

Configure these in **Repository Settings → Secrets and variables → Actions**:

| Secret | Description | How to Generate |
|--------|-------------|-----------------|
| `KEYSTORE_FILE` | Base64 encoded keystore | `base64 -i rootadb-release.keystore \| pbcopy` |
| `KEYSTORE_PASSWORD` | Keystore password | From your local `.env` |
| `KEY_ALIAS` | Key alias name | From your local `.env` (e.g., `phenkey`) |
| `KEY_PASSWORD` | Key password | From your local `.env` |

### Encoding Keystore for GitHub Secrets

**Linux/macOS:**
```bash
base64 -i app/rootadb-release.keystore | tr -d '\n' | pbcopy
```

**Windows (PowerShell):**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\rootadb-release.keystore")) | Set-Clipboard
```

**Windows (Git Bash):**
```bash
base64 -w 0 app/rootadb-release.keystore | clip
```

---

## Release Process

### Branch-based Release (Automatic)

1. **Merge changes to `main` or `release` branch**
2. **GitHub Actions automatically:**
   - Builds signed release APK
   - Generates changelog from commits
   - Creates/updates GitHub Release `v{version}`
   - Uploads APK to release

### Conventional Commit Format

For better changelogs, use these commit prefixes:

| Prefix | Category |
|--------|----------|
| `Add:`, `Feat:`, `Feature:` | 🚀 New Features |
| `Fix:`, `Bugfix:` | 🐛 Bug Fixes |
| `Update:`, `Refactor:`, `Improve:` | 🔧 Improvements |
| `Docs:`, `Chore:`, `Test:` | 📝 Other Changes |

### Example Changelog Generation

```
# What's Changed

## 🚀 New Features
- Add: SSH tunnel support for remote ADB

## 🐛 Bug Fixes
- Fix: Crash on Android 14 when connecting via QR code

## 🔧 Improvements
- Update: Material 3 components
- Refactor: P2P connection handling

## 📝 Other Changes
- Docs: Update README with new features
```

---

## Local Build Testing

Before pushing, test locally with CI parameters:

```bash
# Test release build with CI parameters
./gradlew assembleRelease \
  -PKEYSTORE_FILE=app/rootadb-release.keystore \
  -PKEYSTORE_PASSWORD=your_password \
  -PKEY_ALIAS=phenkey \
  -PKEY_PASSWORD=your_key_password
```

---

## Badges (Optional)

Add to README.md:

```markdown
[![CI](https://github.com/Danz17/Simple-wireless-ADB/actions/workflows/ci.yml/badge.svg)](https://github.com/Danz17/Simple-wireless-ADB/actions/workflows/ci.yml)
[![Release](https://github.com/Danz17/Simple-wireless-ADB/actions/workflows/release.yml/badge.svg)](https://github.com/Danz17/Simple-wireless-ADB/actions/workflows/release.yml)
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails with signing error | Verify all 4 secrets are set correctly |
| Keystore decoding fails | Re-encode with correct base64 command (no line breaks) |
| Release not created | Check versionName in build.gradle.kts |
| Empty changelog | Ensure commits use conventional format |
| "Permission denied" on gradlew | Workflow includes `chmod +x gradlew` step |

---

## Security Notes

1. **Never commit** `.env` file with real credentials
2. **Rotate signing keys** if accidentally exposed
3. **Limit workflow permissions** in GitHub Actions settings
4. **Review workflow logs** for accidental secret exposure
