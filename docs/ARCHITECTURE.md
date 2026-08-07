# PrimaBarcode — Full Architecture Reference

Exhaustive technical reference for the PrimaBarcode Android app: every database table, DAO/repository function, screen, ViewModel, networking call, and build config. Generated from a full source-tree survey. For a terser onboarding summary see `CLAUDE.md`.

---

## 1. Overview

**PrimaBarcode** is a native Android barcode-scanning app for warehouse inventory operations, backed by a **Dynamics NAV 2018** ERP system reached over **OData V4** with NTLM authentication. Single Gradle module `:app`, package `com.prima.barcode`.

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 (BOM `2026.02.01`) |
| Navigation | Compose Navigation `2.8.2` |
| Networking | Ktor Client `2.3.12` (OkHttp engine) + custom hand-rolled NTLMv2 `Authenticator` |
| Local DB | Room `2.7.0` (KSP-generated DAOs), db file `prima_barcode.db`, schema version 13 |
| DI | Hilt/Dagger `2.59` |
| Barcode | ML Kit `17.3.0` (camera) + Zebra DataWedge (hardware wedge) |
| Camera | CameraX `1.4.2` |
| Secure storage | `androidx.security:security-crypto:1.1.0-alpha06` (NAV credentials, TTL-based expiry) |
| JSON | Gson `2.11.0` (hand-serialized; Ktor content-negotiation/kotlinx-json deps are present but unused) |
| Logging | Timber `5.0.1` |
| Kotlin / AGP | Kotlin `2.2.10`, AGP `9.2.1` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |

### Source layout

```
app/src/main/java/com/prima/barcode/
├── PrimaBarcodeApplication.kt    # @HiltAndroidApp, plants Timber
├── MainActivity.kt               # @AndroidEntryPoint, Compose NavHost, app shell
├── data/
│   ├── auth/                     # AppSettings, ExtSystemConfig, credential stores
│   ├── barcode/                  # BarcodeAnalyzer (ML Kit), DataWedgeManager (Zebra wedge)
│   ├── db/                       # Room entities, DAOs, PrimaDatabase, Mappers
│   ├── export/                   # DatabaseExporter (debug raw-dump tool)
│   ├── extsystem/                # ExtSystemODataClient, NtlmAuthenticator, DTOs
│   ├── model/                    # Domain models (Models.kt, Filter.kt, Status.kt)
│   └── repository/               # DocumentRepository
├── di/
│   └── DatabaseModule.kt         # the only Hilt module
└── ui/
    ├── theme/                    # Color.kt, Type.kt, Shape.kt, Theme.kt, Language.kt
    ├── component/                # Reusable composables
    ├── screen/                   # Full screens
    └── viewmodel/                # AppViewModel, RecordingViewModel
```

---

## 2. Database Schema (Room)

**`PrimaDatabase`** — `app/src/main/java/com/prima/barcode/data/db/PrimaDatabase.kt`
`@Database(entities = [DocumentHeaderEntity, DocumentLineEntity, RecordingEntity, LocationEntity, ResponsibilityCenterEntity], version = 13, exportSchema = true)`. DB file name: `prima_barcode.db`. Built in `di/DatabaseModule.kt` with `.addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13).fallbackToDestructiveMigration(dropAllTables = true)` — any version jump not covered by an explicit migration wipes all tables.

### 2.1 `DocumentHeaderEntity` — table `documentHeader`
File: `data/db/Entities.kt`. Composite primary key: `(documentNo, type)`.

| Column | Type | Nullable |
|---|---|---|
| documentNo | String | No |
| type | String (`DocumentType.key`, e.g. `WHSE_SHIP`) | No |
| destinationCode | String | No |
| sourceCode | String | No |
| rcCode | String | No |
| creationDateTime | Long (epoch millis) | No |
| documentDate | Long? (epoch millis) | Yes |
| docState | String (serialized `DocState`: `Downloaded`, `InProgress`, `Completed`, `PendingUpload`, `UploadFailed:<reason>`) | No |

### 2.2 `DocumentLineEntity` — table `documentLine`
Composite PK: `(documentNo, type, lineNo)`. FK `(documentNo, type)` → `documentHeader`, `ON DELETE CASCADE`. Indices: `(documentNo, type)`, `(barcodeNo)`.

| Column | Type | Nullable |
|---|---|---|
| documentNo | String | No |
| type | String | No |
| lineNo | Int | No |
| itemNo | String | No |
| itemName | String | No |
| barcodeNo | String | No |
| expected | Double | No |
| destinationCode | String | No |
| sourceCode | String | No |
| unitOfMeasureCode | String | No |
| scanningQty | Double (default 1.0) | No |

### 2.3 `RecordingEntity` — table `recordings`
Composite PK: `(documentNo, type, documentLine, recordingLineNo)`. FK `(documentNo, type)` → `documentHeader`, `ON DELETE CASCADE`. Indices: `(documentNo, type)`, `(documentLine)`. `documentLine = 0` is a sentinel for "extra line" (barcode not on the document) scans.

| Column | Type | Nullable |
|---|---|---|
| documentNo | String | No |
| type | String | No |
| documentLine | Int (0 = extra/not-on-document) | No |
| recordingLineNo | Int | No |
| barcodeNo | String | No |
| quantity | Double | No |
| creationDateTime | String (ISO-8601, `Instant.toString()`) | No |
| format | String? (barcode symbology) | Yes |
| userId | String | No |
| destinationCode | String | No |
| sourceCode | String | No |
| unitOfMeasureCode | String | No |

### 2.4 `LocationEntity` — table `locations`

| Column | Type | Nullable |
|---|---|---|
| code | String (`@PrimaryKey`) | No |
| name | String | No |
| rcCode | String | No |

### 2.5 `ResponsibilityCenterEntity` — table `responsibility_centers`

| Column | Type | Nullable |
|---|---|---|
| code | String (`@PrimaryKey`) | No |
| name | String | No |
| short | String? | Yes |

### 2.6 Non-entity composite: `DocumentHeaderWithLines`
File: `data/db/DocumentHeaderWithLines.kt` — plain data class (not a Room `@Relation`), manually assembled by the repository: `{ document: DocumentHeaderEntity, lines: List<DocumentLineEntity>, recordings: List<RecordingEntity> }`.

### 2.7 Migrations
- **`MIGRATION_10_11`** — adds `scanningQty REAL NOT NULL DEFAULT 1.0` to `documentLine`.
- **`MIGRATION_11_12`** — rebuilds `recordings`: converts `creationDateTime` from epoch-millis `Long` to ISO-8601 `TEXT` (via `strftime`), recreates PK/FK/indices (`index_recordings_documentNo_type`, `index_recordings_documentLine`).
- **`MIGRATION_12_13`** — rebuilds `documentHeader` (copy-and-rename pattern) to normalize the table definition (column content unchanged).

---

## 3. DAOs

### 3.1 `DocumentHeaderDao` — `data/db/DocumentHeaderDao.kt`

| Function | SQL / behavior |
|---|---|
| `observeHeaders(sourceCode, rcCode): Flow<List<DocumentHeaderEntity>>` | `SELECT * FROM documentHeader WHERE sourceCode = :sourceCode AND rcCode = :rcCode` |
| `observeHeader(documentNo, type): Flow<DocumentHeaderEntity?>` | `SELECT * FROM documentHeader WHERE documentNo = :documentNo AND type = :type` |
| `observeAllHeaders(): Flow<List<DocumentHeaderEntity>>` | `SELECT * FROM documentHeader` |
| `getByKey(documentNo, type): DocumentHeaderEntity?` (suspend) | one-shot key lookup |
| `getAll(): List<DocumentHeaderEntity>` (suspend) | `SELECT * FROM documentHeader` |
| `upsert(doc: DocumentHeaderEntity)` (suspend, `@Upsert`) | insert or replace header |
| `updateState(documentNo, type, state: String)` (suspend) | `UPDATE documentHeader SET docState = :state WHERE documentNo = :documentNo AND type = :type` |
| `deleteByKey(documentNo, type)` (suspend) | `DELETE FROM documentHeader WHERE ...` (cascades to lines/recordings) |
| `deleteAll()` (suspend) | `DELETE FROM documentHeader` |
| `deleteAllByType(type)` (suspend) | `DELETE FROM documentHeader WHERE type = :type` |

### 3.2 `DocumentLineDao` — `data/db/DocumentLineDao.kt`

| Function | SQL / behavior |
|---|---|
| `getByKey(documentNo, type, lineNo): DocumentLineEntity?` (suspend) | key lookup |
| `getByDoc(documentNo, type): List<DocumentLineEntity>` (suspend) | one-shot lines for a doc |
| `observeByDoc(documentNo, type): Flow<List<DocumentLineEntity>>` | reactive lines for a doc |
| `observeAll(): Flow<List<DocumentLineEntity>>` | `SELECT * FROM documentLine` |
| `getAll(): List<DocumentLineEntity>` (suspend) | `SELECT * FROM documentLine` |
| `upsertAll(lines: List<DocumentLineEntity>)` (suspend, `@Upsert`) | bulk insert/replace |

