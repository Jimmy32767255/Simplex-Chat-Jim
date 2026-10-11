# View and copy profile images (TODO item 6)

Base version: 7.0.4 (android 384, desktop 166, ios 361).

## Problem

TODO item 6: 查看和复制头像（点击群和其他用户头像的时候）.

Profile images are rendered by `ProfileImage` (`views/helpers/ChatInfoImage.kt`) and its wrappers
`ChatInfoImage` / `MemberProfileImage`. None of them is clickable, and there is no way to see the
avatar at full size or to copy the image. Group and contact info pages show the avatar at 192.dp
and the group member list shows it at `MEMBER_ROW_AVATAR_SIZE`, but the image can only be looked at.

## Solution

1. A reusable viewer modal that shows the avatar image and offers "Copy image".
2. `ProfileImage`, `ChatInfoImage` and `MemberProfileImage` accept an optional `onClick`, so each
   call site decides whether its avatar opens the viewer.
3. Wire the avatar in the contact info header, the group info header, the member profile header and
   the group member list rows.
4. Copying the image is platform specific, so it is an `expect fun copyImageToClipboard(image:
   ImageBitmap): Boolean`: desktop puts a `BufferedImage` on the AWT clipboard, Android puts a
   `content://` URI to a PNG written into the app files dir on the clipboard (the existing
   `FileProvider` with authority `"$APPLICATION_ID.provider"` is reused, `files-path` is already
   declared in `android/src/main/res/xml/file_paths.xml`).

## Design decisions

- **One viewer, used by every avatar.** The image is shown with `ContentScale.Fit` in a
  fixed-height box, the title is the chat/member display name. No zooming/panning is added:
  avatars are small and the chat image gallery already covers full photo browsing.
- **Copy is a clipboard image, not a file save.** The TODO says 复制 (copy). Desktop AWT image
  flavor and Android `ClipData.newUri` are both "paste an image into another app" workflows, which
  is what a user expects from copying an avatar. Both return `false` instead of throwing when the
  platform refuses, and the UI then shows a failure toast.
- **Android writes one reusable file.** `getAppFilePath("clipboard_image.png")` is overwritten on
  every copy, so the clipboard does not accumulate temp files that a cleanup pass could delete
  while the clipboard still points at them.
- **No new preference, no `AppSettings` change, no core change.** The feature is read-only for the
  model: it never modifies a profile or sends anything to the server.
- **Clickable avatars are opt-in.** The default parameter keeps the existing behaviour for the
  many other `ProfileImage`/`ChatInfoImage`/`MemberProfileImage` call sites (welcome screen, user
  profile editing, chat list previews, call alerts, …), so no unrelated avatar suddenly becomes a
  button.
- **The chat list / chat item avatars are intentionally left alone** for now: those rows/items have
  their own click action (open the chat, open the message menu), and hijacking the avatar tap there
  would be surprising. The viewer is reachable from every dedicated profile page and from the group
  member list.

## Steps

1. `platform/Images.kt`: `expect fun copyImageToClipboard(image: ImageBitmap): Boolean`.
2. `platform/Images.desktop.kt`: `imageToTransferable` (internal, testable) + AWT clipboard write.
3. `platform/Images.android.kt`: write the PNG into the app files dir, `FileProvider` URI,
   `ClipboardManager.setPrimaryClip`.
4. `views/helpers/ChatInfoImage.kt`: `onClick` on `ProfileImage` and `ChatInfoImage`;
   `ProfileImageViewer` + `showProfileImageModal`.
5. `views/chat/group/GroupMemberInfoView.kt`: `onClick` on `MemberProfileImage`, wired in
   `GroupMemberInfoHeader`.
6. `views/chat/ChatInfoView.kt`: wired in `ChatInfoHeader`.
7. `views/chat/group/GroupChatInfoView.kt`: wired in `GroupChatInfoHeader` and in the member list row.
8. Strings `copy_image`, `image_copied`, `image_copy_failed` in base + zh-rCN.
9. Test `ImageClipboardTest.kt` (desktopTest): the transferable supports `DataFlavor.imageFlavor`,
   returns a `BufferedImage` of the source size, and rejects other flavors. The AWT system clipboard
   itself is not touched, so the test also runs headless. `bufferedImageToTransferable` is split out
   from `imageToTransferable` because the `ImageBitmap` -> AWT conversion loads the Skia natives,
   which the unit test JVM does not have (`LibraryLoadException: Cannot find libskiko-linux-x64.so.sha256`).
10. Full desktop test run.

## Adversarial review

- _Does the modal leak or crash when the image is missing?_ `showProfileImageModal` returns early
  when `image == null`, and `ProfileImage`/`ChatInfoImage` still render their icon when
  `image == null` — no change for icon-only avatars.
- _Does adding `clickable` break the avatar's shape?_ `ProfileIconModifier` clips the image but the
  clickable modifier is applied to the surrounding `Box` in `ProfileImage`, whose bounds are the
  requested square; the tap target therefore matches the avatar box, including the transparent
  padding of the rounded shape. The ripple is suppressed (`indication = null`) so the avatar does
  not flash.
- _Are the member list row and the avatar fighting for the click?_ The avatar is a child of the
  clickable row; Compose gives the child priority, so tapping the avatar opens the viewer and
  tapping anywhere else in the row opens the member profile. Verified by reading the row layout.
- _Is the copied Android URI still readable when the user pastes?_ The file lives in the app files
  dir, not the cache, and is re-created (same path) on every copy, so the URI keeps pointing at a
  readable file. `FileProvider` has `grantUriPermissions="true"`.
- _Does the copy stay correct for a remote-host profile picture path?_ It never uses the file path:
  the bytes are regenerated from the base64 profile image, so remote host configuration is
  irrelevant.
- _Do the new platform functions break any target?_ The multiplatform module builds only
  `androidTarget()` and `jvm("desktop")`, and both actuals are provided.
