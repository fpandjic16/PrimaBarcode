# PrimaBarcode — User Guide

**Audience:** warehouse and store staff who use the PrimaBarcode app day-to-day to scan and record inventory movements (shipments, receipts, transfers).

This guide explains what every screen does, what every button and toggle means, and what to do when something looks wrong. It assumes no technical background — if you can use a smartphone, you can use this guide.

---

## Table of Contents

1. [What This App Does](#1-what-this-app-does)
2. [Key Concepts You'll See Everywhere](#2-key-concepts-youll-see-everywhere)
3. [First-Time Setup](#3-first-time-setup)
4. [The Main Menu](#4-the-main-menu)
5. [Choosing Your Location & Responsibility Center](#5-choosing-your-location--responsibility-center)
6. [Document Types](#6-document-types)
7. [The Document List](#7-the-document-list-orders--recordings--errors)
8. [Downloading Documents](#8-downloading-documents)
9. [Scanning a Document (Recording Screen)](#9-scanning-a-document-recording-screen)
10. [Understanding Warnings You May See](#10-understanding-warnings-you-may-see)
11. [The Special Barcode Format (Barcode\|UoM\|Qty)](#11-the-special-barcode-format-barcodeuomqty)
12. [Uploading Documents](#12-uploading-documents)
13. [Fixing Upload Errors](#13-fixing-upload-errors)
14. [The Dashboard (Document Overview)](#14-the-dashboard-document-overview)
15. [Filtering Documents](#15-filtering-documents)
16. [Settings — Every Option Explained](#16-settings--every-option-explained)
17. [Signing In and Out](#17-signing-in-and-out)
18. [Common Situations & What To Do](#18-common-situations--what-to-do)
19. [Glossary](#19-glossary)

---

## 1. What This App Does

PrimaBarcode is a handheld scanning app used to record what's physically been picked, received, or moved in a warehouse or store, and to send that information back to the company's central business system (Dynamics NAV / Business Central) once you're done.

The everyday cycle looks like this:

```
1. DOWNLOAD  — pull the list of documents (orders) you need to work on
2. SCAN      — walk the warehouse, scanning barcodes against each document's lines
3. UPLOAD    — send your completed (or partial) work back to the central system
```

You can also scan **before** downloading — if you're offline or start work before the office has released the document, your scans are kept locally and automatically matched up once you download the real document (see [§18.3](#183-i-scanned-before-the-document-was-downloaded)).

---

## 2. Key Concepts You'll See Everywhere

### 2.1 The four scan statuses (color language)

Every line on a document — and the document as a whole — is always in one of four states, shown with a consistent color everywhere in the app:

| Status | Meaning | Color |
|---|---|---|
| **Empty** | Nothing scanned yet (0 of expected quantity) | 🔴 Red |
| **Partial** | Some, but not all, of the expected quantity scanned | 🟠 Orange |
| **Ready** (internally "Exact") | Scanned quantity exactly matches expected quantity | 🟢 Green |
| **Over-qty** | You've scanned *more* than the expected quantity | 🔵 Blue |

A document's overall status follows simple rules:
- If **any single line** is Over-qty, the **whole document** shows as Over-qty — even if every other line is perfect.
- A document only shows **Ready** (green) when every line is exact **and** there are no "not on document" scans left unresolved.
- Otherwise it shows **Partial**.

### 2.2 Document lifecycle

Behind the scenes, every document also moves through a lifecycle as you work on it:

`Downloaded → In Progress → Completed → (Pending Upload) → gone (on successful upload)`

If an upload fails, the document instead becomes **Upload Failed**, shown on the **Errors** tab, and stays on your device until you retry (or the office fixes the underlying problem).

### 2.3 "Not on document" lines

If you scan a barcode that isn't listed as an expected item on the current document, the app doesn't reject it — it records it separately as an **extra / not-on-document line**, clearly marked in orange, so nothing you scan is ever silently lost. You (or the office) can review and reconcile these later.

---

## 3. First-Time Setup

When you first open the app:

1. You are **not** required to sign in immediately — signing in only happens the first time you try to **Download** or **Upload** data (or refresh Locations/RCs), via a **sign-in sheet** that slides up from the bottom.
2. Enter your **Username** (e.g. `user@prima` or `DOMAIN\user`) and **Password**, then tap the button shown (its label changes depending on what triggered it — "Sign in", "Test connection", or "Sign in & Sync").
3. Your session stays signed in for a configured period (commonly 24 hours, sometimes longer — shown under the password field, e.g. *"Session stored encrypted for 24 hours"*). After that period, you'll simply be asked to sign in again next time it's needed — your credentials are encrypted on the device the whole time.
4. Pick your **Responsibility Center** and **Location** (see [§5](#5-choosing-your-location--responsibility-center)) — this tells the app which warehouse/store you're working from and filters everything you see to that scope.

---

## 4. The Main Menu

This is the home screen you land on every time you open the app.

- **Top bar**: shows "PRIMA BARCODE" and your name (once signed in). A gear ⚙️ icon top-right opens **Settings**.
- **Today card** (tap it anywhere): a summary of today's activity — total lines scanned, and counts of Ready / Partial / Over / Error documents. Tapping this card opens the **Dashboard**.
- **Location/RC bar**: two side-by-side pills showing your current Responsibility Center code and Location code. Tap **either one** to open the Location & RC picker and change them.
- **Document type list**: one row per document type (Warehouse Shipment, Warehouse Receipt, Retail Shipment, Retail Whse. Receipt, Transport Sheet), each showing:
  - An icon and the type's name.
  - A thin colored mini-bar (if there's any activity) previewing the mix of statuses across that type's documents.
  - A count of how many documents of that type you currently have, **or** a 🔒 lock icon if that type is currently blocked (e.g. no location selected yet, or the type has no matching reference data). Locked rows are dimmed and can't be tapped.
  - Tapping an unlocked row opens that type's **Document List**.

---

## 5. Choosing Your Location & Responsibility Center

Open this screen by tapping either pill on the Main Menu.

- **Responsibility Center** row — tap to open a searchable list of RCs. Type to filter by name or code. If you leave the search blank, an extra **"Any responsibility center"** option appears at the top to clear your selection.
  - ⚠️ **Switching RC clears your currently selected Location** — you'll need to pick a location again, since locations belong to a specific RC.
- **Location** row — tap to open a searchable list of locations (already narrowed to your selected RC, if any). Selecting a location also automatically updates the RC to match, if you hadn't already set one.
- **Refresh icon** (top-right): pulls a fresh list of locations/RCs from the central system. If you're not signed in, it will ask you to sign in first (button reads "Sign in & Sync"). While refreshing, a small spinner replaces the icon. The subtitle under the title shows when the list was last synced, e.g. *"Synced 07.08.2026 · 14:32"*.
- **Apply** button at the bottom confirms your selection and returns you to where you came from.

---

## 6. Document Types

| Type | What it represents |
|---|---|
| **Warehouse Shipment** | Goods going out from a warehouse to a store |
| **Warehouse Receipt** | Goods coming in from a supplier into a warehouse |
| **Retail Shipment** | Goods going out from a store to a customer |
| **Retail Whse. Receipt** | Goods/returns coming in from a store back to a warehouse |
| **Transport Sheet** | Inter-location transfer documents |

Which of these you see (and whether they're filtered by Location or by Responsibility Center) depends on how your company has configured the app — ask your administrator/consultant if a type behaves unexpectedly.

---

## 7. The Document List (Orders / Recordings / Errors)

Reached by tapping a document type on the Main Menu. Shows all documents of that type, split into three tabs:

| Tab | Shows |
|---|---|
| **ORDERS** | Documents you haven't finished yet — freshly downloaded, or in progress |
| **RECORDINGS** | Documents that are complete, failed to upload, or have any scanning activity at all |
| **ERRORS** | Documents that failed to upload |

**Each document row** shows: document number, a colored status chip (Ready/Partial/Empty/Over-qty), the source location, and the document date. If it's in the Errors tab, an extra red "ERROR" chip appears, along with the failure reason.

**Scanning/typing a document number** in the scan bar at the top jumps straight to that document if it exists, or offers to **create a new document** with that number if it doesn't (useful for offline scanning before a document has been officially released — see [§18.3](#183-i-scanned-before-the-document-was-downloaded)). Creating a document requires you to have a location selected.

**Deleting a document's recordings**: on the **Recordings** tab, press and hold a row for about 5 seconds (you'll see a progress ring fill in). This opens a confirmation to wipe all scans for that document and reset it back to its downloaded state — use this if you need to start a document over from scratch. Releasing early cancels the hold.

**Bottom buttons** change per tab:
- **Orders**: `DOWNLOAD` and `UPLOAD`.
- **Recordings**: a single full-width `UPLOAD`.
- **Errors**: `CLEAR ERRORS` (removes failed documents from your device without uploading them — use with care) and `UPLOAD` (retries them).

The funnel/filter icon (top-right) turns **coral/orange** when a filter is currently active — see [§15](#15-filtering-documents).

---

## 8. Downloading Documents

From the **Orders** tab, tap **DOWNLOAD**.

1. If you're not signed in, a sign-in sheet appears automatically first.
2. You'll see a filter screen where you can narrow what gets pulled down:
   - **Document Date** — From/To date range.
   - **Destination Code** — free text.
   - **Source Code** or **Responsibility Center** — depending on how this document type is configured, you'll see *one* of these two as a picker (the other is locked to your current selection and hidden).
3. Tap **RESET** to clear the filter fields, or **OK** to start the download.
4. A progress indicator shows while documents are pulled in. If anything fails, you'll see an error message listing what went wrong.

**Important**: downloading **never deletes your existing scans**. If you already have local progress on a document that's no longer in the new download (e.g. it was completed and removed on the server side), it's kept safely on your device rather than silently discarded — it just won't appear in Orders until it's addressed.

---

## 9. Scanning a Document (Recording Screen)

This is the core screen — opened by tapping any document from a list.

### 9.1 Overview

You'll see:
- A subtitle showing the document number, how many lines are exact out of total, and (if configured) the document type code.
- A summary bar: source/destination codes on the left, total scanned/expected on the right (turns green once everything is exact).
- A thin progress bar summarizing every line's status at a glance.
- The full list of expected lines — item number, item name, and a large scanned/expected counter in that line's status color.
- If you have any "not on document" scans, they appear below in a clearly marked orange section.
- At the bottom: a **scan bar** to type/scan a barcode, and a collapsible **"LAST SCANS" tape** showing your most recent scans.

### 9.2 Scanning

You can scan in three ways:
1. **Hardware scanner trigger** (if your device has one) — just point and scan; it works anywhere on this screen.
2. **Scan bar** at the bottom — tap the keyboard icon to type a barcode manually if needed, or the camera icon to scan visually.
3. **Camera** — tap the camera icon in the scan bar; point your camera at the barcode. It beeps and vibrates on a successful read. Unless "Continuous scanning" is on in Settings, the camera closes itself after one successful scan.

**What happens when you scan:**
- **Matches a line** → that line's scanned quantity goes up (usually by 1, or by whatever the document specifies per scan), and the line/document status updates live.
- **Doesn't match any line** → it's recorded as an extra "not on document" line (see [§2.3](#23-not-on-document-lines)), and the scan bar briefly flashes red so you know it didn't match a real line. Depending on your Settings, you may instead be asked to type in a quantity first — see [§16](#16-settings--every-option-explained).

### 9.3 Editing a line manually

Tap any line to open its detail view, where you can:
- Use **−1 / +1** buttons for quick adjustments.
- Tap the big number to open a **numeric keypad** and type an exact quantity, then confirm.
- Tap **Apply** to save your change and return to the overview.

The same pattern applies to "not on document" lines, except the confirm button there reads **Remove** (in red) if you set the quantity down to zero, or **Apply** (in orange) otherwise.

### 9.4 Uploading from here

If there's any scanning activity on the document, a small **Upload** button appears in the top bar, letting you upload without going back to the list.

### 9.5 Leaving the screen

Tapping back normally just takes you back to the document list. However, if the **"Auto-upload completed docs"** setting is on and the document is fully Ready, you'll instead be asked **"This document looks finished. Upload it now?"** — choose **Yes** to upload immediately, or **No** to leave without uploading (you can always upload later).

---

## 10. Understanding Warnings You May See

| Warning | When it appears | What to do |
|---|---|---|
| **Over-scanned** | You scanned more than the expected quantity for a line (if the "Warn on over-scan" setting is on) | Just informational — tap OK. The extra quantity is still recorded (shown as blue "Over-qty"). |
| **Not on document** | You scanned a barcode that isn't an expected item on this document (if "Warn on not on document" is on, and the document actually has expected lines) | Informational — the scan was still recorded as an extra line. Tap OK. |
| **Unit of measure mismatch** | You used the [special barcode format](#11-the-special-barcode-format-barcodeuomqty) and the unit of measure you scanned doesn't match what the document expects for that item | Informational — the scan was still recorded with the quantity you scanned. Tap OK, and flag it to the office if it looks like a real discrepancy. |
| **Document finished** | You try to leave a fully-scanned document (only if "Auto-upload completed docs" is on) | Choose whether to upload now or later. |

None of these warnings block or undo your scan — they're all "heads up" notices. The scan has already been recorded by the time you see the dialog.

---

## 11. The Special Barcode Format (Barcode\|Uom\|Qty)

Some printed labels encode more than just the item — they can carry a unit of measure and quantity directly in the barcode, separated by pipe characters (`|`), for example:

```
NTR1234|M|5.6
```

This means: barcode `NTR1234|M|5.6` (the **whole string** is the barcode used for matching — not just the `NTR1234` part), unit of measure `M`, quantity `5.6`.

- If this format is recognized (exactly two `|` characters, with a valid number at the end), the app records **exactly that quantity** — you won't be asked to type one in, even if your Settings normally ask for quantity on unrecognized barcodes.
- If the unit of measure encoded in the label doesn't match what the document expects for that item, you'll see the **Unit of measure mismatch** warning (see above) — but the scan is still recorded as read.
- If the barcode doesn't fit this exact pattern, it's treated as an ordinary barcode.

**Note**: because some barcode label types (Code 39) cannot physically encode the `|` character, labels using this format must be printed as **Code 128** (or another symbology that supports it). If a special-format barcode scans as garbled text, check the label's barcode type with the office/print team.

---

## 12. Uploading Documents

Tap **UPLOAD** from any document list (or the in-screen upload button while scanning). This sends your recorded scans back to the central system.

- If you're not signed in, you'll be asked to sign in first.
- Depending on how your app is configured, upload either runs in the **background** (you can keep working immediately while it sends) or shows a **blocking progress screen** until it finishes.
- On success, the document is completely removed from your device — you're done with it.
- If something goes wrong (server error, network issue, missing configuration), the document moves to the **Errors** tab instead, with the specific reason recorded — nothing is lost, and only the parts that failed need to be resent (anything that did succeed before the failure is not resent).

---

## 13. Fixing Upload Errors

Open the **Errors** tab (from a Document List, or the Dashboard) and tap a failed document to see:

- A clear "Upload Failed" header.
- Document details (type, source, destination, RC, line count, date).
- The **full error message** returned by the central system — this is the most useful thing to relay to IT/support if the problem isn't obvious (e.g. "not signed in", "server rejected the request", a specific business-system validation message).
- A **Retry Upload** button at the bottom to try again — useful once the underlying issue (network, server, sign-in) has been resolved.

You can also **Clear Errors** in bulk from a document list's Errors tab or the Dashboard's Errors tab — this **removes the failed documents from your device without uploading them**. Only do this if you're sure the data doesn't need to reach the central system (e.g. it was a duplicate, or a test document).

---

## 14. The Dashboard (Document Overview)

Reached by tapping the "Today" card on the Main Menu. A cross-document-type view with three tabs:

- **Errors** — every failed document, across all types.
- **My Location** — every document at your current location/RC, across all types.
- **All** — every document on the device, no filtering.

An empty tab shows a friendly green checkmark and "No issues" rather than a blank screen. If there's anything with scan progress on the current tab, an **Upload** button appears at the bottom to send it all at once.

---

## 15. Filtering Documents

The funnel icon (top-right of a Document List or the Dashboard) opens a filter screen where you can narrow down what's shown:

- **Status** — pick one or more of Empty / Partial / Ready / Over-qty.
- **Document Type** — pick one or more types (only shown on screens that span multiple types).
- **Document Date** — From/To range (picking a "From" date after "To", or vice versa, automatically adjusts the other to stay valid).
- **Destination Code**, **Source Code**, **Responsibility Center** — free text or pick-from-list, depending on context (some may be locked/pre-filled if you arrived here from a location-specific view).

Tap **RESET** to clear everything, or **APPLY** to confirm. The funnel icon turns coral/orange whenever a filter is currently active, so you always know at a glance if you're looking at a filtered (not full) list.

---

## 16. Settings — Every Option Explained

Open Settings via the gear icon on the Main Menu. **Settings are buffered** — nothing is saved until you leave the screen; if you've made changes, you'll be asked **"Save your settings before leaving?"** with **Yes** (save) / **No** (discard everything you just changed).

### Appearance
| Setting | What it does |
|---|---|
| **Text size** | Scales all text in the app up, for readability. |
| **Uppercase text** | Displays all interface labels in CAPITALS. |
| **Language** | App display language. A restart may be needed for the change to fully apply everywhere. |

### Scanning
| Setting | What it does |
|---|---|
| **Continuous scanning** | Keeps the camera open to scan multiple items in a row, instead of closing after each scan. |
| **Debounce time** | Minimum time between camera scans in continuous mode (200ms–2s) — prevents the same label being scanned twice by accident. |
| **Haptic feedback** | Vibrate on scan confirmation and errors. |
| **Warn on over-scan** | Show a warning when you scan more than the expected quantity for a line. |
| **Warn on not on document** | Show a warning when you scan a barcode that isn't an expected item on the document. |
| **Ask for Qty for unknown barcode** | When **on**: scanning an unrecognized barcode opens a quantity-entry screen so you can specify how many. When **off**: it's recorded immediately with quantity = 1, no screen shown. (A barcode using the special `Barcode\|UoM\|Qty` format always records its own quantity directly, regardless of this setting.) |
| **Last scanned lines** | How many recent scans are shown in the "LAST SCANS" tape while scanning (0 hides it entirely, up to 5). |

### Sync
| Setting | What it does |
|---|---|
| **Auto-upload completed docs** | Prompts you to upload immediately when you finish (fully scan) a document and try to leave it. |
| **Enable background sync** | Uploads run in the background so you can keep working instead of waiting on a progress screen. |

### External System Configuration
A single row, **"Server & endpoints"**, that opens the connection settings for the central system — this is normally only touched by IT/consultants during setup. See the Technical Guide for details.

### Debug
| Item | What it does |
|---|---|
| **Debugger active** | Shows the exact web addresses the app is about to contact before every download/upload — useful only for diagnosing connectivity issues with IT support. Leave off for normal daily use. |
| **Export data** | Saves a full dump of everything on your device to a file — useful if IT support asks for diagnostic data. |
| **Insert system defaults** | Lets IT/consultants load, download, or import a starter configuration for the External System connection. Not something you'd normally use day-to-day. |
| **Clear cache** *(red — destructive)* | Deletes **everything**: your sign-in, all settings, all documents and scans. Requires confirmation. Use only when told to by support (e.g. handing the device to a different user). |
| **Delete all documents and recordings** *(red — destructive)* | Deletes all downloaded documents and scans, but **keeps** your settings and sign-in. Requires confirmation. Use this to fully reset your working data without having to sign in again. |

### System Info
Read-only technical details (app version, Android version, database version) — useful when reporting a bug to support.

### Account
Shows who's currently signed in, and a **Sign out** option (immediate, no confirmation needed).

---

## 17. Signing In and Out

- You're prompted to sign in automatically the first time the app needs to talk to the central system (download, upload, or refresh locations).
- Your credentials are stored encrypted on the device and expire automatically after a set period — you'll just be asked to sign in again when that happens, nothing is lost.
- To sign out manually, go to **Settings → Account → Sign out of External System**.

---

## 18. Common Situations & What To Do

### 18.1 "The document type is locked with a padlock icon"
You likely don't have a location selected, or your company hasn't configured that document type yet. Pick a location ([§5](#5-choosing-your-location--responsibility-center)) and try again; if it's still locked, contact your administrator.

### 18.2 "I scanned the wrong quantity"
Tap into the line and either use −1/+1, or tap the number to type the correct total directly — this replaces the line's recorded quantity, it doesn't add to it.

### 18.3 "I scanned before the document was downloaded"
That's supported. Type/scan the document number in the Document List's scan bar; if it doesn't exist yet, you'll be offered to create it, and you can start scanning against it right away. Once the real document is later downloaded, your scans are automatically matched onto the correct lines by barcode — no work is lost or needs to be redone. (If a barcode matches more than one line, the app fills lines in order until the full scanned quantity is placed.)

### 18.4 "A document I was working on disappeared from Orders"
Check the **Recordings** tab — if you'd already made progress on it, it lives there instead once it's Complete, or moves to Errors if an upload attempt failed.

### 18.5 "My upload failed"
Open the document from the **Errors** tab to read the exact reason, then tap **Retry Upload**. If the message isn't clear (e.g. a technical server error), relay the exact text to IT support.

### 18.6 "I want to start a document completely over"
On the Recordings tab, press and hold the document row for ~5 seconds and confirm — this wipes all scans for that document and resets it to its freshly-downloaded state.

### 18.7 "The barcode scanned as garbage/wrong characters"
If it's a special `Barcode|UoM|Qty` label, verify with the office that it was printed as **Code 128** — the older Code 39 symbology cannot represent the `|` character correctly and will scan as gibberish.

---

## 19. Glossary

| Term | Meaning |
|---|---|
| **RC (Responsibility Center)** | An organizational grouping (e.g. a region or business unit) that a location belongs to. |
| **Location / Source Code** | The specific warehouse or store you're working from. |
| **Destination Code** | Where a document's goods are headed. |
| **Extra / Not-on-document line** | A scan that didn't match any expected item on the document — recorded separately, shown in orange. |
| **Downloaded** | A document pulled from the central system but not yet worked on. |
| **In Progress** | A document with some, but not all, scanning done. |
| **Completed** | Every line scanned exactly, no leftover extras. |
| **Pending Upload** | Currently being sent to the central system. |
| **Upload Failed** | The send attempt didn't succeed — see the Errors tab for the reason. |
