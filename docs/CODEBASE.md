# Codebase Notes

## Directory Map

- `app/`: Android Kotlin app.
- `app/src/main/java/com/example/deskly/`: Android source.
- `app/src/main/res/`: Android XML layouts/resources.
- `repos/Deskly/DesklyPC/`: Windows WinForms PC app/server.
- `repos/Deskly/Deskly.slnx`: Windows solution file.
- `gradle/`, `gradlew`, `gradlew.bat`: Android build wrapper.

## Build Requirements

- Android uses the Gradle wrapper in the repository. Recommended command from repo root: `.\gradlew.bat :app:assembleDebug`.
- DesklyPC currently targets `net10.0-windows` in `DesklyPC.csproj` and the checked-in publish profile also targets `net10.0-windows`; use a .NET 10 SDK or newer that supports the `net10.0-windows` target.
- A .NET 10 runtime alone is not enough for local builds. The build machine must have the .NET 10 SDK installed.
- Local SDK 8.x fails with `NETSDK1045` because it cannot build a `net10.0-windows` project. On this machine, `dotnet --info` currently reports SDK `8.0.421`, so `dotnet build repos\Deskly\DesklyPC\DesklyPC.csproj` is expected to fail until the .NET 10 SDK is installed. Do not downgrade the target framework just to satisfy an older local SDK.
- Recommended PC restore command from repo root: `dotnet restore repos/Deskly/DesklyPC/DesklyPC.csproj`.
- Recommended PC build command from repo root: `dotnet build repos/Deskly/DesklyPC/DesklyPC.csproj -c Release`.
- `Deskly.slnx` is the newer XML solution format. Build it only with a `dotnet`/MSBuild/Visual Studio version that supports `.slnx`; older SDKs may fail with `MSB4068` on the `<Solution>` element.
- On machines with only .NET SDK 8, a source-level compatibility check can be done with MSBuild property overrides, but that is not the official target build.

## Windows Host Publish

Run publish commands from the repository root on Windows with a .NET 10-capable SDK installed.

Framework-dependent publish:

```powershell
dotnet publish repos/Deskly/DesklyPC/DesklyPC.csproj -c Release -r win-x64 --self-contained false
```

- Output: `repos/Deskly/DesklyPC/bin/Release/net10.0-windows/win-x64/publish/`
- Smaller output.
- Requires the matching .NET runtime/Windows Desktop runtime on the target PC.

Self-contained publish:

```powershell
dotnet publish repos/Deskly/DesklyPC/DesklyPC.csproj -c Release -r win-x64 --self-contained true
```

- Output: `repos/Deskly/DesklyPC/bin/Release/net10.0-windows/win-x64/publish/`
- Larger output.
- Includes runtime bits so the target PC does not need a separate .NET runtime install.

Legacy `.exe` build artifacts under `repos/Deskly/DesklyPC/bin/` and `repos/Deskly/DesklyPC/obj/` were removed during stabilization. Create fresh executables through `dotnet publish`; do not reuse old publish folders.

The Visual Studio publish profile at `repos/Deskly/DesklyPC/Properties/PublishProfiles/FolderProfile.pubxml` publishes a self-contained `win-x64` build to `C:\Users\rajno\OneDrive\Desktop\DesklyPC_PUBLISH`. Prefer the CLI commands above for reproducible local builds unless that profile path is intentionally desired.

## Android Architecture

- `SplashActivity` is the launcher. It scans for PCs over UDP, allows manual IP/port entry, and saves selected device metadata to `SharedPreferences`.
- `DeviceDiscovery` sends a UDP broadcast magic string and parses `discover_response`.
- `DesklyClient` is a singleton TCP client. It owns socket state, listeners, request IDs, pending responses, secure ping, and heartbeat.
- `MainActivity` is the primary Home dashboard. It keeps quick controls first: connection status, volume, brightness, Night Mode, sleep timer, PC actions, and compact system controls. It auto-connects/auths on resume using saved IP/port/token when Auto Connect is enabled.
- Mouse/touchpad settings live in Android `SharedPreferences`: cursor speed, scroll sensitivity, natural scroll direction, visual feedback, acceleration, left-handed mode, and reserved gyro sensitivity. Cursor movement is filtered locally before sending `mouse_move`.
- Keyboard mode sends text through Unicode `keyboard_text`, special keys through `keyboard_key`, and simple modifier shortcuts through `keyboard_shortcut`. Android can send text as one block or split by Unicode code point for per-character input.
- Performance policy is centralized in `PerformancePolicy`: Low Power mode and Android system Battery Saver increase heartbeat/reconnect/poll intervals and reduce pointer send cadence. MainActivity stops timer polling and local countdown work while paused.
- `SettingsActivity` is organized as settings sections: Connection, Night Mode, Shutdown, and Advanced. Manual connect/disconnect, secure ping, pairing reset, Night Mode defaults, restore-brightness-on-exit, protocol info, unsupported feature info, and Wake-on-LAN live in Advanced-style controls.
- Token lookup/migration logic is duplicated between `MainActivity`, `SettingsActivity`, and `SplashActivity`.

