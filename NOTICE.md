# Notices and attribution

AutoConnect is an independent Android application, designed and maintained by
[@ArJeyDev](https://t.me/ArJeyDev). It is not affiliated with, endorsed by, or a rebadge of the
upstream networking projects it links against. Those projects supply the transport; AutoConnect
supplies the Android application, interface, service lifecycle and updater around them.

AutoConnect itself is licensed under the GNU Affero General Public License, version 3.0
(AGPL-3.0). See `LICENSE`. The license follows from the bundled Aether core, which is AGPL-3.0:
the combined work cannot be distributed under weaker terms.

## Bundled components

### Aether networking core

- Project: <https://github.com/CluvexStudio/Aether>
- License: AGPL-3.0
- Role: builds the encrypted transport and exposes a local SOCKS5 endpoint. The Android release
  binaries are downloaded from the project's official releases at build time and verified against
  the published SHA-256 digests by `scripts/fetch-android-assets.sh`. They are never committed to
  this repository.

### HEV Socks5 Tunnel

- Project: <https://github.com/heiher/hev-socks5-tunnel>
- License: MIT. Full text in `third-party/hev-socks5-tunnel-LICENSE.txt`
- Role: bridges the Android `VpnService` file descriptor to the local SOCKS5 endpoint. Built from a
  pinned source commit with the Android NDK during the build.

### sing-box

- Project: <https://github.com/SagerNet/sing-box>
- License: GPL-3.0-or-later. Full text in `third-party/sing-box-LICENSE.txt`
- Role: retained for completeness of the upstream notice set. AGPL-3.0 section 13 permits
  combining this work with GPL-3.0 material.

## Source availability

Because AutoConnect and its bundled engines are distributed under AGPL-3.0, the complete
corresponding source is this repository plus the upstream projects listed above. Anyone receiving a
binary is entitled to that source under the same license, and anyone interacting with a modified
version over a network is entitled to the source of that modified version.

## Trademarks

Names and marks of the upstream projects belong to their respective owners and are used here only
to identify the components in use, as required by their licenses. "AutoConnect" refers to this
application only.
