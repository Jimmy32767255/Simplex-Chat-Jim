import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import java.nio.file.Files
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.tomtzook.gradle-cmake") version "1.2.2"
}

group = "chat.simplex"
version = extra["desktop.version_name"] as String


kotlin {
  jvm()
  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(project(":common"))
        implementation(compose.desktop.currentOs)
      }
    }
    val jvmTest by getting
  }
}

// https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Native_distributions_and_local_execution
compose {
  desktop {
    application {
      // For debugging via VisualVM
      val debugJava = false
      if (debugJava) {
        jvmArgs += listOf(
          "-Dcom.sun.management.jmxremote.port=8080",
          "-Dcom.sun.management.jmxremote.ssl=false",
          "-Dcom.sun.management.jmxremote.authenticate=false"
        )
      }
      mainClass = "chat.simplex.desktop.MainKt"
      nativeDistributions {
        copyright = "(c) 2020-2026 SimpleX Chat"
        // For debugging via VisualVM
        if (debugJava) {
          modules("jdk.zipfs", "jdk.unsupported", "jdk.management.agent")
        } else {
          // 'jdk.unsupported' is for vlcj
          modules("jdk.zipfs", "jdk.unsupported")
        }
        val os = System.getProperty("os.name", "generic").toDefaultLowerCase()
        // 'jdk.accessibility' provides Java Access Bridge on Windows - without it the app
        // fails to start with "Failed to launch JVM" when assistive technologies are enabled
        // in the system (see #4146). Packages are always built on the target OS, so only
        // the Windows build needs to bundle it.
        if (os.contains("win")) {
          modules("jdk.accessibility")
        }
        //includeAllModules = true
        outputBaseDir.set(project.file("../release"))
        appResourcesRootDir.set(project.file("../build/links"))
        targetFormats(
          TargetFormat.Deb, TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe
          //, TargetFormat.AppImage // Gradle doesn't sync on Mac with it
        )
        linux {
          iconFile.set(project.file("src/jvmMain/resources/distribute/simplex.png"))
          appCategory = "Messenger"
        }
        windows {
          packageName = "SimpleX"
          iconFile.set(project.file("src/jvmMain/resources/distribute/simplex.ico"))
          console = false
          perUserInstall = false
          dirChooser = true
          shortcut = true
          upgradeUuid = "CC9EFBC8-AFFF-40D8-BB69-FCD7CE99EFB9"
        }
        macOS {
          packageName = "SimpleX"
          iconFile.set(project.file("src/jvmMain/resources/distribute/simplex.icns"))
          appCategory = "public.app-category.social-networking"
          bundleID = "chat.simplex.app"
          infoPlist {
            extraKeysRawXml = """
              <key>NSMicrophoneUsageDescription</key>
              <string>SimpleX needs microphone access to record voice messages</string>
              <key>CFBundleURLTypes</key>
              <array>
                <dict>
                  <key>CFBundleURLName</key>
                  <string>$bundleID</string>
                  <key>CFBundleURLSchemes</key>
                  <array>
                    <string>simplexchat</string>
                    <string>simplex</string>
                  </array>
                </dict>
              </array>
            """
          }
          val identity = rootProject.extra["desktop.mac.signing.identity"] as String?
          val keychain = rootProject.extra["desktop.mac.signing.keychain"] as String?
          val appleId = rootProject.extra["desktop.mac.notarization.apple_id"] as String?
          val password = rootProject.extra["desktop.mac.notarization.password"] as String?
          val teamId = rootProject.extra["desktop.mac.notarization.team_id"] as String?
          if (identity != null && keychain != null && appleId != null && password != null) {
            signing {
              sign.set(true)
              this.identity.set(identity)
              this.keychain.set(keychain)
            }
            notarization {
              this.appleID.set(appleId)
              this.password.set(password)
              this.teamID.set(teamId)
            }
          }
        }
        if (os.contains("mac") || os.contains("win")) {
          packageName = "SimpleX"
        } else {
          packageName = "simplex"
        }
        // Packaging requires to have version like MAJOR.MINOR.PATCH
        var adjustedVersion = rootProject.extra["desktop.version_name"] as String
        adjustedVersion = adjustedVersion.replace(Regex("[^0-9.]"), "")
        val split = adjustedVersion.split(".")
        adjustedVersion = split[0] + "." + (split.getOrNull(1) ?: "0") + "." + (split.getOrNull(2) ?: "0")
        version = adjustedVersion
      }
    }
  }
}

