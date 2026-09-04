#!/usr/bin/env bash
# AutoConnect - prepare the verified native payload for the Android build.
#
#   1. Downloads the official Aether Android cores and checks their SHA-256 digests.
#   2. Clones the pinned HEV Socks5 Tunnel source and verifies the commit.
#   3. Builds the HEV JNI bridge with the Android NDK.
#
# Result: android/app/src/main/jniLibs/<abi>/{libaether.so,libhev-socks5-tunnel.so}
#
# Requirements: bash, curl, tar, git, sha256sum and the Android NDK.
set -euo pipefail

AETHER_CORE_VERSION="${AETHER_CORE_VERSION:-v1.7.0}"
HEV_VERSION="2.16.0"
HEV_COMMIT="0a05221275a51a884d93328c55fc2fbc9e9b6974"
NDK_VERSION="${NDK_VERSION:-27.2.12479018}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DESTINATION="$ROOT/android/app/src/main/jniLibs"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/autoconnect-native.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

ABIS=("armeabi-v7a" "arm64-v8a" "x86_64")
ARCHIVES=("aether-android-armv7.tar.gz" "aether-android-arm64.tar.gz" "aether-android-x86_64.tar.gz")

log() { printf '\033[1;36m==>\033[0m %s\n' "$1"; }
fail() { printf '\033[1;31mError:\033[0m %s\n' "$1" >&2; exit 1; }

for tool in curl tar git sha256sum; do
  command -v "$tool" >/dev/null 2>&1 || fail "'$tool' is required. On Ubuntu: sudo apt-get install -y curl tar git coreutils"
done

resolve_ndk() {
  local candidates=()
  [ -n "${ANDROID_NDK_HOME:-}" ] && candidates+=("$ANDROID_NDK_HOME")
  [ -n "${ANDROID_NDK_ROOT:-}" ] && candidates+=("$ANDROID_NDK_ROOT")
  [ -n "${ANDROID_HOME:-}" ] && candidates+=("$ANDROID_HOME/ndk/$NDK_VERSION")
  [ -n "${ANDROID_SDK_ROOT:-}" ] && candidates+=("$ANDROID_SDK_ROOT/ndk/$NDK_VERSION")
  candidates+=("$HOME/Android/Sdk/ndk/$NDK_VERSION" "/usr/lib/android-sdk/ndk/$NDK_VERSION")
  for candidate in "${candidates[@]}"; do
    if [ -x "$candidate/ndk-build" ]; then printf '%s' "$candidate"; return 0; fi
  done
  fail "Android NDK $NDK_VERSION was not found. Install it with: sdkmanager 'ndk;$NDK_VERSION'"
}

mkdir -p "$DESTINATION"

# ---------------------------------------------------------------- Aether cores
for index in "${!ABIS[@]}"; do
  abi="${ABIS[$index]}"
  archive="${ARCHIVES[$index]}"
  base="https://github.com/CluvexStudio/Aether/releases/download/$AETHER_CORE_VERSION"
  log "Downloading Aether core for $abi"
  curl -fsSL "$base/$archive" -o "$WORK/$archive"
  curl -fsSL "$base/$archive.sha256" -o "$WORK/$archive.sha256"
  expected="$(tr -s ' ' < "$WORK/$archive.sha256" | cut -d' ' -f1 | tr -d '\r\n')"
  [[ "$expected" =~ ^[a-fA-F0-9]{64}$ ]] || fail "Invalid upstream checksum file for $abi."
  actual="$(sha256sum "$WORK/$archive" | cut -d' ' -f1)"
  [ "${expected,,}" = "$actual" ] || fail "Checksum mismatch for $archive (expected $expected, got $actual)."
  mkdir -p "$WORK/extract-$abi" "$DESTINATION/$abi"
  tar -xzf "$WORK/$archive" -C "$WORK/extract-$abi"
  core="$(find "$WORK/extract-$abi" -type f -name aether | head -n 1)"
  [ -n "$core" ] || fail "The 'aether' executable was not found inside $archive."
  install -m 0755 "$core" "$DESTINATION/$abi/libaether.so"
  log "Verified Aether core ready for $abi"
done

# ---------------------------------------------------------------- HEV JNI bridge
log "Cloning HEV Socks5 Tunnel $HEV_VERSION"
git clone --quiet --branch "$HEV_VERSION" --depth 1 --recurse-submodules \
  https://github.com/heiher/hev-socks5-tunnel.git "$WORK/hev" || fail "Could not clone HEV Socks5 Tunnel $HEV_VERSION."
checked_out="$(git -C "$WORK/hev" rev-parse HEAD)"
[ "$checked_out" = "$HEV_COMMIT" ] || fail "Unexpected HEV commit $checked_out (expected $HEV_COMMIT)."
if git -C "$WORK/hev" submodule status --recursive | grep -q '^[+-]'; then
  fail "HEV submodules do not match the pinned release."
fi

NDK_ROOT="$(resolve_ndk)"
log "Building the HEV JNI bridge with NDK at $NDK_ROOT"
"$NDK_ROOT/ndk-build" \
  "NDK_PROJECT_PATH=$WORK/hev" \
  "APP_BUILD_SCRIPT=$WORK/hev/Android.mk" \
  "NDK_APPLICATION_MK=$WORK/hev/Application.mk" \
  "APP_ABI=armeabi-v7a arm64-v8a x86_64" \
  "APP_CFLAGS=-O3 -DPKGNAME=hev/htproxy" \
  "NDK_LIBS_OUT=$WORK/hev-libs" \
  "NDK_OUT=$WORK/hev-obj" \
  -j"$(nproc 2>/dev/null || echo 4)"

for abi in "${ABIS[@]}"; do
  library="$WORK/hev-libs/$abi/libhev-socks5-tunnel.so"
  [ -f "$library" ] || fail "The HEV JNI library is missing for $abi."
  install -m 0644 "$library" "$DESTINATION/$abi/libhev-socks5-tunnel.so"
  log "HEV JNI bridge ready for $abi"
done

log "Native payload complete:"
find "$DESTINATION" -type f -printf '    %P (%s bytes)\n' | sort