## Windows Architecture

- `Program` creates the tray icon and runs `Form1`.
- `Form1` owns UI state, starts/stops server/discovery, generates pairing PINs, and routes JSON requests.
- `TcpJsonServer` accepts TCP clients and reads/writes one JSON object per line.
- `UdpDiscoveryServer` responds to `DESKLY_DISCOVER` broadcasts.
- `PairingManager` creates one-time PINs, validates tokens, persists tokens, and prunes old tokens.
- PC capabilities are split across helper services: `SystemActions`, `DisplayService`, `MonitorBrightness`, `WmiBrightnessService`, `NightModeService`, `QuietModeService`, and `SleepTimerService`.
- Windows Host is tray-first: minimizing hides to tray, closing the window hides to tray during normal user close, and only explicit Exit performs full cleanup and process exit.

## Communication Protocol

- Discovery: Android sends UTF-8 text `DESKLY_DISCOVER` to broadcast `255.255.255.255:5051`.
- Discovery response: PC returns JSON with `type: "discover_response"`, `protocolVersion`, `id`, `name`, `ip`, `port`, and `udpPort`.
- Control channel: TCP on default port `5050`, UTF-8, newline-delimited JSON.
- Request shape in Android: `{ "rid": "...", "type": "...", "protocolVersion": 1, "payload": { ... } }`.
- Server echoes `rid` in responses when present.
- Missing protocol versions are treated as legacy v1; explicit unsupported versions are rejected before command dispatch.
- Secure requests include `payload.token`; the server validates it with `PairingManager.IsTokenValid`.
- Command metadata is mirrored in Android `DesklyCommands` and PC `DesklyProtocol.Commands`. It records known request types, expected response types, broad command groups, token requirements, and legacy command status without changing the wire protocol.
- Automatic video detection is represented by `video_list` / `video_list_response`, but the current Windows host intentionally returns `supported: false` with `fallback: "media_remote"`. No screen scraping, browser inspection, or media-session integration is active yet.
- Bluetooth is not an active transport. Android models it only as an unavailable/fallback state in `TransportAvailability`; LAN remains the only implemented connection path.
- No TLS/encryption is visible in code. Treat communication as local-network only.

## Pairing/Auth/Token Flow

- On PC, clicking Pair generates a six-digit PIN valid for 120 seconds.
- Android sends `pair_request` with `payload.pin`.
- PC `PairingManager.TryPair` consumes a valid PIN and returns a generated token in `pair_response.data.token`.
- Tokens are generated from 32 random bytes, base64url-like encoded.
- PC stores tokens in `%APPDATA%\DesklyPC\tokens.json` and prunes tokens older than 180 days.
- Android stores tokens in `SharedPreferences` as `auth_token__<deviceKey>`, with legacy migration from `auth_token` and raw discovery IDs.
- Android sends `auth_request` with `payload.token`; PC returns `auth_response`.

## Connection Lifecycle

- PC app starts TCP server and UDP discovery automatically when `Form1` is shown.
- PC close button hides to tray unless the app is explicitly exited.
- Android launch scans first, then saves discovered or manual IP/port.
- `MainActivity.onResume` calls `ensureConnectedAndAuthed`.
- If Auto Connect is enabled and a saved server exists, `MainActivity` retries transient connect failures with bounded backoff. It stops retrying on explicit auth rejection.
- After auth, Android initializes state by sending `volume_get`, `display_list`, `brightness_get`, `night_get`, `quiet_get`, `power_plan_get`, and `sleep_timer_status`.
- If restore-brightness-on-exit is enabled, `MainActivity` remembers the first received brightness values and sends them back with authenticated `brightness_set` requests during normal activity destruction.
- `DesklyClient` heartbeat sends `ping_secure` every 10 seconds after authorization and disconnects after 3 failures.
- In Low Power mode, Battery Saver, or background state, `DesklyClient` heartbeat uses a 30 second interval instead of 10 seconds.
- If heartbeat receives `Unauthorized`, Android moves to the auth-failed/pair-required path instead of silently looping reconnects.
- PC TCP traffic debug logging summarizes message type/rid/version/ok only; it does not dump raw JSON, tokens, pairing responses, or clipboard payloads.
- Android performance diagnostics are opt-in through Settings and log summarized command throughput, heartbeat latency, pointer throttling, and dropped move counts.
- PC performance diagnostics are opt-in through `DESKLY_PERF_DIAGNOSTICS=1` and summarize request throughput plus process CPU/working-set samples.