### 3.3 `RecordingDao` — `data/db/RecordingDao.kt`

| Function | SQL / behavior |
|---|---|
| `observeByLine(documentNo, type, lineNo): Flow<List<RecordingEntity>>` | `... WHERE documentNo=:d AND type=:t AND documentLine=:l ORDER BY recordingLineNo DESC` |
| `observeByDoc(documentNo, type): Flow<List<RecordingEntity>>` | `... ORDER BY documentLine, recordingLineNo` |
| `observeAll(): Flow<List<RecordingEntity>>` | `SELECT * FROM recordings` |
| `getLastForLine(documentNo, type, lineNo): RecordingEntity?` (suspend) | most recent recording for a line |
| `getNextRecordingLineNo(documentNo, type, documentLine): Int` (suspend) | `SELECT COALESCE(MAX(recordingLineNo),0)+1 ...` — line-number generator |
| `getExtraByBarcode(documentNo, type, barcodeNo): RecordingEntity?` (suspend) | finds existing extra-line (documentLine=0) recording for a barcode, to accumulate qty |
| `getByDoc(documentNo, type): List<RecordingEntity>` (suspend) | one-shot recordings for a doc |
| `insert(recording: RecordingEntity)` (suspend, `@Insert`) | insert new recording |
| `deleteByPk(documentNo, type, documentLine, recordingLineNo)` (suspend) | delete one recording |
| `deleteAllForLine(documentNo, type, lineNo)` (suspend) | delete all recordings for a line |
| `deleteAllForDoc(documentNo, type)` (suspend) | delete all recordings for a doc |
| `updateQuantity(documentNo, type, documentLine, recordingLineNo, quantity)` (suspend) | update qty of one row |
| `getAll(): List<RecordingEntity>` (suspend) | `SELECT * FROM recordings` (used by `DatabaseExporter`) |

### 3.4 `LocationDao` — `data/db/LocationDao.kt`

| Function | SQL / behavior |
|---|---|
| `observeLocations(): Flow<List<LocationEntity>>` | `SELECT * FROM locations ORDER BY name ASC` |
| `observeRcs(): Flow<List<ResponsibilityCenterEntity>>` | `SELECT * FROM responsibility_centers ORDER BY name ASC` |
| `upsertLocations(locations: List<LocationEntity>)` (suspend, `@Upsert`) | bulk upsert |
| `upsertRcs(rcs: List<ResponsibilityCenterEntity>)` (suspend, `@Upsert`) | bulk upsert |
| `clearLocations()` (suspend) | `DELETE FROM locations` |
| `clearRcs()` (suspend) | `DELETE FROM responsibility_centers` |

---

## 4. Repositories

### `DocumentRepository` / `DocumentRepositoryImpl`
File: `data/repository/DocumentRepository.kt`. `@Singleton class DocumentRepositoryImpl @Inject constructor(private val db: PrimaDatabase)` — talks directly to the three document DAOs on `db` (no field-level DAO injection). **This is the only class under `data/repository/`.**

| Function | Description |
|---|---|
| `observeAll(): Flow<List<Document>>` | Combines all headers + all lines + all recordings, assembles domain `Document` list |
| `observeDocuments(sourceCode, rcCode): Flow<List<Document>>` | Same, headers filtered by source/RC |
| `observeDocument(documentNo, type): Flow<Document?>` | Combines single header + its lines + its recordings |
| `upsertDocument(doc: Document)` (suspend) | Transaction: upserts header + all lines; for any line with `scanned > 0` and no existing recording, seeds one recording row (used when importing a doc that already has scanned quantities) |
| `recordScan(documentNo, type, lineNo, barcodeNo, userId, quantity, format)` (suspend) | Transaction: looks up the line, generates next `recordingLineNo`, inserts `RecordingEntity`, calls `advanceToInProgressIfNeeded` |
| `undoLastScan(documentNo, type, lineNo)` (suspend) | Transaction: deletes most recent recording for a line, then regresses doc state if needed |
| `setLineScanned(documentNo, type, lineNo, scanned)` (suspend) | Transaction: deletes all recordings for the line, re-inserts one recording with the exact quantity (if >0); recalculates doc state both directions — manual override path |
| `addExtraLine(documentNo, type, barcodeNo, userId, quantity)` (suspend) | Transaction: adds a not-on-document scan (documentLine=0); accumulates into an existing recording for that barcode if one exists, else inserts new; updates doc state |
| `updateExtraLineQuantity(documentNo, type, recordingLineNo, quantity)` (suspend) | Sets quantity of an extra-line recording; updates doc state |
| `deleteExtraLine(documentNo, type, recordingLineNo)` (suspend) | Deletes one extra-line recording; regresses doc state if needed |
| `updateDocState(documentNo, type, state: DocState)` (suspend) | Direct `UPDATE docState` |
| `deleteDocument(documentNo, type)` (suspend) | Deletes header (cascades lines + recordings) |
| `getUploadableDocs(): List<Document>` (suspend) | One-shot: builds all documents and filters to those with scanned qty > 0 or extra lines — upload candidates |
| `clearAll()` (suspend) | Deletes all headers (cascade wipes everything) |
| `clearByType(type: DocumentType)` (suspend) | Deletes all headers of one document type |
| `deleteDocumentRecordings(documentNo, type)` (suspend) | Transaction: deletes all recordings for a doc, resets state back to `Downloaded` — "reset scan progress" |

**Private helpers:**
- `assembleDocuments(headers, lines, recordings)` — in-memory `groupBy (documentNo, type)` that turns flat DAO lists into `DocumentHeaderWithLines` → `.toDomain()` `Document` list, avoiding N+1 queries.
- `advanceToInProgressIfNeeded` — if state is `Downloaded`/`UploadFailed:*`, bumps to `InProgress` after scan activity.
- `regressToDownloadedIfNeeded` — if state is `InProgress` and there are now zero recordings, reverts to `Downloaded`.
- `regressFromCompletedIfNeeded` — if state is `Completed` but recordings no longer exactly match expected qty per line (or extra lines exist), reverts to `InProgress`.

**Note:** NAV network sync (download/upload) is **not** wrapped in a repository — it's implemented directly in `AppViewModel` (see §12), which calls `ExtSystemODataClient` and the DAOs/repository directly.

---

## 5. Domain Models

File: `data/model/Models.kt`
- `User`, `Location`, `ResponsibilityCenter`
- `DocTypeFilterMode` enum: `LOCATION`, `RESPONSIBILITY_CENTER`
- `DocumentType` enum (5 values, each with `key` used as DB/network discriminator and `display` label): `WAREHOUSE_SHIPMENT`, `WAREHOUSE_RECEIPT`, `RETAIL_SHIPMENT`, `RETAIL_RECEIPT`, `TRANSPORT_SHEET`
- `Item`, `Line` (derived `status: LineStatus`), `ExtraLine`
- `Document` (derived `linesExact`, `linesTotal`, `scannedQty`, `expectedQty`)
- `DocState` sealed interface: `Downloaded`, `InProgress`, `Completed`, `PendingUpload`, `UploadFailed(reason)`
- `TapeEntry` (scan-log UI model)
- extension `Double.formatQty()`

File: `data/model/Filter.kt`
- `DocumentFilter` (dateFrom/To, states, types, destination/source/rc codes, derived `isActive`)
- `DownloadFilter` (dateFrom/To, destination/source/rc codes)

File: `data/model/Status.kt` — the **4-state status language**
- `LineStatus` enum: `EMPTY, PARTIAL, EXACT, OVER`, factory `.of(scanned, expected)`, UI extension properties `.color`/`.bgColor`/`.label`
- `Int.flooredAtZero()`
- `Document.scanStatus(): LineStatus` — aggregate status across all lines + extra lines (EMPTY if all empty and no extras; OVER if any line over; EXACT if all exact and no extras; else PARTIAL)

File: `data/db/Mappers.kt` — Entity↔Domain conversions
- `DocState.toDbString()` / `String.toDocState()`
- `String.toDocumentType()`
- `DocumentLineEntity.toDomain(scanned)`
- `DocumentHeaderWithLines.toDomain()` — computes `scanned` per line by summing matching recordings, builds `extraLines` from `documentLine==0` recordings
- `Document.toEntity()`, `Line.toEntity(type)`
- `LocationEntity.toDomain()` / `Location.toEntity()`
- `ResponsibilityCenterEntity.toDomain()` / `ResponsibilityCenter.toEntity()`

