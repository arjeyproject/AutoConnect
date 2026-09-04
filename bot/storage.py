"""SQLite storage for the AutoConnect config generator bot (@AutoVlessBot).

One small file, no ORM, no migrations framework: the schema is created on first
run and every later change is an additive ALTER guarded by a try/except.
"""

from __future__ import annotations

import sqlite3
import threading
import time
import uuid as uuidlib
from dataclasses import dataclass
from typing import Optional

_LOCK = threading.RLock()

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    telegram_id   INTEGER PRIMARY KEY,
    username      TEXT,
    first_name    TEXT,
    language      TEXT    NOT NULL DEFAULT 'fa',
    is_blocked    INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL,
    last_seen_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS configs (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    telegram_id   INTEGER NOT NULL,
    uuid          TEXT    NOT NULL UNIQUE,
    email         TEXT    NOT NULL UNIQUE,
    kind          TEXT    NOT NULL,
    remark        TEXT    NOT NULL,
    quota_bytes   INTEGER NOT NULL DEFAULT 0,
    used_bytes    INTEGER NOT NULL DEFAULT 0,
    expires_at    INTEGER NOT NULL DEFAULT 0,
    revoked       INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL,
    FOREIGN KEY (telegram_id) REFERENCES users (telegram_id)
);

CREATE INDEX IF NOT EXISTS idx_configs_owner ON configs (telegram_id);
CREATE INDEX IF NOT EXISTS idx_configs_live  ON configs (revoked, expires_at);

