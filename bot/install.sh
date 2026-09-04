#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# AutoConnect config generator bot - one shot installer
#
#   bash install.sh
#
# Installs Xray-core with a VLESS + Reality inbound, generates the keys, wires
# up the bot under systemd and prints the values you need. Tested on Ubuntu
# 22.04 / 24.04 and Debian 12. Run it as root on a fresh VPS.
# ---------------------------------------------------------------------------
set -euo pipefail

APP_DIR=/opt/autovless
ENV_DIR=/etc/autovless
ENV_FILE="$ENV_DIR/bot.env"
STATE_DIR=/var/lib/autovless
XRAY_CONFIG=/usr/local/etc/xray/config.json
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
blue()  { printf '\033[0;36m%s\033[0m\n' "$*"; }
warn()  { printf '\033[0;33m%s\033[0m\n' "$*"; }
die()   { printf '\033[0;31m%s\033[0m\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "Run this as root:  sudo bash install.sh"
command -v apt-get >/dev/null || die "This installer targets Debian and Ubuntu."

blue "==> 1/8  Installing system packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl ca-certificates unzip jq openssl python3 python3-venv python3-pip >/dev/null

blue "==> 2/8  Installing Xray-core"
if ! command -v xray >/dev/null; then
  bash -c "$(curl -fsSL https://github.com/XTLS/Xray-install/raw/main/install-release.sh)" @ install
else
  green "    already installed: $(xray version | head -n1)"
fi

blue "==> 3/8  Collecting settings"
read -rp "  Telegram bot token (from @BotFather): " BOT_TOKEN
[[ -n "$BOT_TOKEN" ]] || die "A bot token is required."
read -rp "  Your Telegram numeric id (admin): " ADMIN_IDS
[[ -n "$ADMIN_IDS" ]] || die "An admin id is required."

DETECTED_IP="$(curl -fsS4 --max-time 8 https://api.ipify.org || true)"
read -rp "  Public IP or domain [${DETECTED_IP}]: " SERVER_HOST
SERVER_HOST="${SERVER_HOST:-$DETECTED_IP}"
[[ -n "$SERVER_HOST" ]] || die "A server host is required."

read -rp "  Reality port [443]: " REALITY_PORT
REALITY_PORT="${REALITY_PORT:-443}"
read -rp "  Reality SNI (a real TLS site) [www.cloudflare.com]: " REALITY_SNI
REALITY_SNI="${REALITY_SNI:-www.cloudflare.com}"
read -rp "  Days per config [30]: " DEFAULT_DAYS
DEFAULT_DAYS="${DEFAULT_DAYS:-30}"
read -rp "  GB per config, 0 for unlimited [50]: " DEFAULT_GB
DEFAULT_GB="${DEFAULT_GB:-50}"
read -rp "  Force join channel, blank to skip (e.g. @ArJeyProject): " FORCE_JOIN_CHANNEL

blue "==> 4/8  Generating Reality keys"
KEYS="$(xray x25519)"
REALITY_PRIVATE_KEY="$(sed -n 's/^\(Private key\|PrivateKey\):[[:space:]]*//p' <<<"$KEYS" | head -n1)"
REALITY_PUBLIC_KEY="$(sed -n 's/^\(Public key\|Password\):[[:space:]]*//p' <<<"$KEYS" | head -n1)"
[[ -n "$REALITY_PRIVATE_KEY" && -n "$REALITY_PUBLIC_KEY" ]] || die "Could not parse the output of 'xray x25519'."
REALITY_SHORT_ID="$(openssl rand -hex 4)"
green "    public key: $REALITY_PUBLIC_KEY"
green "    short id:   $REALITY_SHORT_ID"

blue "==> 5/8  Writing $XRAY_CONFIG"
mkdir -p "$(dirname "$XRAY_CONFIG")"
[[ -f "$XRAY_CONFIG" ]] && cp "$XRAY_CONFIG" "$XRAY_CONFIG.before-autovless"
cat > "$XRAY_CONFIG" <<JSON
{
  "log": { "loglevel": "warning" },
  "api": { "tag": "api", "services": ["HandlerService", "StatsService"] },
  "stats": {},
  "policy": {
    "levels": { "0": { "statsUserUplink": true, "statsUserDownlink": true } },
    "system": { "statsInboundUplink": true, "statsInboundDownlink": true }
  },
  "inbounds": [
    {
      "tag": "api-in",
      "listen": "127.0.0.1",
      "port": 10085,
      "protocol": "dokodemo-door",
      "settings": { "address": "127.0.0.1" }
    },
    {
      "tag": "vless-reality",
      "listen": "0.0.0.0",
      "port": ${REALITY_PORT},
      "protocol": "vless",
      "settings": {
        "clients": [],
        "decryption": "none"
      },
      "streamSettings": {
        "network": "tcp",
        "security": "reality",
        "realitySettings": {
          "show": false,
          "dest": "${REALITY_SNI}:443",
          "xver": 0,
          "serverNames": ["${REALITY_SNI}"],
          "privateKey": "${REALITY_PRIVATE_KEY}",
          "shortIds": ["${REALITY_SHORT_ID}"]
        }
      },
      "sniffing": { "enabled": true, "destOverride": ["http", "tls", "quic"] }
    }
  ],
  "outbounds": [
    { "tag": "direct", "protocol": "freedom" },
    { "tag": "block", "protocol": "blackhole" }
  ],
  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [
      { "type": "field", "inboundTag": ["api-in"], "outboundTag": "api" },
      { "type": "field", "protocol": ["bittorrent"], "outboundTag": "block" },
      { "type": "field", "ip": ["geoip:private"], "outboundTag": "block" }
    ]
  }
}
JSON
xray run -test -config "$XRAY_CONFIG" >/dev/null || die "The generated Xray config did not validate."
systemctl enable --now xray >/dev/null 2>&1 || true
systemctl restart xray
green "    xray is $(systemctl is-active xray)"