### Supporting: `DatabaseExporter`
File: `data/export/DatabaseExporter.kt` — `@Singleton class DatabaseExporter @Inject constructor(context, db: PrimaDatabase)`. `suspend fun exportTo(uri: Uri)` — reads all headers/lines/recordings via the DAOs, wraps in `{exportedAt, documentHeaders, documentLines, recordings}`, Gson-pretty-prints to the given `Uri`. Debug/support tool — full raw DB dump, invoked from `SettingsScreen`.

---

## 6. Networking / NAV Integration

### 6.1 `ExtSystemODataClient`
File: `data/extsystem/ExtSystemODataClient.kt` — `@Singleton class ExtSystemODataClient @Inject constructor()`. The only HTTP client wrapper in the app. Wraps a preconfigured OkHttp client (via Ktor's `OkHttp` engine) carrying a custom NTLM `okhttp3.Authenticator` — Ktor's built-in auth plugin is **not** used.

- `HttpClient(OkHttp) { engine { preconfigured = okHttp }; expectSuccess = false }` — `expectSuccess = false` so 4xx/5xx are returned normally rather than thrown.
- Underlying `OkHttpClient`: `.proxy(Proxy.NO_PROXY)`, `.authenticator(NtlmAuthenticator(domain, username, password))`, `.connectTimeout(30s)`, `.readTimeout(60s)`, `.writeTimeout(60s)`.
- `configure(config, creds)` only rebuilds the `HttpClient` when `(username, password)` changes (cached by `clientKey`); old client is `.close()`d first.
- No fixed Ktor base URL — every call takes a full absolute URL built in `AppViewModel`.
- No Ktor ContentNegotiation plugin installed (despite the dependency being declared) — JSON is hand-built/parsed with **Gson** (`gson.toJson`, manual `JsonParser`/`JsonObject`/`JsonArray`).
- Requests set `Accept: application/json`; downloads additionally force `Accept: application/json;odata=nometadata`.

```kotlin
sealed class ExtSystemResult<out T> {
    data class Success<T>(val data: T) : ExtSystemResult<T>()
    data class Failure(val message: String, val code: Int = -1) : ExtSystemResult<Nothing>()
}
```

| Function | Behavior |
|---|---|
| `testConnection(baseUrl): ExtSystemResult<Unit>` (suspend) | `GET baseUrl`; resets `ntlmAuth.resetPhase()` first; on failure inspects `phaseReached` to distinguish "NTLM not enabled on endpoint" (phase 0) vs "handshake completed, credentials rejected" (phase 2 + 401) vs generic HTTP error (body, truncated 300 chars) |
| `upload(url, payload: ExtSystemUploadPayload): ExtSystemResult<Unit>` (suspend) | `POST url`, `Content-Type: application/json`, body = `gson.toJson(payload)` |
| `downloadRaw(url): ExtSystemResult<String>` (suspend) | `GET url`; follows OData `@odata.nextLink` paging, merges all pages' `value` arrays into one JSON string; logs page counts via Timber |
| `close()` | closes/clears the underlying client |
| `companion.parseDomainUser(raw): Pair<domain, username>` | splits `DOMAIN\user` or `user@domain`; falls back to `("", raw)` |

### 6.2 `NtlmAuthenticator`
File: `data/extsystem/NtlmAuthenticator.kt` — custom OkHttp `Authenticator` implementing **NTLMv2** from scratch (no external library). Uses `javax.crypto` `HmacMD5` plus a hand-rolled inline **MD4** (`internal fun md4`, RFC 1320) since Android's `MessageDigest` lacks MD4 (needed for the NT hash).

- Constructor: `domain`, `username`, `password` (parsed via `parseDomainUser`); credentials never logged.
- `phaseReached: Int` (`@Volatile`, 0/1/2) tracks handshake progress for diagnostics; `resetPhase()` resets before each test connection.
- `authenticate(route, response)`:
  - **Phase 1**: server sends bare `NTLM` challenge → builds/sends Type 1 Negotiate message (`type1()`, flags `0x80800205`). Guards against infinite retry: if the previous request already carried `Authorization: NTLM ...`, the server restarted negotiation (bad credentials) → returns `null`.
  - **Phase 2**: server responds `NTLM <base64 Type2 challenge>` → decodes it, builds Type 3 Authenticate (`type3(challenge)`):
    - NT Hash = `MD4(UTF-16LE(password))`
    - NTLMv2 key = `HMAC-MD5(ntHash, UTF-16LE(uppercase(user+domain)))`
    - Extracts target info from Type2 offset 40/44
    - Builds NTLMv2 blob (signature `0x00000101`, Windows FILETIME timestamp, random 8-byte client challenge via `SecureRandom`, target info)
    - `ntResponse = HMAC-MD5(ntlmv2Key, serverChallenge + blob) + blob`
    - `lmResponse = HMAC-MD5(ntlmv2Key, serverChallenge + clientChallenge) + clientChallenge` (LMv2)
    - Manually lays out the Type 3 byte buffer, base64-encodes as the new `Authorization: NTLM ...` header

**The app is NTLM-only** — despite CLAUDE.md mentioning "Basic + NTLM auth", there is no Basic-auth code path anywhere in the source; `buildClient` always constructs an `NtlmAuthenticator`.

### 6.3 DTOs — `data/extsystem/ExtSystemPayload.kt` (Gson, `@SerializedName`)

**Upload (app → NAV):**
- `ExtSystemUploadPayload(documents: List<ExtSystemUploadDocument>)`
- `ExtSystemUploadDocument(documentNo, type, lines: List<ExtSystemUploadLine>)`
- `ExtSystemUploadLine(itemNo, lineNo, recordingLineNo, barcodeNo, quantity, creationDateTime, userId — all String-typed to match NAV's expected JSON text format)`
- `Document.toUploadPayload(): ExtSystemUploadDocument` — one upload line per document line (`scanned` as total qty, `recordingLineNo="1"`, filters out zero-qty lines) + one per `ExtraLine` (`lineNo="0"`, `itemNo=""`). `List<Document>.toUploadPayload()` wraps many docs into one payload.

**Download (NAV → app):**
- `NavODataList<T>(value: List<T>)` — generic OData `{"value": [...]}` envelope
- `NavDocumentLine` — flat line record: `Document_No, Location_Code→sourceCode, Bin_Code→destinationCode, Responsibility_Center→rcCode, Assigned_User_ID→ownerUserId, Document_Date, Line_No, Item_No, Description, No_2→barcodeNo, Qty_Outstanding→qtyOutstanding, Unit_of_Measure_Code`
- `NavBarcodeEntriesDownload` — flat line record from the `BarcodeAppEntries` page: `Document_Type→documentType, Document_No, Line_No, Source_No→sourceCode, Retail_Location, Destination_No→destinationNo, Document_Date, Responsibility_Center→rcCode, Item_No, Item_Description→description, Item_Qty→qtyOutstanding, Scanning_Qty→scanningQty (default 1.0), Item_UoM→unitOfMeasureCode, Barcode→barcodeNo`
- `NavLocation(Code→code, Name→name, Responsibility_Center→rcCode)`
- `NavResponsibilityCenter(Code→code, Name→name, Short→short?)`

### 6.4 Endpoint table (from bundled `ext_system_defaults.json`)

| Purpose | Config field | Sample URL | Verb |
|---|---|---|---|
| Server root / auth test | `serverBaseUrl` | `http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/` | GET |
| Warehouse Shipment docs | `endpointUrls[WAREHOUSE_SHIPMENT]` | `.../Company('Prima Commerce d.o.o.')/BarcodeAppEntries` | GET (+ `$filter`) |
| Warehouse Receipt docs | `endpointUrls[WAREHOUSE_RECEIPT]` | same page, `Document_Type` filter differs | GET (+ `$filter`) |
| Retail Shipment docs | `endpointUrls[RETAIL_SHIPMENT]` | same page | GET (+ `$filter`) |
| Retail Receipt docs | `endpointUrls[RETAIL_RECEIPT]` | same page | GET (+ `$filter`) |
| Transport Sheet docs | `endpointUrls[TRANSPORT_SHEET]` | same page | GET (+ `$filter`) |
| Locations / RC list | `locationsUrl` | `.../Company('Prima Commerce d.o.o.')/LocationList` | GET |
| Recording (scan) sync/upload | `recordingSyncUrl` | `.../Company('Prima Commerce d.o.o.')/BEMFRecording` | POST |

All five document types share the single `BarcodeAppEntries` OData entity set, differentiated by the `documentTypeCodes` value sent in the `Document_Type` filter clause (`SHIPMENT`, `RECEIPT`, `RETAILSHPT`, `RETAILRCPT`, `TRANSPORT`).

### 6.5 Network operations (business-logic level, in `AppViewModel`)

1. **Test connection** — `AppViewModel.testExtSystemConnection(serverBaseUrl, username, password, persistOnSuccess=true, onResult)`. `GET` the server root; on success (+ `persistOnSuccess`) persists credentials.
2. **Download locations/RCs** — `AppViewModel.downloadLocations()` → `realDownloadLocations()`. `GET locationsUrl` → `NavODataList<NavLocation>`; derives distinct RC codes from each location's `Responsibility_Center`; replaces `locations`/`responsibility_centers` tables (`clear*` + `upsert*`).
3. **Download documents** — `AppViewModel.realDownloadDocuments(filter, docType, onComplete)`. Per document type: builds an OData `$filter` via `buildODataFilterString`/`appendODataFilter` (`Document_Type eq '<code>'`, `Document_Date ge/le`, destination field [`Retail_Location` for WAREHOUSE_SHIPMENT else `Bin_Code`], source field [`Source_No` for WAREHOUSE_SHIPMENT else `Location_Code`], `Responsibility_Center eq '<rc>'`); `repository.clearByType(type)` first; `GET` via `downloadRaw`; parses as `NavBarcodeEntriesDownload` for `WAREHOUSE_SHIPMENT` or `NavDocumentLine` for the rest; groups by `documentNo`; `repository.upsertDocument(...)` with state `Downloaded`.
4. **Upload recordings** — `AppViewModel.runUpload(docs)` (private, shared by `uploadToExtSystem` [blocking] and `uploadInBackground` [marks `PendingUpload` immediately, uploads async]). One `POST` **per document** to `recordingSyncUrl` (falls back to `<serverBaseUrl>/OData/WMS_RecordingSync` if unset). Success → `repository.deleteDocument(...)`; failure → `repository.updateDocState(..., UploadFailed(message))`.
5. **Config loading** — `AppViewModel.loadExtSystemDefaults()` reads bundled asset `ext_system_defaults.json`; `ExtSystemConfigScreen` also supports importing an arbitrary JSON file via `GetContent`.

### 6.6 Network security config
`app/src/main/res/xml/network_security_config.xml` sets `<base-config cleartextTrafficPermitted="true" />` (referenced from the manifest) — required because NAV is served over plain `http://` on the local LAN.

---

## 7. Auth & Config Storage

All under `data/auth/`.

### 7.1 `AppSettings` / `AppSettingsStore` — in-app user/device preferences
`AppSettingsStore` (`@Singleton`, plain unencrypted `SharedPreferences("app_settings")`): `get(): AppSettings`, `save(settings)`, `clear()`.

| Field | Type | Default |
|---|---|---|
| textSize | `TextSize` enum | NORMAL |
| uppercaseText | Boolean | false |
| language | `Language` enum | ENGLISH |
| lastScannedLines | Int | 5 |
| autoScan | Boolean | false |
| debounceTime | Int (ms) | 500 |
| hapticEnabled | Boolean | true |
| warnOnOver | Boolean | true |
| warnNotOnDocument | Boolean | true |
| autoUploadCompleted | Boolean | false |
| backgroundSync | Boolean | false |
| lastLocationCode | String | "" |
| lastRcCode | String | "" |
| disabledDocTypes | Set\<String\> (comma-joined `DocumentType.key` list) | emptySet |
| docTypeFilters | Map<String, `DocTypeFilterMode`> (serialized `"key:MODE,key:MODE"`) | emptyMap |
| debuggerActive | Boolean | false |

### 7.2 `ExtSystemConfig` / `ExtSystemConfigStore` — NAV connection config
```kotlin
data class ExtSystemConfig(
    val serverBaseUrl: String = "",
    val credentialTtlHours: Int = 24,
    val endpointUrls: Map<DocumentType, String> = emptyMap(),
    val documentTypeCodes: Map<DocumentType, String> = emptyMap(),
    val recordingSyncUrl: String = "",
    val locationsUrl: String = "",
) {
    fun endpointFor(type): String
    fun docTypeCodeFor(type): String
    val isConfigured get() = serverBaseUrl.isNotBlank()
}
data class ExtSystemCredentials(val username: String, val password: String)
```
`ExtSystemConfigStore` (`@Singleton`, plain `SharedPreferences("ext_system_config")`, non-secret): stores `serverBaseUrl`, `credentialTtlHours`, per-type `endpoint_<key>` / `doc_type_code_<key>`, `recordingSyncUrl`, `locationsUrl`.

### 7.3 `ExtSystemCredentialStore` — secure credential storage
`@Singleton`, backed by `androidx.security.crypto.EncryptedSharedPreferences` (file `"ext_system_credentials"`) with `MasterKey` using `AES256_GCM` (Android Keystore, hardware-backed on API 28+); pref key scheme `AES256_SIV`, value scheme `AES256_GCM`.

- `save(username, password, ttlHours)` — stores credentials + `expiry = now + ttlHours * 3_600_000L`.
- `get(): ExtSystemCredentials?` — **TTL enforced on every read**: if `now > expiry`, clears and returns `null`.
- `isValid(): Boolean = get() != null`.
- `clear()`.
- Domain is not stored separately — it travels inside the `username` string (`DOMAIN\user` or `user@domain`), parsed by `ExtSystemODataClient.parseDomainUser` at client-build time.

### 7.4 Login flow (end-to-end)
1. `LoginSheet` (`ui/screen/LoginSheet.kt`) — `ModalBottomSheet` with username/password fields + TTL hint. Reused by:
   - `ExtSystemConfigScreen` "Test connection" → `AppViewModel.testExtSystemConnection`, persists on success.
   - `LocationRcPickScreen` refresh-with-no-credentials → `onSaveCredentials` → `AppViewModel.saveCredentials` (no live test).
   - `DownloadFilterScreen` auto-opens if `!hasCredentials` before a download.
2. `AppViewModel.saveCredentials(username, password)` → `extSystemCredentialStore.save(username, password, config.credentialTtlHours)`, refreshes `_credentials` `StateFlow`.
3. `AppViewModel.signOut()` → `extSystemCredentialStore.clear()`, resets `_credentials` to null (`SettingsScreen` "Sign out").
4. `credentials: StateFlow<ExtSystemCredentials?>` is observed in `MainActivity` to derive the current `User` display name/initials (parsed from the username string — no live NAV profile lookup).

### 7.5 JSON config files

**`prima_config.json`** (repo root — blank template):
```json
{
  "serverBaseUrl": "",
  "credentialTtlHours": 24,
  "endpoints": { "WAREHOUSE_SHIPMENT": "", "WAREHOUSE_RECEIPT": "", "RETAIL_SHIPMENT": "", "RETAIL_RECEIPT": "", "TRANSPORT_SHEET": "" },
  "documentTypeCodes": { "WAREHOUSE_SHIPMENT": "", "WAREHOUSE_RECEIPT": "", "RETAIL_SHIPMENT": "", "RETAIL_RECEIPT": "", "TRANSPORT_SHEET": "" },
  "locationsUrl": "",
  "recordingSyncUrl": ""
}
```

**`app/src/main/assets/ext_system_defaults.json`** (bundled default config, points at a real dev/test NAV server):
```json
{
  "serverBaseUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/",
  "credentialTtlHours": 168,
  "endpoints": { "...": "all 5 → BarcodeAppEntries" },
  "documentTypeCodes": { "WAREHOUSE_SHIPMENT": "SHIPMENT", "WAREHOUSE_RECEIPT": "RECEIPT", "RETAIL_SHIPMENT": "RETAILSHPT", "RETAIL_RECEIPT": "RETAILRCPT", "TRANSPORT_SHEET": "TRANSPORT" },
  "locationsUrl": ".../LocationList",
  "recordingSyncUrl": ".../BEMFRecording"
}
```
Keys map 1:1 onto `ExtSystemConfig` fields (`endpoints` → `endpointUrls`, keyed by `DocumentType.name`).

---

## 8. Hilt DI

Only one DI module exists: `di/DatabaseModule.kt`.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    companion object {
        @Provides @Singleton
        fun provideDatabase(@ApplicationContext context: Context): PrimaDatabase = ...

        @Provides @Singleton
        fun provideLocationDao(db: PrimaDatabase): LocationDao = db.locationDao()
    }
}
```
- Installed in `SingletonComponent` (app-wide singletons).
- Other DAOs (`DocumentHeaderDao`, `DocumentLineDao`, `RecordingDao`) are **not** separately provided — `DocumentRepositoryImpl`/`AppViewModel` inject the whole `PrimaDatabase` and call `db.documentHeaderDao()` etc. directly.
- No dedicated network/auth module — `ExtSystemODataClient`, `AppSettingsStore`, `ExtSystemConfigStore`, `ExtSystemCredentialStore`, `DatabaseExporter` are all plain `@Singleton @Inject constructor(...)` classes (constructor injection only).
- `DataWedgeManager` is a plain Kotlin `object` (no DI).

**Entry points:** `PrimaBarcodeApplication` (`@HiltAndroidApp`, plants `Timber.DebugTree()`), `MainActivity` (`@AndroidEntryPoint`), `AppViewModel` + `RecordingViewModel` (`@HiltViewModel`, obtained via `hiltViewModel()`).

---

## 9. Barcode Scanning

Two independent scan-input paths feed the same domain logic (`RecordingViewModel.recordScan` / `DocumentRepository.recordScan`).

### 9.1 Camera path (ML Kit + CameraX)
- **`data/barcode/BarcodeAnalyzer.kt`** — `ImageAnalysis.Analyzer`. Uses `BarcodeScanning.getClient()` (default options, all formats), `InputImage.fromMediaImage(...)`. Picks the **largest bounding box** among detected barcodes to avoid incidental captures. Debounces repeated identical scans (emits only if value changed or `debounceMs` [default 1500ms] elapsed). Always `image.close()`s.
- **`ui/component/CameraPreview.kt`** — `CameraPreview(continuous, onBarcode, onClose, debounceMs=500, modifier)`. Binds `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, single-thread executor) to `CameraSelector.DEFAULT_BACK_CAMERA`. Additional UI-level debounce tied to `AppSettings.debounceTime`. Plays a `ToneGenerator` beep on scan; auto-closes unless `continuous=true`; animated scanning-line + corner-bracket overlay (`ScanningOverlay`); one-shot auto-focus at center via `FocusMeteringAction`. Requires `android.permission.CAMERA` (optional feature — device may rely solely on the hardware wedge).

