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
3. **Scan** — record barcode scans locally against document lines (or as "extra" lines if unmatched).
4. **Upload** — push each individual recorded scan as its own row to a second shared ERP table ("Barcode App Recordings"); on success the local document is fully removed; on failure it's flagged with the exact server error for retry.

## A.2 The Document Lifecycle (functional view)

Every document is always in exactly one of these states, computed automatically from its recordings (except where noted):

| State | Meaning | How it's reached |
|---|---|---|
| **Downloaded** | Fresh from the ERP, zero scans | Initial state after download/merge if no recordings exist for the doc |
| **In Progress** | Some scanning has happened, not yet complete | Automatically entered the moment any scan/edit occurs on a Downloaded or Upload-Failed document; also the state a Completed document regresses to if a later scan/edit breaks its "everything exact" condition |
| **Completed** | Every line is exactly at its expected quantity and no unresolved extra lines remain | Computed on download/merge only — a document is *never* pushed into Completed by an in-app scan action directly; scanning to exact match keeps it "recomputed as Completed" on the next merge, but functionally the app treats "every line exact" as effectively complete in the UI regardless |
| **Pending Upload** | Upload in progress (background-sync mode only) | Set immediately when a background upload starts, before the network call resolves |
| **Upload Failed: \<reason\>** | The last upload attempt failed | Set when any row's POST to the ERP fails; the reason is the literal server/config error text |

**Key business rule: recordings are never silently deleted.** A re-download can refresh a document's header/lines from the ERP, but it will never discard a user's recorded scans, even if the ERP no longer lists that document, or the document's lines have changed. This is the guarantee that makes offline-before-download scanning safe (see §A.3).

## A.3 Offline-Before-Download Scanning ("Barcode Waterfall")

