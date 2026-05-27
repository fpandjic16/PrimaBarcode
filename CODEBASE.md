# PrimaBarcode — Full Codebase Reference

> Native Android barcode scanning app for warehouse inventory operations backed by Microsoft Dynamics NAV 2018.  
> Kotlin + Jetpack Compose · minSdk 24 · compileSdk 36 · Room 2.6.1 · Hilt 2.51.1 · Ktor 2.3.12 · ML Kit 17.3.0

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Build & Tooling](#2-build--tooling)
3. [Architecture](#3-architecture)
4. [Domain Models](#4-domain-models)
5. [Database Layer](#5-database-layer)
6. [Repository](#6-repository)
7. [Auth & Configuration Stores](#7-auth--configuration-stores)
8. [External System Integration](#8-external-system-integration)
9. [Barcode & Hardware](#9-barcode--hardware)
10. [ViewModels](#10-viewmodels)
11. [Navigation & App Shell](#11-navigation--app-shell)
12. [Design System](#12-design-system)
13. [UI Components](#13-ui-components)
14. [Screens](#14-screens)
15. [DI Module](#15-di-module)
16. [Localization](#16-localization)
17. [Assets](#17-assets)

---

## 1. Project Overview

PrimaBarcode is a warehouse barcode scanning app used by Prima Namještaj d.o.o. warehouse staff. Workers use it to scan barcodes on furniture pieces against expected quantities from Dynamics NAV 2018, then upload the completed recordings back to NAV via OData.

**Core workflow:**
1. Worker selects their Location (warehouse) and Responsibility Center (RC) on startup.
2. Downloads open documents for their location from NAV (or loads test data).
3. Opens a document (shipment/receipt/retail order/transport sheet).
4. Scans barcodes line by line; app shows real-time progress using the 4-state status language.
5. When all lines are EXACT or the worker marks complete, uploads the recording to NAV.
6. Errors are visible in the Errors tab; can be retried or cleared.

**Hardware:** Primarily runs on Zebra Android handheld scanners (TC-series) with DataWedge. Falls back to device camera + ML Kit on standard Android phones.

---

## 2. Build & Tooling

### Gradle Files

| File | Purpose |
|---|---|
| `build.gradle.kts` (root) | Plugin declarations, Hilt plugin, KSP plugin |
| `app/build.gradle.kts` | App module config: applicationId, minSdk 24, compileSdk 36, Room KSP |
| `gradle/libs.versions.toml` | Centralized dependency version catalog |
| `settings.gradle.kts` | Module graph |
| `gradle.properties` | JVM args, AndroidX opt-ins |

### Key Versions (libs.versions.toml)

| Library | Version |
|---|---|
| Kotlin | 2.1.21 |
| Compose BOM | 2026.02.01 |
| Navigation Compose | 2.8.2 |
| Room | 2.6.1 |
| Hilt/Dagger | 2.51.1 |
| Ktor Client | 2.3.12 |
| ML Kit Barcode | 17.3.0 |
| CameraX | 1.3.4 |
| Timber | 5.0.1 |
| Gson | 2.10.1 |
| security-crypto | 1.0.0 |

### Build Commands

```bash
gradlew.bat assembleDebug       # Build debug APK
gradlew.bat assembleRelease     # Build release APK
gradlew.bat installDebug        # Install on connected device
gradlew.bat test                # Unit tests
gradlew.bat connectedAndroidTest # Instrumented tests (requires device)
gradlew.bat clean               # Clean build outputs
```

Requires `JAVA_HOME` to point to Android Studio's JBR:  
`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`

---

## 3. Architecture

```
app/src/main/java/com/prima/barcode/
├── MainActivity.kt                      # Activity + nav host + settings state
├── PrimaBarcodeApplication.kt           # Hilt entry point
├── data/
│   ├── auth/                            # Persistent config/settings/credentials
│   │   ├── AppSettings.kt              # Settings data class
│   │   ├── AppSettingsStore.kt         # SharedPreferences wrapper
│   │   ├── ExtSystemConfig.kt          # NAV connection config data class
│   │   ├── ExtSystemConfigStore.kt     # Config SharedPreferences wrapper
│   │   └── ExtSystemCredentialStore.kt # EncryptedSharedPreferences (AES-256)
│   ├── barcode/
│   │   ├── BarcodeAnalyzer.kt          # CameraX ImageAnalysis.Analyzer (ML Kit)
│   │   └── DataWedgeManager.kt         # Zebra DataWedge broadcast API
│   ├── db/                              # Room database
│   │   ├── Entities.kt                 # 5 Room entities
│   │   ├── PrimaDatabase.kt            # RoomDatabase definition (v9)
│   │   ├── DocumentHeaderDao.kt        # Header CRUD + observe
│   │   ├── DocumentLineDao.kt          # Line CRUD + observe
│   │   ├── RecordingDao.kt             # Recording insert/delete/observe
│   │   ├── LocationDao.kt              # Location + RC upsert/observe
│   │   ├── DocumentHeaderWithLines.kt  # JOIN result carrier
│   │   └── Mappers.kt                  # Entity <-> Domain conversions + serialization
│   ├── export/
│   │   └── DatabaseExporter.kt         # JSON export of all DB tables
│   ├── extsystem/
│   │   ├── ExtSystemODataClient.kt     # Ktor+OkHttp HTTP client
│   │   ├── ExtSystemPayload.kt         # Upload/download DTOs + mapping
│   │   └── NtlmAuthenticator.kt        # Inline NTLMv2 OkHttp Authenticator
│   ├── haptic/
│   │   └── HapticEngine.kt             # Vibration feedback (confirm / error)
│   ├── model/
│   │   ├── Models.kt                   # Core domain models
│   │   ├── Status.kt                   # LineStatus enum + color extensions + scanStatus()
│   │   └── Filter.kt                   # DocumentFilter, DownloadFilter
│   └── repository/
│       └── DocumentRepository.kt       # Interface + Impl (all DB operations)
├── di/
│   └── DatabaseModule.kt               # Hilt @Module: Room, DAOs, Repository
├── ui/
│   ├── component/                       # Reusable composables
│   │   ├── CameraPreview.kt
│   │   ├── Chip.kt
│   │   ├── DocumentStatsDashboard.kt
│   │   ├── PrimaTopBar.kt
│   │   ├── ScanBar.kt
│   │   ├── ScanField.kt
│   │   ├── ScanTape.kt
│   │   ├── ScrollbarModifier.kt
│   │   ├── StatusComponents.kt
│   │   └── SyncPip.kt
│   ├── screen/                          # Full-screen composables
│   │   ├── DocumentFilterScreen.kt
│   │   ├── DocumentListScreen.kt
│   │   ├── DocumentOverviewScreen.kt
│   │   ├── DownloadFilterScreen.kt
│   │   ├── ExtSystemConfigScreen.kt
│   │   ├── LocationRcPickScreen.kt
│   │   ├── LoginSheet.kt
│   │   ├── MainMenuScreen.kt
│   │   ├── RecordingScreen.kt
│   │   ├── SettingsScreen.kt
│   │   └── UploadErrorScreen.kt
│   ├── theme/
│   │   ├── Color.kt                    # PrimaPalette, PrimaStatus, color schemes
│   │   ├── Language.kt                 # Language enum
│   │   ├── PrimaTheme.kt              # PrimaBarcodeTheme composable
│   │   ├── Shape.kt                    # Shape tokens
│   │   ├── Theme.kt                    # (legacy/alias)
│   │   └── Type.kt                     # Typography, TextSize, monoCounter, monoLabel
│   └── viewmodel/
│       ├── AppViewModel.kt             # Global app state + sync operations
│       └── RecordingViewModel.kt       # Per-document recording state
└── res/
    ├── values/strings.xml              # English strings
    └── values-hr/strings.xml           # Croatian strings
```

---

## 4. Domain Models

### `data/model/Models.kt`

#### `User`
```kotlin
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val initials: String,
    val role: String? = null,
    val shift: String? = null,
)
```

#### `ResponsibilityCenter`
```kotlin
data class ResponsibilityCenter(
    val code: String,
    val name: String,
    val short: String? = null,
)
```

#### `Location`
```kotlin
data class Location(
    val code: String,
    val name: String,
    val rc: String,       // Responsibility Center code this location belongs to
)
```

#### `DocumentType`
```kotlin
enum class DocumentType(val key: String, val display: String, val filterByRc: Boolean) {
    WAREHOUSE_SHIPMENT("WHSE_SHIP", "Warehouse Shipment",    true),
    WAREHOUSE_RECEIPT( "WHSE_RCPT", "Warehouse Receipt",     true),
    RETAIL_SHIPMENT(   "RT_SHIP",   "Retail Shipment",       false),
    RETAIL_RECEIPT(    "RT_RCPT",   "Retail Whse. Receipt",  false),
    TRANSPORT_SHEET(   "TRANSPORT", "Transport Sheet",       true),
}
```
- `filterByRc = true`: document is filtered by `rcCode`; `false`: filtered by `sourceCode == location.code`
- `key` is the string stored in Room DB and sent to NAV

#### `Item`
```kotlin
data class Item(val no: String, val name: String)
```

#### `Line`
```kotlin
data class Line(
    val documentNo: String,
    val lineNo: Int,
    val item: Item,
    val barcodeNo: String,
    val expected: Double,
    val scanned: Double,
    val destinationCode: String,
    val sourceCode: String,
) {
    val status: LineStatus get() = LineStatus.of(scanned, expected)
}
```

#### `ExtraLine`
Represents a scan for an item not in the original document (unplanned items):
```kotlin
data class ExtraLine(
    val recordingLineNo: Int,
    val barcodeNo: String,
    val quantity: Double,
)
```
Extra lines use `documentLine = 0` in the recordings table.

#### `Document`
```kotlin
data class Document(
    val documentNo: String,
    val type: DocumentType,
    val destinationCode: String,
    val sourceCode: String,
    val rcCode: String,
    val ownerUserId: String,
    val creationDateTime: Instant,
    val documentDate: Instant? = null,
    val lines: List<Line>,
    val extraLines: List<ExtraLine> = emptyList(),
    val state: DocState,
) {
    val linesExact: Int get() = lines.count { it.status == LineStatus.EXACT }
    val linesTotal: Int get() = lines.size
}
```

#### `DocState`
Sealed interface representing lifecycle stages:
```kotlin
sealed interface DocState {
    data object Downloaded   : DocState   // Just downloaded, no scans
    data object InProgress   : DocState   // Has at least one scan
    data object Completed    : DocState   // All lines EXACT, no extra lines
    data class  UploadFailed(val reason: String) : DocState
}
```
DB serialization: `"Downloaded"`, `"InProgress"`, `"Completed"`, `"UploadFailed:<reason>"`.

#### `SyncState`
```kotlin
sealed interface SyncState {
    data object Offline                              : SyncState
    data object Idle                                 : SyncState
    data class  Pending(val count: Int)              : SyncState
    data class  Syncing(val progress: Float)         : SyncState
    data class  Error(val failures: List<SyncError>) : SyncState
}
```

#### `SyncError`
```kotlin
data class SyncError(
    val documentNo: String,
    val reason: String,
    val detail: String,
    val at: Instant,
)
```

#### `TapeEntry`
A single scan event shown in the scan tape (scrolling history on RecordingScreen):
```kotlin
data class TapeEntry(
    val id: String,
    val barcode: String,
    val itemName: String?,
    val quantity: Double,
    val at: Instant,
    val lineStatus: LineStatus?,
) {
    val isError: Boolean get() = lineStatus == null
}
```

#### `fun Double.formatQty(): String`
Formats a quantity for display: whole numbers show as integers (`"5"`), decimals show up to 5 significant decimal places with trailing zeros stripped (`"1.10152"`).

---

### `data/model/Status.kt`

#### `LineStatus`
```kotlin
enum class LineStatus { EMPTY, PARTIAL, EXACT, OVER }
```

| Value | Condition |
|---|---|
| `EMPTY` | `scanned == 0.0` |
| `PARTIAL` | `0.0 < scanned < expected` |
| `EXACT` | `scanned == expected` |
| `OVER` | `scanned > expected` |

**`LineStatus.of(scanned, expected)`** — factory companion method.

**Color extensions:**
- `LineStatus.color: Color` — foreground accent: Empty=`#CE3A3A`, Partial=`#C7943A`, Exact=`#2E8C5E`, Over=`#2D6CE0`
- `LineStatus.bgColor: Color` — 10% alpha tinted background for chips/badges
- `LineStatus.label: String` — English label string: `"Empty"`, `"Partial"`, `"Ready"`, `"Over-qty"`

**`fun Int.flooredAtZero(): Int`** — clamps negative Int to 0; used when undoing scans.

**`fun Document.scanStatus(): LineStatus`** — aggregate status of entire document:
- All lines EMPTY → `EMPTY`
- Any line OVER → `OVER`  
- All lines EXACT AND no extra lines → `EXACT`
- Anything else → `PARTIAL`

---

### `data/model/Filter.kt`

#### `DocumentFilter`
```kotlin
data class DocumentFilter(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val states: Set<LineStatus> = emptySet(),
    val types: Set<DocumentType> = emptySet(),
    val destinationCode: String = "",
    val sourceCode: String = "",
    val rcCode: String = "",
) {
    val isActive: Boolean get() = dateFrom != null || dateTo != null || states.isNotEmpty() ||
        types.isNotEmpty() || destinationCode.isNotBlank() || sourceCode.isNotBlank() || rcCode.isNotBlank()
}
```

#### `DownloadFilter`
```kotlin
data class DownloadFilter(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val destinationCode: String = "",
    val sourceCode: String = "",
    val rcCode: String = "",
)
```
Used by `DownloadFilterScreen` to pass filter criteria to NAV download requests.

---

## 5. Database Layer

### `data/db/Entities.kt` — Room Entities

#### `DocumentHeaderEntity`
Table: `documentHeader` · Primary key: `(documentNo, type)`
```kotlin
data class DocumentHeaderEntity(
    val documentNo: String,
    val type: String,               // DocumentType.key string
    val destinationCode: String,
    val sourceCode: String,
    val rcCode: String,
    val ownerUserId: String,
    val creationDateTime: Long,     // epoch millis
    val documentDate: Long?,        // epoch millis, nullable
    val docState: String,           // serialized DocState
)
```

#### `DocumentLineEntity`
Table: `documentLine` · Primary key: `(documentNo, type, lineNo)`  
Foreign key → `documentHeader(documentNo, type)` ON DELETE CASCADE  
Indices: `(documentNo, type)`, `barcodeNo`
```kotlin
data class DocumentLineEntity(
    val documentNo: String,
    val type: String,
    val lineNo: Int,
    val itemNo: String,
    val itemName: String,
    val barcodeNo: String,
    val expected: Double,
    val destinationCode: String,
    val sourceCode: String,
)
```
Note: `scanned` is NOT stored here — it is computed at query time by summing `RecordingEntity.quantity` for matching `(documentNo, type, lineNo)`.

#### `RecordingEntity`
Table: `recordings` · Primary key: `(documentNo, type, documentLine, recordingLineNo)`  
Foreign key → `documentHeader(documentNo, type)` ON DELETE CASCADE  
Indices: `(documentNo, type)`, `documentLine`
```kotlin
data class RecordingEntity(
    val documentNo: String,
    val type: String,
    val documentLine: Int,         // lineNo; 0 = extra line
    val recordingLineNo: Int,      // auto-incremented per (doc, type, documentLine)
    val barcodeNo: String,
    val quantity: Double,
    val creationDateTime: Long,
    val format: String?,           // barcode format or null
    val userId: String,
    val destinationCode: String,
    val sourceCode: String,
)
```

#### `LocationEntity`
Table: `locations` · Primary key: `code`
```kotlin
data class LocationEntity(
    @PrimaryKey val code: String,
    val name: String,
    val rcCode: String,
)
```

#### `ResponsibilityCenterEntity`
Table: `responsibility_centers` · Primary key: `code`
```kotlin
data class ResponsibilityCenterEntity(
    @PrimaryKey val code: String,
    val name: String,
    val short: String?,
)
```

---

### `data/db/PrimaDatabase.kt`

```kotlin
@Database(
    entities = [DocumentHeaderEntity, DocumentLineEntity, RecordingEntity,
                LocationEntity, ResponsibilityCenterEntity],
    version = 9,
    exportSchema = true,
)
abstract class PrimaDatabase : RoomDatabase() {
    abstract fun documentHeaderDao(): DocumentHeaderDao
    abstract fun documentLineDao(): DocumentLineDao
    abstract fun recordingDao(): RecordingDao
    abstract fun locationDao(): LocationDao
}
```

---

### `data/db/DocumentHeaderDao.kt`

```kotlin
@Dao interface DocumentHeaderDao {
    @Query("SELECT * FROM documentHeader WHERE sourceCode = :sourceCode AND rcCode = :rcCode")
    fun observeHeaders(sourceCode: String, rcCode: String): Flow<List<DocumentHeaderEntity>>

    @Query("SELECT * FROM documentHeader WHERE documentNo = :documentNo AND type = :type")
    fun observeHeader(documentNo: String, type: String): Flow<DocumentHeaderEntity?>

    @Query("SELECT * FROM documentHeader")
    fun observeAllHeaders(): Flow<List<DocumentHeaderEntity>>

    @Query("SELECT * FROM documentHeader WHERE documentNo = :documentNo AND type = :type")
    suspend fun getByKey(documentNo: String, type: String): DocumentHeaderEntity?

    @Query("SELECT * FROM documentHeader")
    suspend fun getAll(): List<DocumentHeaderEntity>

    @Upsert suspend fun upsert(doc: DocumentHeaderEntity)

    @Query("UPDATE documentHeader SET docState = :state WHERE documentNo = :documentNo AND type = :type")
    suspend fun updateState(documentNo: String, type: String, state: String)

    @Query("DELETE FROM documentHeader WHERE documentNo = :documentNo AND type = :type")
    suspend fun deleteByKey(documentNo: String, type: String)

    @Query("DELETE FROM documentHeader")
    suspend fun deleteAll()
}
```

---

### `data/db/DocumentLineDao.kt`

```kotlin
@Dao interface DocumentLineDao {
    @Query("SELECT * FROM documentLine WHERE documentNo = :documentNo AND type = :type AND lineNo = :lineNo")
    suspend fun getByKey(documentNo: String, type: String, lineNo: Int): DocumentLineEntity?

    @Query("SELECT * FROM documentLine WHERE documentNo = :documentNo AND type = :type")
    suspend fun getByDoc(documentNo: String, type: String): List<DocumentLineEntity>

    @Query("SELECT * FROM documentLine WHERE documentNo = :documentNo AND type = :type")
    fun observeByDoc(documentNo: String, type: String): Flow<List<DocumentLineEntity>>

    @Query("SELECT * FROM documentLine")
    fun observeAll(): Flow<List<DocumentLineEntity>>

    @Query("SELECT * FROM documentLine")
    suspend fun getAll(): List<DocumentLineEntity>

    @Upsert suspend fun upsertAll(lines: List<DocumentLineEntity>)
}
```

---

### `data/db/RecordingDao.kt`

```kotlin
@Dao interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :lineNo ORDER BY recordingLineNo DESC")
    fun observeByLine(documentNo: String, type: String, lineNo: Int): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE documentNo = :documentNo AND type = :type ORDER BY documentLine, recordingLineNo")
    fun observeByDoc(documentNo: String, type: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :lineNo ORDER BY recordingLineNo DESC LIMIT 1")
    suspend fun getLastForLine(documentNo: String, type: String, lineNo: Int): RecordingEntity?

    @Query("SELECT COALESCE(MAX(recordingLineNo), 0) + 1 FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :documentLine")
    suspend fun getNextRecordingLineNo(documentNo: String, type: String, documentLine: Int): Int

    @Query("SELECT * FROM recordings WHERE documentNo = :documentNo AND type = :type")
    suspend fun getByDoc(documentNo: String, type: String): List<RecordingEntity>

    @Insert suspend fun insert(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :documentLine AND recordingLineNo = :recordingLineNo")
    suspend fun deleteByPk(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)

    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :lineNo")
    suspend fun deleteAllForLine(documentNo: String, type: String, lineNo: Int)

    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type")
    suspend fun deleteAllForDoc(documentNo: String, type: String)

    @Query("UPDATE recordings SET quantity = :quantity WHERE documentNo = :documentNo AND type = :type AND documentLine = :documentLine AND recordingLineNo = :recordingLineNo")
    suspend fun updateQuantity(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int, quantity: Double)

    @Query("SELECT * FROM recordings")
    suspend fun getAll(): List<RecordingEntity>
}
```

---

### `data/db/LocationDao.kt`

```kotlin
@Dao interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name ASC")
    fun observeLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM responsibility_centers ORDER BY name ASC")
    fun observeRcs(): Flow<List<ResponsibilityCenterEntity>>

    @Upsert suspend fun upsertLocations(locations: List<LocationEntity>)
    @Upsert suspend fun upsertRcs(rcs: List<ResponsibilityCenterEntity>)
    @Query("DELETE FROM locations") suspend fun clearLocations()
    @Query("DELETE FROM responsibility_centers") suspend fun clearRcs()
}
```

---

### `data/db/Mappers.kt` — Serialization & Mapping

**DocState serialization:**
- `fun DocState.toDbString(): String` — serializes to `"Downloaded"` / `"InProgress"` / `"Completed"` / `"UploadFailed:<reason>"`
- `fun String.toDocState(): DocState` — deserializes; prefix `"UploadFailed:"` → `DocState.UploadFailed(reason)`; unknown → `Downloaded`
- `fun String.toDocumentType(): DocumentType` — maps `DocumentType.key` → enum; defaults to `WAREHOUSE_SHIPMENT` on unknown

**Entity → Domain:**
- `fun DocumentLineEntity.toDomain(scanned: Double): Line` — creates Line with computed scanned value
- `fun DocumentHeaderWithLines.toDomain(): Document` — assembles full Document; sums recordings per lineNo; extra lines are recordings with `documentLine == 0`

**Domain → Entity:**
- `fun Document.toEntity(): DocumentHeaderEntity`
- `fun Line.toEntity(type: String): DocumentLineEntity`

**Location/RC mappers:**
- `fun LocationEntity.toDomain(): Location`
- `fun ResponsibilityCenterEntity.toDomain(): ResponsibilityCenter`
- `fun Location.toEntity(): LocationEntity`
- `fun ResponsibilityCenter.toEntity(): ResponsibilityCenterEntity`

---

### `data/db/DocumentHeaderWithLines.kt`

```kotlin
data class DocumentHeaderWithLines(
    val document: DocumentHeaderEntity,
    val lines: List<DocumentLineEntity>,
    val recordings: List<RecordingEntity>,
)
```
Join result carrier — passed to `toDomain()` to assemble a full `Document`.

---

## 6. Repository

### `data/repository/DocumentRepository.kt` — Interface

```kotlin
interface DocumentRepository {
    fun observeAll(): Flow<List<Document>>
    fun observeDocuments(sourceCode: String, rcCode: String): Flow<List<Document>>
    fun observeDocument(documentNo: String, type: String): Flow<Document?>
    suspend fun upsertDocument(doc: Document)
    suspend fun recordScan(documentNo: String, type: String, lineNo: Int,
                           barcodeNo: String, userId: String, quantity: Double, format: String?)
    suspend fun undoLastScan(documentNo: String, type: String, lineNo: Int)
    suspend fun setLineScanned(documentNo: String, type: String, lineNo: Int, scanned: Double)
    suspend fun addExtraLine(documentNo: String, type: String, barcodeNo: String, userId: String, quantity: Double)
    suspend fun updateExtraLineQuantity(documentNo: String, type: String, recordingLineNo: Int, quantity: Double)
    suspend fun deleteExtraLine(documentNo: String, type: String, recordingLineNo: Int)
    suspend fun updateDocState(documentNo: String, type: String, state: DocState)
    suspend fun deleteDocument(documentNo: String, type: String)
    suspend fun getUploadableDocs(): List<Document>
    suspend fun clearAll()
    suspend fun deleteDocumentRecordings(documentNo: String, type: String)
}
```

### `DocumentRepositoryImpl` — Implementation

Annotated `@Singleton`, injected via Hilt. Holds a reference to `PrimaDatabase`.

#### `observeAll(): Flow<List<Document>>`
Combines `observeAllHeaders()` + `observeAll()` lines + `observeAll()` recordings into a single reactive stream. Reassembles all documents on any change.

#### `observeDocuments(sourceCode, rcCode): Flow<List<Document>>`
Like `observeAll()` but filters headers by `sourceCode` + `rcCode` at the DAO level. Still loads ALL lines and recordings for efficiency (filtering happens in headers query).

#### `observeDocument(documentNo, type): Flow<Document?>`
Combines `observeHeader` + `observeByDoc` (lines) + `observeByDoc` (recordings) for a single document. Returns null if header not found.

#### `upsertDocument(doc: Document)`
Runs in a Room transaction:
1. Upserts the header
2. Upserts all lines
3. For each line with `scanned > 0.0`, inserts a seed recording if none exists yet (preserves existing recordings on re-download)

#### `recordScan(...)`
Transaction:
1. Loads the line to get `destinationCode` + `sourceCode`
2. Gets next `recordingLineNo` (`MAX + 1` or `1`)
3. Inserts `RecordingEntity`
4. Calls `advanceToInProgressIfNeeded`

#### `undoLastScan(documentNo, type, lineNo)`
Transaction:
1. Gets most recent recording for the line (highest `recordingLineNo`)
2. Deletes it
3. Calls `regressFromCompletedIfNeeded` + `regressToDownloadedIfNeeded`

#### `setLineScanned(documentNo, type, lineNo, scanned)`
Transaction:
1. Deletes all recordings for this line
2. If `scanned > 0.0`: inserts a single replacement recording
3. Runs all three state machine helpers

#### `addExtraLine(documentNo, type, barcodeNo, userId, quantity)`
Inserts a recording with `documentLine = 0` (the extra-line sentinel). Advances state to InProgress if needed.

#### `updateExtraLineQuantity(documentNo, type, recordingLineNo, quantity)`
Updates quantity for a specific extra-line recording (`documentLine = 0`).

#### `deleteExtraLine(documentNo, type, recordingLineNo)`
Deletes by PK with `documentLine = 0`. Regresses from Completed if needed.

#### `updateDocState(documentNo, type, state)`
Direct SQL UPDATE of `docState` column.

#### `deleteDocument(documentNo, type)`
Deletes header; cascades to lines and recordings.

#### `getUploadableDocs(): List<Document>`
Loads all headers, assembles full documents, filters to those with `any line scanned > 0` OR `extraLines.isNotEmpty()`.

#### `clearAll()`
Deletes all headers (cascades everything).

#### `deleteDocumentRecordings(documentNo, type)`
Transaction: deletes all recordings for the document, then resets state to `Downloaded`.

#### State Machine Helpers (private)

**`advanceToInProgressIfNeeded`**  
If current state is `Downloaded` or `UploadFailed`, advances to `InProgress`.

**`regressToDownloadedIfNeeded`**  
If current state is `InProgress` and no recordings remain, reverts to `Downloaded`.

**`regressFromCompletedIfNeeded`**  
If current state is `Completed` and not all lines are exact or extra lines exist, reverts to `InProgress`.

**`assembleDocuments(headers, lines, recordings): List<Document>`**  
Groups lines and recordings by `(documentNo, type)` maps, then maps each header to a `DocumentHeaderWithLines.toDomain()`.

---

## 7. Auth & Configuration Stores

### `data/auth/AppSettings.kt`

```kotlin
data class AppSettings(
    val textSize: TextSize = TextSize.NORMAL,
    val uppercaseText: Boolean = false,
    val language: Language = Language.ENGLISH,
    val lastScannedLines: Int = 5,
    val autoScan: Boolean = false,
    val debounceTime: Int = 500,
    val hapticEnabled: Boolean = true,
    val muteSound: Boolean = false,
    val lastLocationCode: String = "",
    val lastRcCode: String = "",
    val liveMode: Boolean = false,
    val disabledDocTypes: Set<String> = emptySet(),
)
```

### `data/auth/AppSettingsStore.kt`

`@Singleton`, `SharedPreferences("app_settings", MODE_PRIVATE)`.

| Method | Description |
|---|---|
| `fun get(): AppSettings` | Reads all preferences with defaults |
| `fun save(settings: AppSettings)` | Writes all fields atomically |
| `fun clear()` | Wipes all preferences |

`disabledDocTypes` stored as comma-separated string (e.g. `"RT_SHIP,RT_RCPT"`).

---

### `data/auth/ExtSystemConfig.kt`

```kotlin
data class ExtSystemConfig(
    val serverBaseUrl: String = "",
    val domain: String = "",
    val credentialTtlHours: Int = 24,
    val endpointUrls: Map<DocumentType, String> = emptyMap(),
    val recordingSyncUrl: String = "",
    val locationsUrl: String = "",
    val responsibilityCentersUrl: String = "",
) {
    fun endpointFor(type: DocumentType): String = endpointUrls[type] ?: ""
    val isConfigured: Boolean get() = serverBaseUrl.isNotBlank()
}

data class ExtSystemCredentials(val username: String, val password: String)
```

### `data/auth/ExtSystemConfigStore.kt`

`@Singleton`, `SharedPreferences("ext_system_config", MODE_PRIVATE)`. Per-type endpoint URLs keyed as `"endpoint_${type.key}"`.

| Method | Description |
|---|---|
| `fun get(): ExtSystemConfig` | Reads all config fields |
| `fun save(config: ExtSystemConfig)` | Writes all fields including per-type URLs |
| `fun clear()` | Wipes all config |

### `data/auth/ExtSystemCredentialStore.kt`

`@Singleton`. `EncryptedSharedPreferences` with AES-256-GCM keys in Android Keystore (hardware-backed on API 28+). File: `"ext_system_credentials"`.

| Method | Description |
|---|---|
| `fun save(username, password, ttlHours)` | Stores credentials + expiry = `now + ttlHours * 3_600_000ms` |
| `fun get(): ExtSystemCredentials?` | Returns credentials if not expired; calls `clear()` on expiry |
| `fun isValid(): Boolean` | `get() != null` |
| `fun clear()` | Wipes encrypted preferences |

---

## 8. External System Integration

### `data/extsystem/ExtSystemODataClient.kt`

`@Singleton`. Ktor client backed by OkHttp + NTLM. Client is keyed by `Triple(domain, user, pass)` — rebuilt only when credentials change.

OkHttp timeouts: connect 30s, read 60s, write 60s.

| Method | Description |
|---|---|
| `fun configure(config, creds)` | Builds/reuses OkHttp client |
| `suspend fun testConnection(baseUrl)` | GET to baseUrl, returns Success/Failure |
| `suspend fun upload(url, payload)` | POST Gson JSON, returns Success/Failure |
| `suspend fun downloadRaw(url)` | GET with `odata=nometadata`, returns raw JSON String |
| `fun close()` | Closes client, resets state |

```kotlin
sealed class ExtSystemResult<out T> {
    data class Success<T>(val data: T) : ExtSystemResult<T>()
    data class Failure(val message: String, val code: Int = -1) : ExtSystemResult<Nothing>()
}
```

### `data/extsystem/ExtSystemPayload.kt`

**Upload DTOs:**
```kotlin
data class ExtSystemUploadPayload(val documents: List<ExtSystemUploadDocument>)
data class ExtSystemUploadDocument(val documentNo: String, val type: String, val lines: List<ExtSystemUploadLine>)
data class ExtSystemUploadLine(
    val itemNo: String, val lineNo: String, val recordingLineNo: String,
    val barcodeNo: String, val quantity: String, val creationDateTime: String, val userId: String,
)
```

`fun Document.toUploadPayload()` — regular lines: qty != "0.0", recordingLineNo = "1"; extra lines: lineNo = "0", itemNo = "".  
`fun List<Document>.toUploadPayload()` — wraps in `ExtSystemUploadPayload`.

**Download DTOs:**
- `NavODataList<T>` — wrapper with `@SerializedName("value") val value: List<T>`
- `NavDocumentLine` — flat OData row (Document_No, Location_Code, Bin_Code, Responsibility_Center, Assigned_User_ID, Document_Date, Line_No, Item_No, Description, No_2 → barcodeNo, Qty_Outstanding)
- `NavLocation` — Code, Name, Responsibility_Center
- `NavResponsibilityCenter` — Code, Name, Short

### `data/extsystem/NtlmAuthenticator.kt`

`okhttp3.Authenticator` implementing NTLMv2 without an external library. Uses Android crypto (MD4 inline, HMAC-MD5 via `javax.crypto.Mac`).

**Protocol:**
1. 401 with `WWW-Authenticate: NTLM` → send Type 1 Negotiate
2. 401 with `WWW-Authenticate: NTLM <base64>` → decode Type 2 Challenge, send Type 3 Authenticate
3. Type 3 already sent + still 401 → return null (stop retrying)

**Type 3:** NTHash = HMAC-MD5(MD4(UTF-16LE(password)), UTF-16LE(uppercase(username + domain))), then NTLMv2 response = HMAC-MD5(NTHash, server-challenge + client-blob).

---

## 9. Barcode & Hardware

### `data/barcode/BarcodeAnalyzer.kt`

```kotlin
class BarcodeAnalyzer(private val debounceMs: Long = 1500L, private val onResult: (String) -> Unit)
    : ImageAnalysis.Analyzer
```

`analyze(ImageProxy)`:
1. Creates `InputImage.fromMediaImage`
2. ML Kit processes: picks barcode with largest bounding box
3. Debounces: fires `onResult` only if value changed OR > debounceMs since last
4. Always calls `image.close()` in `addOnCompleteListener`

### `data/barcode/DataWedgeManager.kt`

`object` singleton. Manages Zebra DataWedge via broadcast intents.

- Action: `"com.symbol.datawedge.api.ACTION"`
- Scan broadcast: `"com.prima.barcode.SCAN"`, extra: `"com.symbol.datawedge.data_string"`

| Method | Description |
|---|---|
| `fun configure(context)` | Creates/updates DataWedge profile "PrimaBarcode" with intent output |
| `fun setAudioFeedback(context, muted)` | Sets decode_audio_feedback_uri: `""` (muted) or default URI |
| `fun createReceiver(onScan)` | Returns BroadcastReceiver filtering SCAN_ACTION |
| `fun intentFilter()` | Returns IntentFilter(SCAN_ACTION) |

### `data/haptic/HapticEngine.kt`

```kotlin
class HapticEngine(context: Context)
```

| Method | Vibration | Use case |
|---|---|---|
| `fun confirm()` | 40ms single pulse | Successful scan, exact qty |
| `fun error()` | Waveform 0/70/60/120ms | Unknown barcode, scan error |

---

## 10. ViewModels

### `AppViewModel`

`@HiltViewModel`. App-wide state + all NAV sync operations.

**Constructor:** `@ApplicationContext Context`, `DocumentRepository`, `LocationDao`, `DatabaseExporter`, `ExtSystemConfigStore`, `ExtSystemCredentialStore`, `ExtSystemODataClient`, `AppSettingsStore`.

**`init`:** Seeds sample locations from CSV if locations table is empty.

**State Flows (all `SharingStarted.WhileSubscribed(5_000)`):**
- `locations: StateFlow<List<Location>>`
- `responsibilityCenters: StateFlow<List<ResponsibilityCenter>>`
- `isRefreshingLocations: StateFlow<Boolean>`
- `lastLocationSyncAt: StateFlow<Instant?>`
- `documents: StateFlow<List<Document>>`
- `extSystemConfig: ExtSystemConfig` (computed getter from `_extSystemConfig.value`)

**Methods:**

`fun downloadLocations(liveMode, onComplete)` — If liveMode: fetches from NAV locationsUrl + responsibilityCentersUrl; else: 1.5s delay then re-seeds from CSV.

`fun realDownloadDocuments(onComplete: (failureCount: Int) -> Unit)` — Clears all docs. For each DocumentType with a non-blank endpoint URL: downloads `NavODataList<NavDocumentLine>`, groups by documentNo, assembles and upserts Documents.

`fun uploadToExtSystem(docs, onComplete: (failureCount: Int) -> Unit)` — For each doc: POSTs to recordingSyncUrl (fallback: `"${serverBaseUrl}/OData/WMS_RecordingSync"`). On success: deletes document. On failure: sets UploadFailed state.

`fun saveExtSystemConfig(config)` — Saves to store + updates `_extSystemConfig`.

`fun saveCredentials(domain, username, password)` — Saves to EncryptedSharedPreferences. Updates domain in config if changed.

`fun saveCredentialTtl(hours)` — Updates `credentialTtlHours` in config.

`fun loadSettings(): AppSettings` — Returns `appSettingsStore.get()`.

`fun saveSettings(settings)` — Saves to store.

`fun exportDatabase(uri, onComplete)` — Delegates to `DatabaseExporter.exportTo(uri)`.

`fun clearCache()` — Clears repository, settings, config, credentials.

`fun createDocument(doc)` — Upserts a manually-created document.

`fun testDownload(onComplete)` — Clears docs then seeds sample data.

`fun testImportDocs(docs, onComplete)` — Simulates upload: 300ms delay per doc, every 3rd fails with UploadFailed.

`fun clearDocumentRecordings(documentNo, type)` — Delegates to `repository.deleteDocumentRecordings`.

`fun clearErrorDocs()` — Resets all UploadFailed documents back to Completed/InProgress/Downloaded based on scan state.

`fun insertTestData()` — Calls `seedSampleData()`.

`private suspend fun seedSampleData()` — Creates 9 sample documents (6 WAREHOUSE_SHIPMENT, 3 WAREHOUSE_RECEIPT) with varying states.

`private suspend fun seedSampleLocations()` — Reads `assets/Data_RC_Location.csv`, seeds LocationEntity + ResponsibilityCenterEntity records.

---

### `RecordingViewModel`

`@HiltViewModel`. Per-document. Reads `documentNo` + `type` from `SavedStateHandle`.

`document: StateFlow<Document?>` — `repository.observeDocument(documentNo, type)`.

| Method | Delegates to |
|---|---|
| `fun recordScan(lineNo, barcodeNo, userId, quantity)` | `repository.recordScan` |
| `fun setLineScanned(lineNo, scanned)` | `repository.setLineScanned` |
| `fun addExtraLine(barcodeNo, userId, quantity)` | `repository.addExtraLine` |
| `fun updateExtraLineQuantity(recordingLineNo, quantity)` | `repository.updateExtraLineQuantity` |
| `fun deleteExtraLine(recordingLineNo)` | `repository.deleteExtraLine` |

---

## 11. Navigation & App Shell

### `MainActivity`

`AppCompatActivity @AndroidEntryPoint`.

`onCreate`: `enableEdgeToEdge()` → `DataWedgeManager.configure(this)` → `setContent` with `PrimaBarcodeTheme` + `PrimaBarcodeApp`. All settings state held in `remember { mutableStateOf(...) }` from `appVm.loadSettings()` and saved on each change callback via `appVm.saveSettings(buildSettings().copy(...))`.

### `PrimaBarcodeApp` (private composable)

Root composable with `NavHost`. Key state:

- `rc`, `location` — resolved from rcCode/locationCode against loaded lists
- `filteredDocs` — documents filtered by RC/location + `documentType.filterByRc`
- `docTypes: List<DocTypeSummary>` — per-type summary (count, statusMini), filtered by `disabledDocTypes`
- `processingMessage: String?` — drives non-dismissible blocking progress Dialog
- `showSyncErrorDialog: Boolean` — drives post-upload error AlertDialog

**LaunchedEffects:**
- Auto-select first RC if rcCode no longer matches any loaded RC
- Auto-select first location for current RC if current location doesn't match
- Update DataWedge audio feedback on `muteSound` change

**NavHost routes:**

| Route | Screen | Notes |
|---|---|---|
| `"main"` | MainMenuScreen | `onDocumentOverview → "dashboard?tab=1"` |
| `"location_rc_pick"` | LocationRcPickScreen | |
| `"ext_system_config"` | ExtSystemConfigScreen | Passes `disabledDocTypes` + change callback |
| `"settings"` | SettingsScreen | |
| `"docs"` | DocumentListScreen | `filter = docFilter`; `showDocTypeFilter = false` in filter route |
| `"dashboard?tab={tab}"` | DocumentOverviewScreen | tab defaults to 0 |
| `"filter"` | DocumentFilterScreen | `showDocTypeFilter = false` |
| `"overview_filter"` | DocumentFilterScreen | `lockedSourceCode/lockedRcCode` from state |
| `"download_filter"` | DownloadFilterScreen | |
| `"recording/{documentNo}/{type}"` | RecordingScreen | Uses RecordingViewModel |
| `"upload_error/{documentNo}"` | UploadErrorScreen | |

---

## 12. Design System

### `ui/theme/Color.kt`

**`PrimaPalette`** — brand colors:

| Token | Hex | Role |
|---|---|---|
| `Slate` | `#2E3539` | App chrome (top bars) |
| `Cream` | `#F2EBDE` | Main background |
| `Coral` | `#C95B4D` | Primary CTA / accent |
| `Teal` | `#2F5455` | Supporting accent |
| `Ink` / `Ink2` / `Ink3` / `Ink4` | `#1A1C1F` → `#A4A6AC` | Text scale |

**`PrimaStatus`** — 4-state colors:

| Token | Hex |
|---|---|
| `Empty` | `#CE3A3A` |
| `Partial` | `#C7943A` |
| `Exact` | `#2E8C5E` |
| `Over` | `#2D6CE0` |
| `*Bg` variants | 10% alpha tints |

`PrimaLightColors` / `PrimaDarkColors` — Material 3 color schemes.

### `ui/theme/Type.kt`

- `Geist = FontFamily.Default` (placeholder until TTFs added to res/font/)
- `GeistMono = FontFamily.Monospace`
- `LocalTextSizeOffset` — compositionLocal, 0/2/4 sp offset
- `LocalUppercaseEnabled` — compositionLocal bool
- `String.uppercased` — `@Composable` extension; uppercases when `LocalUppercaseEnabled.current`
- `enum TextSize { NORMAL(spOffset=2), LARGER(spOffset=4) }`
- `scaledTypography(offset)` — adds offset to all sizes
- `val monoCounter` — `@Composable`, 17sp+offset, GeistMono Medium — large qty counters
- `val monoLabel` — `@Composable`, 12sp+offset, GeistMono Normal — codes/timestamps/labels

### `ui/theme/Language.kt`

`enum Language { ENGLISH("en"), CROATIAN("hr"), SLOVENIAN("sl"), MACEDONIAN("mk") }` — applied via `AppCompatDelegate.setApplicationLocales`.

---

## 13. UI Components

### `PrimaTopBar`
Slate header with title, optional subtitle, optional back arrow, right actions slot.
```kotlin
@Composable fun PrimaTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})
```

### `DocumentStatsDashboard`
Shift stats widget. Left: big scan count. Right: 3× `DocStatLine` (Errors/Ready/Partial). Lines inactive at 40% alpha when count == 0.
```kotlin
@Composable fun DocumentStatsDashboard(shiftScans: Int, shiftErrors: Int, shiftReady: Int, shiftPartial: Int, onShowErrors: () -> Unit = {}, onShowReady: () -> Unit = {})
```

### `ScanBar`
Docked barcode input bar. Handles DataWedge + soft keyboard. Auto-scan mode fires on value stabilization with debounce.
```kotlin
@Composable fun ScanBar(value: String, onValueChange: (String) -> Unit, onScan: (String) -> Unit, enabled: Boolean = true, autoScan: Boolean = false, debounceMs: Long = 500, modifier: Modifier = Modifier)
```

### `ScanTape`
Scrollable recent-scan history. Error entries shown with red tint.
```kotlin
@Composable fun ScanTape(entries: List<TapeEntry>, maxVisible: Int = 5, modifier: Modifier = Modifier)
```

### `CameraPreview`
`AndroidView` wrapping `PreviewView`. Binds CameraX `Preview` + `ImageAnalysis` with provided analyzer.
```kotlin
@Composable fun CameraPreview(analyzer: ImageAnalysis.Analyzer, modifier: Modifier = Modifier)
```

### `StatusComponents`
- `StatusProgressBar` — horizontal bar: EXACT count / total lines
- `StatusDot` — small colored circle
- `StatusBadge` — pill with status color + text

### `Chip`
Generic selectable chip. Used in filter screens.

### `ScanField`
Lightweight barcode input field for dialog/modal contexts.

### `SyncPip`
Small animated sync state indicator.

### `ScrollbarModifier`
`fun Modifier.verticalScrollbar(scrollState: ScrollState): Modifier` — draws thin right-edge scrollbar via `drawWithContent`. Hidden when content fits.

---

## 14. Screens

### `MainMenuScreen`
Home. Shows `DocTypeSummary` tiles in a `LazyColumn`. `DocumentStatsDashboard` at top. Location/RC context strip. Settings icon button in top bar.

```kotlin
data class DocTypeSummary(val type: DocumentType, val short: String, val count: Int, val statusMini: List<LineStatus>)

@Composable fun MainMenuScreen(user, location, rc, docTypes, shiftScans, shiftErrors, shiftReady, shiftPartial, onChangeLocationRc, onOpenSettings, onTypeTap, onDocumentOverview, onShowErrors)
```

### `DocumentListScreen`
Per-type document list, 3 tabs: Orders / Recordings / Errors.

```kotlin
@Composable fun DocumentListScreen(docType, locationCode, documents, onBack, onDocTap, onDownload, onUpload, onErrorTap, onCreateDoc, onDeleteRecordings, onClearErrors, filter, onOpenFilter)
```

- **Tab 0 (Orders):** Downloaded docs. Buttons: Download + Upload.
- **Tab 1 (Recordings):** InProgress + Completed. Button: Upload only (full width). 5-second long-press on row → delete recordings (with progress indicator overlay).
- **Tab 2 (Errors):** UploadFailed. Buttons: Clear Errors + Upload. Clear Errors shows confirmation AlertDialog.

### `DocumentOverviewScreen`
Cross-type overview, 2 tabs: All / My Location.

```kotlin
@Composable fun DocumentOverviewScreen(locationCode, rcCode, documents, onBack, onDocTap, onClearErrors, onErrorTap, filter, onOpenFilter, initialTab)
```

Tab 1 (My Location): shows `sourceCode == locationCode` OR `rcCode == rcCode`. Opens filter with both fields locked.

Filter predicate: `dateOk && stateOk && typeOk && destOk && srcOk && rcOk`.

### `DocumentFilterScreen`
Filter config. Sections: Status chips (LineStatus), Document Type checkboxes (optional), Date range, Destination Code, Source Code (lockable), Responsibility Center (lockable).

```kotlin
@Composable fun DocumentFilterScreen(initialFilter, lockedSourceCode, lockedRcCode, showDocTypeFilter, onApply, onBack)
```

### `DownloadFilterScreen`
Filter before NAV download. Shows login prompt when no credentials.

```kotlin
@Composable fun DownloadFilterScreen(hasCredentials, onConfirm, onCancel)
```

### `RecordingScreen`
Core scanning interface. Most complex screen.

```kotlin
@Composable fun RecordingScreen(doc, onBack, onScan, onLineUpdate, onExtraLineAdd, onExtraLineUpdate, onExtraLineDelete, onUpload, lastScannedLines, autoScan, hapticEnabled, muteSound, debounceTime)
```

Views: `OVERVIEW` | `ACTIVE_LINE` | `KEYPAD` | `UNKNOWN_BARCODE` | `EXTRA_LINE` | `EXTRA_KEYPAD`.  
Inputs: DataWedge broadcast receiver + CameraPreview fallback (with runtime CAMERA permission request).  
Haptic: `confirm()` on EXACT, `error()` on unknown barcode.

### `ExtSystemConfigScreen`
NAV server config. Back arrow = save.

```kotlin
@Composable fun ExtSystemConfigScreen(initial, onSave, disabledDocTypes, onDisabledDocTypesChange)
```

Sections: Connection (baseUrl, domain), Endpoints (one Switch + URL field per DocumentType), System URLs (recording sync, locations, RCs).

### `SettingsScreen`
App preferences. Sections: display, scan, connection (credentials, live mode), data (export, cache clear, test data), sign out.

### `LocationRcPickScreen`
Location/RC picker with optional NAV credentials for refresh.

### `UploadErrorScreen`
Document detail for UploadFailed state. Shows full error reason + Retry button.

### `LoginSheet`
Bottom sheet for NAV credential entry (shown before download/upload in live mode).

---

## 15. DI Module

### `di/DatabaseModule.kt`

`@Module @InstallIn(SingletonComponent::class)` provides:
- `@Singleton PrimaDatabase` via `Room.databaseBuilder` with `fallbackToDestructiveMigration()`
- `DocumentHeaderDao`, `DocumentLineDao`, `RecordingDao`, `LocationDao` from DB
- `@Singleton DocumentRepository` bound to `DocumentRepositoryImpl`

---

## 16. Localization

`res/values/strings.xml` (English) + `res/values-hr/strings.xml` (Croatian).

Key string groups:

| Prefix | Content |
|---|---|
| `btn_*` | Button labels |
| `doc_filter_*` | Filter screen labels |
| `doc_status_*` | LineStatus display names |
| `doctype_*` | DocumentType display names |
| `filter_*` | Filter field labels |

Language switched via `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))`.

---

## 17. Assets

### `assets/Data_RC_Location.csv`

Semicolon-delimited CSV (header row: `code;rc`). Maps warehouse location codes to responsibility centers. Seeded into Room on first run or after cache clear. If `rc` column is blank, `code` serves as its own RC.

---

## Appendix: Key Design Decisions

### Scanned qty is computed, not stored
`DocumentLineEntity` has no `scanned` column. Scanned quantity = `SUM(RecordingEntity.quantity)` for `(documentNo, type, lineNo)`. Enables full audit trail, undo-last-scan (delete highest recordingLineNo), and quantity correction (delete all + insert one).

### Extra lines use `documentLine = 0`
NAV line numbers start at 1. Sentinel `0` for extra lines avoids a separate table. All recording operations are uniform.

### NTLMv2 inline
No external NTLM library. Inline implementation uses only Android crypto (MD4 inline, HMAC-MD5). Supports NTLMv2 only; client challenge is cryptographically random. Credentials never stored in authenticator.

### DocType filtering modes
- `filterByRc = true` (WHSE_SHIP, WHSE_RCPT, TRANSPORT): filtered by `rcCode`
- `filterByRc = false` (RT_SHIP, RT_RCPT): filtered by `sourceCode == location.code`  
Mirrors NAV business logic: retail documents belong to a store (location), not an RC.

### Settings state in MainActivity
Settings held in `remember { mutableStateOf(...) }` in `MainActivity.onCreate`, passed as props + callbacks. Avoids extra StateFlow; changes immediately propagate through Compose tree.

### Back = Save in ExtSystemConfigScreen
No explicit Save button. PrimaTopBar `onBack = { onSave(buildConfig()) }`. `onSave` in MainActivity calls `nav.popBackStack()`.