# Security policy

## Reporting

Found something that affects user privacy or device security? Report it privately on Telegram to
[@ArJeyDev](https://t.me/ArJeyDev) before opening a public issue. Include the version, the device
and Android version, and the steps to reproduce.

Please do not post working exploits in public channels while a fix is pending.

## What the application does with your data

- No account, no registration, no analytics and no advertising SDK.
- Settings stay in Android private storage on the device.
- Nothing is uploaded anywhere except the traffic you deliberately send through the tunnel.

## Release integrity

- Cleartext HTTP is disabled application wide by the network security configuration.
- The in-app updater only accepts release assets from
  `https://github.com/arjeyproject/AutoConnect/releases/download/`.
- Downloaded packages are verified against their SHA-256 digest, and the signing certificate is
  compared with the installed application before installation is offered.
- The native network cores are verified against upstream SHA-256 digests at build time, and the
  tunnel bridge is compiled from a pinned commit.

## Signing keys

Release keystores are never committed. `android/.android-signing/` and every `*.jks` or
`*.keystore` file are excluded by `.gitignore`. If a signing key is ever exposed, rotate it, publish
a notice on [@ArJeyProject](https://t.me/ArJeyProject) and expect users to reinstall.
