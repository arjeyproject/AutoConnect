# AutoConnect

Private routing for Android, one tap away.

AutoConnect is a native Android client with a real routing stack:

```text
Aether core -> local SOCKS5 -> HEV Socks5 Tunnel -> Android TUN
```

## Features

- MASQUE, WireGuard and Gool protocols
- Smart Connect benchmarks handshake, latency, DNS and stability
- Device VPN and local SOCKS5 modes
- Split routing, fail-closed mode and automatic recovery
- Live activity logs and Quick Settings tile
- Persian and English UI with RTL support
- SHA-256 and signing-certificate verified in-app updates
- No account, ads, analytics or telemetry

## Build on Ubuntu

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Android NDK 27.2.12479018 and Gradle 8.11+.

```bash
git clone https://github.com/arjeyproject/AutoConnect.git
cd AutoConnect
./scripts/fetch-native-assets.sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Native assets are downloaded and verified at build time. They are not committed to the repository.
Release builds require a keystore and produce APKs, an AAB and SHA256SUMS.txt with `scripts/package-release.sh`.

## Compatibility note

The current client speaks the Aether protocol family. `Custom endpoint` is not a generic VLESS/Reality or arbitrary sing-box JSON parser. A sing-box or Xray service can be run separately for compatible clients, but pasting a VLESS link into AutoConnect does not add VLESS support.

## Support

Developer: [@ArJeyDev](https://t.me/ArJeyDev)

Channel: [@ArJeyProject](https://t.me/ArJeyProject)

Install guide: [docs/index.html](docs/index.html)

Source: https://github.com/arjeyproject/AutoConnect

## Donate

- Bitcoin: `bc1qcskpuht93n25955qlkz3clh27tfa5njcg6k5t7`
- USDT TRC20: `TQ3FbpDYiKxf8aZtKuxBiWaj9N7LQnQY4F`
- TON: `UQBMAeTrxdEP85mIvOPZoa0_xcJfOEvA0apODFARMHU2A8oq`

GNU AGPL v3.0. See NOTICE.md.