val cppPath = "../common/src/commonMain/cpp"
cmake {
  // Run this command to make build for all targets:
  // ./gradlew common:cmakeBuild -PcrossCompile
  if (project.hasProperty("crossCompile")) {
    machines.customMachines.register("linux-amd64") {
      toolchainFile.set(project.file("$cppPath/toolchains/x86_64-linux-gnu-gcc.cmake"))
    }
    /*machines.customMachines.register("linux-aarch64") {
      toolchainFile.set(project.file("$cppPath/toolchains/aarch64-linux-gnu-gcc.cmake"))
    }*/
    /*machines.customMachines.register("win-amd64") {
      toolchainFile.set(project.file("$cppPath/toolchains/x86_64-windows-mingw32-gcc.cmake"))
    }*/
    if (machines.host.name == "mac-amd64") {
      machines.customMachines.register("mac-amd64") {
        toolchainFile.set(project.file("$cppPath/toolchains/x86_64-mac-apple-darwin-gcc.cmake"))
      }
    }
    if (machines.host.name == "mac-aarch64") {
      machines.customMachines.register("mac-aarch64") {
        toolchainFile.set(project.file("$cppPath/toolchains/aarch64-mac-apple-darwin-gcc.cmake"))
      }
    }
  }
  val compileMachineTargets = arrayListOf<com.github.tomtzook.gcmake.targets.TargetMachine>(machines.host)
  compileMachineTargets.addAll(machines.customMachines)
  targets {
    val main by creating {
      cmakeLists.set(file("$cppPath/desktop/CMakeLists.txt"))
      targetMachines.addAll(compileMachineTargets.toSet())
      //if (machines.host.name.contains("win")) {
      //  cmakeArgs.add("-G MinGW Makefiles")
      //}
    }
  }
}

tasks.named("clean") {
  dependsOn("cmakeClean")
}
tasks.named("compileKotlinJvm") {
  dependsOn("cmakeBuildAndCopy")
}
tasks.withType<AbstractJPackageTask>().configureEach {
  if (targetFormat == TargetFormat.Msi) {
    doLast {
      exec {
        commandLine(
          "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
          "-File", project.file("../../../scripts/desktop/disable-msi-install-dir-wipe-windows.ps1"),
          destinationDir.asFileTree.matching { include("*.msi") }.singleFile
        )
      }
    }
  }
}
afterEvaluate {
  tasks.create("cmakeBuildAndCopy") {
    dependsOn("cmakeBuild")
    doLast {
      copy {
        from("${project(":desktop").buildDir}/cmake/main/linux-amd64")
        into("$cppPath/desktop/libs/linux-x86_64")
        include("*.so*")
        eachFile {
          path = name
        }
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
      }
      copy {
        from("${project(":desktop").buildDir}/cmake/main/linux-aarch64")
        into("$cppPath/desktop/libs/linux-aarch64")
        include("*.so*")
        eachFile {
          path = name
        }
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
      }
      copy {
        from("${project(":desktop").buildDir}/cmake/main/windows-amd64")
        into("$cppPath/desktop/libs/windows-x86_64")
        include("*.dll")
        eachFile {
          path = name
        }
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
      }
	  copy {
        from("${project(":desktop").buildDir}/cmake/main/windows-amd64")
        into("../build/links/windows-x64")
        include("*.dll")
        eachFile {
          path = name
        }
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
      }
      copy {
        from("${project(":desktop").buildDir}/cmake/main/mac-x86_64")
        into("$cppPath/desktop/libs/mac-x86_64")
        include("*.dylib")
        eachFile {
          path = name
        }
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
      }
      copy {
        from("${project(":desktop").buildDir}/cmake/main/mac-aarch64")
        into("$cppPath/desktop/libs/mac-aarch64")
        include("*.dylib")
        eachFile {
          path = name
        }
        includeEmptyDirs = false
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
      }
    }
  }
}

