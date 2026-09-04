"""Xray-core glue for the AutoConnect config generator bot.

Responsibilities:
  * add and remove VLESS clients in the live Xray config
  * build vless:// share links for the AutoConnect app and any other client
  * read per-user traffic counters through the Xray stats API

The bot never restarts Xray for a client change when the gRPC API is reachable:
it uses `xray api adu` / `xray api rmu` so existing sessions stay up. Editing the
JSON on disk keeps the change after a reboot.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import time
import urllib.parse
from typing import Optional

REALITY_TAG = "vless-reality"
WS_TAG = "vless-ws-tls"


class XrayError(RuntimeError):
    pass


class Xray:
    def __init__(
        self,
        config_path: str = "/usr/local/etc/xray/config.json",
        binary: str = "/usr/local/bin/xray",
        api_address: str = "127.0.0.1:10085",
        service: str = "xray",
    ) -> None:
        self.config_path = config_path
        self.binary = binary
        self.api_address = api_address
        self.service = service

    # ------------------------------------------------------------- low level
    def _run(self, args: list, timeout: int = 20) -> str:
        try:
            done = subprocess.run(
                [self.binary, *args],
                capture_output=True,
                text=True,
                timeout=timeout,
                check=False,
            )
        except FileNotFoundError as exc:
            raise XrayError(f"xray binary not found at {self.binary}") from exc
        except subprocess.TimeoutExpired as exc:
            raise XrayError("xray api call timed out") from exc
        if done.returncode != 0:
            raise XrayError((done.stderr or done.stdout).strip()[:400] or "xray api call failed")
        return done.stdout

    def load(self) -> dict:
        with open(self.config_path, "r", encoding="utf-8") as handle:
            return json.load(handle)

    def save(self, config: dict) -> None:
        """Atomic write plus a timestamped backup, so a bad edit is always recoverable."""
        directory = os.path.dirname(self.config_path) or "."
        os.makedirs(directory, exist_ok=True)
        if os.path.exists(self.config_path):
            shutil.copy2(self.config_path, f"{self.config_path}.bak")
        handle = tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=directory, delete=False)
        try:
            json.dump(config, handle, indent=2, ensure_ascii=False)
            handle.flush()
            os.fsync(handle.fileno())
        finally:
            handle.close()
        os.replace(handle.name, self.config_path)

    def inbound(self, config: dict, tag: str) -> Optional[dict]:
        for item in config.get("inbounds", []):
            if item.get("tag") == tag:
                return item
        return None

    def test_config(self) -> None:
        self._run(["run", "-test", "-config", self.config_path])

    def reload(self) -> None:
        subprocess.run(["systemctl", "restart", self.service], check=False, capture_output=True)

    # --------------------------------------------------------------- clients
    def add_client(self, tag: str, client_uuid: str, email: str, flow: str = "") -> None:
        config = self.load()
        inbound = self.inbound(config, tag)
        if inbound is None:
            raise XrayError(f"inbound '{tag}' is missing from {self.config_path}")
        clients = inbound.setdefault("settings", {}).setdefault("clients", [])
        if any(c.get("id") == client_uuid for c in clients):
            return
        entry = {"id": client_uuid, "email": email, "level": 0}
        if flow:
            entry["flow"] = flow
        clients.append(entry)
        self.save(config)
        self.test_config()
        try:
            payload = json.dumps({"id": client_uuid, "email": email, **({"flow": flow} if flow else {})})
            self._run(["api", "adu", f"--server={self.api_address}", f"-i={tag}", payload])
        except XrayError:
            self.reload()

    def remove_client(self, tag: str, email: str) -> None:
        config = self.load()
        inbound = self.inbound(config, tag)
        if inbound is None:
            return
        clients = inbound.setdefault("settings", {}).setdefault("clients", [])
        remaining = [c for c in clients if c.get("email") != email]
        if len(remaining) == len(clients):
            return
        inbound["settings"]["clients"] = remaining
        self.save(config)
        try:
            self._run(["api", "rmu", f"--server={self.api_address}", f"-i={tag}", email])
        except XrayError:
            self.reload()

    def client_emails(self, tag: str) -> list:
        inbound = self.inbound(self.load(), tag)
        if not inbound:
            return []
        return [c.get("email", "") for c in inbound.get("settings", {}).get("clients", [])]

    # ----------------------------------------------------------------- stats
    def traffic(self) -> dict:
        """Returns {email: uplink + downlink} in bytes. Empty dict when the API is off."""
        try:
            raw = self._run(["api", "statsquery", f"--server={self.api_address}", "--pattern=user>>>"])
        except XrayError:
            return {}
        try:
            parsed = json.loads(raw or "{}")
        except json.JSONDecodeError:
            return {}
        totals = {}
        for stat in parsed.get("stat", []) or []:
            name = stat.get("name", "")
            value = int(stat.get("value", 0) or 0)
            parts = name.split(">>>")
            if len(parts) >= 2 and parts[0] == "user":
                totals[parts[1]] = totals.get(parts[1], 0) + value
        return totals

    def server_uptime(self) -> str:
        try:
            with open("/proc/uptime", "r", encoding="utf-8") as handle:
                seconds = int(float(handle.read().split()[0]))
        except OSError:
            return "-"
        days, rest = divmod(seconds, 86400)
        hours, rest = divmod(rest, 3600)
        return f"{days}d {hours}h {rest // 60}m"


# --------------------------------------------------------------- share links
def reality_link(
    client_uuid: str,
    host: str,
    port: int,
    public_key: str,
    sni: str,
    short_id: str,
    remark: str,
    fingerprint: str = "chrome",
    flow: str = "xtls-rprx-vision",
) -> str:
    query = {
        "type": "tcp",
        "security": "reality",
        "encryption": "none",
        "pbk": public_key,
        "fp": fingerprint,
        "sni": sni,
        "sid": short_id,
        "spx": "/",
    }
    if flow:
        query["flow"] = flow
    return (
        f"vless://{client_uuid}@{host}:{port}?"
        + urllib.parse.urlencode(query, safe="/")
        + "#"
        + urllib.parse.quote(remark)
    )


def ws_tls_link(
    client_uuid: str,
    host: str,
    port: int,
    sni: str,
    path: str,
    remark: str,
    fingerprint: str = "chrome",
) -> str:
    query = {
        "type": "ws",
        "security": "tls",
        "encryption": "none",
        "host": sni,
        "sni": sni,
        "path": path,
        "fp": fingerprint,
        "alpn": "h2,http/1.1",
    }
    return (
        f"vless://{client_uuid}@{host}:{port}?"
        + urllib.parse.urlencode(query, safe="/")
        + "#"
        + urllib.parse.quote(remark)
    )


def human_bytes(value: int) -> str:
    if value <= 0:
        return "0 B"
    units = ["B", "KB", "MB", "GB", "TB"]
    index = 0
    size = float(value)
    while size >= 1024 and index < len(units) - 1:
        size /= 1024.0
        index += 1
    return f"{size:.1f} {units[index]}" if index else f"{int(size)} {units[index]}"


def human_left(expires_at: int) -> str:
    if expires_at <= 0:
        return "unlimited"
    remaining = expires_at - int(time.time())
    if remaining <= 0:
        return "expired"
    days, rest = divmod(remaining, 86400)
    if days:
        return f"{days}d {rest // 3600}h"
    return f"{rest // 3600}h {(rest % 3600) // 60}m"
