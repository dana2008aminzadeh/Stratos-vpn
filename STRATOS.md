# Stratos VPN — Custom build of v2rayNG

A fully rebranded, account-based VPN client built on top of v2rayNG (GPLv3).
**Beyond the sky.**

## What changed vs upstream v2rayNG

| Area | Stratos VPN |
|---|---|
| Package id | `com.stratos.vpn` — installs side-by-side with the original v2rayNG |
| Name / icons | Stratos VPN + custom “planet & orbit” brand (adaptive icon, in-app icon set) |
| Theme | Dark-space Material 3 theme (indigo/sky/cyan), Persian-first with full RTL |
| Entry point | Login first: username `fyx…` (4–8 chars) + password (5–10), or QR login |
| Backend | Panel API (`docs/STRATOS_API.md`) with built-in **offline demo backend** |
| Subscription link | Never shown — servers arrive via the API and are imported internally |
| Device limit | 1 device per account (login returns device-conflict when bound elsewhere) |
| Home screen | Connect button with remaining-data ring, original/secondary IP cards, country & DNS chips, plan / live / expired bottom panel with a live speed sparkline |
| Servers | Country list with per-server **real ping**, “ping all”, tags (Gaming, …) and a fixed **auto-connect** button that picks the fastest server |
| DNS | User-selectable presets: default / ad-blocking / family-safe / gaming |
| Notification | Mandatory foreground notification with live up/down speeds + stop action |
| Settings | Full engine settings (LAN, IPv6, …) + **operator-forced settings** on sync + one-tap “apply optimal settings” |
| Usage accounting | Measured in-app (proxy byte counters), counted against the plan, reported to the panel every ~30 s; expired volume/time ⇒ instant disconnect + reconnect blocked, renewal/refresh UI shown |
| Sync | Account snapshot every 2 min; fleet + operator config every ~10 min |
| Expired users | Can ping and update servers, **cannot connect** |

## Repository layout

```
V2rayNG/app/src/main/java/com/v2ray/ang/
├─ stratos/                 # account layer (no UI)
│  ├─ StratosModels.kt       # DTOs (User, Server, AdminConfig, TrafficUpdate…)
│  ├─ StratosValidators.kt   # fyx-username + password rules (pure, tested)
│  ├─ StratosQrLogin.kt      # QR login payload parser (pure, tested)
│  ├─ StratosApi.kt          # panel contract
│  ├─ StratosDemoApi.kt      # offline demo backend (any fyx user, 120 GiB / 30 d)
│  ├─ StratosHttpApi.kt      # real panel client (OkHttp+Gson)
│  ├─ StratosApiProvider.kt  # demo ⇄ http switch (blank base url ⇒ demo)
│  ├─ StratosSession.kt      # session store (MMKV + StateFlows)
│  ├─ StratosServersStore.kt # panel servers → v2rayNG profiles (hidden sub)
│  ├─ StratosTrafficEngine.kt# local usage accounting + quota gate (daemon side)
│  ├─ StratosSync.kt         # 2-minute user sync, heartbeat, fleet refresh
│  ├─ StratosSettingsController.kt # DNS presets + forced/best settings
│  └─ StratosBootstrap.kt    # first-run defaults (dark, fa, speeds, brand)
├─ ui/stratos/               # branded Compose screens
│  ├─ StratosLoginActivity(+Screen)
│  ├─ StratosHomeActivity(+Screen)   # ring, IPs, panels, drawer, dialogs
│  ├─ StratosServersActivity(+Screen)# countries, ping, tags, auto-connect
│  ├─ StratosDnsActivity
│  ├─ StratosSettingsActivity       # + “Advanced settings” (classic screens)
│  └─ StratosAboutActivity
└─ (classic v2rayNG UI kept intact for advanced settings)
```

## Demo mode

With no panel configured every `fyx????` username works (password 5–10 chars;
first login creates the demo account). Demo plan: 120 GiB / 30 days, one device.
Demo servers use clearly-marked `*.stratos-demo.internal` hosts — they parse and
appear everywhere, so the full UX (ping, auto-connect, quota, renewal) is
exercisable end-to-end.

## Switching to the real panel

1. Implement `docs/STRATOS_API.md` on the server (it is what the upcoming
   management panel will provide).
2. Point the app at it: set MMKV string `stratos_api_base` to the panel base URL
   (e.g. `https://panel.example.com`). Until removed, this can be done from a
   debug entry; afterwards it will be set at build time.

## Building

`.github/workflows/build.yml` builds release APKs on every push
(`universal`, `arm64-v8a`, `armeabi-v7a`, `x86_64`) and uploads them as artifacts.
If `APP_KEYSTORE_*` secrets exist they are used; otherwise a throwaway debug
keystore is generated so CI always produces signed APKs. Artifact name:
`StratosVPN_<version>_<abi>.apk`.

## Credits

- v2rayNG (GPLv3) — the entire proxy engine and advanced UI this builds on.
- Custom Stratos brand assets are generated in-repo
  (`fastlane/tools/gen_stratos_icons.py`).
