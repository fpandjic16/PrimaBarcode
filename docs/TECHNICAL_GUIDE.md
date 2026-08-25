# PrimaBarcode — Technical & Consultant Guide

**Audience:** functional consultants configuring/supporting the app, and developers maintaining or extending it.

This document supersedes the older `docs/ARCHITECTURE.md` (now a stub pointing here). It was generated from a full, current source-tree survey and is organized in two parts:

- **Part A — Consultant / Functional Reference**: business rules, document lifecycle, NAV/Business Central integration setup, configuration reference, common support scenarios. No code-reading required.
- **Part B — Developer / Technical Reference**: architecture, database schema, repository/DAO/ViewModel APIs, networking internals, screen-by-screen implementation notes, extension points.

For the end-user manual (how warehouse staff use the app day to day), see `docs/USER_GUIDE.md`.

---

# Part A — Consultant / Functional Reference

## A.1 What the App Does, End to End

PrimaBarcode is a native Android app that lets warehouse/store staff scan barcodes against documents (shipments, receipts, transfers) sourced from a Dynamics NAV 2018 / Business Central–family ERP system, and sync results back. There is no offline "queue and forget" — every download and upload is a direct, synchronous OData call to the ERP over the local network (or VPN), authenticated per-user with the user's own Windows credentials via NTLM.

The functional loop:

1. **Reference data sync** — pull Locations + derive Responsibility Centers from the ERP.
2. **Download** — pull document lines for one or more document types from a single shared ERP table ("Barcode App Entry"), filtered by document type code + date range + destination/source/RC.
3. **Scan** — record barcode scans locally against document lines. A scan that doesn't match any line's barcode is rejected outright with a "Barcode not found" error — it is never recorded.
4. **Upload** — push each individual recorded scan as its own row to a second shared ERP table ("Barcode App Recordings"); on success the local document is fully removed; on failure it's flagged with the exact server error for retry.

## A.2 The Document Lifecycle (functional view)

Every document is always in exactly one of these states, computed automatically from its recordings (except where noted):

| State | Meaning | How it's reached |
|---|---|---|
| **Downloaded** | Fresh from the ERP, zero scans | Initial state after download/merge if no recordings exist for the doc |
| **In Progress** | Some scanning has happened, not yet complete | Automatically entered the moment any scan/edit occurs on a Downloaded or Upload-Failed document; also the state a Completed document regresses to if a later scan/edit breaks its "everything exact" condition |
| **Completed** | Every line is exactly at its expected quantity | Computed on download/merge only — a document is *never* pushed into Completed by an in-app scan action directly; scanning to exact match keeps it "recomputed as Completed" on the next merge, but functionally the app treats "every line exact" as effectively complete in the UI regardless |
| **Pending Upload** | Upload in progress (background-sync mode only) | Set immediately when a background upload starts, before the network call resolves |
| **Upload Failed: \<reason\>** | The last upload attempt failed | Set when any row's POST to the ERP fails; the reason is the literal server/config error text |

**Key business rule: recordings are never silently deleted.** A re-download can refresh a document's header/lines from the ERP, but it will never discard a user's recorded scans, even if the ERP no longer lists that document, or the document's lines have changed. `DocumentRepositoryImpl.mergeDocument()` always refreshes header/lines while re-summing docState from whatever recordings already exist locally (see §B.4.1).

## A.3 Unmatched Scans

A scanned barcode that doesn't match any line's `Barcode` field on the document is **not recorded**. The app shows a hard "Barcode not found" error (red flash + error haptic) and the user must re-scan the correct item or check the document. There is no offline/placeholder-document scanning and no automatic reconciliation step — every scan either lands on a real line at scan time or is rejected outright; there's nothing left over to resolve later.

## A.4 The Four-State Line Status Language

| Status | Rule | Color | Where it's used |
|---|---|---|---|
| Empty | scanned == 0 | Red `#CE3A3A` | Line & document status everywhere |
| Partial | 0 < scanned < expected | Amber `#C7943A` | " |
| Exact ("Ready" in UI) | scanned == expected | Green `#2E8C5E` | " |
| Over ("Over-qty" in UI) | scanned > expected | Blue `#2D6CE0` | " |

**Document-level aggregate** (`Document.scanStatus()`): Empty if the document has zero lines or every line is Empty; **Over wins over everything** — a single Over line makes the whole document show Over regardless of other lines; Exact only if every line is Exact; otherwise Partial.

## A.5 Configuring the NAV / Business Central Connection

All of this lives under **Settings → External System Configuration** (`ExtSystemConfigScreen`), normally touched only during setup or by support.

### A.5.1 Fields to configure

| Field | Purpose | Example |
|---|---|---|
| Server base URL | Root used only for the "Test connection" NTLM probe | `http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/` |
| Domain | Windows/NTLM domain, merged with the typed username at login so users only ever type a bare username (added 2026-08) — leave blank to fall back to a domain embedded in the username itself (`DOMAIN\user`/`user@domain`) | `PRIMA` |
| Session duration | How long a signed-in session's credentials stay valid before requiring re-entry (8h / 24h / 48h / 7 days) | 168h |
| Document lines URL | The **"Barcode App Entry"** OData endpoint — shared by all 5 document types | `.../Company('Prima Commerce d.o.o.')/BarcodeAppEntries` |
| Per-document-type: enabled switch | Whether that document type is offered in the app at all | — |
| Per-document-type: Document Type Code | The exact `Document_Type` filter value the ERP expects for that type (read-only in this screen; comes from imported/loaded config, not typed here) | `SHIPMENT`, `RECEIPT`, `RETAILSHPT`, `RETAILRCPT`, `TRANSPORT` |
| Per-document-type: Filter by | Whether that type's "download" and "My Location" filtering scope by **Location** (source code) or by **Responsibility Center** | `LOCATION` (default) |
| Locations URL | Reference-data endpoint for Locations (RCs are derived client-side from distinct location RC codes, not fetched from a separate RC endpoint) | `.../LocationList` |
| Recording sync URL | The **"Barcode App Recordings"** OData endpoint — one POST per recorded scan | `.../BarcodeAppRecordings` |

### A.5.2 Loading configuration