### 9.2 Hardware wedge path (Zebra DataWedge)
- **`data/barcode/DataWedgeManager.kt`** — plain Kotlin `object`, talks to the DataWedge service via broadcast intents.
  - `configure(context)` — creates/updates a DataWedge profile `"PrimaBarcode"` scoped to this app's package, configures the `INTENT` output plugin to broadcast scans as `intent_action = "com.prima.barcode.SCAN"`, `intent_delivery = "2"`. Called once from `MainActivity.onCreate()`.
  - `setContinuousScan(context, enabled, sameSymbolTimeoutMs=500)` — toggles `BARCODE` plugin `aim_type` between single-shot (`"0"`) and continuous/press-and-hold (`"4"`), sets `same_symbol_timeout` (clamped 0–5000ms, tied to `AppSettings.debounceTime`) and fixed `different_symbol_timeout="100"`. Invoked reactively via `LaunchedEffect(autoScan, debounceTime)` in `PrimaBarcodeApp`.
  - `createReceiver(onScan)` — `BroadcastReceiver` extracting the scanned string from extra `com.symbol.datawedge.data_string`.
  - `intentFilter()` — `IntentFilter("com.prima.barcode.SCAN")`, registered by `RecordingScreen` via `DisposableEffect`.
  - Code comments note `aim_type` values are scanner/DataWedge-version dependent.

