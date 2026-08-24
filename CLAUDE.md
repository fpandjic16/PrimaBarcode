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

**Native Android barcode scanning app** for warehouse inventory operations backed by Dynamics NAV 2018. Kotlin + Jetpack Compose, minSdk 26 (Android 8.0+), compileSdk 36.

The app is fully wired end-to-end: Room DB + DAOs, Hilt-injected ViewModels, Ktor/NTLM networking against NAV OData, and DataWedge + CameraX/ML Kit scanning are all live — this is not a design-to-code-in-progress prototype.

### Key Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 (BOM 2026.02.01) |
| Navigation | Compose Navigation 2.8.2 |
| Networking | Ktor Client 2.3.12 over OkHttp, custom NTLMv2 `Authenticator` (no external NTLM lib) |
| Local DB | Room 2.7.0 (KSP-generated DAOs) |
| DI | Hilt/Dagger 2.59 |
| Barcode | ML Kit 17.3.0 + CameraX 1.4.2 (fallback to hardware DataWedge scanning) |
| Secure storage | androidx.security:security-crypto (NAV credentials, TTL-bound) |
| Logging | Timber |

Dependency versions are centralized in `gradle/libs.versions.toml` (and inline in `app/build.gradle.kts` for libraries not yet promoted to the catalog).

### Source Layout

```
app/src/main/java/com/prima/barcode/
├── MainActivity.kt              # Entry point, NavHost, top-level app state (settings, location/RC, dialogs)
├── di/                          # Hilt modules (DatabaseModule)
└── ui/
    ├── theme/                   # Color.kt, Type.kt, Shape.kt, Theme.kt, Language.kt
    ├── component/               # Reusable Compose components (ScanBar, ScanTape, CameraPreview, ...)
    ├── screen/                  # Full screens
    └── viewmodel/                # AppViewModel (app-wide state/sync), RecordingViewModel (per-document)
data/
├── model/                       # Domain models (Document, Line, DocState, ...) — data/model/Models.kt
├── db/                          # Room entities, DAOs, PrimaDatabase, Mappers (entity ↔ domain)
├── repository/                  # DocumentRepository — merge/state-machine logic over Room
├── extsystem/                   # ExtSystemODataClient, NtlmAuthenticator, NAV DTOs (ExtSystemPayload)
├── auth/                        # ExtSystemConfig(Store), ExtSystemCredentialStore (encrypted), AppSettings(Store)
├── barcode/                     # BarcodeAnalyzer (ML Kit), DataWedgeManager (Zebra profile + broadcasts)
├── haptic/                      # HapticEngine
└── export/                      # DatabaseExporter (JSON dump via SAF)
```

### Screens

- **MainMenuScreen** — Document type list with counts/status bars; context strip for location/RC switching
- **RecordingScreen** — Core scanning interface: per-line progress, docked ScanBar, hardware wedge + camera fallback, keypad entry, extra ("not on document") lines, over-scan/UoM-mismatch warnings
- **DocumentListScreen** — Per-doc-type list with tabs, create/delete, filter, upload
- **DocumentOverviewScreen** — Cross-type dashboard (errors/ready/partial/over tabs)
- **DocumentFilterScreen** / **DownloadFilterScreen** — Filter builders for the list/overview and for NAV downloads
- **LocationRcPickScreen** — Location/RC switcher with NAV refresh
- **ExtSystemConfigScreen** — NAV endpoint URLs, doc type codes, credential TTL, JSON import/export of defaults
- **SettingsScreen** — Text size, language, scan behavior toggles, cache/export/sign-out, embeds ext-system config
- **LoginSheet** — Bottom-sheet NAV credential capture (summoned on Download/Upload, not app launch)
- **UploadErrorScreen** — Failure detail + retry for a single document

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

Domain models live in `data/model/Models.kt`; Room entities in `data/db/Entities.kt`; `data/db/Mappers.kt` converts between them.

- `Document` — documentNo, type, destination/source/RC codes, lines[], extraLines[] (not-on-document scans), `DocState`
- `DocState` — sealed interface: `Downloaded / InProgress / Completed / PendingUpload / UploadFailed(reason)`; transitions are computed from recordings by `DocumentRepository` (advance/regress helpers), not set directly by the UI
- `Line` — documentNo, lineNo, item, barcodeNo, expected/scanned qty, UoM; `LineStatus` (`EMPTY/PARTIAL/EXACT/OVER`) is computed from expected vs. scanned
- `ExtraLine` — a recorded scan whose barcode didn't match any document line (`documentLine == 0` in the `RecordingEntity` table)
- `DocumentType` — enum: `WAREHOUSE_SHIPMENT`, `WAREHOUSE_RECEIPT`, `RETAIL_SHIPMENT`, `RETAIL_RECEIPT`, `TRANSPORT_SHEET`
- Local persistence is recording-first: every scan writes a `RecordingEntity` row; documents/lines are freely replaced on re-download, but recordings (real user progress) are never deleted by a sync — `DocumentRepository.mergeDocument` re-attributes them to freshly downloaded lines by barcode match.

### NAV Integration

- `ExtSystemODataClient` (Ktor over a preconfigured OkHttp client) talks to NAV's OData V4 endpoints for locations, document lines, and recording upload; auth is a hand-rolled NTLMv2 `okhttp3.Authenticator` (`NtlmAuthenticator`, includes an inline MD4 implementation — Android's `MessageDigest` has no MD4).
- Login is username+password only; the domain travels inside the username as `user@domain` or `DOMAIN\user`, split by `ExtSystemODataClient.parseDomainUser`.
- `network_security_config.xml` permits cleartext traffic globally — NAV is reached over the local LAN, not HTTPS.
- Endpoint URLs, per-doc-type codes, and credential TTL are user-configurable (`ExtSystemConfigScreen`); `assets/ext_system_defaults.json` seeds sane defaults and can be re-imported from Settings.
- Credentials are cached in `ExtSystemCredentialStore` (`EncryptedSharedPreferences`, AES-256-GCM) with a TTL; expired credentials are wiped on next read.