## Key JSON Message Types

- Pairing/auth: `pair_status`, `pair_request`, `auth_request`, `ping_secure`.
- Discovery response: `discover_response`.
- Display/capability: `display_list`, `capabilities_external_brightness`.
- Display control: `display_control`, `display_mode_set`.
- Brightness: `brightness_get`, `brightness_set`.
- Audio: `volume_get`, `volume_set`, `mute_toggle`.
- Night mode: `night_get`, `night_set`.
- Quiet mode: `quiet_get`, `quiet_set`.
- Power plan: `power_plan_get`, `power_plan_set`.
- Timer: `sleep_timer_set`, `sleep_timer_cancel`, `sleep_timer_status`.
- Power: `power_lock`, `power_sleep`, `power_shutdown`, `power_restart`.
- Mouse: `mouse_move`, `mouse_click`, `mouse_scroll`, `mouse_button`.
- Keyboard: `keyboard_text`, `keyboard_key`, `keyboard_shortcut`, plus semantic `shortcut_action`.
- Media: semantic `media_action`, with legacy `media_key` still accepted by the PC host.
- Video detection: `video_list` returns `video_list_response`; currently safe fallback only, with `data.supported = false`, empty `videos`, and `fallback = "media_remote"`.
- Open actions: `app_shortcuts_get`, `app_catalog_get`, `app_shortcut_set`, `app_open`, `app_windows_get`, `app_switch`, `web_open`.
- Common responses include `pair_response`, `auth_response`, `display_list_response`, `display_control_response`, `display_mode_response`, `brightness_response`, `audio_response`, `night_response`, `quiet_response`, `power_plan_response`, `sleep_timer_response`, `sleep_timer_status_response`, `power_response`, `app_response`, `web_response`, and generic `response`.

## Newer Control Payloads

- `night_set` remains backward-compatible with `enabled` and `intensity`. New Android clients may also send `mode`, `kelvin`, and `screenDim`.
- `intensity` is the strength of the color-temperature filter.
- `screenDim` is a separate black overlay dim amount and is not treated as color warmth.
- `power_shutdown` and shutdown sleep timers may include `fadeOutVolume: true` to fade system audio before shutdown.
- If `fadeOutVolume` is absent, the server keeps legacy behavior and does not fade. Android sends the user's saved preference when issuing shutdown commands.
- `display_control` currently supports `action: "turn_off"` through Windows monitor power messages.
- `display_control` also supports `action: "turn_on"` through the same Windows monitor power API.
- `display_mode_set` supports Windows projection modes: `internal`, `duplicate`, `extend`, and `external`.
- `power_plan_get` and `power_plan_set` expose stable Windows power plans when available: `power_saver`, `balanced`, and `high_performance`. Android labels these as Low, Balanced, and Max.
- `shortcut_action` uses semantic IDs grouped as system and browser actions. Browser actions include back/forward, refresh, new/close/switch tab, fullscreen, page scroll, and zoom. Windows mappings are executed by the PC host; macOS mappings are kept explicit in the protocol model for compatibility. Zoom in/out uses browser-friendly Ctrl+mouse-wheel injection on Windows; reset remains Ctrl+0. Android maps accepted two-finger pinch gestures on the touchpad to the existing zoom presets and keeps two-finger scroll separate.
- Web Remote reuses `shortcut_action` for Chrome/Firefox/Opera-compatible browser controls: back/forward, refresh, new/close tab, previous/next tab, page scroll, and fullscreen. These shortcuts act on the focused browser/window.
- `media_action` uses semantic media IDs: `play_pause`, `previous`, `next`, `volume_down`, `volume_up`, `mute`, `seek_backward`, `seek_forward`, and `fullscreen`. The PC host maps them to Windows media/volume keys or active-window shortcuts. Legacy `media_key` remains accepted for backward compatibility.
- `mouse_button` accepts `payload.button = "left" | "right" | "middle"` and `payload.down = true | false`. Android uses it for drag/drop by sending button down on Drag and button up on Drop; the PC host executes it through the same SendInput mouse injection path as clicks.
- `keyboard_text` accepts Unicode `payload.text` and the PC host injects it with `KEYEVENTF_UNICODE`, preserving diacritics and dictated text that the Android IME inserted into the text box. `keyboard_key` and `keyboard_shortcut` remain virtual-key based for Enter, Backspace, Tab, Esc, arrows, and modifier shortcuts.
- `web_open` opens an explicit user-selected website on the PC through the default browser. Only absolute `http://` and `https://` URLs are accepted, and Android prefixes bare domains with `https://`.
- App Switcher uses Windows visible top-level windows only. Android requests `app_windows_get`, displays returned titles in a chooser, and sends `app_switch` with the opaque `windowId`. The PC host validates that the window still exists before trying to activate it. Window titles are response data for the feature, but logs avoid printing specific titles.
- Quiet Mode remains a separate on/off command via `quiet_get` and `quiet_set`; it is not direct fan speed control.
- Windows Host autostart is stored under the current user's `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` key as `Deskly Host`.