// ---- Distribution resources: the native core ----
// Main.kt loads `libapp-lib.<ext>` from `compose.application.resources.dir`, which is populated from
// the subdirectories of `appResourcesRootDir` above ("linux-x64", "macos-arm64", "windows-x64", ...).
// The Haskell core (libapp-lib + libsimplex + libHS*) has to be staged there, otherwise packaging
// silently produces an app that dies on startup with UnsatisfiedLinkError because the resources
// directory is empty. The libraries are linked rather than copied: the directory is ~600 MB, and a
// symlink keeps packaging cheap while still bundling everything the loader needs.
val hostOs = System.getProperty("os.name", "generic").toDefaultLowerCase()
val hostArch = System.getProperty("os.arch", "generic").toDefaultLowerCase()
val hostIsArm = hostArch.contains("aarch64") || hostArch.contains("arm64")
val coreLibsResourcesSubdir = when {
  hostOs.contains("linux") -> if (hostIsArm) "linux-arm64" else "linux-x64"
  hostOs.contains("mac") -> if (hostIsArm) "macos-arm64" else "macos-x64"
  hostOs.contains("win") -> if (hostIsArm) "windows-arm64" else "windows-x64"
  else -> null
}
val coreLibsSourceSubdir = when (coreLibsResourcesSubdir) {
  "linux-x64" -> "linux-x86_64"
  "linux-arm64" -> "linux-aarch64"
  "macos-x64" -> "mac-x86_64"
  "macos-arm64" -> "mac-aarch64"
  "windows-x64" -> "windows-x86_64"
  "windows-arm64" -> "windows-aarch64"
  else -> null
}
val stageCoreLibs = tasks.register("stageCoreLibs") {
  group = "distribution"
  description = "Stages the native Haskell core into the Compose Desktop app resources directory"
  // A stale resources directory silently produces a package that cannot start, so always verify it.
  outputs.upToDateWhen { false }
  doLast {
    val subdir = coreLibsResourcesSubdir
    val source = coreLibsSourceSubdir
    if (subdir == null || source == null) {
      logger.info("Unknown platform, skipping native core staging")
      return@doLast
    }
    val src = project.file("$cppPath/desktop/libs/$source")
    if (!src.isDirectory) {
      throw GradleException(
        "Native core libraries are missing: $src does not exist, so the packaged app would fail to " +
        "start with UnsatisfiedLinkError. Build the Haskell core for this platform first " +
        "(see README, 'Build the core'), then re-run the packaging task."
      )
    }
    val linksDir = project.file("../build/links")
    linksDir.mkdirs()
    val dest = File(linksDir, subdir)
    if (dest.exists() && !Files.isSymbolicLink(dest.toPath())) {
      // A real directory (e.g. Windows DLLs copied by cmakeBuildAndCopy): fill in what is missing.
      project.copy {
        from(src)
        into(dest)
        include("*.so*", "*.dylib", "*.dll")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      }
      return@doLast
    }
    if (dest.exists()) dest.delete()
    try {
      Files.createSymbolicLink(dest.toPath(), src.toPath())
    } catch (e: Exception) {
      logger.info("Cannot link ${dest.path}, copying instead: ${e.message}")
      project.copy {
        from(src)
        into(dest)
        include("*.so*", "*.dylib", "*.dll")
      }
    }
  }
}
tasks.matching { it.name == "prepareAppResources" || it.name == "run" || it.name == "runDistributable" }
  .configureEach { dependsOn(stageCoreLibs) }
