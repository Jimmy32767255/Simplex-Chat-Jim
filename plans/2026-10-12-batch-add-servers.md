# Add several servers at once in "New server"

## Problem

Settings → Advanced → Network and servers → Your servers → Add server →
Enter manually opens `NewServerView`, whose address field accepts exactly one
server address:

- `CustomServer` (`ProtocolServerView.kt`) validates the whole field with a
  single `parseServerAddress(...)?.valid`, and
- `addServer` (`NewServerView.kt`) feeds that same single string to
  `serverProtocolAndOperator`, appends one `UserServer` and stops.

TODO.md item 4 (`批量添加服务器`) asks for entering several addresses at once,
one per line, with every server added in that batch sharing the settings
chosen in the form (i.e. the "Use server for new connections" toggle and the
protocol/operator detection).

`TextEditor` already allows newlines (`singleLine = false`), so the field
itself needs no new widget – only the parsing, validation and add path do.

## Solution summary

Keep one address field and one set of settings. Treat the field as a
newline-separated list of addresses:

- `NewServerView` passes `allowMultipleAddresses = true` to `CustomServer`.
- `CustomServer` validates *every* non-blank line, hides the single-address
  QR code and disables the "Test server" row when more than one address is
  entered (testing a batch is ambiguous; servers can still be tested from the
  list afterwards), and shows a hint footer describing the syntax.
- `addServer` splits the field into distinct trimmed non-blank addresses and
  appends one `UserServer` per address, each one derived from the same
  template `server` value, so all of them share the entered settings.
  Operator/protocol detection runs per address, so addresses belonging to
  different operators land in the right operator's list.

Single-address behaviour is unchanged: an empty field still just closes the
dialog, and one address takes exactly the old path (same alerts, same
`tested` flag). Relay addresses are not affected – `NewChatRelayView` keeps
its own single-address form.

## Design decisions

- **Split in `addServer`, not in the editor.** `CustomServer` is shared with
  the edit-server flows (`ProtocolServerView`), where `UserServer.server` must
  stay a single address. `allowMultipleAddresses` defaults to `false`, so the
  editing paths are byte-for-byte unchanged; only `NewServerView` opts in.
- **Partial success is allowed, with an alert.** A typo on line 7 of a
  ten-line paste must not silently discard lines 1–6, and must not block the
  valid ones either. Valid addresses are added, invalid ones are listed in
  the existing `smp_servers_invalid_address` alert.
- **`tested = null` when batched.** The template may carry a `tested` flag
  from a single-address test; applying it to every address of a batch would
  be a lie, so only the one-address case keeps it.
- **No new preference, no core change.** Servers are still added by mutating
  the `userServers` state; `YourServersView` already validates reactively and
  the existing save path persists the list.

## Steps

1. `NewServerView.kt`: add `serverAddresses(String): List<String>` helper;
   rewrite `addServer` to loop over the split addresses, keeping the old
   alerts (`operator_server_alert_title` / `server_added_to_operator__name`,
   `smp_servers_invalid_address`, `error_adding_server`).
2. `ProtocolServerView.kt`: add `allowMultipleAddresses: Boolean = false` to
   `CustomServer`; make `valid` line-aware in that mode; add the hint footer;
   hide QR / disable test row when multiple addresses are present.
3. `NewServerView.kt`: pass `allowMultipleAddresses = true`.
4. Strings: `smp_servers_new_server_multiple_hint` in `base` and `zh-rCN`.
5. Docs: `product/views/settings.md`, `product/concepts.md`,
   `spec/impact.md`, `spec/api.md` (if needed) and `TODO.md` item 4.
6. Test with `./gradlew :common:desktopTest`, then commit and export the diff
   patch to `diff/`.

## Adversarial review

- *Does the multiline text leak into `UserServer.server`?* Yes, while the
  dialog is open, but `addServer` splits before creating the stored values, so
  no `UserServer` with newlines is ever stored.
- *Does `CustomServer`'s `LaunchedEffect` still sync correctly?* It copies the
  whole text and the per-text `tested` memory; unchanged in both modes.
- *Could a batch add a server to the wrong operator?* Operator matching is
  per address via `serverProtocolAndOperator`, and the list is re-read from
  `userServers.value` after every append, so consecutive addresses see the
  previous insertions.
- *Does hiding the QR code lose functionality for a single address?* No,
  `multipleAddresses` is only true when at least two non-blank lines exist.
