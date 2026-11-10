#!/usr/bin/env bash
# Installs the Haskell core libraries (libsimplex.so, libsupport.so) that the Android app links
# against into apps/multiplatform/common/src/commonMain/cpp/android/libs/<abi>.
#
# That directory is not tracked by git (apps/multiplatform/.gitignore): the libraries are prebuilt
# binaries, not source, and they have to match the client version. With libraries from another
# version the native link fails with undefined `chat_*` symbols (the 7.0.4 core lacks
# `chat_parse_badge_code`, `chat_resize_image_uri`, `chat_resize_image`, `chat_resize_image_fd_uri`
# and `chat_resize_image_fd`).
#
# The libraries are extracted from the official Android APK of the same version, which keeps the
# client code in this repository and the packaged core in sync.
#
# Usage:
#   scripts/android/fetch-core-libs.sh [version]
#
# The version defaults to `android.version_name` from apps/multiplatform/gradle.properties. Set
# HTTPS_PROXY / https_proxy to download through a proxy, for example https_proxy=http://127.0.0.1:1080.

set -euo pipefail

repo_root="$(cd "$(dirname "$(readlink -f "$0")")/../.." && pwd)"
gradle_props="$repo_root/apps/multiplatform/gradle.properties"
version="${1:-$(sed -n 's/^android\.version_name=//p' "$gradle_props" | tr -d '[:space:]')}"

if [ -z "$version" ]; then
  echo "Cannot determine the version, pass it as the first argument" >&2
  exit 1
fi

libs_dir="$repo_root/apps/multiplatform/common/src/commonMain/cpp/android/libs"
work_dir="$repo_root/apps/multiplatform/build/dl/core-$version-android"
mkdir -p "$work_dir"

install_arch() {
  local apk_arch="$1"
  local abi="$2"
  local apk="$work_dir/simplex-$apk_arch.apk"
  local url="https://github.com/simplex-chat/simplex-chat/releases/download/v$version/simplex-$apk_arch.apk"

  if [ ! -f "$apk" ]; then
    echo "Downloading $url"
    curl --fail --location --retry 3 -o "$apk" "$url"
  fi

  rm -rf "$work_dir/$abi"
  mkdir -p "$work_dir/$abi"
  unzip -j -o "$apk" "lib/$abi/libsimplex.so" "lib/$abi/libsupport.so" -d "$work_dir/$abi" >/dev/null

  mkdir -p "$libs_dir/$abi"
  cp -a "$work_dir/$abi/libsimplex.so" "$work_dir/$abi/libsupport.so" "$libs_dir/$abi/"

  local core_version
  core_version="$(strings "$libs_dir/$abi/libsimplex.so" | grep -o 'simplex-chat-[0-9A-Za-z.-]*' | head -1 || true)"
  echo "Installed $abi: ${core_version:-unknown}"
}

install_arch aarch64 arm64-v8a
install_arch armv7a armeabi-v7a
echo "Android core: expected version $version"