Three interchangeable ways to populate the above (available both in `ExtSystemConfigScreen`'s "Load configuration" button and Settings' "Insert system defaults" row — functionally identical, differing only in when they're persisted, see §B.11.6):

1. **Load built-in defaults** — presents a company picker (built from every bundled `assets/ext_system_defaults_*.json`'s `companyName`, see §B.6.6), reads the selected one, and fills the form immediately.
2. **Download built-in defaults** — same company picker, but writes the selected file's raw JSON to a file the user picks (for editing/distribution as a starting template).
3. **Import from file** — reads an arbitrary JSON file the user picks (same shape) and fills the form from it.

None of these three actions saves anything by themselves — the screen's own **Save** (or Settings' exit-save flow) is still required afterward.

### A.5.3 The "Barcode App Entry" table (download)

One flat OData row per document **line** (header fields repeated on every row of the same document). All 5 document types share this single table/endpoint, discriminated purely by an OData filter on `Document_Type`.

| NAV field | Meaning |
|---|---|
| `Document_Type` | Which of the 5 document types this row belongs to (filtered by, not stored per-row after grouping) |
| `Document_No` | Document number — the grouping key |
| `Line_No` | Line number (rows with `Line_No <= 0` are treated as the header row and excluded from lines) |
| `Source_No` | Source location code |
| `Retail_Location` | Boolean — whether the source is a retail location |
| `Destination_No` | Destination code |
| `Document_Date` | Document date |
| `Responsibility_Center` | RC code |
| `Item_No` | Item number |
| `Item_Description` | Item name |
| `Item_Qty` | Expected quantity |
| `Scanning_Qty` | Quantity added per single scan of this line's barcode (defaults to 1.0) |
| `Item_UoM` | Expected unit of measure |
| `Barcode` | The barcode string to match scans against |

### A.5.4 The "Barcode App Recordings" table (upload)

One flat OData row **per individual scanned recording** — not one row per document, not batched. `Document_Line_No` is always a real line number the app knows about — the app no longer has any concept of an unmatched/"extra" recording (see §A.3), so it never sends `0` here.

| NAV field | Meaning |
|---|---|
| `Document_Type` | Document type code |
| `Document_No` | Document number |
| `Document_Line_No` | Which line this recording applies to |
| `Recording_Line_No` | Sequence number within that line's recordings |
| `Recording_Guid` | A fresh random GUID generated for **every upload attempt** (not stored locally) — exists purely so a retry after a lost success response can never collide with a previous attempt on the ERP-side unique key |
| `Barcode` | The scanned barcode string |
| `Scanned_Quantity` | Quantity for this recording |
| `Unit_Of_Measure_Code` | UoM for this recording |
| `Source_Creation_DateTime` | When the scan happened (ISO-8601) |
| `Source_User_ID` | Who scanned it (blank if nobody was signed in at scan time) |
| `Source_Code` | Source location code |
| `Destination_Code` | Destination code |

**Upload semantics to know for support**: rows are sent **sequentially, one POST per row**. Each row that succeeds is deleted from the device *immediately*, before the next row is sent. If a row fails, the loop stops for that document — rows already sent are already gone (success), remaining rows stay queued locally for the next retry. This means a "partial failure" is completely safe and expected: retrying only resends what genuinely didn't make it.

## A.6 Business Settings Reference (functional)

These map 1:1 to the User Guide's Settings section but framed for support/consultants:

| Setting | Functional effect | Default |
|---|---|---|
| `warnOnOver` | Whether an over-scan pops a confirmation | On |
| `backgroundSync` | Uploads fire-and-forget in the background vs. block with a progress screen | Off |
| `debuggerActive` | Shows exact outbound URLs before every network call — diagnostic aid for support, not for daily use | Off |
| Per-document-type **Filter by** (Location vs Responsibility Center) | Governs both download-filter defaults and "My Location" dashboard matching for that type | Location |

## A.7 Common Support Scenarios

| Symptom | Likely cause | Where to look |
|---|---|---|
| Document type shows locked on Main Menu | No location selected, or the type has no valid endpoint/code configured | `ExtSystemConfigScreen` — confirm the type is enabled and has a Document Type Code |
| Upload fails with an ERP validation message | Business-system-side issue (e.g. a malformed field, a locked document) | `UploadErrorScreen`'s full error text — read verbatim, it's the ERP's own response body (truncated to 300 characters) |
| Upload fails with "Not signed in" / "not configured" | Session expired, or a URL/field is blank in External System Configuration | Re-authenticate; verify config fields |
| "NTLM not enabled" / "credentials rejected" on Test Connection | Distinguishes ERP-side NTLM misconfiguration (phase 0 — no challenge issued at all) from genuinely wrong credentials (phase 2 + 401) | `NtlmAuthenticator.phaseReached`, surfaced in the Test Connection result dialog's message |
| A document with real progress "disappeared" from Orders | It has moved to Recordings (Completed) or Errors (Upload Failed) — never silently deleted by download | Check Recordings/Errors tabs |
| Barcode with `|` scans as garbage | Printed with Code 39 symbology, which cannot encode `|` | Reprint the label as Code 128 |
| A scan is rejected as "Barcode not found" even though the item is on the document | The scanned value doesn't byte-for-byte match that line's `Barcode` field (wrong symbology, stray whitespace, wrong `\|UOM\|QTY` suffix) | Compare the raw scanned value against the line's `Barcode` field; reprint the label if needed |

---

# Part B — Developer / Technical Reference

## B.1 Stack & Source Layout

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| Networking | Ktor Client (OkHttp engine) + a hand-rolled NTLMv2 `okhttp3.Authenticator` — Ktor's own auth plugin and ContentNegotiation are **not** used; JSON is hand-built/parsed with Gson |
| Local DB | Room (KSP-generated DAOs) |
| DI | Hilt/Dagger |
| Barcode | ML Kit (camera path) + Zebra DataWedge (hardware wedge, Intent-based) |
| Camera | CameraX |
| Secure storage | `androidx.security:security-crypto` (`EncryptedSharedPreferences`, AES-256-GCM, Android Keystore-backed) for NAV credentials only |
| Logging | Timber |

```
app/src/main/java/com/prima/barcode/
├── MainActivity.kt               # NavHost, app shell, all settings state hoisting
├── data/
│   ├── auth/                     # AppSettings(Store), ExtSystemConfig(Store), credential store
│   ├── barcode/                  # BarcodeAnalyzer (ML Kit), DataWedgeManager (Zebra wedge)
│   ├── db/                       # Room entities, DAOs, PrimaDatabase, Mappers
│   ├── export/                   # DatabaseExporter (raw DB → JSON dump)
│   ├── extsystem/                # ExtSystemODataClient, NtlmAuthenticator, DTOs
│   ├── model/                    # Domain models (Models.kt, Filter.kt, Status.kt)
│   └── repository/               # DocumentRepository (the only repository in the app)
├── di/DatabaseModule.kt          # the only Hilt module
└── ui/
    ├── theme/                    # Color.kt, Type.kt, Shape.kt, Theme.kt, Language.kt
    ├── component/                # Reusable composables
    ├── screen/                   # Full screens
    └── viewmodel/                # AppViewModel, RecordingViewModel
```

There is deliberately **no separate "SyncRepository"** — NAV networking (download/upload/test-connection) is orchestrated directly in `AppViewModel`, which calls `ExtSystemODataClient` and `DocumentRepository`/DAOs itself.

## B.2 Database Schema (Room)

`PrimaDatabase` — file `prima_barcode.db`, **schema version 16**. Built with `.addMigrations(MIGRATION_10_11 … MIGRATION_15_16).fallbackToDestructiveMigration(dropAllTables = true)` — any DB older than v10, or any version jump not covered by an explicit migration, is destructively recreated.

### `documentHeader` (`DocumentHeaderEntity`)
PK: `(documentNo, type)`.

| Column | Type | Notes |
|---|---|---|
| documentNo | TEXT NOT NULL | |
| type | TEXT NOT NULL | serialized `DocumentType.key` |
| destinationCode | TEXT NOT NULL | |
| sourceCode | TEXT NOT NULL | |
| rcCode | TEXT NOT NULL | |
| isSourceRetail | INTEGER NOT NULL | boolean, added v13→v14, default 0 |
| creationDateTime | INTEGER NOT NULL | epoch millis |
| documentDate | INTEGER NULL | epoch millis |
| docState | TEXT NOT NULL | serialized `DocState` |

### `documentLine` (`DocumentLineEntity`)
PK: `(documentNo, type, lineNo)`. FK `(documentNo, type) → documentHeader`, `ON DELETE CASCADE`. Indices: `(documentNo, type)`, `(barcodeNo)`.

| Column | Type | Notes |
|---|---|---|
| documentNo, type, lineNo | — | PK |
| itemNo, itemName | TEXT NOT NULL | |
| barcodeNo | TEXT NOT NULL | |
| expected | REAL NOT NULL | |
| destinationCode, sourceCode, unitOfMeasureCode | TEXT NOT NULL | |
| scanningQty | REAL NOT NULL DEFAULT 1.0 | added v10→v11 |

### `recordings` (`RecordingEntity`)
PK: `(documentNo, type, documentLine, recordingLineNo)`. FK `(documentNo, type) → documentHeader`, `ON DELETE CASCADE`. Indices: `(documentNo, type)`, `(documentLine)`. `documentLine` always references a real `DocumentLineEntity.lineNo` — every recording is tied to a line.

| Column | Type | Notes |
|---|---|---|
| documentNo, type, documentLine, recordingLineNo | — | PK |
| barcodeNo | TEXT NOT NULL | |
| quantity | REAL NOT NULL | |
| creationDateTime | TEXT NOT NULL | ISO-8601 string (changed from epoch-millis Long in v11→v12) |
| userId | TEXT NOT NULL | |
| destinationCode, sourceCode, unitOfMeasureCode | TEXT NOT NULL | |
| rcCode | TEXT NOT NULL DEFAULT '' | added v14→v15, backfilled from parent header |

Note: a nullable `format` TEXT column existed between v12 and v16 (barcode symbology, never actually populated by any code path) and was **dropped in v15→v16** along with the corresponding `DocumentRepository.getUploadableDocs`/upload-DTO field, after confirming zero write sites during a 2026-08 codebase audit.

### `locations` (`LocationEntity`)
PK `code`. Columns: `name`, `rcCode` — all `TEXT NOT NULL`.

### `responsibility_centers` (`ResponsibilityCenterEntity`)
PK `code`. Columns: `name TEXT NOT NULL`, `short TEXT NULL`.

### `DocumentHeaderWithLines`
Not a Room entity — a plain data class `{ document: DocumentHeaderEntity, lines: List<DocumentLineEntity>, recordings: List<RecordingEntity> }` manually assembled by the repository (avoids N+1 queries; see `assembleDocuments`).

### Migrations — full list and rationale

| Migration | Change | Why the "recreate" pattern (if used) |
|---|---|---|
| `MIGRATION_10_11` | `ALTER TABLE documentLine ADD COLUMN scanningQty REAL NOT NULL DEFAULT 1.0` | Plain additive column — no rebuild needed |
| `MIGRATION_11_12` | Rebuilds `recordings`: converts `creationDateTime` INTEGER (epoch millis) → TEXT (ISO-8601, via SQLite `strftime`); adds `format` column | SQLite `ALTER TABLE` cannot change a column's type — requires create-new/copy/drop/rename + recreate indices |
| `MIGRATION_12_13` | Rebuilds `documentHeader`, dropping an obsolete pre-v13 column | SQLite `ALTER TABLE` (at the time) could not `DROP COLUMN` — same recreate pattern |
| `MIGRATION_13_14` | `ALTER TABLE documentHeader ADD COLUMN isSourceRetail INTEGER NOT NULL DEFAULT 0` | Additive — no rebuild |
| `MIGRATION_14_15` | `ALTER TABLE recordings ADD COLUMN rcCode TEXT NOT NULL DEFAULT ''`, then `UPDATE recordings SET rcCode = (correlated subquery against documentHeader.rcCode)` | Additive column + backfill — no rebuild |
| `MIGRATION_15_16` | Rebuilds `recordings`, dropping the now-unused `format` column | Column drop — recreate pattern again |

**General rule**: SQLite's native `ALTER TABLE` only supports `ADD COLUMN`, `RENAME COLUMN`, `RENAME TABLE`. Any migration that needs to change a column's type or drop a column must: `CREATE TABLE x_new (...)` with the desired final shape → `INSERT INTO x_new SELECT ... FROM x` (explicit column list, applying any needed transform) → `DROP TABLE x` → `ALTER TABLE x_new RENAME TO x` → manually recreate indices (they don't survive `DROP TABLE`).

## B.3 DAOs (full function reference)

### `DocumentHeaderDao`
- `observeHeader(documentNo, type): Flow<DocumentHeaderEntity?>`
- `observeAllHeaders(): Flow<List<DocumentHeaderEntity>>`
- `getByKey(documentNo, type): DocumentHeaderEntity?` (suspend)
- `getAll(): List<DocumentHeaderEntity>` (suspend)
- `upsert(doc)` (suspend, `@Upsert`)
- `updateState(documentNo, type, state: String)` (suspend)
- `deleteByKey(documentNo, type)` (suspend) — cascades to lines/recordings
- `deleteAll()` (suspend)

### `DocumentLineDao`
- `getByKey(documentNo, type, lineNo): DocumentLineEntity?` (suspend)
- `getByDoc(documentNo, type): List<DocumentLineEntity>` (suspend)
- `observeByDoc(documentNo, type): Flow<List<DocumentLineEntity>>`
- `observeAll(): Flow<List<DocumentLineEntity>>`
- `getAll(): List<DocumentLineEntity>` (suspend)
- `upsertAll(lines)` (suspend, `@Upsert`)
- `deleteAllForDoc(documentNo, type)` (suspend)

### `RecordingDao`
- `observeByDoc(documentNo, type): Flow<List<RecordingEntity>>` — `ORDER BY documentLine, recordingLineNo`
- `observeAll(): Flow<List<RecordingEntity>>`
- `getNextRecordingLineNo(documentNo, type, documentLine): Int` (suspend) — `COALESCE(MAX(recordingLineNo),0)+1`, scoped per `(documentNo, type, documentLine)` triple (each line has its own independent sequence)
- `getByDoc(documentNo, type): List<RecordingEntity>` (suspend)
- `insert(recording)` (suspend, `@Insert` — plain insert, fails on PK conflict by design; recordings are append-only)
- `deleteByPk(documentNo, type, documentLine, recordingLineNo)` (suspend)
- `deleteAllForLine(documentNo, type, lineNo)` (suspend)
- `deleteAllForDoc(documentNo, type)` (suspend)
- `getAll(): List<RecordingEntity>` (suspend) — used by `DatabaseExporter`

### `LocationDao`
- `observeLocations(): Flow<List<LocationEntity>>` — `ORDER BY name ASC`
- `observeRcs(): Flow<List<ResponsibilityCenterEntity>>` — `ORDER BY name ASC`
- `upsertLocations(locations)` / `upsertRcs(rcs)` (suspend, `@Upsert`)
- `clearLocations()` / `clearRcs()` (suspend)

## B.4 `DocumentRepository` — Full Function Reference

`@Singleton class DocumentRepositoryImpl @Inject constructor(private val db: PrimaDatabase)` — talks directly to `db.documentHeaderDao()` / `db.documentLineDao()` / `db.recordingDao()`; no field-level DAO injection.

| Function | Behavior |
|---|---|
| `observeAll(): Flow<List<Document>>` | Combines all headers + lines + recordings, assembles domain `Document`s via `assembleDocuments` (in-memory groupBy, avoids N+1 queries) |
| `observeDocument(documentNo, type): Flow<Document?>` | Same, scoped to one document |
| `replaceDownloadedDocuments(type, docs)` (suspend, transactional) | Deletes headers of `type` not present in `docs` **only if they have zero recordings**; calls `mergeDocument` for every doc in `docs` |
| `mergeDocument(doc, type)` (private, suspend) | Refreshes header + fully replaces lines from the download, recomputes docState from whatever recordings already exist locally — see §B.4.1 for the exact code path |
| `computeStateAfterMerge(lines, recordings)` (private) | `Downloaded` if no recordings; else `Completed` if every line's summed recordings exactly equal `expected`; else `InProgress`. Never returns `PendingUpload`/`UploadFailed` — those are only set via `updateDocState` from outside, and any subsequent merge will silently overwrite them |
| `recordScan(documentNo, type, lineNo, barcodeNo, userId, quantity)` (suspend, transactional) | Looks up line+header, generates next `recordingLineNo`, **inserts** a new recording (always additive, never updates existing rows), calls `advanceToInProgressIfNeeded` + `regressFromCompletedIfNeeded` |
| `setLineScanned(documentNo, type, lineNo, scanned, userId)` (suspend, transactional) | **Deletes all recordings for that line**, then inserts one fresh recording with the exact new total if `scanned > 0`. Runs all three state helpers (including `regressToDownloadedIfNeeded`, since zeroing a line could empty the doc entirely). This is the one path that legitimately deletes real-line recording rows — it's a deliberate user-driven overwrite |
| `updateDocState(documentNo, type, state)` (suspend) | Direct state write — used by the upload flow for `PendingUpload`/`UploadFailed` |
| `deleteDocument(documentNo, type)` (suspend) | Deletes header, cascades lines+recordings — used on upload success |
| `getRecordings(documentNo, type): List<RecordingEntity>` (suspend) | Pass-through, used by the upload flow to enumerate rows to send |
| `deleteRecording(documentNo, type, documentLine, recordingLineNo)` (suspend) | Raw single-row delete, no state recomputation — used by the upload flow to delete a row the instant its POST succeeds |
| `clearAll()` (suspend) | Deletes all headers, cascades everything — full local wipe ("Clear cache") |
| `deleteDocumentRecordings(documentNo, type)` (suspend, transactional) | Deletes all recordings for a doc, force-resets state to `Downloaded` — the long-press "delete recordings" UI action |

**"Never delete recordings" — where deletions actually happen and why each is safe:**
- `setLineScanned`, `deleteRecording`, `deleteDocumentRecordings` — explicit, user-initiated corrections.
- `deleteDocument`, `clearAll` — explicit whole-document/whole-DB deletion, never invoked from background sync/merge paths.
- `recordScan` — never deletes anything, purely additive.
- `replaceDownloadedDocuments` explicitly checks `hasRecordings` before deleting a header no longer present in a fresh download; `mergeDocument` itself never touches the `recordings` table at all, only `documentHeader`/`documentLine`.

### B.4.1 `mergeDocument` — exact algorithm

```kotlin
private suspend fun mergeDocument(doc: Document, type: String) {
    val recordings = db.recordingDao().getByDoc(doc.documentNo, type)
    val state = computeStateAfterMerge(doc.lines, recordings)
    db.documentHeaderDao().upsert(doc.toEntity().copy(docState = state.toDbString()))
    db.documentLineDao().deleteAllForDoc(doc.documentNo, type)
    db.documentLineDao().upsertAll(doc.lines.map { it.toEntity(type) })
}
```

That's the whole function — it never touches the `recordings` table. Recordings are keyed by `(documentNo, type, documentLine, recordingLineNo)`, and `documentLine` is a line number that's stable across re-downloads of the same document, so existing recordings simply keep applying to whichever line has that number after the lines table is replaced. `computeStateAfterMerge` re-sums those existing recordings against the *freshly downloaded* `expected` quantities to decide `Downloaded`/`Completed`/`InProgress` — this is what makes a re-download safe even if the ERP changed a line's expected quantity: the doc's state is always recomputed, never carried over.

## B.5 Domain Models

### `DocumentType` (`Models.kt`)
| Enum | `key` | `display` |
|---|---|---|
| WAREHOUSE_SHIPMENT | `WHSE_SHIP` | "Warehouse Shipment" |
| WAREHOUSE_RECEIPT | `WHSE_RCPT` | "Warehouse Receipt" |
| RETAIL_SHIPMENT | `RT_SHIP` | "Retail Shipment" |
| RETAIL_RECEIPT | `RT_RCPT` | "Retail Whse. Receipt" |
| TRANSPORT_SHEET | `TRANSPORT` | "Transport Sheet" |

`String.toDocumentType()` defaults to `WAREHOUSE_SHIPMENT` on an unrecognized key.

### `DocState` (sealed interface, `Models.kt`)
```kotlin
sealed interface DocState {
    data object Downloaded : DocState
    data object InProgress : DocState
    data object Completed : DocState
    data object PendingUpload : DocState
    data class UploadFailed(val reason: String) : DocState
}
```
Serialized (`Mappers.kt`) as the bare state name, except `UploadFailed(reason)` → `"UploadFailed:$reason"` (deserialized via `removePrefix`). Unrecognized strings deserialize to `Downloaded`.

### `LineStatus` (`Status.kt`)
```kotlin
enum class LineStatus { EMPTY, PARTIAL, EXACT, OVER }
fun of(scanned: Double, expected: Double) = when {
    scanned == 0.0      -> EMPTY
    scanned < expected  -> PARTIAL
    scanned == expected -> EXACT
    else                -> OVER
}
```
Plus `.color`/`.bgColor`/`.label` UI extension properties, and `Document.scanStatus()` (aggregate — see §A.4).

### `DocTypeFilterMode`
`{ LOCATION, RESPONSIBILITY_CENTER }` — governs whether a document type's download/dashboard filtering scopes by source location code or RC code. Stored per-type in `AppSettings.docTypeFilters`.

### `DocumentFilter` / `DownloadFilter` (`Filter.kt`)
```kotlin
data class DocumentFilter(
    val dateFrom: LocalDate? = null, val dateTo: LocalDate? = null,
    val states: Set<LineStatus> = emptySet(), val types: Set<DocumentType> = emptySet(),
    val destinationCode: String = "", val sourceCode: String = "", val rcCode: String = "",
) { val isActive: Boolean }

data class DownloadFilter(
    val dateFrom: LocalDate? = null, val dateTo: LocalDate? = null,
    val destinationCode: String = "", val sourceCode: String = "", val rcCode: String = "",
)
```

Other `Models.kt` types: `User`, `Location`, `ResponsibilityCenter`, `Item(no, name)`, `Line` (computed `status`), `Document` (computed `linesExact`, `linesTotal`, `scannedQty`, `expectedQty`, `hasProgress`), `TapeEntry` (scan-log UI model, `isError = lineStatus == null`), `Double.formatQty()` extension.

### Supporting: `DatabaseExporter`
`data/export/DatabaseExporter.kt` — `suspend fun exportTo(uri: Uri)`, reads all headers/lines/recordings via DAOs, wraps as `{exportedAt, documentHeaders, documentLines, recordings}`, Gson-pretty-prints to the given `Uri`. Debug/support tool, invoked from `SettingsScreen`'s "Export data".

## B.6 Networking / NAV Integration — Implementation Detail

### B.6.1 `ExtSystemODataClient`
`data/extsystem/ExtSystemODataClient.kt` — the only HTTP wrapper in the app. `HttpClient(OkHttp) { engine { preconfigured = okHttp }; expectSuccess = false }` (so 4xx/5xx return normally instead of throwing). Underlying `OkHttpClient`: `.proxy(Proxy.NO_PROXY)` (NAV is always on local LAN), `.authenticator(NtlmAuthenticator(domain, username, password))`, connect/read/write timeouts 30s/60s/60s.

`configure(config, creds)` only rebuilds the client when `(username, password)` changed since the last call (cached via a `clientKey`); the old client is `.close()`d first. No fixed Ktor base URL — every call takes a full absolute URL. Requests set `Accept: application/json`; downloads additionally force `Accept: application/json;odata=nometadata`.

```kotlin
sealed class ExtSystemResult<out T> {
    data class Success<T>(val data: T) : ExtSystemResult<T>()
    data class Failure(val message: String, val code: Int = -1) : ExtSystemResult<Nothing>()
}
```

| Function | Behavior |
|---|---|
| `testConnection(baseUrl)` (suspend) | `GET baseUrl`; resets `ntlmAuth.resetPhase()` first; on failure inspects `phaseReached` to distinguish "NTLM not enabled" (phase 0) vs "handshake completed, credentials rejected" (phase 2 + 401) vs a generic HTTP error (body, truncated 300 chars) |
| `uploadRecording(url, row: NavBarcodeAppRecording)` (suspend) | `POST url`, `Content-Type: application/json`, body = `gson.toJson(row)`. On non-2xx: `Failure("HTTP ${status}: $body".take(300), status)`. On thrown exception: `Failure(message ?: "Network error", code=-1)` |
| `downloadRaw(url)` (suspend) | `GET url`; follows OData `@odata.nextLink` pagination, merging every page's `value` array into one combined JSON array before returning |
| `close()` | Closes/clears the underlying client |
| `companion.parseDomainUser(raw)` | Splits `DOMAIN\user` or `user@domain`; falls back to `("", raw)` |

### B.6.2 `NtlmAuthenticator` — NTLMv2 from scratch
`data/extsystem/NtlmAuthenticator.kt` — a custom `okhttp3.Authenticator`, invoked reactively by OkHttp on any 401 with `WWW-Authenticate`. No external NTLM library; uses `javax.crypto` `HmacMD5` plus a hand-rolled inline MD4 (RFC 1320, since Android's `MessageDigest` doesn't expose MD4, needed for the NT hash).

- `phaseReached: Int` (`@Volatile`, 0/1/2) — diagnostic only, reset via `resetPhase()` before each test connection.
- **Phase 1 (Negotiate)**: triggered when the challenge header is bare `"NTLM"` or missing a token. Builds a 32-byte Type-1 message (`"NTLMSSP\0"`, MessageType=1, `FLAGS=0x80800205`, empty Domain/Workstation), base64, sent as `Authorization: NTLM <b64>`. Guard: if the original request already carried an `Authorization: NTLM ` header, the server is re-negotiating after a completed handshake → returns `null` (bad credentials, stop retrying).
- **Phase 2 (Authenticate)**: triggered when the header is `"NTLM <base64 Type2>"`. Decodes the challenge, computes:
  - NT Hash = `MD4(UTF-16LE(password))`
  - NTLMv2 key = `HMAC-MD5(ntHash, UTF-16LE(uppercase(user+domain)))`
  - Target info extracted from Type2 offset 40/44
  - NTLMv2 blob: signature `0x00000101`, Windows FILETIME timestamp, random 8-byte client challenge (`SecureRandom`), target info
  - `ntResponse = HMAC-MD5(key, serverChallenge+blob) + blob`
  - `lmResponse = HMAC-MD5(key, serverChallenge+clientChallenge) + clientChallenge`
  - Manually lays out the Type-3 byte buffer (security-buffer descriptors + domain/username/lmResponse/ntResponse bytes), base64-encodes as the new `Authorization` header.

**Domain parsing** (changed 2026-08): `ExtSystemConfig.domain` (Settings → External System Configuration → Credential session) is now a separate configured field — when non-blank, `ExtSystemODataClient.buildClient` uses it directly with the typed username as-is, so users only ever type a bare username on the login screen. When `domain` is blank, it falls back to the original behavior: a domain embedded in the typed username itself (`DOMAIN\user` or `user@domain`), split by `ExtSystemODataClient.parseDomainUser` at client-build time. The bundled per-company defaults files carry a `domain` field too (see §B.6.6).

**Credential TTL** (`ExtSystemCredentialStore`, `EncryptedSharedPreferences` file `ext_system_credentials`, AES-256-GCM/Keystore): `save(username, password, ttlHours)` stores `expiry = now + ttlHours*3_600_000L`; `get()` checks `now > expiry` on every read — if expired, clears and returns `null` (lazy expiry, not a background timer). `isValid() = get() != null`.

### B.6.3 DTOs — `data/extsystem/ExtSystemPayload.kt`

**`NavBarcodeAppRecording`** (upload) — see field table in §A.5.4. `recordingGuid` is generated inline in `AppViewModel.runUpload` (`UUID.randomUUID().toString()`) fresh on every attempt, never persisted to Room.

**`NavBarcodeAppEntry`** (download) — see field table in §A.5.3. Rows with `lineNo <= 0` are excluded from `lines` (treated as the header row). `documentDate` is parsed as `Instant.parse("${it}T00:00:00Z")`.

**`NavLocation`**: `Code`→code, `Name`→name, `Responsibility_Center`→rcCode.

**`NavResponsibilityCenter`**: `Code`, `Name`, `Short?` — defined but **not currently used**; RCs are derived client-side from distinct `NavLocation.rcCode` values rather than fetched from a dedicated endpoint.

**`NavODataList<T>`**: `{ "value": List<T> }` generic OData envelope, used for all three GET response types.

### B.6.4 Full download flow

1. `AppViewModel.realDownloadDocuments(filter, docType, onComplete)` — reloads `config`/`creds`, aborts with a specific message if not signed in / not configured / URL blank.
2. `extSystemClient.configure(config, creds)`.
3. For each `DocumentType` in scope (one, or all 5 if `docType == null`):
   - `typeCode = config.docTypeCodeFor(type)`.
   - `buildODataFilterString(filter, typeCode)`: `Document_Type eq '<typeCode>'` (always, if non-blank) + `Document_Date ge/le` (if set) + `Destination_No eq` (if set) + `Source_No eq` (if set) + `Responsibility_Center eq` (if set) — joined with `" and "`.
   - `appendODataFilter`: appends `?$filter=<urlencoded>` (or `&$filter=` if `?` already present); `URLEncoder.encode(..., "UTF-8")` with a `+`→`%20` post-fix.
   - `downloadRaw(finalUrl)` — paginated GET.
   - Parses as `NavODataList<NavBarcodeAppEntry>`, groups by `documentNo`, builds `Document`+`Line`s, `state=Downloaded`.
   - `repository.replaceDownloadedDocuments(type, documents)`.
   - On failure: increments `failures`, appends the message, **continues to the next doc type** (doesn't abort the whole batch).
4. `onComplete(failures, errorMessages)`.

Locations: `realDownloadLocations()` — single `GET config.locationsUrl` → `NavODataList<NavLocation>` → derives distinct non-blank `rcCode`s → `locationDao.clearRcs()+upsertRcs()` then `clearLocations()+upsertLocations()`. No `$filter` applied.

### B.6.5 Full upload flow

Entry points: `uploadToExtSystem` (blocking) and `uploadInBackground` (marks every doc `PendingUpload` immediately, then runs async) — both funnel into private `runUpload(docs: List<Document>): Int`.

```
for each doc in docs:
    rows = repository.getRecordings(doc.documentNo, doc.type.key)
    for each row in rows (sequential):
        recordingGuid = UUID.randomUUID().toString()      // fresh every attempt
        result = extSystemClient.uploadRecording(url, row.toNavRecording(docTypeCode, recordingGuid))
        if success: repository.deleteRecording(...)        // deleted immediately, one at a time
        if failure: failureMessage = result.message; break  // remaining rows NOT attempted this pass
    if failureMessage != null:
        repository.updateDocState(doc, UploadFailed(failureMessage)); failures++
    else:
        repository.deleteDocument(doc)                      // whole doc removed, cascade
return failures
```

**No automatic retry** anywhere in this path for any failure class (400/404/500/network) — a failed document simply sits in `UploadFailed(reason)` until the user taps Retry (which re-reads whatever `getRecordings` still returns, i.e. only rows that weren't already deleted by a prior partial success).

### B.6.6 Config file reference

**`app/src/main/assets/ext_system_defaults_*.json`** (bundled, one file per company; as of 2026-08: `_commerce`, `_mebel`, `_pohistvo`, `_mobilis`) — discovered dynamically at runtime rather than referenced by a fixed name, so adding a new company is just adding a new asset file:
```json
{
  "companyName": "Prima Commerce d.o.o.",
  "serverBaseUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/",
  "credentialTtlHours": 168,
  "domain": "",
  "documentLinesUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/BarcodeAppEntries",
  "documentTypeCodes": {
    "WAREHOUSE_SHIPMENT": "SHIPMENT", "WAREHOUSE_RECEIPT": "RECEIPT",
    "RETAIL_SHIPMENT": "RETAILSHPT", "RETAIL_RECEIPT": "RETAILRCPT", "TRANSPORT_SHEET": "TRANSPORT"
  },
  "locationsUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/LocationList",
  "recordingSyncUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/BarcodeAppRecordings"
}
```
`companyName` is only read by `AppViewModel.listExtSystemDefaultsCompanies()` (via a tiny private `CompanyNameDto`) to build the "Load built-in defaults" picker list shown in `ExtSystemConfigScreen`/`SettingsScreen` — it's not part of `ExtSystemConfig` itself. `domain` (added 2026-08) feeds `ExtSystemConfig.domain` — see §B.6.1/§B.6.2's domain-parsing note. `documentTypeCodes` keys use the enum's `.name` (matched via `dto.documentTypeCodes?.get(type.name)`).

**`prima_config.json`** (repo root) — an all-blank template of the identical shape (predates the per-company split); not read by any app code, purely a distributable seed for generating a deployment-specific defaults file.

**`AppViewModel.parseExtSystemConfigJson(json)`** — deserializes into a private nullable-mirror DTO, applies defaults (`credentialTtlHours ?: 24`, others `.orEmpty()`), never persists directly. `loadExtSystemDefaults(fileName)` reads the given bundled asset then delegates here. `getExtSystemDefaultsJsonText(fileName)` returns the raw bundled text unparsed (for "download as file"). `listExtSystemDefaultsCompanies()` lists `assets.list("")`, filters `ext_system_defaults_*.json`, and reads each file's `companyName`.

## B.7 Auth & Config Storage

### `AppSettings` / `AppSettingsStore`
Plain (unencrypted) `SharedPreferences("app_settings")`.

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
| backgroundSync | Boolean | false |
| lastLocationCode / lastRcCode | String | "" |
| disabledDocTypes | `Set<String>` (comma-joined `DocumentType.key`) | emptySet |
| docTypeFilters | `Map<String, DocTypeFilterMode>` (`"key:MODE,..."`) | emptyMap |
| debuggerActive | Boolean | false |

### `ExtSystemConfig` / `ExtSystemConfigStore`
Plain `SharedPreferences("ext_system_config")` (non-secret — no credentials stored here).
```kotlin
data class ExtSystemConfig(
    val serverBaseUrl: String = "",
    val credentialTtlHours: Int = 24,
    val documentLinesUrl: String = "",
    val documentTypeCodes: Map<DocumentType, String> = emptyMap(),
    val recordingSyncUrl: String = "",
    val locationsUrl: String = "",
    val domain: String = "",   // added 2026-08 — see B.6.2's domain-parsing note
) {
    fun docTypeCodeFor(type): String
    val isConfigured get() = serverBaseUrl.isNotBlank()
}
data class ExtSystemCredentials(val username: String, val password: String)
```

### `ExtSystemCredentialStore`
`EncryptedSharedPreferences` (file `ext_system_credentials`), `MasterKey` with `AES256_GCM` (Android Keystore, hardware-backed on API 28+), pref key scheme `AES256_SIV`, value scheme `AES256_GCM`. `save`/`get` (TTL-checked)/`isValid`/`clear` as described in §B.6.2.

### Login flow (end-to-end)
1. `LoginSheet` (`ui/screen/LoginSheet.kt`) — reusable full-screen `Dialog` (`DialogProperties(usePlatformDefaultWidth = false)`, resized to `MATCH_PARENT` via `DialogWindowProvider`; changed from a `ModalBottomSheet` in 2026-08), reused by `ExtSystemConfigScreen` (Test connection), `LocationRcPickScreen` (refresh without credentials), `DownloadFilterScreen` (auto-opens if `!hasCredentials`), and the main-menu sign-in entry point.
2. Optional `onTestConnection` callback (2026-08): when provided, submit first verifies the typed credentials against the NAV server (same check as `ExtSystemConfigScreen`'s own "Test connection") before calling `onSubmit` — shows an inline spinner and error text on failure rather than blindly accepting whatever was typed. Left `null` for flows that already do their own testing.
3. `AppViewModel.saveCredentials(username, password)` → `extSystemCredentialStore.save(...)`, refreshes the `_credentials` `StateFlow`.
4. `AppViewModel.signOut()` → `extSystemCredentialStore.clear()`, resets `_credentials` to null.
5. `credentials: StateFlow<ExtSystemCredentials?>` is observed in `MainActivity` to derive the current `User` display name (parsed client-side from the username string — no live ERP profile lookup), shown via an avatar/initials button on `MainMenuScreen` that opens `UserInfoScreen` (2026-08).

## B.8 Hilt DI

Only one module: `di/DatabaseModule.kt`.
```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository
    companion object {
        @Provides @Singleton fun provideDatabase(...): PrimaDatabase = ...
        @Provides @Singleton fun provideLocationDao(db): LocationDao = db.locationDao()
    }
}
```
Other DAOs (`DocumentHeaderDao`, `DocumentLineDao`, `RecordingDao`) are **not** separately provided — consumers inject the whole `PrimaDatabase` and call `db.documentHeaderDao()` etc. directly. No dedicated network/auth module — `ExtSystemODataClient`, `AppSettingsStore`, `ExtSystemConfigStore`, `ExtSystemCredentialStore`, `DatabaseExporter` are plain `@Singleton @Inject constructor(...)` classes. `DataWedgeManager` is a plain Kotlin `object` (no DI). Entry points: `PrimaBarcodeApplication` (`@HiltAndroidApp`), `MainActivity` (`@AndroidEntryPoint`), `AppViewModel`/`RecordingViewModel` (`@HiltViewModel`).

## B.9 Barcode Scanning Subsystem

Two independent input paths feed the same domain logic (`RecordingScreen.handleScan` → `RecordingViewModel`/callbacks → `DocumentRepository.recordScan`).

### B.9.1 Camera path (ML Kit + CameraX)
- **`BarcodeAnalyzer.kt`** — `ImageAnalysis.Analyzer` using `BarcodeScanning.getClient()` (default options, all formats). Picks the **largest bounding box** among detected barcodes (avoids incidental captures of background labels). Debounces repeated identical values (emits only on value change or after `debounceMs`, default 1500ms internal / overridden by the caller's setting). Always `image.close()`s.
- **`CameraPreview.kt`** (`ui/component/`) — binds `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, single-thread executor) to `CameraSelector.DEFAULT_BACK_CAMERA`. Plays a `ToneGenerator` beep on scan; auto-closes unless `continuous=true`; animated scanning-line + corner-bracket overlay; one-shot auto-focus at center (`FocusMeteringAction`, auto-cancel disabled — focus stays locked). Requires `android.permission.CAMERA` (optional — a device can rely solely on the hardware wedge). Camera bind failures are only logged (`Timber.e`), no user-facing error UI.

### B.9.2 Hardware wedge path (Zebra DataWedge)
- **`DataWedgeManager.kt`** — plain Kotlin `object`, talks to the DataWedge service via broadcast intents.
  - `configure(context)` — creates/updates a DataWedge profile `"PrimaBarcode"` scoped to this app's package, configures the `INTENT` output plugin (`intent_action = "com.prima.barcode.SCAN"`, `intent_delivery = "2"`). Called once from `MainActivity.onCreate()`.
  - `setContinuousScan(context, enabled, sameSymbolTimeoutMs=500)` — toggles the `BARCODE` plugin's `aim_type` between single-shot (`"0"`) and continuous/press-and-hold (`"4"`); `same_symbol_timeout` clamped 0–5000ms (tied to `AppSettings.debounceTime`), fixed `different_symbol_timeout="100"`. Reactively invoked via `LaunchedEffect(autoScan, debounceTime)` in `PrimaBarcodeApp`.
  - `createReceiver(onScan)` — `BroadcastReceiver` extracting the scanned string from extra `com.symbol.datawedge.data_string`. Registered with `RECEIVER_NOT_EXPORTED` on API 33+.
  - `intentFilter()` — `IntentFilter("com.prima.barcode.SCAN")`, registered by `RecordingScreen` via `DisposableEffect` for the screen's whole composed lifetime.

### B.9.3 `handleScan` — the shared entry point for every scan path

Implemented entirely client-side in `RecordingScreen.kt`'s local `handleScan(rawInput: String)` (not in `AppViewModel`/repository). All three input paths — the hardware DataWedge broadcast receiver, the camera (`CameraPreview.onBarcode`), and `ScanBar`'s manual entry field — call this same function; nothing about parsing or recording differs by source.

**Cross-path duplicate guard** (added 2026-08): the hardware trigger and the camera can both be "live" at once (the DataWedge receiver is registered for the whole screen lifetime regardless of whether the camera is open), so a single physical scan gesture could otherwise reach `handleScan` twice and record the quantity twice. The first lines of `handleScan` ignore a repeat of the identical raw value arriving within `debounceTime` ms of the last one handled, regardless of which path delivered it:
```kotlin
val now = System.currentTimeMillis()
if (rawInput == lastHandledBarcode && now - lastHandledAtMs < debounceTime) return
lastHandledBarcode = rawInput; lastHandledAtMs = now
```

**The `BARCODE|UOM|QTY` format**:
```kotlin
val barcode = rawInput                                     // ALWAYS the full raw string — matched & stored as-is
val pipeParts = rawInput.split("|")
val parsedQty = if (pipeParts.size == 3) pipeParts[2].toDoubleOrNull() else null
val parsedUom = if (parsedQty != null) pipeParts[1] else null
val matchedLine = doc.lines.find { it.barcodeNo == barcode }
```
- Trigger: **exactly 3 pipe-separated parts**, last part parses as `Double`. Anything else falls through to ordinary handling.
- **Matched line**: `qty = parsedQty ?: matchedLine.scanningQty`; calls `onScan(barcode, qty)` (→ `RecordingViewModel.recordScan`), immediately. If `warnOnOver` is on and the new total pushes the line into Over, an over-scan warning dialog shows (informational only — the scan is already recorded). If `parsedUom != null && parsedUom != matchedLine.unitOfMeasureCode` → UoM mismatch dialog (also informational only).
- **Unmatched**: nothing is recorded. `barcodeNotFoundError` is set (shows the "Barcode not found" dialog), the scan bar flashes red for 600ms (Slate → `#7A1A1A` → back), and an error haptic fires.

**Fixed 2026-08**: the DataWedge receiver's callback is registered once via `DisposableEffect(Unit)` — since `handleScan` is a local function redefined on every recomposition, the receiver was pinned to the very first composition's `handleScan` (and therefore a stale `doc` snapshot) for the rest of the screen's lifetime, silently evaluating over-scan/UoM-mismatch checks against outdated data for every hardware scan after the first. Fixed via `rememberUpdatedState(::handleScan)` so the receiver always calls the current version.

## B.10 Screens (`ui/screen/`) — Reference

### `MainMenuScreen.kt`
`data class DocTypeSummary(type, short, count, statusMini: List<LineStatus>, blocked: Boolean = false)`. Home screen: `PrimaTopBar` → `DocumentStatsDashboard` (tap → Dashboard) → RC/Location pill row (either cell → Location/RC pick) → "DOCUMENTS" `LazyColumn` of type rows (icon, label, mini `StatusProgressBar`, count badge or 🔒 if `blocked`, dimmed+non-interactive when blocked).

### `DocumentListScreen.kt`
Three tabs — **Orders** (`Downloaded`/`InProgress`/`UploadFailed`), **Recordings** (`Completed`/`UploadFailed`/`InProgress`-with-scans), **Errors** (`UploadFailed`). Client-side filters: location match OR `doc.hasProgress` override, plus `DocumentFilter`. Dark `ScanField` (`handleDocScan`) offers doc creation if not found, gated by `canCreateDoc`. `DocRow` supports a 5s (5000ms, 16ms tick) long-press on the Recordings tab → delete-recordings confirmation; releasing early cancels. Bottom bar varies per tab (Download+Upload / Upload / Clear-errors+Upload).

### `DocumentOverviewScreen.kt` (the "Dashboard")
Cross-type view, 3 tabs: **Errors**, **My Location** (per-type `DocTypeFilterMode` match), **All**. Opening the filter from the "My Location" tab locks source/RC to the current selection. Empty-state green checkmark + "No issues" shown on any empty tab, not just Errors.

### `DocumentFilterScreen.kt`
Generic filter editor shared by DocumentListScreen and DocumentOverviewScreen. Sections: Status (multi-select chips), Document Type (checkboxes, hidden if `showDocTypeFilter=false`), Document Date (from/to, cross-clamping date pickers), Destination/Source code, Responsibility Center (dropdown-only `ExposedDropdownMenuBox`, or locked/greyed field if `lockedSourceCode`/`lockedRcCode` passed). Reset / Apply footer.

### `DownloadFilterScreen.kt`
Pre-download filter form: date range, destination text field, and **exactly one** of Source/RC picker rows shown (the other hidden entirely, not just disabled) depending on which is fixed via `fixedSourceCode`/`fixedRcCode`. Auto-opens `LoginSheet` on entry if `!hasCredentials`.

### `ExtSystemConfigScreen.kt`
Server URL, Test Connection (opens `LoginSheet`), TTL segmented buttons (8h/24h/48h/7d), per-`DocumentType` cards (enable switch, URL field, read-only doc-type-code, Filter-by segmented control), Locations URL, Recording sync URL. "Load configuration" dialog (3 options, see §A.5.2). `BackHandler` + top-bar back both route through `attemptExit()` → shows "Save changes?" only if the buffered form differs (structural equality) from `initial`.

### `LocationRcPickScreen.kt`
Two picker rows (RC, then Location filtered to that RC) opening bottom sheets (`RcPickerSheet`/`LocationPickerSheet`, also reused by `DownloadFilterScreen`). Refresh icon triggers `onRefresh` directly if credentials exist, else opens `LoginSheet` first. Selecting a different RC clears the selected location; selecting a location auto-syncs its owning RC.

### `LoginSheet.kt`
Full-screen `Dialog` with a `PrimaTopBar` back arrow (not a bottom sheet, see §B.7's Login flow note), username/password fields (visibility toggle), footer line naming the credential TTL, submit enabled only when both non-blank; only username is `.trim()`'d, password is sent as-typed. If `onTestConnection` is supplied, submit blocks on a live NAV auth check first (spinner + inline error on failure) before calling `onSubmit`.

### `RecordingScreen.kt` — the core scanning workflow (most complex screen)
Internal state machine, `RecordingView` enum: `OVERVIEW, ACTIVE_LINE, KEYPAD` — `OVERVIEW` is the line list, `ACTIVE_LINE` shows one line's detail with +1/-1 steppers, `KEYPAD` is manual quantity entry for the active line. `handleScan` — see §B.9.3. Registers the DataWedge broadcast receiver via `DisposableEffect`. `handleBack()` — per-view back navigation (KEYPAD/ACTIVE_LINE back to OVERVIEW, OVERVIEW back to the caller). Sub-composables: `OverviewContent`, `ItemQtyDetails` (ACTIVE_LINE), `ItemQtyExtraDetails` (KEYPAD — the name is a holdover from an earlier iteration where it was shared with a since-removed flow; it's an ordinary quantity-entry composable, nothing to do with extra lines), private `StatusChip` (status pill shown in the top bar).

### `SettingsScreen.kt`
Buffered-edit-then-confirm-on-exit pattern (identical to `ExtSystemConfigScreen`'s): every field is local `remember` state; `attemptExit()` compares the rebuilt `AppSettings` (plus `pendingExtSystemConfig != null`) against `initial`; only diverges → "Save changes?" dialog. Sections: Appearance, Scanning, Sync, External System Configuration (single row → `ExtSystemConfigScreen`), Debug (Debugger active, Export data, **Insert system defaults** [3-option picker, stages into `pendingExtSystemConfig`, only persisted via Settings' own save], **Clear cache** [red, wipes credentials+settings+documents], **Delete all documents and recordings** [red, wipes only documents/recordings, not settings/sign-in]), System Info (read-only version/schema info), Account (avatar/name, immediate Sign out — no confirmation).

### `UploadErrorScreen.kt`
Read-only failed-upload detail: header card, document-info card, full raw error-message card (`(document.state as DocState.UploadFailed).reason`), "Retry Upload" button.

## B.11 Navigation & App Shell

### `MainActivity.kt`
`@AndroidEntryPoint class MainActivity : AppCompatActivity()`. `onCreate`: `enableEdgeToEdge()`, `hideNavBar()` (immersive nav-bar hiding, re-applied on `onWindowFocusChanged`), `DataWedgeManager.configure(this)`, then `setContent { ... }`.

Inside `setContent`: obtains `AppViewModel` via `hiltViewModel()`, loads `initialSettings` once, holds every setting as its own `remember { mutableStateOf(...) }`, with `buildSettings()` reassembling `AppSettings` and every `on...Change` callback persisting via `appVm.saveSettings(...)`. Wraps everything in `PrimaBarcodeTheme(textSizeOffset, uppercaseEnabled) { PrimaBarcodeApp(...) }`.

### `PrimaBarcodeApp` composable (private, in `MainActivity.kt`) — the NavHost/app shell
Creates `nav = rememberNavController()`; derives `user` from `appVm.credentials`; collects `locations`/`responsibilityCenters`/`documents`/`extSystemConfig` (all `StateFlow`, `collectAsState()`); auto-recovers stale RC/location selections; pushes continuous-scan config to `DataWedgeManager` reactively; filters `documents` per document type via `DocTypeFilterMode`; builds `docTypes: List<DocTypeSummary>`; computes shift-wide counters:
```kotlin
val shiftScans  = filteredDocs.sumOf { d -> d.lines.count { it.scanned > 0 } }  // count of lines-with-progress, not total qty
val errorDocs   = filteredDocs.filter { it.state is DocState.UploadFailed }
val readyDocs   = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.EXACT }
val partialDocs = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.PARTIAL }
val overDocs    = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.OVER }
```
`ready`/`partial`/`over` are mutually exclusive by `scanStatus()` and deliberately exclude anything already `UploadFailed` (an over-scanned-and-failed doc counts only in `errorDocs`). Manages UI-only state (`selectedDocType`, `docFilter`, `overviewFilter`, dialogs, `processingMessage` blocking-progress state, debug-URL confirmation via `launchWithDebug(urls, onCancel, action)` gated by `debuggerActive` — see §B.11.5); `exportLauncher` (`CreateDocument("application/json")`) → `appVm.exportDatabase`.

**NavHost routes** (`startDestination = "main"`):

| Route | Screen | Notes |
|---|---|---|
| `main` | `MainMenuScreen` | `onTypeTap`→`docs`, `onDocumentOverview`→`dashboard?tab=1`, `onShowErrors`→`dashboard` |
| `location_rc_pick` | `LocationRcPickScreen` | wired to `isRefreshingLocations`, `lastLocationSyncAt`, `downloadLocations()`, `saveCredentials()` |
| `ext_system_config` | `ExtSystemConfigScreen` | save/discard/loadDefaults/testConnection/importJson delegate to `AppViewModel` |
| `settings` | `SettingsScreen` | all settings + export/clearCache/deleteAllDocuments/insertSystemDefaults/signOut |
| `docs` | `DocumentListScreen` | filtered by `selectedDocType` + location/RC per `DocTypeFilterMode`; upload branches on `backgroundSync` |
| `dashboard?tab={tab}` (Int, default 0) | `DocumentOverviewScreen` | `initialTab` from nav arg |
| `filter` | `DocumentFilterScreen` | edits `docFilter`, `showDocTypeFilter=false` |
| `overview_filter` | `DocumentFilterScreen` | edits `overviewFilter`, locked source/RC from dashboard drill-down |
| `download_filter` | `DownloadFilterScreen` | fixed source/RC per filter mode; URLs via `buildDownloadUrls`; downloads via `realDownloadDocuments` |
| `recording/{documentNo}/{type}` (String args) | `RecordingScreen` | route-scoped `RecordingViewModel` via `hiltViewModel()` |
| `upload_error/{documentNo}` (String arg) | `UploadErrorScreen` | retry delegates to `AppViewModel` |

**App-level overlay dialogs** (outside/after the NavHost): blocking "processing" `Dialog` (spinner + message), download-error `AlertDialog`, debug-URL confirmation `AlertDialog` ("Proceed"/"Cancel"), sync-error `AlertDialog` ("See errors" → `dashboard` / "Dismiss").

### B.11.1 `RecordingViewModel` (`@HiltViewModel`)
Route-scoped (one instance per `recording/{documentNo}/{type}` navigation entry). Wraps `DocumentRepository`, exposing `document: StateFlow<Document?>` (via `observeDocument`) plus thin suspend wrappers around the repository's scan-mutation functions (`recordScan`, `setLineScanned`) that `RecordingScreen` calls directly from its callbacks.

### B.11.2 `AppViewModel` (`@HiltViewModel`)
Constructor deps: `Context`, `DocumentRepository`, `LocationDao`, `DatabaseExporter`, `ExtSystemConfigStore` (public `val`), `ExtSystemCredentialStore` (public `val`), `ExtSystemODataClient`, `AppSettingsStore`, private `Gson`. The orchestrator wiring repository + NAV networking together — there is no separate "SyncRepository" (see §B.1).

**Exposed state** (all `StateFlow`, all safe to `collectAsState()` from any composable):
- `credentials: StateFlow<ExtSystemCredentials?>`
- `locations: StateFlow<List<Location>>`
- `responsibilityCenters: StateFlow<List<ResponsibilityCenter>>`
- `isRefreshingLocations: StateFlow<Boolean>`
- `lastLocationSyncAt: StateFlow<Instant?>`
- `documents: StateFlow<List<Document>>`
- `extSystemConfig: StateFlow<ExtSystemConfig>` — converted from a plain getter to a real `StateFlow` in 2026-08 (see §B.13); all internal (non-composable) reads inside `AppViewModel` itself use `.value`, all composable reads go through `.collectAsState()`.

**Key public functions**: `downloadLocations`, `buildDownloadUrls`, `getLocationsUrl`/`getRecordingSyncUrl`, `realDownloadDocuments`, `loadSettings`/`saveSettings`, `saveExtSystemConfig`, `saveCredentials`, `signOut`, `testExtSystemConnection`, `parseExtSystemConfigJson`, `loadExtSystemDefaults`, `getExtSystemDefaultsJsonText`, `listExtSystemDefaultsCompanies` (scans bundled `ext_system_defaults_*.json` assets for the "Load built-in defaults" company picker — see §B.6.6), `uploadToExtSystem`/`uploadInBackground` (both call private `runUpload`), `exportDatabase`, `clearCache` (wipes settings+config+credentials+all documents), `deleteAllDocuments` (wipes only documents/recordings via `repository.clearAll()`), `clearDocumentRecordings`, `clearErrorDocs`.

There is no manual/offline document-creation path — a scanned or typed document number that doesn't exist locally is simply reported as not found (see §A.3); `AppViewModel` has no `createDocument`-shaped function.

### B.11.3 `Language` (`ui/theme/Language.kt`)
```kotlin
enum class Language(val tag: String, val label: String) {
    ENGLISH("en", "English"), CROATIAN("hr", "Croatian"),
    SLOVENIAN("sl", "Slovenian"), MACEDONIAN("mk", "Macedonian"),
}
```
Matching `values-hr/`, `values-sl/`, `values-mk/` string resource folders (all complete as of 2026-08) plus `res/xml/locales_config.xml` (required for the Android 13+ per-app-language system picker, referenced via `android:localeConfig` in the manifest).

**Runtime switching** (`MainActivity.applySettings()`):
```kotlin
if (language != s.language) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(s.language.tag))
}
```
Only called from the Settings-save path, only when the language actually changed. On API 33+ this delegates to the platform `LocaleManager` (persisted by the OS); on older APIs, AppCompat persists it internally and recreates the Activity to re-resolve string resources. **Not** re-applied from `AppSettings.language` on cold start — persistence relies entirely on AppCompatDelegate's/LocaleManager's own storage, since language is only ever changed through this one code path (the two stay in sync in practice, but a developer restoring `AppSettings` from a backup/import should be aware `setApplicationLocales` would need to be re-invoked manually if ever bypassing this normal flow).

### B.11.4 `DocTypeFilterMode` — full effect trace
Configured per document type in `ExtSystemConfigScreen` (persists **immediately**, not buffered like the rest of that screen — writes straight through `onDocTypeFiltersChange` → `appVm.saveSettings`). Consumed identically in three places in `MainActivity.kt` (main-menu filtering, doc-type "blocked" computation, and the `docs` route's `typeDocs`):
```kotlin
when (docTypeFilters[doc.type.key] ?: DocTypeFilterMode.LOCATION) {
    LOCATION -> location != null && doc.sourceCode == location.code
    RESPONSIBILITY_CENTER -> rc == null || doc.rcCode == rc.code
}
```
Always OR'd with `doc.hasProgress` so in-progress documents are never hidden regardless of filter mode. Note the asymmetry: LOCATION mode requires an explicit match (no location selected ⇒ nothing shows); RESPONSIBILITY_CENTER mode is permissive when nothing is selected (no RC selected ⇒ everything shows). Also drives which of Source/RC is fixed vs shown as a picker in `DownloadFilterScreen`, and gates `canCreateDoc` in the `docs` route.

### B.11.5 Debug mode (`debuggerActive`)
Central gate, `MainActivity.launchWithDebug(urls, onCancel = {}, action)`:
```kotlin
if (debuggerActive && urls.isNotEmpty()) {
    debugUrls = urls; pendingAction = action; pendingCancel = onCancel; showDebugDialog = true
} else action()
```
Wired at every network entry point: locations refresh, ext-system test-connection, all upload call sites (doc list, dashboard, recording screen, upload-error retry), and document download (`DownloadFilterScreen.onConfirm`, URLs formatted `"$type: $url"` per doc type).

### B.11.6 Settings' buffered-edit pattern, precisely
Both `SettingsScreen` and `ExtSystemConfigScreen` share the identical shape:
- All fields are local `remember { mutableStateOf(initial.X) }` — no persistence during editing.
- `BackHandler` and the top-bar back button both call `attemptExit()`.
- `attemptExit()`: `if (buildX() != initial [|| pendingExtSystemConfig != null for Settings]) showExitDialog = true else onDiscard()` — structural (data class) equality check.
- Exit dialog "Yes" → `onSave(buildX())` [+ `pendingExtSystemConfig?.let { onSaveExtSystemConfig(it) }` for Settings] → in `MainActivity`, Settings' `onSave` is wired to `applySettings` (bulk-copies every field back into `MainActivity`'s top-level `remember` state, triggers locale change if needed, then `appVm.saveSettings`) followed by `nav.popBackStack()`.
- Exit dialog "No" → `onDiscard()` = `nav.popBackStack()`, nothing written.
- No changes at all → back-navigates silently, no dialog.

**"Insert system defaults" persistence timing** — the three load/download/import actions only ever set local `pendingExtSystemConfig`; the row label appends `" (pending)"` while non-null. It is **only** actually saved when Settings' own exit-save flow commits (`pendingExtSystemConfig?.let { onSaveExtSystemConfig(it) }` → `AppViewModel.saveExtSystemConfig` → persists to `ExtSystemConfigStore` + updates the `extSystemConfig` StateFlow). Discarding the screen drops it with zero persistence.

## B.12 Network Security Config
`app/src/main/res/xml/network_security_config.xml` sets `<base-config cleartextTrafficPermitted="true" />` (referenced from the manifest) — required because the ERP is served over plain `http://` on the local LAN. **Do not remove this** unless the ERP endpoint is moved behind TLS.

## B.13 Recent Codebase Audit Findings (2026-08) — Applied

A full source audit was performed for dead code, missing wiring, and correctness gaps. The following were found and fixed:

| Finding | Fix |
|---|---|
| `RecordingDao.observeByLine()` — zero callers | Removed |
| `DocumentRepository.observeDocuments(sourceCode, rcCode)` — zero callers, superseded by `observeAll()` + client-side filtering | Removed (and the now-orphaned `DocumentHeaderDao.observeHeaders`) |
| `DocumentRepository.undoLastScan(...)` — fully correct but zero UI wiring; abandoned feature | Removed |
| `Int.flooredAtZero()` (`Status.kt`) — dead, referenced the same abandoned undo-scan feature | Removed |
| `DocumentRepository.clearByType(type)` — zero callers, unconditional delete with no recordings-safety check (contradicted the "never delete recordings" principle) | Removed (and the now-orphaned `DocumentHeaderDao.deleteAllByType`) |
| `DocumentRepository.getUploadableDocs()` — zero callers | Removed |
| `DocumentRepository.recordScan()` didn't call `regressFromCompletedIfNeeded()` unlike its siblings — an over-scan on an already-`Completed` document left `docState` incorrectly stuck at `Completed` | Fixed: now calls it, matching `setLineScanned`/`addExtraLine`/`updateExtraLineQuantity` |
| `AppViewModel.extSystemConfig` was a plain getter over a `MutableStateFlow`, not itself a `StateFlow` — Compose composables reading it directly weren't tracked by the snapshot system, and only "worked" via incidental recomposition from unrelated state | Converted to a real `StateFlow<ExtSystemConfig>`; all composable call sites now `collectAsState()`, all internal ViewModel reads use `.value` |
| Geist/GeistMono typefaces were bundled (`res/font/`, ~40 TTFs) but never wired — `Type.kt` used `FontFamily.Default`/`Monospace` behind a `TODO` | Wired up (`Font(R.font.geist_*, ...)`) — likely root cause of earlier recurring backspace-glyph mojibake rendering bugs |
| Mojibake em-dashes in two `AppViewModel.kt` doc comments | Fixed |
| Slovenian (`values-sl`) and Macedonian (`values-mk`) string resources missing — `Language` enum offered them but they silently fell back to English | Added, full 234-string translations |

This list is kept here so future contributors don't have to re-derive it from git history.

### 2026-08 — "not-on-document / extra line" feature removed end-to-end

The offline-before-download scanning workflow described in earlier drafts of this doc (a placeholder document, "extra"/not-on-document recordings, the barcode-waterfall reattribution algorithm on merge, `askQtyForUnknownBarcode`/`warnNotOnDocument`/`autoUploadCompleted` settings, the `UNKNOWN_BARCODE`/`EXTRA_LINE`/`EXTRA_KEYPAD` `RecordingView` states) was fully removed from the app — confirmed via a full-codebase grep sweep to have left zero orphaned fields, enum entries, or string resources behind. `handleScan` on an unmatched barcode now does exactly one thing: shows a "Barcode not found" error and records nothing (§B.9.3). This changelog wasn't updated at the time the feature was removed, so Parts A and B of this doc had drifted significantly out of sync with the actual source for a while — the whole doc was corrected against current source in the same pass that produced the next entry below.

### 2026-08 — dead-code sweep

A systematic sweep for orphaned files/functions/fields found and removed:
- **`ui/component/Chip.kt`** (whole file — `Chip()`, `StatusChip()`, `ChipTone`) and **`ui/theme/PrimaTheme.kt`** (whole file — the dead theme duplicate, see §B.14) — zero references anywhere.
- `RecordingDao.getLastForLine()`, `DocumentHeaderDao.upsertAll()`, `Mappers.kt`'s `Location.toEntity()`/`ResponsibilityCenter.toEntity()` (their `toDomain()` counterparts are used, just not this direction) — zero call sites.
- `Document.isSourceRetail` was flagged as write-only (set on every merge, never read) but deliberately **kept**, not removed — see §B.17.
- This document (`TECHNICAL_GUIDE.md`) and `USER_GUIDE.md` were both corrected in the same pass — see the entry above for what had drifted.

## B.14 Design System / Theme

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
| Ink / Ink2 / Ink3 / Ink4 | `#1A1C1F` / `#3D4046` / `#6F7378` / `#A4A6AC` (darkest → lightest text hierarchy) |

`PrimaStatus` — the four-state semantic language (see §A.4): `Empty #CE3A3A`, `Partial #C7943A`, `Exact #2E8C5E`, `Over #2D6CE0`, each with a ~10–12%-alpha `*Bg` tint variant for chips/badges.

`PrimaLightColors` (Material3 `lightColorScheme`) is the **only** scheme actually applied: primary=Coral, secondary=Slate, tertiary=Teal, background=Cream, surface=White, error=`PrimaStatus.Empty`. **`PrimaDarkColors` is defined in `Color.kt` but never referenced anywhere else in the codebase — dead code.** The app always renders light, regardless of system dark-mode setting.

### `Type.kt` — Typography
`Geist`/`GeistMono` `FontFamily`s (wired to bundled `res/font/` TTFs as of the 2026-08 audit, see §B.13). `LocalTextSizeOffset` (Settings → Text size, adds an sp offset app-wide) and `LocalUppercaseEnabled` (Settings → Uppercase text, drives the `.uppercased` string extension used pervasively on labels/tabs/buttons/status chips). `TextSize` enum: `NORMAL(+2sp)`, `LARGER(+4sp)`. `monoCounter`/`monoLabel` composable vals are the two GeistMono styles used for quantity counters and codes/timestamps respectively.

### `Shape.kt` — `PrimaShapes`
`extraSmall=4dp`, `small=8dp`, `medium=12dp`, `large=14dp`, `extraLarge=22dp` (all `RoundedCornerShape`).

### Theme entry point
**`Theme.kt` → `PrimaBarcodeTheme(textSizeOffset, uppercaseEnabled, content)`** — the only theme composable, wired into `MainActivity`; provides `LocalTextSizeOffset`+`LocalUppercaseEnabled`, then `MaterialTheme(colorScheme=PrimaLightColors, typography=scaledTypography(...), shapes=PrimaShapes)`.

(Until 2026-08 there was a second, dead `PrimaTheme.kt → PrimaTheme(textSizeOffset, content)` — an older, unreferenced duplicate. Removed in a dead-code sweep; if you're looking at git history from before that and see two theme files, that's why.)

## B.15 Build & Project Configuration

### `app/build.gradle.kts`
- `namespace`/`applicationId`: `com.prima.barcode`. `compileSdk` 36, `minSdk` 26, `targetSdk` 36. Java/Kotlin target `VERSION_11`. `versionCode`/`versionName` are bumped on every commit to this project (see `app/build.gradle.kts` for the current value — don't hardcode it here, it's stale the moment it's written).
- Only the `release` build type is customized (`isMinifyEnabled = false`); no `debug` overrides, no product flavors.
- `buildFeatures`: `compose = true`, `buildConfig = true`. KSP arg `room.schemaLocation = "$projectDir/schemas"` (Room schema JSON exports are committed to the repo per convention — include them in commits when the schema changes).
- Notable dependency facts:
  - **Ktor's `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, and `ktor-client-auth` are declared but unused** — confirmed via source grep (no `ContentNegotiation` plugin install, no `io.ktor...auth` imports anywhere). All JSON is Gson; all auth is the hand-rolled `NtlmAuthenticator`. Don't assume Ktor's request/response bodies are JSON-typed anywhere — they're always raw strings hand-parsed with Gson.
  - ML Kit `barcode-scanning:17.3.0` has an in-source comment flagging an unaligned `.so` — update when a newer release fixes it.
  - `androidx.security:security-crypto:1.1.0-alpha06` — still on an alpha release; watch for a stable release when upgrading.

### `settings.gradle.kts`
Single module `:app`. Repositories: `google()`, `mavenCentral()`, `gradlePluginPortal()`. `rootProject.name = "PrimaBarcode"`.

### `gradle/libs.versions.toml`
Only Compose/AndroidX/test/Hilt libraries and the 4 Gradle plugins are catalog aliases (`agp=9.2.1`, `hilt=2.59`, `kotlin=2.2.10`, `ksp=2.2.10-2.0.2`, `composeBom=2026.02.01`, etc.). Room/Ktor/CameraX/ML Kit/Gson/Timber/security-crypto/appcompat are plain string coordinates directly in `app/build.gradle.kts`, not catalog entries — keep this in mind when bumping versions.

### `AndroidManifest.xml`
- Permissions: `INTERNET`, `CAMERA`, `VIBRATE`. `<uses-feature android:name="android.hardware.camera" android:required="false" />` — camera is optional; a device can run on the hardware wedge scanner alone.
- `networkSecurityConfig="@xml/network_security_config"` (cleartext HTTP allowed — required for the LAN-hosted ERP; see §B.12). `localeConfig="@xml/locales_config"` (four languages, see §B.11.3).
- Only one component: `MainActivity` (`MAIN`/`LAUNCHER`). No services/providers declared — the DataWedge `BroadcastReceiver` is registered dynamically in code (`RecordingScreen`'s `DisposableEffect`), not in the manifest.

## B.16 String Resources — Leftovers Worth Knowing About

A 2026-08 localization audit found every screen had been using `stringResource()` correctly for a while, *except* a substantial number of `Text()`/`Toast`/`contentDescription`/`ctaLabel` call sites that had hardcoded English literals directly — meaning those strings had never even become resource keys, so they'd never had a chance to be translated. That pass added the missing keys (with real hr/sl/mk translations, not copies of the English text) and fixed every call site found at the time. If you're adding new UI text, always add it as a `stringResource()` key in all 4 locale files from the start rather than a literal — see the memory note this rule is tracked under for the project's assistant.

Separately, `res/values/strings.xml` (+ `values-hr`/`values-sl`/`values-mk`, kept in sync, see §B.11.3) also contains a number of keys with **no corresponding UI** in the current screens at all — remnants of earlier iterations of the login/settings/multiplier flows, plus at least one cluster (`doc_state_new/active/done/failed`, all 5 `doctype_*_desc` keys) that don't correspond to any current status vocabulary or screen. These are harmless (unused string resources cost nothing at runtime) but a candidate for a cleanup pass — confirm via grep for the exact key before removing, since new UI could reuse one of these names.

## B.17 Extension Points & Notes for Future Developers

- **Adding a new document type**: add an enum value to `DocumentType` (Models.kt) with a `key`/`display`; add its endpoint/code fields to `ExtSystemConfig`; add a card for it in `ExtSystemConfigScreen`; it will automatically appear in `MainMenuScreen`'s type list and all downstream screens, since they all iterate `DocumentType.entries`.
- **Adding a new AppSettings field**: add to the `AppSettings` data class + `AppSettingsStore` serialization, add local `remember` state + callback wiring in `MainActivity.kt`, and a control in `SettingsScreen.kt`. Remember the buffered-edit pattern — don't persist directly from the toggle, let it flow through `buildSettings()`/exit-save.
- **The upload payload is intentionally flat and per-row**, not batched — if ERP-side batching is ever desired, `AppViewModel.runUpload`'s row loop is the place to change, but note the per-row-immediate-delete behavior is what makes partial-failure retries safe; batching would need an equivalent all-or-nothing safety guarantee.
- **`NavResponsibilityCenter` DTO** is defined but unused (RCs are derived from Location rows) — if the ERP ever exposes a genuine RC list with richer fields (e.g. using the `short` field), this DTO is ready to wire into `AppViewModel.realDownloadLocations` (or a new `realDownloadResponsibilityCenters`).
- **`Document.isSourceRetail`** (`documentHeader.isSourceRetail`) is written on every download/merge (from NAV's `Retail_Location` flag) but not currently read anywhere in the app — captured for a retail-specific feature that hasn't been built yet. Deliberately kept, not dead code to clean up.
