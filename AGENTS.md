# Deskly Agent Context

## Project Overview

Deskly is a two-part local-control system:

- Android app: discovers, pairs with, and controls a Windows PC over the local network.
- Windows PC app/server: WinForms tray app that exposes PC controls through TCP JSON and UDP discovery.

The apps are separate codebases in one repository. Keep orientation light and verify behavior in code before changing protocol or lifecycle logic.

## Android App Responsibilities

- Launch flow and device selection in `SplashActivity`.
- UDP discovery client in `DeviceDiscovery` using `DESKLY_DISCOVER` on UDP `5051`.
- Shared TCP client/session state in `DesklyClient`.
- Pairing/auth token storage in Android `SharedPreferences` under per-device keys.
- Main controls in `MainActivity`: volume/mute, display brightness, night mode, quiet mode, sleep/shutdown timer, lock/sleep/restart/shutdown.
- Settings controls in `SettingsActivity`: manual IP/port connect, pairing by PIN, secure ping, token clearing, Wake-on-LAN.

Known bug: Android sometimes requires manual Connect/Ping in Settings before PC controls work.

## Windows PC/Server Responsibilities

- WinForms app and tray lifecycle in `repos/Deskly/DesklyPC`.
- TCP newline-delimited JSON server on default port `5050` via `TcpJsonServer`.
- UDP discovery responder on port `5051` via `UdpDiscoveryServer`.
- Pairing PIN and token persistence via `PairingManager`.
- Request routing in `Form1.HandleIncomingRequest`.
- System controls through services/helpers:
  - `SystemActions`: power and audio actions.
  - `DisplayService`, `MonitorBrightness`, `WmiBrightnessService`: display discovery and brightness.
  - `NightModeService`, `NightOverlayForm`: night mode.
  - `QuietModeService`: quiet mode/power plan behavior.
  - `SleepTimerService`: one-shot sleep/shutdown timer.

## Important Directories

- `app/`: Android Kotlin app module.
- `app/src/main/java/com/example/deskly/`: Android activities, discovery, and TCP client.
- `app/src/main/res/`: Android layouts, drawables, values, launcher assets.
- `gradle/`: Gradle wrapper and version catalog.
- `repos/Deskly/`: Windows solution folder.
- `repos/Deskly/DesklyPC/`: WinForms PC app/server source.
- `docs/`: lightweight codebase context for future AI sessions.

## Important Files

- `settings.gradle.kts`: Android root project includes `:app`.
- `app/build.gradle.kts`: Android SDK/plugin/dependency config.
- `app/src/main/AndroidManifest.xml`: Android permissions and launcher activity.
- `app/src/main/java/com/example/deskly/DesklyClient.kt`: TCP client, request/response correlation, heartbeat.
- `app/src/main/java/com/example/deskly/DeviceDiscovery.kt`: UDP discovery client.
- `app/src/main/java/com/example/deskly/SplashActivity.kt`: scan/manual device selection.
- `app/src/main/java/com/example/deskly/MainActivity.kt`: primary Android control UI.
- `app/src/main/java/com/example/deskly/SettingsActivity.kt`: manual connection, pairing, ping, WOL.
- `repos/Deskly/Deskly.slnx`: Windows solution.
- `repos/Deskly/DesklyPC/DesklyPC.csproj`: WinForms `.NET net10.0-windows` project.
- `repos/Deskly/DesklyPC/Form1.cs`: PC UI plus JSON request router.
- `repos/Deskly/DesklyPC/PairingManager.cs`: PIN/token auth.
- `repos/Deskly/DesklyPC/TcpJsonServer.cs`: TCP server.
- `repos/Deskly/DesklyPC/UdpDiscoveryServer.cs`: UDP discovery responder.

## Build/Run Notes

- Android: Gradle wrapper is present. Likely build command: `.\gradlew.bat :app:assembleDebug`.
- Android config: `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`, Kotlin/JVM target `11`.
- Windows: solution is under `repos/Deskly`; likely build command from that directory: `dotnet build Deskly.slnx`.
- Windows requires a .NET SDK that supports `net10.0-windows`. Needs verification on the local machine.
- PC app auto-starts TCP server and UDP discovery in `Form1.Shown`; closing the window normally hides to tray.

## Coding Rules

- Inspect both Android and Windows sides before changing any JSON message, payload, response, auth, or connection behavior.
- Keep protocol changes backward-aware: Android and PC server must stay in sync.
- Prefer small reliability fixes over broad UI/server rewrites.
- Preserve per-device token key behavior and legacy token migration unless intentionally replacing auth.
- Keep local network assumptions explicit; current transport is plain TCP JSON plus UDP discovery.
- Add or update docs when changing protocol, pairing, lifecycle, or ports.

## Do Not Change Without Approval

- Default TCP/UDP ports (`5050` TCP, `5051` UDP).
- Pairing/token security model.
- JSON message names and payload/response shapes.
- Android package/application identity (`com.example.deskly`) or persisted preference keys.
- Windows token storage location/format in `%APPDATA%\DesklyPC`.
- Power actions, sleep/shutdown timer behavior, quiet mode behavior, or brightness mechanisms.
- The Android/Windows split as separate apps.

