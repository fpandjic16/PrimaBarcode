# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Install on connected device/emulator
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
./gradlew clean                  # Clean build outputs
```

Use `gradlew.bat` instead of `./gradlew` on Windows.

## Architecture

**Native Android barcode scanning app** for warehouse inventory operations backed by Dynamics NAV 2018. Kotlin + Jetpack Compose, minSdk 24, compileSdk 36.

### Key Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 (BOM 2026.02.01) |
| Navigation | Compose Navigation 2.8.2 |
| Networking | Ktor Client 2.3.12 (Basic + NTLM auth for NAV) |
| Local DB | Room 2.6.1 (KSP-generated DAOs) |
| DI | Hilt/Dagger 2.51.1 |
| Barcode | ML Kit 17.3.0 + CameraX 1.3.4 (fallback) |
| Secure storage | androidx.security:security-crypto (NAV credentials, 24h TTL) |
| Logging | Timber |

Dependency versions are centralized in `gradle/libs.versions.toml`.

### Source Layout

```
app/src/main/java/com/prima/barcode/
├── MainActivity.kt              # Entry point, Compose scaffold, nav host
└── ui/
    ├── theme/                   # Color.kt, Type.kt, Shape.kt, Theme.kt
    ├── component/               # Reusable Compose components
    └── screen/                  # Full screens
```

### Screens

- **MainMenuScreen** — Document type list with counts/status bars; context strip for location/RC switching
- **RecordingScreen** — Core scanning interface: per-line progress, docked ScanBar, hardware wedge + camera fallback
- **LoginSheet** — Bottom-sheet NAV credential capture (summoned on Download/Upload, not app launch)

A Document List screen is planned but not yet implemented.

### Design System

**4-state status language** drives the entire UX — every line and document resolves to one of:

| State | Condition | Color |
|---|---|---|
| Empty | 0 scanned | Red `#CE3A3A` |
| Partial | 0 < scanned < expected | Orange `#C7943A` |
| Exact | scanned == expected | Green `#2E8C5E` |
| Over | scanned > expected | Blue `#2D6CE0` |

**Brand palette** — Coral (`#C95B4D`), Slate (`#2E3539`), Cream (`#F2EBDE`), Teal (`#2F5455`).

**Typography** — Geist (primary) + GeistMono (codes/counters). GeistMono is specifically used for `monoCounter` and `monoLabel` text styles.

**Shape radii** — 4 / 8 / 12 / 14 / 22 dp tokens defined in `Shape.kt`.

### Data Model

Core entities (currently in `Handoff - barcode/compose/` design stubs, not yet ported to app source):
- `Document` — has type, location, RC, owner, lines[], state (`Downloaded → InProgress → Completed → PendingUpload → Uploaded | UploadFailed`)
- `Line` — id, item, expected qty, scanned qty; `LineStatus` is computed from those two values
- `SyncState` — sealed class: `Offline / Idle / Pending(count) / Syncing(progress) / Error(failures[])`
- `DocumentType` — enum: `WAREHOUSE_SHIPMENT`, `WAREHOUSE_RECEIPT`, `RETAIL_SHIPMENT`, `RETAIL_RECEIPT`, `TRANSPORT_SHEET`

### What's Scaffolded but Not Yet Wired

The design-to-code handoff is in progress. The following are stubbed/TODO:
- Room entities and DAOs
- ViewModels + state management
- Ktor networking endpoints for NAV
- Hardware scanner wedge integration
- Camera/ML Kit barcode scanning in `RecordingScreen`
- Multiplier sheet modal and Location picker modal

Design specs live in `Handoff - barcode/` (HTML + compose stubs + README).
