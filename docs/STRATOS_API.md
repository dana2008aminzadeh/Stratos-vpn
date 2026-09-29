# Stratos VPN — Panel API Contract (v1)

The app talks to the management panel over HTTPS + JSON with a Bearer token.
All datetimes are Unix epoch **milliseconds** (epoch **seconds** are also accepted and
converted automatically). Until the panel ships, the app runs the same contract against
the built‑in offline demo backend (`StratosDemoApi`).

Base URL: configured in `stratos_api_base` (MMKV). Blank ⇒ demo mode.

---

## 1. Auth

### `POST {base}/api/v1/auth/login`
Request:
```json
{
  "username": "fyx12345",          // starts with "fyx", 4–8 chars
  "password": "secret",            // 5–10 chars
  "device_id": "and-xxxx",         // stable anonymous device id
  "device_name": "Pixel 8"
}
```
Response `200`:
```json
{
  "token": "…",
  "user":  { /* User object */ },
  "admin": { /* AdminConfig object */ }
}
```
Errors:
- `401/403` — bad credentials
- `409 + {"error":"device_conflict"}` — account already bound to another device.
  The app shows: «این حساب روی دستگاه دیگری فعال است…»

### `POST {base}/api/v1/auth/logout`  (Bearer)
Releases the device binding. Best effort.

### `POST {base}/api/v1/auth/heartbeat`  (Bearer)
```json
{ "device_id": "and-xxxx" }
```
- `200` — token still bound to this device
- `401/403` — session moved ⇒ the app signs out locally and shows the kicked dialog

### `POST {base}/api/v1/auth/password`  (Bearer)
```json
{ "current_password": "old", "new_password": "new" }
```

---

## 2. Account

### `GET {base}/api/v1/user/me`  (Bearer) — User object
```json
{
  "username": "fyx12345",
  "status": "active",               // active | expired | disabled | limited
  "data_limit_bytes": 128849018880, // 0 or negative = unlimited
  "used_bytes": 12345,
  "expire_at": 1760000000000,       // 0 = never
  "device_limit": 1
}
```
The app treats anything except `active` (+time +data ok) as non-connectable.
Ping & update keep working for expired users; connecting is blocked.

### `POST {base}/api/v1/usage`  (Bearer) — **local usage accounting**
The app measures proxy traffic itself (many upstream nodes can't count it) and posts
throttled deltas; the response must be the fresh User object so the app stops
double-counting what the panel now knows:
```json
{ "bytes_up": 123456, "bytes_down": 654321 }
```

---

## 3. Fleet

### `GET {base}/api/v1/servers`  (Bearer)
```json
[
  {
    "id": "de-1",
    "name": "Frankfurt 1",
    "country_code": "de",
    "country_name": "Germany",
    "config": "vless://uuid@host:443?...#%F0%9F%87%A9%F0%9F%87%AA%20Frankfurt%201",
    "tags": ["Gaming", "نیم‌بها"]
  }
]
```
`config` is any share link the app can parse (vless/vmess/ss/trojan/socks/wireguard/hy2).
It is imported internally and **never displayed** — the user has no access to a
subscription link anywhere in the app.

---

## 4. Operator config

### `GET {base}/api/v1/config`
```json
{
  "renew_url": "https://shop.example.com/renew",      // «تمدید» button target
  "website_url": "https://www.example.com",
  "telegram_url": "https://t.me/yourbot",
  "notice": "",
  "forced_settings": {"pref_proxy_sharing_enabled": "true"},  // applied on every sync
  "best_settings":  {"pref_mode": "VPN", "pref_ipv6_enabled": "false"},
  "min_supported_version_code": 1
}
```
- `forced_settings` — pushed on login and every 2-minute sync (operator wins).
- `best_settings` — applied when the user taps «اعمال تنظیمات بهینه».
- Allowed keys are AppConfig `PREF_*` names (whitelist in
  `StratosSettingsController.ALLOWED_KEYS`), including:
  `pref_proxy_sharing_enabled` (LAN), `pref_ipv6_enabled`, `pref_prefer_ipv6`,
  `pref_mode`, `pref_remote_dns`, `pref_domestic_dns`, `pref_vpn_dns`, `pref_speed_enabled`,
  `pref_per_app_proxy`, `pref_bypass_apps`, Mux/Fragment/Sniffing family, …

---

## Sync & enforcement behavior (app side)

| Loop | Interval | Runs in |
|---|---|---|
| User snapshot + heartbeat | 2 min | UI process (while app alive) |
| Fleet + admin config | ~10 min | UI process |
| Usage report | ≥4 MiB or 30 s throttle | daemon process |
| Quota gate | every traffic sample (~3 s) | daemon process |

When volume/time runs out: the connection is cut immediately, reconnecting is blocked,
and the home screen swaps to the «تمدید / به‌روزرسانی» panel. Ping and fleet update stay
available for expired users.
