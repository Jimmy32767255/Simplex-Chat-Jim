# Plan: Server Grouping and Notes (TODO item 5)

Date: 2026-10-12
Base version: 7.0.4
Status: implemented

## Problem

The user's server list (`Settings -> Advanced settings -> Network and servers -> Your servers`)
renders one row per server showing only the host address. With several manually added servers the
list is hard to read and impossible to organize. The request (TODO.md item 5) is to let the user put
manually added servers into groups and attach a free-form note to each server.

## Why not store this on `UserServer`

`UserServer` is not a client-only structure. The whole `List<UserOperatorServers>` is serialized
straight into a core command:

```
// model/SimpleXAPI.kt
is ApiSetUserServers -> "/_servers $userId ${json.encodeToString(userServers)}"
```

and the client list is the result of parsing the core's reply (`getUserServers`). The Haskell store
owns the server list; any Kotlin-only field added to `UserServer` would survive at most one
round-trip and then silently disappear (or, worse, make the core command fail to decode). Adding the
metadata to the core would require changes in the pinned `simplexmq` / simplex-chat core, which the
TODO.md portability requirement asks us to avoid for a purely cosmetic feature.

## Solution

Store the metadata in client preferences, keyed by the server address string:

```kotlin
@Serializable data class ServerMeta(val group: String? = null, val note: String? = null)

// AppPreferences
val serverMetadata = mkMapPreference(
  SHARED_PREFS_SERVER_METADATA, mapOf(),
  encode = { json.encodeToString(MapSerializer(String.serializer(), ServerMeta.serializer()), it) },
  decode = { jsonCoerceInputValues.decodeFromString(MapSerializer(String.serializer(), ServerMeta.serializer()), it) }
)
```

- `SharedPreference.state` is a Compose `State`, so the server list recomposes as soon as metadata
  is saved (no extra plumbing).
- `jsonCoerceInputValues` tolerates unknown values on decode, so a future `ServerMeta` with more
  fields stays forward/backward compatible.
- Entries for servers that no longer exist remain in the map but are never rendered; they are
  dropped when a blank metadata is saved for that address.

## Design decisions

- **Keyed by address, not by `serverId`.** `UserServer.serverId` and `remoteHostId` are assigned by
  the core and change when a server is re-added; the address is what the user sees and edits. When
  the user edits the address in `ProtocolServerView`, `saveServerMetadata(previousAddress, address,
  meta)` moves the entry to the new key.
- **Split of the pure part.** `updatedServerMetadata` is a pure function over the map so it can be
  unit tested without preferences; `saveServerMetadata` only wires it to `appPrefs`.
- **Grouped rendering in one place.** `ServersSection` renders one protocol's list (SMP or XFTP):
  servers without a group first in their original order, then groups in first-appearance order
  (stable `sortedBy`, ungrouped = `""`), with a small caption header before each group. The indices
  are kept from the original list so `navigateToProtocolView(i, server, protocol)` still addresses
  the right item.
- **Editable in both modals.** `ServerGroupAndNoteSection` is shared by the add form
  (`NewServerView`) and the edit modal (`ProtocolServerView`), so a server can be grouped when it is
  added or later.
- **Batch add.** When several servers are added at once (TODO item 4), the same group/note is applied
  to every address that was actually added (invalid lines are skipped).
- **Localized text.** New strings are `smp_servers_group_and_note`, `smp_servers_group`,
  `smp_servers_group_placeholder`, `smp_servers_note`, `smp_servers_note_placeholder`,
  `smp_servers_group_and_note_footer` (base + zh-rCN). The footer tells the user the data never
  leaves the device.
- **Not part of `AppSettings`.** Like the desktop Enter-key preferences (TODO item 1), this stays a
  device-local preference and the cross-device backup format is unchanged. The tradeoff is that the
  group/note does not follow a device migration; folding the map into `AppSettings` later is
  possible (nullable fields are backward-compatible) but was out of scope here.

## Steps

1. `model/SimpleXAPI.kt`: add `ServerMeta`, `SHARED_PREFS_SERVER_METADATA`, `serverMetadata`.
2. `ProtocolServersView.kt`: note line in `ProtocolServerViewLink`, new `ServersSection`,
   `serverGroup` / `updatedServerMetadata` / `saveServerMetadata`; use `ServersSection` for the SMP
   and XFTP lists.
3. `ProtocolServerView.kt`: load metadata for the edited server, save it on close, new shared
   `ServerGroupAndNoteSection`.
4. `NewServerView.kt`: metadata state, section in the add form, write metadata for every added
   address in `addServer`.
5. `strings.xml` (base + zh-rCN).
6. Test `updatedServerMetadata` / `serverGroup` in `ServerMetadataTest.kt`.
7. Docs: `product/concepts.md` (PC34), `product/views/settings.md`, `product/rules.md` (RULE-19),
   `spec/api.md` §2.10, `spec/impact.md`, `spec/state.md`.

## Adversarial review

- *Does a missing entry break rendering?* No: `serverGroup` returns `null` and `metadata[address]`
  is `null`, so the row renders exactly as before.
- *Does the metadata leak to the core?* No: it is only read/written through `appPrefs`, and
  `UserServer` is untouched (the FFI/JSON payload is byte-identical to before, so
  `ApiSetUserServers` keeps working).
- *Does the sort break navigation?* `sortedBy` is stable and the `IndexedValue` index is carried
  through, so `navigateToProtocolView` still gets the list index.
- *Can a group header be emitted twice?* Only when the group changes (`group != currentGroup`), and
  `currentGroup` tracks the last emitted group; equal groups are adjacent because of the sort.
- *Is a stale entry a leak?* It is a few bytes in a preference; it is removed whenever the user
  saves a blank group/note for that key and cannot be displayed, since it is keyed by an address
  that no longer exists in the list.
- *Untested UI path?* The composables are not unit-testable in this project (no Compose test
  harness), so the test target is the pure metadata map logic plus the full desktop test run; the
  two clean passes were done by re-reading the final diff.
