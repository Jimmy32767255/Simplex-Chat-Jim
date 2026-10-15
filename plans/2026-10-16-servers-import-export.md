# Import and export of custom servers (based on 7.0.4)

## Problem

TODO item 12 (m00648): the user wants to export their own (custom) servers to a JSON file so the
configuration can be backed up or moved to another device, and import such a file back. The export
must warn that the file contains server verification keys and addresses that can leak; the import
must warn that only trusted servers should be imported.

## Solution

Pure logic in a new file `views/usersettings/networkAndServers/ServersBackup.kt`:

- `ServersBackup(version, smpServers, xftpServers)` / `ServersBackupItem(server, enabled, tested,
  roles, operator, group, note)` — `@Serializable`, JSON, pretty-printed.
- `serversBackupOf(userServers, metadata)` — collects every non-preset, non-deleted SMP/XFTP server
  of the whole profile (all operator groups), tagging each with its `OperatorTag` and with the
  client-side group/note from `serverMetadata`.
- `exportServersBackup(backup): String` / `parseServersBackup(json): ServersBackup?`.
- `mergeServersBackup(current, backup, parseAddress)` — validates every address, skips duplicates and
  invalid addresses, appends to the operator group matching the item's `OperatorTag` (falling back
  to the group without an operator, which is created if missing), and returns the new list plus what
  was added/skipped.
- `importedServerMetadata(current, added)` — writes the imported groups/notes back into the
  client-side metadata map.

UI: `NetworkAndServers.kt` gets a new `ServersImportExportSection` between the "message servers"
section and the "save servers" section, with two rows:

- **Export servers** — confirm dialog carrying the leak warning, then the platform save dialog,
  then the JSON is written with `copyBytesToFile` (same as theme export).
- **Import servers** — confirm dialog carrying the trust warning, then the platform open dialog;
  after parsing, a second confirm dialog summarises how many servers will be added and how many are
  duplicates/invalid; on confirm the merged list is persisted with `saveServers`.

## Design decisions

- Only non-preset servers are exported: preset servers come from the client/operator configuration
  and change between versions, so exporting them would only create duplicates on import.
- The `parseAddress` parameter of `mergeServersBackup` defaults to `ServerAddress.parseServerAddress`
  but is injectable, because the core function behind it (`chatParseServer`) is an external call
  that cannot be loaded in the desktop unit tests.
- Group and note are included, because they are part of the user's custom-server configuration
  (item 5); they are written back into `serverMetadata` on import.
- Import merges into the existing list instead of replacing it, and reuses the existing
  `saveServers` path so the core, `currUserServers` and `userServers` stay in sync.
- No changes to the `AppSettings` cross-device backup format and no core (Haskell) changes, same as
  items 1, 4, 5, 6, 8 and 10.

## Steps

1. Add `ServersBackup.kt` with the pure logic and JSON (de)serialisation.
2. Add the new strings to `MR/base/strings.xml` and `MR/zh-rCN/strings.xml`.
3. Add `ServersImportExportSection` and call it from `NetworkAndServersLayout`.
4. Add `common/src/commonTest/kotlin/chat/simplex/app/ServersBackupTest.kt` covering export
   filtering, JSON round-trip, operator/nil-operator merging, duplicates, invalid addresses and
   metadata round-trip.
5. Update `product/views/settings.md`, `product/concepts.md` (PC38), `product/rules.md` (RULE-23),
   `product/README.md`, `spec/impact.md` and `spec/api.md`.
6. Run `./gradlew :common:desktopTest`, then commit and store the diff as
   `diff/0009-servers-import-export.patch`.

## Adversarial review

- *Does importing replace the user's servers?* No — it appends to the existing list; only rows that
  do not exist yet are added, and the result is saved through the normal save path.
- *Can an import smuggle in a preset server?* Imported rows are always created with `preset = false`
  and `deleted = false`, so they always behave like manually added servers.
- *What if the file contains an address of the other protocol?* A `smp://` address inside
  `xftpServers` (or the reverse) is reported as invalid and skipped, matching the list it was
  exported from.
- *What if the `OperatorTag` of an imported server does not exist anymore?* The server goes to the
  group without an operator instead of being dropped.
- *Is the export warning real?* Yes — the address string carries the server key hash and (when set)
  the basic-auth password, so the file must be treated as a secret.
