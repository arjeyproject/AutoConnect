# AutoConnect for Android

Native Java application built on Android `VpnService`. It supervises the Aether network core as a
local SOCKS5 process and routes the VPN file descriptor through the HEV Socks5 Tunnel JNI bridge.

## Requirements

- JDK 17
- Android SDK Platform 35, Build Tools 35.0.0
- Android NDK `27.2.12479018`
- Gradle 8.10.2 (installed separately; no wrapper binary is committed)

## Build

The native libraries are not committed. Prepare the verified payload first, from the repository
root:

```bash
bash scripts/fetch-android-assets.sh
cd android
gradle assembleDebug              # development build
gradle assembleRelease bundleRelease   # release APK splits + app bundle
```

Supported ABIs: `armeabi-v7a`, `arm64-v8a`, `x86_64`, plus a universal APK containing all three.

If you want the Gradle wrapper back, run `gradle wrapper --gradle-version 8.10.2` in this folder.

## Signing

Release signing reads, in order: the `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` environment variables, then
`android/.android-signing/signing.properties`. With neither configured, the release build is signed
with the local debug key so that a fresh clone still produces an installable APK. Published releases
must use a real, permanent keystore, otherwise in-app updates cannot verify the certificate.

## Code map

| Path | Responsibility |
| --- | --- |
| `MainActivity` | Shell, tab navigation, settings, telemetry rendering |
| `ConnectionOrbView` | The connection dial and its state motion |
| `AutoConnectVpnService` | Tunnel lifecycle, core supervision, routing, stats, recovery |
| `VpnConnectionController` | Connect and disconnect intents |
| `AutoConnectTileService` | Quick Settings tile |
| `AppSelectionActivity` | Split tunnelling app picker |
| `AppUpdateManager` and friends | Update checks, verified downloads, install handoff |
| `hev.htproxy.TProxyService` | JNI contract of the tunnel bridge. Package name is fixed. |

## Design system

Tokens live in `res/values/colors.xml` (light), `res/values-night/colors.xml` (dark) and
`res/values/themes.xml` (typography, spacing styles, component styles). Layouts reference tokens
only. Strings exist in `res/values` (English) and `res/values-fa` (Persian) with full parity.

Defaults on a fresh install: Turbo scan mode, gool / WARP-in-WARP, Device VPN, auto reconnect on,
system theme. The Android VPN permission is requested only when a tunnel actually starts.
