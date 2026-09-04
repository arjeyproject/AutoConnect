#!/usr/bin/env bash
set -euo pipefail
AETHER_VERSION="${AETHER_CORE_VERSION:-v1.7.0}"
HEV_TAG="2.16.0"
HEV_COMMIT="0a05221275a51a884d93328c55fc2fbc9e9b6974"
NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/autoconnect-native.XXXXXXXX")"
trap 'rm -rf "$WORK"' EXIT
ABIS=("armeabi-v7a" "arm64-v8a" "x86_64")
ARCHIVES=("aether-android-armv7.tar.gz" "aether-android-arm64.tar.gz" "aether-android-x86_64.tar.gz")
for tool in curl tar git sha256sum; do command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }; done
mkdir -p "$DEST"
BASE="https://github.com/CluvexStudio/Aether/releases/download/$AETHER_VERSION"
for i in "${!ABIS[@]}"; do abi="${ABIS[$i]}"; archive="${ARCHIVES[$i]}"; mkdir -p "$DEST/$abi" "$WORK/$abi"; curl -fsSL "$BASE/$archive" -o "$WORK/$archive"; curl -fsSL "$BASE/$archive.sha256" -o "$WORK/$archive.sha256"; expected="$(awk 'NR==1{print $1}' "$WORK/$archive.sha256")"; actual="$(sha256sum "$WORK/$archive"|awk '{print $1}')"; [[ "${expected,,}" == "$actual" ]] || { echo "checksum mismatch for $abi" >&2; exit 1; }; tar -xzf "$WORK/$archive" -C "$WORK/$abi"; core="$(find "$WORK/$abi" -type f -name aether|head -n1)"; [[ -n "$core" ]] || exit 1; install -m0644 "$core" "$DEST/$abi/libaether.so"; done
git clone --quiet --branch "$HEV_TAG" --depth 1 --recurse-submodules https://github.com/heiher/hev-socks5-tunnel.git "$WORK/hev"
[[ "$(git -C "$WORK/hev" rev-parse HEAD)" == "$HEV_COMMIT" ]] || { echo "unexpected HEV commit" >&2; exit 1; }
NDK_ROOT="${ANDROID_NDK_HOME:-$ANDROID_SDK_ROOT/ndk/$NDK_VERSION}"
"$NDK_ROOT/ndk-build" NDK_PROJECT_PATH="$WORK/hev" APP_BUILD_SCRIPT="$WORK/hev/Android.mk" APP_ABI="armeabi-v7a arm64-v8a x86_64" APP_CFLAGS="-O3 -DPKGNAME=hev/htproxy" NDK_LIBS_OUT="$WORK/hev-libs" NDK_OUT="$WORK/hev-obj" -j"$(nproc)"
for abi in "${ABIS[@]}"; do install -m0644 "$WORK/hev-libs/$abi/libhev-socks5-tunnel.so" "$DEST/$abi/libhev-socks5-tunnel.so"; done
echo "AutoConnect native assets ready"
