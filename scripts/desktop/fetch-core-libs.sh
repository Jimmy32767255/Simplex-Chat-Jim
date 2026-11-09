#!/usr/bin/env bash
# Installs the Haskell core libraries (libsimplex, libHS*, libcrypto, libffi) that the desktop app
# links against into apps/multiplatform/common/src/commonMain/cpp/desktop/libs/<platform>.
#
# That directory is not tracked by git (apps/multiplatform/.gitignore): the libraries are prebuilt
# binaries, not source. Without them `:desktop:stageCoreLibs` fails, and with libraries from another
# version the app starts but cannot parse core responses (for example "failed to list users" from
# `usersList`), so the client and the core have to come from the same release.
#
# The libraries are extracted from the official desktop package of the same version, which keeps the
# client code in this repository and the packaged core in sync.
#
# Usage:
#   scripts/desktop/fetch-core-libs.sh [version]
#
# The version defaults to `desktop.version_name` from apps/multiplatform/gradle.properties. Set
# HTTPS_PROXY / https_proxy to download through a proxy, for example https_proxy=http://127.0.0.1:1080.
#
# `libapp-lib.so` is built from this repository by `common:cmakeBuild` and is preserved.

set -euo pipefail

repo_root="$(cd "$(dirname "$(readlink -f "$0")")/../.." && pwd)"
gradle_props="$repo_root/apps/multiplatform/gradle.properties"
version="${1:-$(sed -n 's/^desktop\.version_name=//p' "$gradle_props" | tr -d '[:space:]')}"

if [ -z "$version" ]; then
  echo "Cannot determine the version, pass it as the first argument" >&2
  exit 1
fi

case "$(uname -m)" in
  x86_64 | amd64) deb_arch=x86_64 ;;
  aarch64 | arm64) deb_arch=aarch64 ;;
  *)
    echo "Unsupported architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

libs_dir="$repo_root/apps/multiplatform/common/src/commonMain/cpp/desktop/libs/linux-$deb_arch"
work_dir="$repo_root/apps/multiplatform/build/dl/core-$version-$deb_arch"
deb="$work_dir/simplex-desktop.deb"
url="https://github.com/simplex-chat/simplex-chat/releases/download/v$version/simplex-desktop-ubuntu-24_04-$deb_arch.deb"

mkdir -p "$work_dir"
if [ ! -f "$deb" ]; then
  echo "Downloading $url"
  curl --fail --location --retry 3 -o "$deb" "$url"
fi

echo "Extracting the core libraries"
rm -rf "$work_dir/extract"
mkdir -p "$work_dir/extract"
(
  cd "$work_dir/extract"
  ar x "$deb" data.tar.zst
  tar --zstd -xf data.tar.zst ./opt/simplex/lib/app/resources
)

src="$work_dir/extract/opt/simplex/lib/app/resources"
if [ ! -d "$src" ]; then
  echo "Unexpected package layout: $src is missing" >&2
  exit 1
fi

mkdir -p "$libs_dir"
# libapp-lib is compiled from common/src/commonMain/cpp, keep the local build instead of the packaged one.
if [ -f "$libs_dir/libapp-lib.so" ]; then
  cp -a "$libs_dir/libapp-lib.so" "$work_dir/libapp-lib.so"
fi
find "$libs_dir" -maxdepth 1 -name '*.so*' -delete
cp -a "$src"/. "$libs_dir"/
if [ -f "$work_dir/libapp-lib.so" ]; then
  cp -a "$work_dir/libapp-lib.so" "$libs_dir/libapp-lib.so"
fi

core_version="$(strings "$libs_dir/libsimplex.so" | grep -o 'libHSsimplexmq-[0-9.]*' | head -1 || true)"
echo "Installed $(find "$libs_dir" -maxdepth 1 -name '*.so*' | wc -l) libraries into ${libs_dir#"$repo_root"/}"
echo "Core: ${core_version:-unknown} (expected version $version)"