---

## 10. Screens (`ui/screen/`)

### MainMenuScreen.kt
Home screen. `data class DocTypeSummary(type, short, count, statusMini: List<LineStatus>, blocked: Boolean = false)`. `MainMenuScreen(user, location, rc, docTypes, shiftScans=0, shiftErrors=0, shiftReady=0, shiftPartial=0, shiftOver=0, onChangeLocationRc, onOpenSettings, onTypeTap, onDocumentOverview, onShowErrors={})`. Layout: `PrimaTopBar` → `DocumentStatsDashboard` (today's totals, tap to open dashboard) → RC/Location chip row → "DOCUMENTS" `LazyColumn` of `DocumentTypeList` rows (icon, label, mini `StatusProgressBar`, count badge or lock icon when `blocked`).

### DocumentListScreen.kt
`DocumentListScreen(docType, locationCode, docTypeCode="", documents, onBack, onDocTap, onDownload, onUpload, onErrorTap={}, onCreateDoc={_,_->}, canCreateDoc=true, onDeleteRecordings={}, onClearErrors={}, filter=DocumentFilter(), onOpenFilter={})`. One document type's documents in 3 tabs: **Orders** (Downloaded/InProgress/UploadFailed), **Recordings** (Completed/UploadFailed/InProgress-with-scans), **Errors** (UploadFailed). Client-side filters by location + `DocumentFilter`. Dark `ScanField` to scan/type a doc number (`handleDocScan` — offers "create document?" dialog if not found, gated by `canCreateDoc`). Bottom bar varies by tab (Download+Upload / Upload / Clear-errors+Upload). `DocRow` supports a 5s long-press delete gesture on the Recordings tab.

### DocumentOverviewScreen.kt (the "Dashboard")
`DocumentOverviewScreen(locationCode, rcCode, documents, onBack, onDocTap, onClearErrors, onUpload={}, onErrorTap={}, filter=DocumentFilter(), onOpenFilter={_,_->}, docTypeFilters=emptyMap(), initialTab=0)`. Cross-document-type view, 3 tabs: **Errors**, **My Location** (per-type `DocTypeFilterMode` match), **All**. Filter locks source/RC when opened from "My Location". Empty-state checkmark ("No issues").

### DocumentFilterScreen.kt
`DocumentFilterScreen(initialFilter=DocumentFilter(), lockedSourceCode=null, lockedRcCode=null, showDocTypeFilter=true, locations=emptyList(), rcs=emptyList(), onApply, onBack)`. Generic filter editor reused by both the doc-list filter and dashboard filter. Sections: Status (multi-select chips), Document Type (checkboxes, hidden if `showDocTypeFilter=false`), Document Date (from/to date pickers), Destination/Source code, Responsibility Center (dropdowns or locked fields). Reset / Apply footer.

### DownloadFilterScreen.kt
`DownloadFilterScreen(hasCredentials=false, docType=null, fixedSourceCode=null, fixedRcCode=null, locations=emptyList(), rcs=emptyList(), onConfirm: (DownloadFilter, username?, password?) -> Unit, onCancel)`. Pre-download filter form: date range, destination text field, Location/RC picker rows (bottom sheets) unless fixed. Auto-opens `LoginSheet` if `!hasCredentials`.

### ExtSystemConfigScreen.kt
`ExtSystemConfigScreen(initial: ExtSystemConfig, onSave, onDiscard={}, loadDefaults={null}, disabledDocTypes=emptySet(), onDisabledDocTypesChange={}, docTypeFilters=emptyMap(), onDocTypeFiltersChange={}, onTestConnection={...}, onImportJson=null)`. Admin screen for NAV connectivity: server URL, "Test connection" (opens `LoginSheet`), TTL segmented buttons (8h/24h/48h/7d), per-`DocumentType` endpoint cards (enable switch, URL field, read-only doc-type-code, filter-by LOCATION/RESPONSIBILITY_CENTER), Locations URL, Recording sync URL. "Load configuration" dialog offers built-in defaults or file import. `BackHandler` shows a "Save changes?" dialog if the form diverges from `initial`.

### LocationRcPickScreen.kt
`LocationRcPickScreen(currentRcCode, currentLocationCode, availableRcs, availableLocations, isRefreshing=false, lastSyncedAt=null, hasCredentials=false, credentialTtlHours=24, onSelect: (rcCode, locationCode)->Unit, onRefresh={}, onSaveCredentials={_,_->}, onBack)`. Two picker rows (RC, then Location filtered to that RC) opening bottom sheets; refresh icon triggers `onRefresh` directly if credentials exist, else opens `LoginSheet` first. Also defines reusable `RcPickerSheet`/`LocationPickerSheet` (searchable lists) used elsewhere.

### LoginSheet.kt
`LoginSheet(credentialTtlHours=24, onSubmit: (username, password)->Unit, onDismiss, ctaLabel="Sign in")`. `ModalBottomSheet`, username/password fields (visibility toggle), TTL info line, submit enabled only when both fields non-blank.

### RecordingScreen.kt — the core scanning workflow (most complex screen)
`RecordingScreen(doc: Document, docTypeCode="", onBack, onScan: (barcode, multiplier)->Unit, onLineUpdate: (lineNo, newScanned)->Unit, onExtraLineAdd: (barcodeNo, quantity)->Unit, onExtraLineUpdate={_,_->}, onExtraLineDelete={}, onUpload={}, lastScannedLines=5, autoScan=false, hapticEnabled=true, debounceTime=500, warnOnOver=true, warnNotOnDocument=true, autoUploadCompleted=false)`.

- Internal state machine, `RecordingView` enum: `OVERVIEW, ACTIVE_LINE, KEYPAD, UNKNOWN_BARCODE, EXTRA_LINE, EXTRA_KEYPAD`.
- `handleScan(barcode)` — looks up by barcode; if unmatched, in `autoScan` mode auto-adds as extra line (+ optional warning), else opens `UNKNOWN_BARCODE` numeric entry. If matched, calls `onScan`, appends to `tape` (`ScanTape`), shows an over-scan warning if `warnOnOver` and new status is `OVER`.
- Registers `DataWedgeManager` broadcast receiver via `DisposableEffect`.
- Bottom bar in OVERVIEW: `ScanTape` (last N scans) + `ScanBar` (scan input + camera button opening `CameraPreview`, permission-gated).
- `handleBack()` — per-view back navigation; from OVERVIEW, if `autoUploadCompleted` and doc is fully `EXACT`, shows "upload now?" instead of leaving.
- Sub-composables: `OverviewContent` (`BigNumberLineRow` list + extra-line "NOT ON DOCUMENT" section with `ExtraLineRow`), `ItemQtyDetails` (+1/-1 + tap-to-type + Apply), `ItemQtyExtraDetails` (numeric keypad, matched line), `ItemQtyNotOnDocDetails`/`ItemQtyNotOnDocExtraDetails` (same for extra/unrecognized lines, orange theme), `UnknownBarcodeContent` (keypad for a brand-new unmatched barcode), `StatusChip`, and an unused `UnknownBarcodeSheet` bottom-sheet variant.

### SettingsScreen.kt
`SettingsScreen(user, location, rc, textSize, onTextSizeChange, uppercaseText=false, onUppercaseTextChange={}, language=Language.ENGLISH, onLanguageChange={}, lastScannedLines=5, onLastScannedLinesChange={}, autoScan=false, onAutoScanChange={}, debounceTime=500, onDebounceTimeChange={}, hapticEnabled=true, onHapticEnabledChange={}, warnOnOver=true, onWarnOnOverChange={}, warnNotOnDocument=true, onWarnNotOnDocumentChange={}, autoUploadCompleted=false, onAutoUploadChange={}, backgroundSync=false, onBackgroundSyncChange={}, debuggerActive=false, onDebuggerActiveChange={}, onExport={}, onClearCache={}, onInsertTestData={}, onBack, onChangeLocation, onOpenExtSystemConfig={}, onSignOut)`.

Sections: **Appearance** (text size, uppercase toggle, language dropdown), **Scanning** (continuous-scan toggle, debounce segmented control [200/300/500/1000/2000ms], haptic toggle, warn-on-over toggle, warn-not-on-document toggle, expandable last-scanned-lines picker [0–5]), **Sync** (auto-upload toggle, background-sync toggle), **External System Configuration** (row → config screen), **Debug** (debugger-active toggle, Export data, Insert test data [confirm], Clear cache [confirm]), **System info** (app version via `BuildConfig`, Android OS version, min/target SDK via `apiToAndroid()` lookup, `DB_SCHEMA_VERSION = "7.0.0"` label), **Account** (avatar/initials + username, Sign out).

### UploadErrorScreen.kt
`UploadErrorScreen(document: Document, onBack, onRetryUpload)`. Read-only failed-upload detail: red header card, document-info card (`ErrorInfoRow`s), full error message card (`(document.state as DocState.UploadFailed).reason`), "Retry Upload" button.

---

## 11. Navigation & App Shell

### `MainActivity.kt`
`MainActivity : AppCompatActivity()` (`@AndroidEntryPoint`). `onCreate`: `enableEdgeToEdge()`, `hideNavBar()` (immersive kiosk-style nav bar hiding via `WindowInsetsControllerCompat`, re-applied on `onWindowFocusChanged`), `DataWedgeManager.configure(this)`, then `setContent { ... }`.

Inside `setContent`: obtains `AppViewModel` via `hiltViewModel()`, loads `initialSettings = appVm.loadSettings()` once, holds every setting as its own `remember { mutableStateOf(...) }`, with `buildSettings()` reassembling `AppSettings` and every `on...Change` callback persisting via `appVm.saveSettings(...)`. Wraps everything in `PrimaBarcodeTheme(textSizeOffset, uppercaseEnabled) { PrimaBarcodeApp(...) }`.

### `PrimaBarcodeApp` composable (private, in `MainActivity.kt`) — the NavHost/app shell
Takes ~26 params (all settings + change callbacks). Responsibilities: creates `nav = rememberNavController()`; derives `user` from `appVm.credentials`; collects `locations`/`responsibilityCenters`/`documents` state; auto-recovers stale RC/location selections; pushes continuous-scan config to `DataWedgeManager`; filters `documents` per document type via `DocTypeFilterMode`; builds `docTypes: List<DocTypeSummary>`; computes shift-wide counters (`shiftScans`, `errorDocs`, `readyDocs`, `partialDocs`, `overDocs`); manages UI-only state (`selectedDocType`, `docFilter`, `overviewFilter`, dialogs, `processingMessage` blocking-progress state, debug-URL confirmation flow via `launchWithDebug(urls, action)` gated by `debuggerActive`); `exportLauncher` (`CreateDocument("application/json")`) → `appVm.exportDatabase`.

**NavHost routes** (`startDestination = "main"`):

| Route | Screen | Notes |
|---|---|---|
| `main` | `MainMenuScreen` | `onTypeTap`→`docs`, `onDocumentOverview`→`dashboard?tab=1`, `onShowErrors`→`dashboard` |
| `location_rc_pick` | `LocationRcPickScreen` | wired to `isRefreshingLocations`, `lastLocationSyncAt`, `downloadLocations()`, `saveCredentials()` |
| `ext_system_config` | `ExtSystemConfigScreen` | save/discard/loadDefaults/testConnection/importJson delegate to `AppViewModel` |
| `settings` | `SettingsScreen` | all settings + export/clearCache/insertTestData/signOut |
| `docs` | `DocumentListScreen` | filtered by `selectedDocType` + location/RC per `DocTypeFilterMode`; upload branches on `backgroundSync` |
| `dashboard?tab={tab}` (Int, default 0) | `DocumentOverviewScreen` | `initialTab` from nav arg |
| `filter` | `DocumentFilterScreen` | edits `docFilter`, `showDocTypeFilter=false` |
| `overview_filter` | `DocumentFilterScreen` | edits `overviewFilter`, locked source/RC from dashboard drill-down |
| `download_filter` | `DownloadFilterScreen` | fixed source/RC per filter mode; URLs via `buildDownloadUrls`; downloads via `realDownloadDocuments` |
| `recording/{documentNo}/{type}` (String args) | `RecordingScreen` | route-scoped `RecordingViewModel` via `hiltViewModel()` |
| `upload_error/{documentNo}` (String arg) | `UploadErrorScreen` | retry delegates to `AppViewModel` |

**App-level overlay dialogs** (outside/after the NavHost): blocking "processing" `Dialog` (spinner + message), download-error `AlertDialog`, debug-URL confirmation `AlertDialog` ("Proceed"/"Cancel"), sync-error `AlertDialog` ("See errors" → `dashboard` / "Dismiss").

---

## 12. ViewModels

### `AppViewModel` (`@HiltViewModel`)
File: `ui/viewmodel/AppViewModel.kt`. Constructor deps: `Context`, `DocumentRepository`, `LocationDao`, `DatabaseExporter`, `ExtSystemConfigStore` (public `val`), `ExtSystemCredentialStore` (public `val`), `ExtSystemODataClient`, `AppSettingsStore`, private `Gson`. This is the orchestrator wiring together repository + NAV networking — there is no separate "SyncRepository".

**Exposed state:**
- `credentials: StateFlow<ExtSystemCredentials?>`
- `locations: StateFlow<List<Location>>`
- `responsibilityCenters: StateFlow<List<ResponsibilityCenter>>`
- `isRefreshingLocations: StateFlow<Boolean>`
- `lastLocationSyncAt: StateFlow<Instant?>`
- `documents: StateFlow<List<Document>>`
- `extSystemConfig: ExtSystemConfig` (computed property)

**Public functions:**
| Function | Description |
|---|---|
| `downloadLocations(onComplete={})` | sets refreshing flag, calls `realDownloadLocations()`, stamps `lastLocationSyncAt`, clears flag |
| `buildDownloadUrls(filter, docType=null): List<Pair<String,String>>` | display-name → final-URL pairs (with `$filter`) for debug preview / download |
| `getLocationsUrl(): String` | |
| `getRecordingSyncUrl(): String` | falls back to `<serverBaseUrl>/OData/WMS_RecordingSync` |
| `realDownloadDocuments(filter=DownloadFilter(), docType=null, onComplete: (failureCount, errors)->Unit={_,_->})` | see §6.5 |
| `loadSettings(): AppSettings` / `saveSettings(settings)` | thin wrappers over `appSettingsStore` |
| `saveExtSystemConfig(config)` | persists + updates in-memory state |
| `saveCredentials(username, password)` | persists with current TTL, refreshes `_credentials` |
| `signOut()` | clears credential store and `_credentials` |
| `testExtSystemConnection(serverBaseUrl, username, password, persistOnSuccess=true, onResult)` | see §6.5 |
| `parseExtSystemConfigJson(json): ExtSystemConfig?` | parses `ext_system_defaults.json`-shaped string; null on malformed input |
| `loadExtSystemDefaults(): ExtSystemConfig?` | reads bundled asset and parses it |
| `uploadToExtSystem(docs, onComplete: (failureCount)->Unit={})` | blocking/foreground upload |
| `uploadInBackground(docs)` | marks `PendingUpload` immediately, uploads async |
| `exportDatabase(uri, onComplete)` | delegates to `DatabaseExporter` |
| `clearCache()` | clears repository, app settings, ext-system config, credentials |
| `createDocument(doc)` | upserts a manually created document |
| `testDownload(onComplete={})` | clears all docs and reseeds sample data (demo helper) |
| `testImportDocs(docs, onComplete: (failureCount)->Unit={})` | simulated staggered "upload," failing every 3rd doc (demo/testing) |
| `insertTestData()` | calls `seedSampleData()` (does NOT clear first, unlike `testDownload`) |
| `clearDocumentRecordings(documentNo, type)` | deletes recorded scans for one document |
| `clearErrorDocs()` | resets all `UploadFailed` docs back to `Completed`/`InProgress`/`Downloaded` per scan state |

**Private helpers:** `realDownloadLocations()`, `buildODataFilterString()`, `appendODataFilter()`, `seedSampleLocations()` (reads bundled `assets/Data_RC_Location.csv` on first launch if locations table is empty — from `init {}`), `runUpload()` (shared by both upload paths), `runTestImport()`, `seedSampleData()` (~9 realistic demo documents across shipment/receipt types with varying states), nested `ExtSystemDefaultsDto`.

### `RecordingViewModel` (`@HiltViewModel`)
File: `ui/viewmodel/RecordingViewModel.kt`. Constructor deps: `DocumentRepository`, `SavedStateHandle` (reads `documentNo`/`type` nav args — route-scoped to `recording/{documentNo}/{type}`).

| Function | Description |
|---|---|
| `document: StateFlow<Document?>` | from `repository.observeDocument(documentNo, type)` |
| `recordScan(lineNo, barcodeNo, userId, quantity)` | records a scan against a line |
| `setLineScanned(lineNo, scanned)` | overwrites a line's scanned quantity (keypad "set to" flow) |
| `addExtraLine(barcodeNo, userId, quantity)` | adds an unrecognized-barcode line |
| `updateExtraLineQuantity(recordingLineNo, quantity)` | |
| `deleteExtraLine(recordingLineNo)` | |

---

## 13. Reusable Components (`ui/component/`)

| Component | Purpose / key params |
|---|---|
| `CameraPreview.kt` | `CameraPreview(continuous, onBarcode, onClose, debounceMs=500, modifier)` — see §9.1 |
| `Chip.kt` | `ChipTone` enum (DEFAULT/INK/CORAL/TEAL/SLATE/CREAM); `Chip(text, modifier, tone)` generic pill; `StatusChip(text, status: LineStatus, modifier)` — 4-state status pill (note: `RecordingScreen.kt` has its own private, distinct `StatusChip`) |
| `DocumentStatsDashboard.kt` | `DocumentStatsDashboard(totalScans, errors, readyForUpload=0, partial=0, over=0, onDocumentOverview, modifier)` — "TODAY" card on main menu: big scan count + stacked colored count/label rows (Ready green, Partial amber, Over blue, Error red), whole card clickable |
| `PrimaTopBar.kt` | `PrimaTopBar(title, subtitle=null, onBack=null, actions={}, modifier)` — standard 56dp slate app bar with optional back chip; title/subtitle auto-uppercased per preference; used on virtually every screen |
| `ScanBar.kt` | `ScanBar(onScan, onCameraTap, modifier, containerColor=PrimaPalette.Slate)` — docks a dark `ScanField` at the bottom of `RecordingScreen` |
| `ScanField.kt` | `ScanField(placeholder, onScan, onCameraTap, modifier, dark=false)` — hardware-scanner-friendly input: `AndroidView`-wrapped `EditText` with `showSoftInputOnFocus=false` to capture wedge keystroke bursts without popping the soft keyboard; auto-submits 200ms after burst idles or on Enter/Tab/Done; keyboard icon (manual soft keyboard) + camera icon |
| `ScanTape.kt` | `ScanTape(tape: List<TapeEntry>, maxLines=5, modifier)` — collapsible "LAST SCANS" strip; renders nothing if `maxLines==0`/empty; `TapeRow` shows barcode, item name (or "Not found"/"Unknown item"), quantity, timestamp, colored left stripe |
| `ScrollbarModifier.kt` | `Modifier.verticalScrollbar(ScrollState)` / `(LazyListState)` — custom thumb-only scrollbar (`0x55000000`, 4px wide, min 40px, rounded) via `drawWithContent`; used across nearly all scrollable lists instead of the platform scrollbar |
| `StatusComponents.kt` | `StatusProgressBar(segments: List<LineStatus>, modifier, height=6.dp, gap=3.dp)` — one colored rounded cell per line, used on doc rows/headers and doc-type list rows |

---

## 14. Theme / Design System

### `Color.kt` — `PrimaPalette` (brand palette)

| Token | Hex |
|---|---|
| Slate | `#2E3539` |
| SlateAlt | `#3A4146` |
| Cream | `#F2EBDE` |
| CreamAlt | `#E7DECF` |
| Coral | `#C95B4D` |
| CoralDeep | `#B04638` |
| Teal | `#2F5455` |
| Mustard | `#B89A3A` |
| Pink | `#E89AAA` |
| Oak | `#B49880` |
| Ink | `#1A1C1F` |
| Ink2 | `#3D4046` |
| Ink3 | `#6F7378` |
| Ink4 | `#A4A6AC` |

`PrimaStatus` — the 4-state semantic language:

| Token | Hex | Bg variant |
|---|---|---|
| Empty | `#CE3A3A` | `EmptyBg` = `#1ACE3A3A` (~10%) |
| Partial | `#C7943A` | `PartialBg` = `#1FC7943A` |
| Exact | `#2E8C5E` | `ExactBg` = `#1A2E8C5E` |
| Over | `#2D6CE0` | `OverBg` = `#1A2D6CE0` |

`PrimaLightColors` (Material3 `lightColorScheme`, **the one actually used**): primary=Coral/onPrimary=White, primaryContainer=CreamAlt/onPrimaryContainer=Ink, secondary=Slate/onSecondary=White, tertiary=Teal/onTertiary=White, background=Cream/onBackground=Ink, surface=White/onSurface=Ink, surfaceVariant=CreamAlt/onSurfaceVariant=Ink2, error=PrimaStatus.Empty/onError=White, outline=`#24000000`, outlineVariant=`#12000000`.

`PrimaDarkColors` — **defined but never used** anywhere (app always renders with the light scheme): primary=Coral, secondary=`#CBD0D6`/onSecondary=Slate, background=`#0F1113`, onBackground=`#E7E8EA`, surface=`#15181C`, onSurface=`#E7E8EA`, surfaceVariant=`#1F2429`, onSurfaceVariant=`#B6B8BC`, error=PrimaStatus.Empty, outline=`#40FFFFFF`, outlineVariant=`#14FFFFFF`.

### `Type.kt`
- `Geist = FontFamily.Default`, `GeistMono = FontFamily.Monospace` — the actual Geist TTFs are **not yet bundled** (commented-out `Font(R.font...)` with a TODO); currently falls back to system default/monospace.
- `LocalTextSizeOffset` (default 0), `LocalUppercaseEnabled` (default false), `String.uppercased` extension.
- `enum class TextSize(spOffset, label)`: `NORMAL(2, "Normal")`, `LARGER(4, "Larger")`.
- `BasePrimaTypography` (fixed) and `scaledTypography(offset)` (live, from `TextSize`):
  - `displayLarge`: Geist Medium, 40sp(+offset), lineHeight 44sp, letterSpacing -0.5sp
  - `titleLarge`: Geist Medium, 22sp, lineHeight 28sp, letterSpacing -0.2sp
  - `titleMedium`: Geist Medium, 18sp, lineHeight 24sp, letterSpacing -0.1sp
  - `titleSmall`: Geist Medium, 15sp, lineHeight 20sp
  - `bodyLarge`: Geist Normal, 16sp, lineHeight 24sp
  - `bodyMedium`: Geist Normal, 14sp, lineHeight 20sp
  - `bodySmall`: Geist Normal, 13sp, lineHeight 18sp
  - `labelSmall`: GeistMono Medium, 11sp, lineHeight 14sp, letterSpacing 1.1sp
- `monoCounter` (Composable val): GeistMono Medium, `17 + LocalTextSizeOffset.current` sp, letterSpacing 0.4sp — big digit displays (recording quantities).
- `monoLabel` (Composable val): GeistMono Normal, `12 + LocalTextSizeOffset.current` sp, letterSpacing 0.4sp — codes/timestamps/labels everywhere.

### `Shape.kt` — `PrimaShapes`
`extraSmall = 4dp`, `small = 8dp`, `medium = 12dp`, `large = 14dp`, `extraLarge = 22dp` (all `RoundedCornerShape`).

### `Theme.kt` / `PrimaTheme.kt`
Two near-duplicate theme entry points exist:
- **`PrimaTheme.kt` → `PrimaBarcodeTheme(textSizeOffset=0, uppercaseEnabled=false, content)`** — the one actually used by `MainActivity`; provides `LocalTextSizeOffset` + `LocalUppercaseEnabled`, then `MaterialTheme(colorScheme=PrimaLightColors, typography=scaledTypography(...), shapes=PrimaShapes)`.
- **`Theme.kt` → `PrimaTheme(textSizeOffset=0, content)`** — appears to be an older/unused duplicate (only provides `LocalTextSizeOffset`, no uppercase support); not referenced from `MainActivity`. Likely dead code.

### `Language.kt`
`enum class Language(val tag: String, val label: String)`: `ENGLISH("en","English")`, `CROATIAN("hr","Croatian")`, `SLOVENIAN("sl","Slovenian")`, `MACEDONIAN("mk","Macedonian")`. Drives `AppCompatDelegate.setApplicationLocales`.

---

## 15. String Resources

`res/values/strings.xml` and `res/values-hr/strings.xml` (Croatian localization) — 238 lines / same key set. Categories present, in file order:

- Common buttons (OK/Cancel/Save/Apply/Reset/Download/Upload/Create/Clear/Retry Upload/Sign in/Sign in & Sync/Yes-insert)
- Dashboard (scan count label, ready/partial/error pill labels)
- Main menu (documents header, "no location")
- Document types (display names + descriptions for all 5 `DocumentType`s)
- Doc list screen (tab labels, empty state, scan placeholder, create-document dialog, status chips, doc state labels, qty-scanned format)
- Document overview/dashboard (title/subtitle, tab labels, no-issues state, clear-errors dialogs)
- Upload error screen (header/section/row labels, line count plurals)
- Recording screen (title, "not on document" labels, UoM/barcode prefixes, status chips, keypad preview phrases)
- Multiplier (strings exist but no active multiplier UI was found — likely legacy)
- Settings (all section headers/rows, clear-cache and insert-test-data confirmation dialogs)
- Login sheet (title, domain/username/password labels, show/hide password, AES-256-GCM footer note, TTL format strings)
- Download filter (error title, shared filter labels reused by `DocumentFilterScreen`)
- Document filter (title/subtitle, status/type section labels)
- Location & RC / LRC (title, subtitle, synced-at format, section headers, placeholders, "no locations for this RC", search)
- External System Configuration (all section headers, server/company/domain labels, session-duration description, load-config dialog strings, import success/parse-error/read-error messages)
- Document recordings (delete-recordings confirmation)
- Accessibility (content descriptions for refresh/settings/filter/clear icons)

**Note:** several keys (`multiplier_*`, `settings_mute*`, `settings_auto_collapse*`, `settings_wifi_only*`, `settings_test_signin*`, `login_domain`) exist but don't correspond to any UI observed in the current screens — likely leftovers from earlier iterations.

---

## 16. Build & Project Config

### `app/build.gradle.kts`
- Plugins: `com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android` (via version catalog aliases).
- `namespace` / `applicationId`: `com.prima.barcode`
- `compileSdk`: 36 (minor API level 1, new `compileSdk { version = release(36) { minorApiLevel = 1 } }` DSL)
- `minSdk`: 26, `targetSdk`: 36
- `versionCode`: 1, `versionName`: `"1.0.0"`
- `testInstrumentationRunner`: `androidx.test.runner.AndroidJUnitRunner`
- Build types: only `release` customized (`isMinifyEnabled = false`, standard ProGuard files); no `debug` overrides, no product flavors.
- Java/Kotlin compatibility: `VERSION_11`.
- `buildFeatures`: `compose = true`, `buildConfig = true`.
- KSP arg: `room.schemaLocation = "$projectDir/schemas"`.
- Key dependencies (hardcoded versions, not in the catalog):
  - Compose: BOM, `material-icons-extended`, `lifecycle-viewmodel-compose:2.10.0`, `lifecycle-runtime-compose:2.10.0`, `navigation-compose:2.8.2`
  - Room: `room-runtime/room-ktx:2.7.0`, `room-compiler:2.7.0` (ksp)
  - Ktor `2.3.12`: `ktor-client-okhttp`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-client-auth` — **the latter three appear unused**; Gson + custom NTLM authenticator are used instead
  - Barcode/Camera: `com.google.mlkit:barcode-scanning:17.3.0` (comment: "has unaligned .so; update when a newer release is available"), `androidx.camera:camera-camera2/camera-lifecycle/camera-view:1.4.2`
  - Security: `androidx.security:security-crypto:1.1.0-alpha06`
  - DI: `hilt-android`/`hilt-compiler` (catalog, 2.59), `androidx.hilt:hilt-navigation-compose:1.2.0`
  - `com.google.code.gson:gson:2.11.0`
  - `androidx.appcompat:appcompat:1.7.0`
  - `com.jakewharton.timber:timber:5.0.1`
  - Test: `junit`, `androidx.junit`, `espresso-core`, Compose UI test (`junit4`, `manifest`/`tooling` debug)

### Root `build.gradle.kts`
Declares plugin aliases with `apply false`: `android.application`, `kotlin.compose`, `ksp`, `hilt.android`.

### `settings.gradle.kts`
Repositories: `google()` (scoped content filters for `com.android.*`/`com.google.*`/`androidx.*`), `mavenCentral()`, `gradlePluginPortal()` in `pluginManagement`; `google()` + `mavenCentral()` in `dependencyResolutionManagement` (`FAIL_ON_PROJECT_REPOS`). Plugin: `org.gradle.toolchains.foojay-resolver-convention:1.0.0`. `rootProject.name = "PrimaBarcode"`, single module `:app`. No product flavors.

### `gradle/libs.versions.toml`
```toml
[versions]
agp = "9.2.1"
hilt = "2.59"
coreKtx = "1.18.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.10.0"
activityCompose = "1.13.0"
kotlin = "2.2.10"
ksp = "2.2.10-2.0.2"
composeBom = "2026.02.01"
```
Only Compose/AndroidX/test/Hilt libraries and the 4 plugins are catalog aliases; Room/Ktor/CameraX/ML Kit/Gson/Timber/security-crypto/appcompat are plain string coordinates directly in `app/build.gradle.kts`.

### `AndroidManifest.xml`
- Permissions: `INTERNET`, `CAMERA`, `VIBRATE`.
- Features: `<uses-feature android:name="android.hardware.camera" android:required="false" />` (camera optional — hardware wedge scanner can be sole input).
- `<application>`: `android:name=".PrimaBarcodeApplication"`, `allowBackup=true`, `dataExtractionRules`/`fullBackupContent` XML, `icon`/`roundIcon` = `@mipmap/logo[_round]`, `networkSecurityConfig="@xml/network_security_config"` (cleartext HTTP allowed), `supportsRtl=true`, `localeConfig="@xml/locales_config"`, theme `@style/Theme.PrimaBarcode`.
- Components: only `MainActivity` (`exported=true`, `MAIN`/`LAUNCHER` intent filter). No services/providers; the DataWedge `BroadcastReceiver` is registered dynamically in code, not in the manifest.

---

## 17. Known Gaps / Dead-Code Notes

Observations surfaced during the source survey — flagged, not confirmed by the original authors:

- **Ktor `content-negotiation`/`kotlinx-json`/`client-auth` dependencies appear unused.** All JSON handling goes through Gson, and auth is a hand-rolled OkHttp `Authenticator`, not a Ktor auth plugin.
- **`PrimaDarkColors` and `Theme.kt` (`PrimaTheme` composable) appear to be dead code.** The app always renders with `PrimaLightColors` via `PrimaBarcodeTheme` in `PrimaTheme.kt`; `Theme.kt` is a separate, seemingly-superseded entry point not referenced anywhere.
- **Leftover string resources with no matching UI**: `multiplier_*`, `settings_mute*`, `settings_auto_collapse*`, `settings_wifi_only*`, `settings_test_signin*`, `login_domain` — likely remnants of earlier iterations of the multiplier/login/settings flows.
- **The app is NTLM-only**, despite CLAUDE.md's stack table listing "Basic + NTLM auth" — no Basic-auth code path exists.
- **`DatabaseExporter`** is a debug/support-only tool (full raw JSON dump of all tables via `SettingsScreen`'s "Export data" row) — not part of normal app data flow.
- **No dedicated "sync"/"upload" repository** — NAV download/upload orchestration lives directly in `AppViewModel`, mixing business logic with ViewModel responsibilities. Worth extracting if this logic grows further.
- **A Document List screen** (per CLAUDE.md's older description as "planned but not yet implemented") is now fully implemented as `DocumentListScreen.kt` — CLAUDE.md is stale on this point.
