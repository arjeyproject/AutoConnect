#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$ROOT"
VERSION="$(awk -F"'" '/versionName/ {print $2; exit}' app/build.gradle)"; OUT="$ROOT/release"
gradle --no-daemon clean testReleaseUnitTest assembleRelease bundleRelease
rm -rf "$OUT"; mkdir -p "$OUT"; cp app/build/outputs/apk/release/app-universal-release.apk "$OUT/AutoConnect-v$VERSION-Android-Universal.apk" 2>/dev/null || true; cp app/build/outputs/bundle/release/app-release.aab "$OUT/AutoConnect-v$VERSION-Android.aab" 2>/dev/null || true
cd "$OUT"; sha256sum ./* > SHA256SUMS.txt
