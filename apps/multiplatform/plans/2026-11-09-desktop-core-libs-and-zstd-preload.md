# Desktop: core libraries per version and the libzstd preload

Date: 2026-11-09
Status: done

## Problem

Two failures appeared when the desktop app was built from this repository after the rebase onto 7.1.2:

1. The Haskell core in `common/src/commonMain/cpp/desktop/libs/linux-x86_64` was `libHSsimplexmq-7.0.0.6`
   from the 7.0.4 era. The directory is git-ignored, so nothing in the tree tied it to a client version.
   With the 7.1.2 client the app started but `listUsers` failed (`failed to list users * usersList {...}`)
   because the old core does not send the fields the newer model requires (`fullPreferences`,
   `autoAcceptGroupInvitations`, `viewPwdHash`), so the UI stayed empty.
2. After installing the 7.1.2 core the app crashed at the first frame with
   `SIGSEGV ... libHSzstd-0.1.3.0-...so ZSTD_freeDDict+0x11` on the AWT event thread inside
   `DirectContext_nFlushDefault`. GL initialisation on this machine also logs
   `MESA: error: Failed to query drm device` / `failed to load driver: iris`.

## Root causes

1. Nothing pinned the core libraries to the client version; they were fetched once by hand.
2. Symbol collision. The GHC RTS opens the package libraries with `RTLD_GLOBAL`, and `libHSzstd` exports
   268 `ZSTD_*`/`ZDICT_*` symbols. Mesa's `libgallium` links `libzstd.so.1` and resolves those symbols
   lazily, so after the core is loaded Mesa's calls bind to the Haskell copy and it frees a dictionary
   that its own zstd never created. `LD_PRELOAD=/usr/lib/libzstd.so.1` binds the real implementation first
   and the crash disappears; `nm -D` confirms `libskiko-linux-x64.so` has no zstd dependency at all, so the
   collision is between Mesa and the Haskell core, not skiko.

## Changes

- `scripts/desktop/fetch-core-libs.sh` installs the core of the version in `gradle.properties` from the
  official desktop package (`simplex-desktop-ubuntu-24_04-<arch>.deb`), keeping the locally built
  `libapp-lib.so`. It prints the installed `libHSsimplexmq-*` version for verification.
- `desktop/build.gradle.kts`: a `doLast` on `createDistributable` copies `libzstd.so.1` into `lib/app` and
  writes a `simplex` wrapper next to `bin/` that preloads it via `LD_PRELOAD`. jpackage's launcher is a
  native binary and `simplex.cfg` only accepts JVM options, so a wrapper is the only place to set it.

## Verification

- `scripts/desktop/fetch-core-libs.sh` → 169 libraries, `Core: libHSsimplexmq-7.1.0.17`.
- `./gradlew :desktop:createDistributable` → BUILD SUCCESSFUL, the wrapper and `lib/app/libzstd.so.1` are
  present afterwards.
- Launching `release/main/app/simplex/simplex` keeps the window open with the 7.1.2 client and the 7.1.2
  core, no `usersList` error and no `hs_err` file; launching `bin/simplex` directly still crashes on this
  machine, which is why the wrapper is the documented entry point.