blue "==> 6/8  Installing the bot into $APP_DIR"
mkdir -p "$APP_DIR" "$ENV_DIR" "$STATE_DIR"
install -m 0644 "$SOURCE_DIR/bot.py" "$SOURCE_DIR/xray.py" "$SOURCE_DIR/storage.py" "$SOURCE_DIR/requirements.txt" "$APP_DIR/"
python3 -m venv "$APP_DIR/venv"
"$APP_DIR/venv/bin/pip" install --quiet --upgrade pip
"$APP_DIR/venv/bin/pip" install --quiet -r "$APP_DIR/requirements.txt"

cat > "$ENV_FILE" <<ENV
BOT_TOKEN=${BOT_TOKEN}
ADMIN_IDS=${ADMIN_IDS}
FORCE_JOIN_CHANNEL=${FORCE_JOIN_CHANNEL}
CHANNEL_URL=https://t.me/ArJeyProject
APP_URL=https://github.com/arjeyproject/AutoConnect/releases
DEFAULT_LANGUAGE=fa

SERVER_HOST=${SERVER_HOST}
REALITY_PORT=${REALITY_PORT}
REALITY_PUBLIC_KEY=${REALITY_PUBLIC_KEY}
REALITY_SNI=${REALITY_SNI}
REALITY_SHORT_ID=${REALITY_SHORT_ID}

WS_ENABLED=0
WS_PORT=8443
WS_SNI=
WS_PATH=/autoconnect

DEFAULT_DAYS=${DEFAULT_DAYS}
DEFAULT_GB=${DEFAULT_GB}
MAX_CONFIGS_PER_USER=2

DATABASE_PATH=${STATE_DIR}/bot.db
XRAY_CONFIG_PATH=${XRAY_CONFIG}
XRAY_BINARY=/usr/local/bin/xray
XRAY_API_ADDRESS=127.0.0.1:10085
XRAY_SERVICE=xray
LOG_LEVEL=INFO
ENV
chmod 600 "$ENV_FILE"

blue "==> 7/8  Registering the systemd service"
install -m 0644 "$SOURCE_DIR/autovless-bot.service" /etc/systemd/system/autovless-bot.service
systemctl daemon-reload
systemctl enable --now autovless-bot
sleep 2
green "    autovless-bot is $(systemctl is-active autovless-bot)"

blue "==> 8/8  Firewall"
if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow "${REALITY_PORT}/tcp" >/dev/null || true
  green "    opened ${REALITY_PORT}/tcp in ufw"
else
  warn "    ufw is not active, skipping. Open ${REALITY_PORT}/tcp in your provider firewall."
fi

cat <<SUMMARY

$(green "Done.")

  Bot            @$(curl -fsS "https://api.telegram.org/bot${BOT_TOKEN}/getMe" | jq -r '.result.username // "unknown"')
  Server host    ${SERVER_HOST}
  Reality port   ${REALITY_PORT}
  Reality SNI    ${REALITY_SNI}
  Public key     ${REALITY_PUBLIC_KEY}
  Short id       ${REALITY_SHORT_ID}
  Settings       ${ENV_FILE}
  Database       ${STATE_DIR}/bot.db

  Logs           journalctl -u autovless-bot -f
                 journalctl -u xray -f
  Restart        systemctl restart autovless-bot
  Xray status    systemctl status xray --no-pager

  Open Telegram, send /start to your bot, then /id to confirm you are the admin.

SUMMARY
