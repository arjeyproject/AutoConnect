<div align="center">

# AutoConnect

**Free, ad-free, account-free tunnelling for Android.**
Built by [@ArJeyDev](https://t.me/ArJeyDev) · News and releases: [@ArJeyProject](https://t.me/ArJeyProject)

</div>

---

AutoConnect is an independent Android client that builds an encrypted tunnel straight from the
phone, using an open-source networking core. There is no account, no advertising, no paid tier and
no usage limit. Install it, press the dial, done.

## What is inside

- **Three connection modes.** Device VPN for the whole phone, Smart mode that races every protocol
  and keeps the fastest reliable route, and a local SOCKS5 proxy on `127.0.0.1:1819`.
- **Real protocols.** MASQUE over HTTP/3 or HTTP/2, WireGuard, and gool / WARP-in-WARP, with
  selectable scan modes and obfuscation profiles.
- **Split tunnelling.** Include only the apps you choose, or exclude the ones that should bypass
  the tunnel.
- **Live session telemetry.** Download, upload, latency and the active gateway, updated while
  connected.
- **English and Persian, both first class.** Full right-to-left layout, Persian typography, and
  numbers, addresses and code always rendered left to right where they should be.
- **Light and dark appearance.** Each one designed on its own, not an inverted copy of the other.
- **Quick Settings tile.** Connect and disconnect from the notification shade.
- **Verified in-app updates.** Releases are checked against SHA-256 digests and the installed
  signing certificate before anything is installed.
- **Fail closed and auto reconnect.** Keep the network blocked if the tunnel drops, or recover
  automatically.

AutoConnect is a standalone Android application. It needs no server of yours, no subscription link
and no companion service: everything it does, it does on the device.

## Install

Grab the latest APK from [Releases](https://github.com/arjeyproject/AutoConnect/releases).

| File | Use it for |
| --- | --- |
| `AutoConnect-v<version>-Android-Universal.apk` | Any phone. Pick this if unsure. |
| `AutoConnect-v<version>-Android-ARM64.apk` | Modern 64-bit phones, smallest download |
| `AutoConnect-v<version>-Android-ARMv7.apk` | Older 32-bit devices |
| `AutoConnect-v<version>-Android-x86_64.apk` | Emulators and x86 devices |

Android 8.0 or newer is required. Verify the download against `SHA256SUMS.txt` on the release page.

## Build it yourself

On Ubuntu 22.04 or 24.04:

```bash
sudo apt -y install git curl unzip zip coreutils openjdk-17-jdk
# Android SDK command line tools + platforms;android-35 + build-tools;35.0.0 + ndk;27.2.12479018
# Gradle 8.10.2

git clone https://github.com/arjeyproject/AutoConnect.git
cd AutoConnect
bash scripts/fetch-android-assets.sh     # downloads and verifies the native payload
cd android
gradle assembleRelease bundleRelease
```

Output lands in `android/app/build/outputs/`. Without a keystore the release build falls back to the
debug key so that a fresh clone still produces an installable APK. For anything you publish, create
a real keystore, keep it forever, and never commit it.

Prefer no server at all? Push to GitHub and run the **Build AutoConnect for Android** workflow. It
does everything above on a free runner and attaches the APKs to the run.

## Project layout

```
android/                  the Android application
  app/src/main/java/      VPN service, tunnel controller, updater, UI
  app/src/main/res/       design tokens, layouts, English and Persian strings
scripts/
  fetch-android-assets.sh downloads and verifies the network cores, builds the JNI bridge
docs/                     project page and assets
.github/workflows/        CI that builds, signs and publishes releases
```

## Our other projects

[@AutoVlessBot](https://t.me/AutoVlessBot) is a separate Telegram utility bot of ours. AutoConnect
links to it from the About tab as a courtesy and nothing more: the application never contacts it and
does not depend on it in any way.

## Support and donations

AutoConnect stays free. If it helps you, a donation keeps releases coming.

- Bitcoin (BTC): `bc1qcskpuht93n25955qlkz3clh27tfa5njcg6k5t7`
- Tether USDT (TRC20): `TQ3FbpDYiKxf8aZtKuxBiWaj9N7LQnQY4F`
- TON (Gram): `UQBMAeTrxdEP85mIvOPZoa0_xcJfOEvA0apODFARMHU2A8oq`

Support and questions: [@ArJeyDev](https://t.me/ArJeyDev) · Channel: [@ArJeyProject](https://t.me/ArJeyProject)

## License and attribution

AutoConnect is released under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).

The application is an independent interface and is not the upstream networking project. Traffic is
carried by the open-source Aether core and the HEV Socks5 Tunnel bridge, both under GPL-3.0. Their
notices are in [NOTICE.md](NOTICE.md) and `third-party/`.