CREATE TABLE IF NOT EXISTS events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    telegram_id INTEGER,
    kind        TEXT    NOT NULL,
    detail      TEXT,
    created_at  INTEGER NOT NULL
);
"""


@dataclass
class Config:
    id: int
    telegram_id: int
    uuid: str
    email: str
    kind: str
    remark: str
    quota_bytes: int
    used_bytes: int
    expires_at: int
    revoked: int
    created_at: int

    @property
    def expired(self) -> bool:
        return self.expires_at > 0 and self.expires_at <= int(time.time())

    @property
    def over_quota(self) -> bool:
        return self.quota_bytes > 0 and self.used_bytes >= self.quota_bytes

    @property
    def active(self) -> bool:
        return not self.revoked and not self.expired and not self.over_quota

    @property
    def days_left(self) -> int:
        if self.expires_at <= 0:
            return -1
        return max(0, int((self.expires_at - time.time()) // 86400))


class Storage:
    def __init__(self, path: str) -> None:
        self.path = path
        self._db = sqlite3.connect(path, check_same_thread=False)
        self._db.row_factory = sqlite3.Row
        with _LOCK:
            self._db.executescript(SCHEMA)
            self._db.execute("PRAGMA journal_mode=WAL")
            self._db.commit()

    # ---------------------------------------------------------------- users
    def touch_user(self, telegram_id: int, username: str = "", first_name: str = "") -> None:
        now = int(time.time())
        with _LOCK:
            self._db.execute(
                """INSERT INTO users (telegram_id, username, first_name, created_at, last_seen_at)
                   VALUES (?, ?, ?, ?, ?)
                   ON CONFLICT(telegram_id) DO UPDATE SET
                     username = excluded.username,
                     first_name = excluded.first_name,
                     last_seen_at = excluded.last_seen_at""",
                (telegram_id, username or "", first_name or "", now, now),
            )
            self._db.commit()

    def set_language(self, telegram_id: int, language: str) -> None:
        with _LOCK:
            self._db.execute("UPDATE users SET language = ? WHERE telegram_id = ?", (language, telegram_id))
            self._db.commit()

    def language(self, telegram_id: int, default: str = "fa") -> str:
        with _LOCK:
            row = self._db.execute("SELECT language FROM users WHERE telegram_id = ?", (telegram_id,)).fetchone()
        return row["language"] if row else default

    def is_blocked(self, telegram_id: int) -> bool:
        with _LOCK:
            row = self._db.execute("SELECT is_blocked FROM users WHERE telegram_id = ?", (telegram_id,)).fetchone()
        return bool(row and row["is_blocked"])

    def set_blocked(self, telegram_id: int, blocked: bool) -> None:
        with _LOCK:
            self._db.execute("UPDATE users SET is_blocked = ? WHERE telegram_id = ?", (1 if blocked else 0, telegram_id))
            self._db.commit()

    def all_user_ids(self) -> list:
        with _LOCK:
            rows = self._db.execute("SELECT telegram_id FROM users WHERE is_blocked = 0").fetchall()
        return [r["telegram_id"] for r in rows]

    # -------------------------------------------------------------- configs
    def create_config(self, telegram_id: int, kind: str, remark: str, quota_bytes: int, ttl_days: int) -> Config:
        now = int(time.time())
        new_uuid = str(uuidlib.uuid4())
        email = f"u{telegram_id}-{now % 100000}@autoconnect"
        expires_at = now + ttl_days * 86400 if ttl_days > 0 else 0
        with _LOCK:
            cursor = self._db.execute(
                """INSERT INTO configs
                     (telegram_id, uuid, email, kind, remark, quota_bytes, used_bytes, expires_at, revoked, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, 0, ?, 0, ?)""",
                (telegram_id, new_uuid, email, kind, remark, quota_bytes, expires_at, now),
            )
            self._db.commit()
            row = self._db.execute("SELECT * FROM configs WHERE id = ?", (cursor.lastrowid,)).fetchone()
        return Config(**dict(row))

    def configs_for(self, telegram_id: int, include_dead: bool = False) -> list:
        query = "SELECT * FROM configs WHERE telegram_id = ?"
        if not include_dead:
            query += " AND revoked = 0"
        query += " ORDER BY id DESC"
        with _LOCK:
            rows = self._db.execute(query, (telegram_id,)).fetchall()
        return [Config(**dict(r)) for r in rows]

    def config_by_uuid(self, value: str) -> Optional[Config]:
        with _LOCK:
            row = self._db.execute("SELECT * FROM configs WHERE uuid = ?", (value,)).fetchone()
        return Config(**dict(row)) if row else None

    def config_by_id(self, config_id: int) -> Optional[Config]:
        with _LOCK:
            row = self._db.execute("SELECT * FROM configs WHERE id = ?", (config_id,)).fetchone()
        return Config(**dict(row)) if row else None

    def live_configs(self) -> list:
        with _LOCK:
            rows = self._db.execute("SELECT * FROM configs WHERE revoked = 0").fetchall()
        return [Config(**dict(r)) for r in rows]

    def active_count(self, telegram_id: int) -> int:
        return len([c for c in self.configs_for(telegram_id) if c.active])

    def revoke(self, config_id: int) -> None:
        with _LOCK:
            self._db.execute("UPDATE configs SET revoked = 1 WHERE id = ?", (config_id,))
            self._db.commit()

    def set_usage(self, email: str, used_bytes: int) -> None:
        with _LOCK:
            self._db.execute("UPDATE configs SET used_bytes = ? WHERE email = ?", (used_bytes, email))
            self._db.commit()

    def extend(self, config_id: int, days: int) -> None:
        now = int(time.time())
        with _LOCK:
            row = self._db.execute("SELECT expires_at FROM configs WHERE id = ?", (config_id,)).fetchone()
            if not row:
                return
            base = max(row["expires_at"], now) if row["expires_at"] else now
            self._db.execute("UPDATE configs SET expires_at = ? WHERE id = ?", (base + days * 86400, config_id))
            self._db.commit()

    def add_quota(self, config_id: int, extra_bytes: int) -> None:
        with _LOCK:
            self._db.execute("UPDATE configs SET quota_bytes = quota_bytes + ? WHERE id = ?", (extra_bytes, config_id))
            self._db.commit()

    # ---------------------------------------------------------------- stats
    def stats(self) -> dict:
        with _LOCK:
            users = self._db.execute("SELECT COUNT(*) c FROM users").fetchone()["c"]
            total = self._db.execute("SELECT COUNT(*) c FROM configs").fetchone()["c"]
            live = self._db.execute("SELECT COUNT(*) c FROM configs WHERE revoked = 0").fetchone()["c"]
            traffic = self._db.execute("SELECT COALESCE(SUM(used_bytes), 0) s FROM configs").fetchone()["s"]
            day_ago = int(time.time()) - 86400
            new_today = self._db.execute("SELECT COUNT(*) c FROM configs WHERE created_at >= ?", (day_ago,)).fetchone()["c"]
            active_today = self._db.execute("SELECT COUNT(*) c FROM users WHERE last_seen_at >= ?", (day_ago,)).fetchone()["c"]
        return {
            "users": users,
            "configs_total": total,
            "configs_live": live,
            "traffic_bytes": traffic,
            "configs_24h": new_today,
            "users_24h": active_today,
        }

    def log(self, telegram_id: Optional[int], kind: str, detail: str = "") -> None:
        with _LOCK:
            self._db.execute(
                "INSERT INTO events (telegram_id, kind, detail, created_at) VALUES (?, ?, ?, ?)",
                (telegram_id, kind, detail, int(time.time())),
            )
            self._db.commit()