### API Contract Details

All secure requests keep the existing shape: `{ "rid": "...", "type": "...", "payload": { "token": "..." } }`.

- Turn off monitor: `display_control` with `payload.action = "turn_off"` returns `display_control_response` with `ok`, `message`, and `data.action/supported`.
- Turn on monitor: `display_control` with `payload.action = "turn_on"` returns `display_control_response` with `ok`, `message`, and `data.action/supported`.
- Windows projection mode: `display_mode_set` with `payload.mode = "internal" | "duplicate" | "extend" | "external"` returns `display_mode_response` with `ok`, `message`, and `data.mode/supported`.
- Power plan status: `power_plan_get` returns `power_plan_response` with `ok`, `message`, and `data.plan/name/supported/plans`.
- Power plan change: `power_plan_set` with `payload.plan = "power_saver" | "balanced" | "high_performance"` returns `power_plan_response` with current status and supported plan list.
- Night Mode: `night_set` accepts old payloads plus `mode`, `kelvin`, `intensity`, and `screenDim`; response is `night_response` with `ok`, `message`, and `data.enabled/intensity/kelvin/screenDim`.
- Power shutdown: `power_shutdown` may include `payload.fadeOutVolume`; response is `power_response` with `ok`, `message`, and `data.action/fadeOutVolume`.
- New Android power requests include `issuedAtUtcMs`; sleep, restart, and shutdown also include `confirmed: true` after the Android confirmation dialog. The PC host rejects stale timestamped requests and repeated dangerous power requests while still accepting older compatible clients that do not send these fields.
- Sleep timer: `sleep_timer_set` may include `payload.fadeOutVolume` for shutdown timers; response includes timer state and the accepted fade flag.

## Unsupported / Limited Features

- Physical HDMI/DisplayPort monitor input switching is not implemented. Windows has no universal stable API for arbitrary monitor input source switching.
- Fan speed control is not implemented. Generic fan control is OEM/hardware-specific and should stay unsupported unless a safe device-specific integration is added later. Low/Balanced/Max controls Windows power plans, not fan RPM directly.
- Power plan availability depends on the local Windows installation. Missing plans are returned as unsupported instead of shown as working actions.

## Current Technical Debt

- Connection reliability is improved with MainActivity reconnect backoff, but manual testing is still needed across Wi-Fi sleep, PC host restart, and invalid-token scenarios.
- `Form1.cs` is a large combined UI/server/router file and is marked as auto-generated in its first comment.
- Token/device preference logic is repeated across multiple Android activities.
- Android connection lifecycle is split between `SplashActivity`, `MainActivity`, `SettingsActivity`, and singleton `DesklyClient`.
- Many Windows service paths swallow exceptions; failure visibility is inconsistent.
- Some message handling uses generic `response` for `ping_secure` instead of a dedicated response type.
- Command metadata is now centralized, but `Form1.HandleIncomingRequest` still contains the concrete dispatch branches. A future low-risk refactor can use the registry when extracting router tests.
- `sleep_timer_cancel` server response uses `sleep_timer_response`; Android also has a branch for `sleep_timer_cancel_response`, which may be unused. Needs verification.
- Windows target `net10.0-windows` and Android `compileSdk 36` may require newer local SDKs. Needs verification.
