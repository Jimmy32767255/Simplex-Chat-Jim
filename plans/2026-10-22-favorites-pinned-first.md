# Favorite chats pinned to the top of the home chat list

Date: 2026-10-22
Base version: 7.0.4
Request: add a setting switch that treats favorites as pinned — when it is on, favorited contacts and
groups are always shown at the top of the home chat list.

## Problem

The chat list is ordered by last activity only. Favorited chats are just a filter (the star tag) and the
star icon on the row, so a favorite that rarely gets new messages sinks to the bottom of the list.

## Solution

A new client-side boolean preference `AppPreferences.favoritesAsPinned` (default off) with a switch in
Settings → **Chat list**, and a stable reorder of the filtered chat list in `ChatListView`.

## Design decisions

- **Display order only.** The order of `ChatModel.chats` (core order + the `ChatsContext` recent-activity
  pops) is the source of truth and stays untouched; the reorder happens in the composable on the list
  returned by `filteredChats`. Nothing is sent to the core, nothing is stored per chat, and the shipped
  `AppSettings` back-up format is not extended — same portability stance as the other TODO items.
- **"Favorite" is the existing flag.** `chatInfo.chatSettings?.favorite` is what the favorites filter and
  the star icon already use, so no new per-chat state and no core command is needed. Contacts and groups
  are treated identically.
- **Stable partition.** `chats.partition { isFavoriteChat(it) }` keeps the relative order inside both
  halves, so message activity can reorder within a half but never move a favorite below a non-favorite,
  and two favorites keep their activity order among themselves.
- **One place applies it.** Only `ChatListView` calls `favoritesPinnedFirst`; the share sheet and other
  users of `filteredChats` are unaffected, which keeps the change minimal and predictable.
- **Pure helpers, unit-testable.** `isFavoriteChat` and `favoritesPinnedFirst` are top-level `internal`
  functions in `ChatModel.kt`, so they can be tested without Compose.

## Steps

1. Add `favoritesAsPinned` to `AppPreferences` (`mkBoolPreference`, default `false`) with its own
   `SHARED_PREFS_*` constant.
2. Add `isFavoriteChat` / `favoritesPinnedFirst` to `ChatModel.kt`.
3. Apply the reorder in `ChatListView` next to `filteredChats`, reading the preference state so the list
   recomposes when the switch is toggled.
4. Add a "Chat list" section with the switch and a footer to `SettingsLayout`.
5. Add the strings (`settings_chat_list`, `favorites_as_pinned`, `favorites_as_pinned_footer`) to the base
   and zh-rCN string files.
6. Unit-test the helpers (favorites moved, order preserved in both halves, no-op when off, both chat
   kinds, chat without settings, empty list).

## Adversarial review

- *Does it fight the pop-to-top behaviour?* No: pops reorder `ChatModel.chats`, and the partition is
  applied afterwards on the fresh list, so within the non-favorite half the activity order is still
  correct, and a favorite that receives a message stays on top.
- *What about the search results?* The same order is applied to them, which is consistent with "always at
  the top"; it is documented rather than special-cased.
- *Does it affect unread/other filters?* It only reorders whatever the filters return, so a filter that
  already shrank the list is reordered the same way.
- *Does it hide any chat?* No, it is a reorder: the same set of chats is shown.
- *Android/desktop consistency?* The preference is platform independent (both platforms use the same
  settings page), unlike the desktop-only message-sending section.
