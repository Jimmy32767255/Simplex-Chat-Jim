# Fix the desktop distribution: stage the native core so the packaged app can start

Date: 2026-11-04 · Baseline: SimpleX Chat 7.0.4 · Scope: `apps/multiplatform` desktop packaging only

## 1. Problem statement

Running the packaged desktop build (`release/main/app/simplex/bin/simplex`) died immediately:

```
Exception in thread "main" java.lang.UnsatisfiedLinkError: Can't load library:
  .../release/main/app/simplex/lib/app/resources/libapp-lib.so
	at chat.simplex.desktop.MainKt.initHaskell(Main.kt:56)
```

`Main.initHaskell()` reads `compose.application.resources.dir` and loads `libapp-lib.<ext>` from it. That
directory is filled from `nativeDistributions.appResourcesRootDir`, which `desktop/build.gradle.kts` points at
`apps/multiplatform/build/links`. Nothing ever populated that directory on Linux or macOS: `cmakeBuildAndCopy`
copies the CMake output into `common/src/commonMain/cpp/desktop/libs/<platform>` and additionally copies only
the Windows DLLs into `build/links/windows-x64`. The packaged `lib/app/resources` therefore existed but was
empty, and the Haskell core (`libapp-lib.so`, `libsimplex.so`, ~160 `libHS*.so` files, 605 MB) never reached
the distribution.

## 2. Solution summary

Add a `stageCoreLibs` task to `desktop/build.gradle.kts` that maps the host OS/arch to the Compose resources
subdirectory (`linux-x64`, `linux-arm64`, `macos-x64`, `macos-arm64`, `windows-x64`, `windows-arm64`), maps
that to the source directory under `cpp/desktop/libs` (`linux-x86_64`, `linux-aarch64`, `mac-x86_64`,
`mac-aarch64`, `windows-x86_64`, `windows-aarch64`), and stages the core there as a **symlink** (600 MB dir;
`project.copy` fallback when symlinks are unavailable). The task runs before `prepareAppResources`, `run` and
`runDistributable`, always re-runs (a stale resources directory silently produces a broken package), and fails
the build with an explicit message when the core libraries are missing instead of producing a package that
crashes on startup.

## 3. Detailed technical design

### 3.1 Directory names

Compose 1.8.2 only accepts fixed subdirectory names under `appResourcesRootDir`
(`ComposePlugin$DesktopDependencies`: `common`, `linux-x64`, `linux-arm64`, `windows-x64`, `windows-arm64`,
`macos-x64`, `macos-arm64`). Everything directly inside the chosen subdirectory is copied next to the app jars,
which is exactly what the loader needs: `libapp-lib.so` has `NEEDED libsimplex.so` and
`RUNPATH = $ORIGIN:<repo>/common/src/commonMain/cpp/desktop/libs/linux-x86_64`, so the whole set must travel
together.

### 3.2 Task behaviour

- Unknown platform → logged at info level and skipped.
- Source directory missing → `GradleException` naming the missing path and telling the developer to build the
  Haskell core first.
- Destination is an existing **real** directory (Windows, where `cmakeBuildAndCopy` already put the DLLs) →
  `project.copy` with `include("*.so*", "*.dylib", "*.dll")` and `DuplicatesStrategy.EXCLUDE`, so the existing
  files are kept.
- Otherwise → delete a stale destination, then `Files.createSymbolicLink(dest, src)`; on failure fall back to
  `project.copy`.
- `outputs.upToDateWhen { false }` and `dependsOn` wiring in `tasks.matching { ... }.configureEach`, so
  `./gradlew :desktop:run`, `runDistributable` and `packageDistributionForCurrentOS` all stage the libraries
  first.

## 4. Implementation steps

1. Append the task plus the `import java.nio.file.Files` it needs (`java` is the Gradle `java` extension inside
   a build script, so `java.nio.file.Files` does not resolve).
2. Re-run `./gradlew :desktop:createDistributable` after deleting the stale `release/main`.
3. Update `README.md` and `spec/architecture.md`.

## 5. Verification

- `:desktop:createDistributable` → BUILD SUCCESSFUL; `release/main/app/simplex/lib/app/resources` now holds 164
  files, including `libapp-lib.so` (26 088 B) and `libsimplex.so` (56 MB); the distribution is 862 MB.
- `release/main/app/simplex/bin/simplex` starts and keeps running with its window open (previously it aborted in
  `initHaskell`).
- `lib/app/simplex.cfg` already carries `-Dskiko.library.path=$APPDIR` and
  `-Dcompose.application.resources.dir=$APPDIR/resources`; launching the jars with a bare `java -cp` still fails
  in skiko because those properties are missing — that is an artefact of manual launching, not of the package.