**Scenario the app is explicitly designed for**: a warehouse worker has a printed pick list but no network connectivity yet (or the document hasn't been released in the ERP yet). They scan a document number that doesn't exist locally, the app offers to create a placeholder document, and they start scanning items against it. Since the placeholder has no real lines, every scan lands as an "extra" (not-on-document) line.

Later, once connectivity returns and the real document is downloaded, the app **automatically reattributes** those extra scans onto the real lines by matching barcode — this is the "barcode waterfall" algorithm, implemented in `DocumentRepositoryImpl.mergeDocument()`.

**Algorithm** (functional description — see Part B §B.4 for the exact code path):
1. For every existing "extra" recording (barcode X, quantity Q) on the document being merged, find every real line whose barcode equals X, sorted by line number ascending.
2. If there are no matching lines, the extra recording is left alone (it stays "not on document" — nothing is lost, it just isn't resolved yet).
3. If there are matches, quantity Q is distributed across the matching lines **in line-number order**, filling each line up to its expected quantity before spilling into the next matching line. The **last** matching line always absorbs whatever remains — even if that pushes it into Over-qty — guaranteeing the extra's full quantity is always fully placed and none of it is lost.
4. Once fully distributed, the original extra recording row is deleted (its quantity has been losslessly moved onto real-line recordings, not discarded).

**Worked example**: Document `DOC1` downloads with Line 10 (barcode `B1`, expects 5) and Line 20 (barcode `B1`, expects 3). Locally there's an unresolved extra recording of barcode `B1`, quantity 6 (scanned offline before download). After merge: Line 10 gets 5 (now exact), Line 20 gets the remaining 1 (partial, 1/3) — the extra row is gone, all 6 units accounted for. If the extra had been quantity 9 instead, Line 10 still gets 5, and Line 20 (the last match) absorbs the full remaining 4 — landing at 4/3, i.e. Over-qty — again by design (last match always takes the remainder, even over expected).

**Multiple lines sharing a barcode** is therefore a supported, intentional scenario this algorithm is built to handle — not an edge case to avoid.

## A.4 The Four-State Line Status Language

| Status | Rule | Color | Where it's used |
|---|---|---|---|
| Empty | scanned == 0 | Red `#CE3A3A` | Line & document status everywhere |
| Partial | 0 < scanned < expected | Amber `#C7943A` | " |
| Exact ("Ready" in UI) | scanned == expected | Green `#2E8C5E` | " |
| Over ("Over-qty" in UI) | scanned > expected | Blue `#2D6CE0` | " |

**Document-level aggregate** (`Document.scanStatus()`): Empty only if every line is Empty and there are no extras; **Over wins over everything** — a single Over line makes the whole document show Over regardless of other lines; Exact only if every line is Exact **and** there are no unresolved extras; otherwise Partial. This means a document with all lines exact but one leftover unresolved extra scan is shown as Partial, not Exact/Ready — extras must be reconciled (matched to a real line via re-download, or manually deleted/adjusted) before a document can read as fully done.

## A.5 Configuring the NAV / Business Central Connection

All of this lives under **Settings → External System Configuration** (`ExtSystemConfigScreen`), normally touched only during setup or by support.

### A.5.1 Fields to configure

| Field | Purpose | Example |
|---|---|---|
| Server base URL | Root used only for the "Test connection" NTLM probe | `http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/` |
| Session duration | How long a signed-in session's credentials stay valid before requiring re-entry (8h / 24h / 48h / 7 days) | 168h |
| Document lines URL | The **"Barcode App Entry"** OData endpoint — shared by all 5 document types | `.../Company('Prima Commerce d.o.o.')/BarcodeAppEntries` |
| Per-document-type: enabled switch | Whether that document type is offered in the app at all | — |
| Per-document-type: Document Type Code | The exact `Document_Type` filter value the ERP expects for that type (read-only in this screen; comes from imported/loaded config, not typed here) | `SHIPMENT`, `RECEIPT`, `RETAILSHPT`, `RETAILRCPT`, `TRANSPORT` |
| Per-document-type: Filter by | Whether that type's "download" and "My Location" filtering scope by **Location** (source code) or by **Responsibility Center** | `LOCATION` (default) |
| Locations URL | Reference-data endpoint for Locations (RCs are derived client-side from distinct location RC codes, not fetched from a separate RC endpoint) | `.../LocationList` |
| Recording sync URL | The **"Barcode App Recordings"** OData endpoint — one POST per recorded scan | `.../BarcodeAppRecordings` |

### A.5.2 Loading configuration

Three interchangeable ways to populate the above (available both in `ExtSystemConfigScreen`'s "Load configuration" button and Settings' "Insert system defaults" row — functionally identical, differing only in when they're persisted, see §B.11.6):

1. **Load built-in defaults** — reads the app-bundled `assets/ext_system_defaults.json` and fills the form immediately.
2. **Download built-in defaults** — writes that same bundled JSON to a file the user picks (for editing/distribution as a starting template).
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

One flat OData row **per individual scanned recording** — not one row per document, not batched. `Document_Line_No = 0` marks an "extra"/not-on-document recording.

| NAV field | Meaning |
|---|---|
| `Document_Type` | Document type code |
| `Document_No` | Document number |
| `Document_Line_No` | Which line this recording applies to (0 = extra/not-on-document) |
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
| `askQtyForUnknownBarcode` | Whether an operator is prompted for quantity on an unrecognized scan, or it auto-records as 1 | On |
| `warnOnOver` | Whether an over-scan pops a confirmation | On |
| `warnNotOnDocument` | Whether an unmatched scan pops a confirmation (suppressed automatically on manually-created documents that have no expected lines at all — everything there is definitionally "extra") | On |
| `autoUploadCompleted` | Prompt to upload immediately when a document becomes fully exact | Off |
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
| A document that should have merged offline scans didn't | Usually a location/RC mismatch — the document was downloaded under a different location/RC filter than expected, or the barcode used offline genuinely doesn't match any line's `Barcode` field | Re-check the document was downloaded, and that the offline barcode actually matches a line |

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
PK: `(documentNo, type, documentLine, recordingLineNo)`. FK `(documentNo, type) → documentHeader`, `ON DELETE CASCADE`. Indices: `(documentNo, type)`, `(documentLine)`. `documentLine = 0` is the sentinel for "extra"/not-on-document.

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
- `upsert(doc)` / `upsertAll(docs)` (suspend, `@Upsert`)
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
- `getLastForLine(documentNo, type, lineNo): RecordingEntity?` (suspend) — most recent for a line
- `getNextRecordingLineNo(documentNo, type, documentLine): Int` (suspend) — `COALESCE(MAX(recordingLineNo),0)+1`, scoped per `(documentNo, type, documentLine)` triple (each line — and the extra bucket — has its own independent sequence)
- `getExtraByBarcode(documentNo, type, barcodeNo): RecordingEntity?` (suspend) — existing extra row for accumulation
- `getByDoc(documentNo, type): List<RecordingEntity>` (suspend)
- `insert(recording)` (suspend, `@Insert` — plain insert, fails on PK conflict by design; recordings are append-only)
- `deleteByPk(documentNo, type, documentLine, recordingLineNo)` (suspend)
- `deleteAllForLine(documentNo, type, lineNo)` (suspend)
- `deleteAllForDoc(documentNo, type)` (suspend)
- `updateQuantity(documentNo, type, documentLine, recordingLineNo, quantity)` (suspend)
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
| `upsertDocument(doc)` (suspend, transactional) | Upserts header + all lines; for every line with `scanned > 0.0` that has **no existing recording**, synthesizes one backfill recording carrying the full quantity — only ever adds one recording per line, never duplicates on repeated calls |
| `replaceDownloadedDocuments(type, docs)` (suspend, transactional) | Deletes headers of `type` not present in `docs` **only if they have zero recordings**; calls `mergeDocument` for every doc in `docs` |
| `mergeDocument(doc, type)` (private, suspend) | The barcode-waterfall algorithm — see §A.3 for functional description and §B.4.1 below for the exact code path |
| `computeStateAfterMerge(lines, recordings)` (private) | `Downloaded` if no recordings; else `Completed` if every line's summed recordings exactly equal `expected` **and** no extras remain; else `InProgress`. Never returns `PendingUpload`/`UploadFailed` — those are only set via `updateDocState` from outside, and any subsequent merge will silently overwrite them |
| `recordScan(documentNo, type, lineNo, barcodeNo, userId, quantity)` (suspend, transactional) | Looks up line+header, generates next `recordingLineNo`, **inserts** a new recording (always additive, never updates existing rows), calls `advanceToInProgressIfNeeded` + `regressFromCompletedIfNeeded` |
| `setLineScanned(documentNo, type, lineNo, scanned, userId)` (suspend, transactional) | **Deletes all recordings for that line**, then inserts one fresh recording with the exact new total if `scanned > 0`. Runs all three state helpers (including `regressToDownloadedIfNeeded`, since zeroing a line could empty the doc entirely). This is the one path that legitimately deletes real-line recording rows — it's a deliberate user-driven overwrite |
| `addExtraLine(documentNo, type, barcodeNo, userId, quantity)` (suspend, transactional) | Accumulates onto an existing extra recording for that barcode if one exists, else inserts new (`documentLine=0`). `advanceToInProgressIfNeeded` + `regressFromCompletedIfNeeded` |
| `updateExtraLineQuantity(documentNo, type, recordingLineNo, quantity)` (suspend, transactional) | Direct `updateQuantity` on an extra row; `advanceToInProgressIfNeeded` + `regressFromCompletedIfNeeded` (deliberately **not** `regressToDownloadedIfNeeded`) |
| `deleteExtraLine(documentNo, type, recordingLineNo)` (suspend, transactional) | `deleteByPk` with `documentLine=0`; `regressFromCompletedIfNeeded` + `regressToDownloadedIfNeeded` |
| `updateDocState(documentNo, type, state)` (suspend) | Direct state write — used by the upload flow for `PendingUpload`/`UploadFailed` |
| `deleteDocument(documentNo, type)` (suspend) | Deletes header, cascades lines+recordings — used on upload success |
| `getRecordings(documentNo, type): List<RecordingEntity>` (suspend) | Pass-through, used by the upload flow to enumerate rows to send |
| `deleteRecording(documentNo, type, documentLine, recordingLineNo)` (suspend) | Raw single-row delete, no state recomputation — used by the upload flow to delete a row the instant its POST succeeds |
| `clearAll()` (suspend) | Deletes all headers, cascades everything — full local wipe ("Clear cache") |
| `deleteDocumentRecordings(documentNo, type)` (suspend, transactional) | Deletes all recordings for a doc, force-resets state to `Downloaded` — the long-press "delete recordings" UI action |

**"Never delete recordings" — where deletions actually happen and why each is safe:**
- `setLineScanned`, `deleteExtraLine`, `deleteRecording`, `deleteDocumentRecordings` — explicit, user-initiated corrections.
- `deleteDocument`, `clearAll` — explicit whole-document/whole-DB deletion, never invoked from background sync/merge paths.
- `mergeDocument`'s extra-row deletion — only after the quantity has been fully, losslessly transferred onto real-line recordings (conservation, not loss).
- `recordScan`, `addExtraLine`, `updateExtraLineQuantity`, `upsertDocument` — never delete anything, purely additive.
- `replaceDownloadedDocuments` explicitly checks `hasRecordings` before deleting a header no longer present in a fresh download.

### B.4.1 `mergeDocument` — exact algorithm

```
1. existingRecordings = getByDoc(doc.documentNo, type)
2. lineTiedRecordings = existingRecordings.filter { documentLine != 0 }
3. scannedByLine = lineTiedRecordings.groupBy(documentLine).mapValues(sum quantity)   // mutable
4. extras = existingRecordings.filter { documentLine == 0 }
5. for each extra in extras:
     matchingLines = doc.lines.filter { barcodeNo == extra.barcodeNo }.sortedBy(lineNo)
     if matchingLines.isEmpty(): continue                      // left unresolved, not deleted
     remainingQty = extra.quantity
     for (index, line) in matchingLines.withIndex():
         isLast = index == matchingLines.lastIndex
         alreadyScanned = scannedByLine[line.lineNo] ?: 0.0
         room = max(0, line.expected - alreadyScanned)
         allocation = if (isLast) remainingQty else min(remainingQty, room)
         if allocation <= 0.0: continue
         insert RecordingEntity(documentLine = line.lineNo, quantity = allocation,
                                 barcodeNo = extra.barcodeNo,           // provenance preserved
                                 creationDateTime = extra.creationDateTime,
                                 userId = extra.userId, ...)
         scannedByLine[line.lineNo] += allocation
         remainingQty -= allocation
     deleteByPk(documentLine = 0, recordingLineNo = extra.recordingLineNo)
6. finalRecordings = getByDoc(...)   // re-read
7. state = computeStateAfterMerge(doc.lines, finalRecordings)
8. upsert header with downloaded field values but the *computed* docState
9. deleteAllForDoc (lines table) then upsertAll(doc.lines)   // lines always fully replaced; only recordings carry state
```

Note step 5's ordering: extras are processed in `getByDoc`'s natural order (no explicit `ORDER BY`, effectively PK/insertion order) — with multiple extras sharing a barcode, `scannedByLine` is updated in-place across iterations, so an earlier extra's allocation affects room available to a later extra targeting the same lines.

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

Other `Models.kt` types: `User`, `Location`, `ResponsibilityCenter`, `Item(no, name)`, `Line` (computed `status`), `ExtraLine(recordingLineNo, barcodeNo, quantity, unitOfMeasureCode)`, `Document` (computed `linesExact`, `linesTotal`, `scannedQty`, `expectedQty`, `hasProgress`), `TapeEntry` (scan-log UI model, `isError = lineStatus == null`), `Double.formatQty()` extension.

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

**Domain parsing**: not a separate config field — always embedded in the typed username (`DOMAIN\user` or `user@domain`), split by `ExtSystemODataClient.parseDomainUser` at client-build time.

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

**`app/src/main/assets/ext_system_defaults.json`** (bundled, current dev/test values):
```json
{
  "serverBaseUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/",
  "credentialTtlHours": 168,
  "documentLinesUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/BarcodeAppEntries",
  "documentTypeCodes": {
    "WAREHOUSE_SHIPMENT": "SHIPMENT", "WAREHOUSE_RECEIPT": "RECEIPT",
    "RETAIL_SHIPMENT": "RETAILSHPT", "RETAIL_RECEIPT": "RETAILRCPT", "TRANSPORT_SHEET": "TRANSPORT"
  },
  "locationsUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/LocationList",
  "recordingSyncUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/BarcodeAppRecordings"
}
```
`documentTypeCodes` keys use the enum's `.name` (matched via `dto.documentTypeCodes?.get(type.name)`).

**`prima_config.json`** (repo root) — an all-blank template of the identical shape; not read by any app code, purely a distributable seed for generating a deployment-specific `ext_system_defaults.json`.

**`AppViewModel.parseExtSystemConfigJson(json)`** — deserializes into a private nullable-mirror DTO, applies defaults (`credentialTtlHours ?: 24`, others `.orEmpty()`), never persists directly. `loadExtSystemDefaults()` reads the bundled asset then delegates here. `getExtSystemDefaultsJsonText()` returns the raw bundled text unparsed (for "download as file").

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
| warnNotOnDocument | Boolean | true |
| askQtyForUnknownBarcode | Boolean | true |
| autoUploadCompleted | Boolean | false |
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
) {
    fun docTypeCodeFor(type): String
    val isConfigured get() = serverBaseUrl.isNotBlank()
}
data class ExtSystemCredentials(val username: String, val password: String)
```

### `ExtSystemCredentialStore`
`EncryptedSharedPreferences` (file `ext_system_credentials`), `MasterKey` with `AES256_GCM` (Android Keystore, hardware-backed on API 28+), pref key scheme `AES256_SIV`, value scheme `AES256_GCM`. `save`/`get` (TTL-checked)/`isValid`/`clear` as described in §B.6.2.

### Login flow (end-to-end)
1. `LoginSheet` (`ui/screen/LoginSheet.kt`) — reusable `ModalBottomSheet`, reused by `ExtSystemConfigScreen` (Test connection), `LocationRcPickScreen` (refresh without credentials), `DownloadFilterScreen` (auto-opens if `!hasCredentials`).
2. `AppViewModel.saveCredentials(username, password)` → `extSystemCredentialStore.save(...)`, refreshes the `_credentials` `StateFlow`.
3. `AppViewModel.signOut()` → `extSystemCredentialStore.clear()`, resets `_credentials` to null.
4. `credentials: StateFlow<ExtSystemCredentials?>` is observed in `MainActivity` to derive the current `User` display name (parsed client-side from the username string — no live ERP profile lookup).

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

Two independent input paths feed the same domain logic (`RecordingScreen.handleScan` → `RecordingViewModel`/callbacks → `DocumentRepository.recordScan`/`addExtraLine`).

### B.9.1 Camera path (ML Kit + CameraX)
- **`BarcodeAnalyzer.kt`** — `ImageAnalysis.Analyzer` using `BarcodeScanning.getClient()` (default options, all formats). Picks the **largest bounding box** among detected barcodes (avoids incidental captures of background labels). Debounces repeated identical values (emits only on value change or after `debounceMs`, default 1500ms internal / overridden by the caller's setting). Always `image.close()`s.
- **`CameraPreview.kt`** (`ui/component/`) — binds `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, single-thread executor) to `CameraSelector.DEFAULT_BACK_CAMERA`. Plays a `ToneGenerator` beep on scan; auto-closes unless `continuous=true`; animated scanning-line + corner-bracket overlay; one-shot auto-focus at center (`FocusMeteringAction`, auto-cancel disabled — focus stays locked). Requires `android.permission.CAMERA` (optional — a device can rely solely on the hardware wedge). Camera bind failures are only logged (`Timber.e`), no user-facing error UI.

### B.9.2 Hardware wedge path (Zebra DataWedge)
- **`DataWedgeManager.kt`** — plain Kotlin `object`, talks to the DataWedge service via broadcast intents.
  - `configure(context)` — creates/updates a DataWedge profile `"PrimaBarcode"` scoped to this app's package, configures the `INTENT` output plugin (`intent_action = "com.prima.barcode.SCAN"`, `intent_delivery = "2"`). Called once from `MainActivity.onCreate()`.
  - `setContinuousScan(context, enabled, sameSymbolTimeoutMs=500)` — toggles the `BARCODE` plugin's `aim_type` between single-shot (`"0"`) and continuous/press-and-hold (`"4"`); `same_symbol_timeout` clamped 0–5000ms (tied to `AppSettings.debounceTime`), fixed `different_symbol_timeout="100"`. Reactively invoked via `LaunchedEffect(autoScan, debounceTime)` in `PrimaBarcodeApp`.
  - `createReceiver(onScan)` — `BroadcastReceiver` extracting the scanned string from extra `com.symbol.datawedge.data_string`. Registered with `RECEIVER_NOT_EXPORTED` on API 33+.
  - `intentFilter()` — `IntentFilter("com.prima.barcode.SCAN")`, registered by `RecordingScreen` via `DisposableEffect` for the screen's whole composed lifetime.

### B.9.3 The special format: `BARCODE|UOM|QTY`
Implemented entirely client-side in `RecordingScreen.kt`'s local `handleScan(rawInput: String)` (not in `AppViewModel`/repository):
```kotlin
val barcode = rawInput                                     // ALWAYS the full raw string — matched & stored as-is
val pipeParts = rawInput.split("|")
val parsedQty = if (pipeParts.size == 3) pipeParts[2].toDoubleOrNull() else null
val parsedUom = if (parsedQty != null) pipeParts[1] else null
val matchedLine = doc.lines.find { it.barcodeNo == barcode }
```
- Trigger: **exactly 3 pipe-separated parts**, last part parses as `Double`. Anything else falls through to ordinary handling.
- **Matched line**: `qty = parsedQty ?: matchedLine.scanningQty`. If `parsedUom != null && parsedUom != matchedLine.unitOfMeasureCode` → UoM mismatch dialog (informational only, doesn't block or alter the already-applied scan).
- **Unmatched**: `if (parsedQty != null || !askQtyForUnknownBarcode) { record immediately with qty = parsedQty ?: 1.0 } else { open UNKNOWN_BARCODE quantity screen }` — a valid triplet scan always bypasses the ask-qty setting since the quantity is already known.
- Scan-error UX on unmatched: 600ms red flash of the scan bar background (Slate → `#7A1A1A` → back) plus an error haptic; if `warnNotOnDocument` is on **and** `doc.lines.isNotEmpty()` (manually-created docs with zero expected lines never warn — everything there is definitionally "extra"), the "Not on document" dialog also shows.

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
`ModalBottomSheet`, username/password fields (visibility toggle), TTL info line, submit enabled only when both non-blank; only username is `.trim()`'d, password is sent as-typed.

### `RecordingScreen.kt` — the core scanning workflow (most complex screen)
Internal state machine, `RecordingView` enum: `OVERVIEW, ACTIVE_LINE, KEYPAD, UNKNOWN_BARCODE, EXTRA_LINE, EXTRA_KEYPAD` (full transition table in the Explore-agent research; summarized in User Guide §9). `handleScan` — see §B.9.3. Registers the DataWedge broadcast receiver via `DisposableEffect`. `handleBack()` — per-view back navigation; from `OVERVIEW`, if `autoUploadCompleted` and the doc is fully `EXACT` (and not already `UploadFailed`), shows an "upload now?" dialog instead of leaving. Sub-composables: `OverviewContent`, `ItemQtyDetails`, `ItemQtyExtraDetails`, `ItemQtyNotOnDocDetails`/`ItemQtyNotOnDocExtraDetails`, `UnknownBarcodeContent`, `StatusChip`. **`UnknownBarcodeSheet`** (a bottom-sheet alternate UI for the unknown-barcode flow) is defined in the file but **not wired into the `view` switch** — dead code, kept for a possible future alternate flow.

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
val shiftScans  = filteredDocs.sumOf { d -> d.lines.count { it.scanned > 0 } + d.extraLines.size }  // count of lines-with-progress, not total qty
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
Route-scoped (one instance per `recording/{documentNo}/{type}` navigation entry). Wraps `DocumentRepository`, exposing `document: StateFlow<Document?>` (via `observeDocument`) plus thin suspend wrappers around the repository's scan-mutation functions (`recordScan`, `setLineScanned`, `addExtraLine`, `updateExtraLineQuantity`, `deleteExtraLine`) that `RecordingScreen` calls directly from its callbacks.

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

**Key public functions**: `downloadLocations`, `buildDownloadUrls`, `getLocationsUrl`/`getRecordingSyncUrl`, `realDownloadDocuments`, `saveExtSystemConfig`, `saveCredentials`, `signOut`, `testExtSystemConnection`, `parseExtSystemConfigJson`, `loadExtSystemDefaults`, `getExtSystemDefaultsJsonText`, `uploadToExtSystem`/`uploadInBackground` (both call private `runUpload`), `exportDatabase`, `clearCache` (wipes settings+config+credentials+all documents), `deleteAllDocuments` (wipes only documents/recordings via `repository.clearAll()`), `createDocument` (manual doc creation from the scan-not-found flow), `clearDocumentRecordings`, `clearErrorDocs`.

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

### Theme entry point — a duplicate to be aware of
**Two theme composables exist**, both still present in source:
- **`PrimaTheme.kt` → `PrimaBarcodeTheme(textSizeOffset, uppercaseEnabled, content)`** — the one actually wired into `MainActivity`; provides `LocalTextSizeOffset`+`LocalUppercaseEnabled`, then `MaterialTheme(colorScheme=PrimaLightColors, typography=scaledTypography(...), shapes=PrimaShapes)`.
- **`Theme.kt` → `PrimaTheme(textSizeOffset, content)`** — an older, apparently-superseded duplicate (no uppercase support, different name). **Not referenced from `MainActivity` or anywhere else** — dead code, confirmed still present as of 2026-08.

Do not confuse the two when making theme changes — edit `PrimaTheme.kt`'s `PrimaBarcodeTheme`, not `Theme.kt`.

## B.15 Build & Project Configuration

### `app/build.gradle.kts`
- `namespace`/`applicationId`: `com.prima.barcode`. `compileSdk` 36, `minSdk` 26, `targetSdk` 36. `versionCode` 1, `versionName` "1.0.0". Java/Kotlin target `VERSION_11`.
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

`res/values/strings.xml` (+ `values-hr`/`values-sl`/`values-mk`, all kept in sync, see §B.11.3) contain a handful of keys with **no corresponding UI** in the current screens — most likely remnants of earlier iterations of the login/settings/multiplier flows: `multiplier_*`, `settings_mute*`, `settings_auto_collapse*`, `settings_wifi_only*`, `settings_test_signin*`, `login_domain`. They're harmless (unused string resources cost nothing at runtime) but a candidate for cleanup if you're doing a strings-file pass — just confirm via grep for the exact key before removing, since new UI could reuse one of these names in the future.

Also note: several composables use **hardcoded English label/description strings instead of `stringResource()`** (e.g. some of Settings' newer toggles like "Warn on not on document" and "Ask for Qty for unknown barcode", and `DocumentStatsDashboard`'s "Over" label) — these will **not** translate when the user switches language. If full localization coverage matters, search `SettingsScreen.kt` and `DocumentStatsDashboard.kt` for string literals and move them into `strings.xml` (and the three translated variants) following the existing pattern.

## B.17 Extension Points & Notes for Future Developers

- **Adding a new document type**: add an enum value to `DocumentType` (Models.kt) with a `key`/`display`; add its endpoint/code fields to `ExtSystemConfig`; add a card for it in `ExtSystemConfigScreen`; it will automatically appear in `MainMenuScreen`'s type list and all downstream screens, since they all iterate `DocumentType.entries`.
- **Adding a new AppSettings field**: add to the `AppSettings` data class + `AppSettingsStore` serialization, add local `remember` state + callback wiring in `MainActivity.kt`, and a control in `SettingsScreen.kt`. Remember the buffered-edit pattern — don't persist directly from the toggle, let it flow through `buildSettings()`/exit-save.
- **The upload payload is intentionally flat and per-row**, not batched — if ERP-side batching is ever desired, `AppViewModel.runUpload`'s row loop is the place to change, but note the per-row-immediate-delete behavior is what makes partial-failure retries safe; batching would need an equivalent all-or-nothing safety guarantee.
- **The `documentLine = 0` sentinel** for "extra" recordings is relied upon throughout the repository, mappers, and DAOs — do not repurpose `0` as a real line number in any future NAV schema change without a full search for this convention.
- **`UnknownBarcodeSheet`** in `RecordingScreen.kt` is unused dead code (a bottom-sheet alternate UI for the unknown-barcode flow) — either wire it in as an alternate UX or remove it; left as-is pending a product decision.
- **`NavResponsibilityCenter` DTO** is defined but unused (RCs are derived from Location rows) — if the ERP ever exposes a genuine RC list with richer fields (e.g. using the `short` field), this DTO is ready to wire into `AppViewModel.realDownloadLocations` (or a new `realDownloadResponsibilityCenters`).
- **Chip.kt's `ChipTone`-based shared chip components** exist but most screens build their own inline chip `Box`es instead of using them — a candidate for future UI consolidation, not a bug.
