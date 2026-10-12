# Conflict-free sync and cloud-agnostic storage

> Read when designing multi-device synchronization, file-based cloud syncing (Nextcloud, Syncthing, Google Drive, SSHFS), or concurrent page merge algorithms.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## The sync problem with `.xopp`

MobiXournal intentionally avoids proprietary cloud accounts and backend server locks. Users synchronize
their files across devices (Linux laptop, Android tablet, Windows PC) using third-party sync engines:
**Nextcloud, Syncthing, Google Drive, Dropbox, or SSHFS/WebDAV**.

Under `.xopp`, this model is fragile:
- `.xopp` is a single monolithic gzip XML file.
- If a user jots down notes on Page 1 on their tablet while reviewing Page 10 on their laptop, both sync
  clients save the file concurrently.
- The sync engine flags a **file collision**, producing `.sync-conflict-xxx.xopp`. One of the two sets of edits
  is hidden or requires manual, painful XML diffing.

---

## Conflict-free synchronization in `.mobix`

Because `.mobix` is a structured container with isolated per-page JSON files
(see [mobix-schema-spec.md](mobix-schema-spec.md)), the format turns document-level collisions into
trivially resolvable **granular page operations**.

```text
Tablet (Edits Page 1)                       Desktop (Edits Page 5)
        │                                           │
        ▼ Saves page-0001.json                      ▼ Saves page-0005.json
        │                                           │
        └─────────────────────┬─────────────────────┘
                              ▼
                Three-Way Merge Engine (No Conflict)
                ├── manifest.json: Combined page order
                ├── pages/page-0001.json: From Tablet
                ├── pages/page-0005.json: From Desktop
                └── Result: Single unified document, 0 collisions!
```

---

## Three-way merge resolution algorithm

When a sync conflict file is detected (e.g. `notes.sync-conflict.mobix` alongside `notes.mobix`):

1. **Unpack envelopes**: The merge driver opens both archives and reads their `manifest.json`.
2. **Page-level comparison**:
   - For each page, compare `pageModifiedTimestamp` and SHA-256 hash.
   - If Page $A$ was modified only on Device 1, adopt Device 1's `page-0001.json`.
   - If Page $B$ was modified only on Device 2, adopt Device 2's `page-0002.json`.
3. **Same-page collision (CRDT element merge)**:
   - If the exact same page was modified concurrently on both devices, the engine performs an **Element Merge**:
     - Elements carry a unique `id` (e.g. `stk_9f81`, `plt_02`).
     - Distinct elements are unioned (both sets of strokes appear on the page).
     - Conflicting edits to the same element adopt the latest timestamp (Last-Write-Wins per element).
4. **Clean repack**: The resolved document is repacked into `notes.mobix`, and the conflict marker file
   is safely archived or removed.

### Where the merge runs (and what it is still missing)

The app owns no filesystem watcher, and on Android it cannot: documents arrive as SAF `content://`
URIs, so "a conflict file appeared next to yours" is not an event anyone can subscribe to. The merge
is therefore **invoked, not triggered** — it runs when the user opens a document whose siblings the
grant actually exposes, and again before a save repacks the archive, and the result is a normal save
of the document the user opened (never a silent second file). The fields the comparison needs are
`pages[]` in `manifest.json` ([mobix-schema-spec.md](mobix-schema-spec.md)): per-page `sha256` and
`modified`, plus the element `id`s the same-page CRDT pass keys on.

**Resolved scoping and roadmap tasks:**
- Both Syncthing (`.sync-conflict-YYYYMMDD-HHMMSS-*`) and Nextcloud (`*(conflicted copy YYYY-MM-DD *)*`)
  conflict filename patterns are supported via pattern matching.
- Detection is executed at document open time and prior to re-saving via `DocumentReferenceResolver`
  and SAF sibling probing.
- The losing / incoming conflict file is safely archived (e.g. `.mobix.conflict-merged.bak`) rather than
  silently deleted.
- The user is notified via UI feedback (e.g. snackbar / banner) detailing which pages or elements were merged.
- Tasks broken down in `TODO.toml`:
  1. `recognise-sync-conflict-patterns-for-syncthing-and-nextcloud`
  2. `core-three-way-merge-engine-for-mobix-documents`
  3. `documentio-integration-and-conflict-resolution-ux-notification`

---

## File system atomic staging

To guarantee that sync clients (Syncthing, Nextcloud) never read half-written archives mid-save:

1. **Temporary stage write**: The archive is constructed in `cacheDir/staging_<uuid>.tmp`.
2. **Atomic rename**: On completion, `File.renameTo` (POSIX atomic rename) atomically replaces the target
   `document.mobix`.
3. Sync engines only ever observe fully-formed, valid ZIP archives, eliminating truncated sync artifacts.
