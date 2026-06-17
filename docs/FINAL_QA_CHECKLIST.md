# Deskly Final QA Checklist

Use this checklist after feature work or before packaging a build.

## Automated Validation

Run from `C:\Users\rajno\Documents\Projekty\Deskly`:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
dotnet --info
dotnet build repos\Deskly\DesklyPC\DesklyPC.csproj
```

The PC build requires the .NET 10 SDK because `DesklyPC.csproj` targets `net10.0-windows`. A local SDK 8.x failure with `NETSDK1045` is an environment limitation, not a source regression.

## Android Manual QA

- Fresh install: launch, discover PC over LAN, select device, pair by PIN.
- Reconnect: close and reopen Android app, verify saved PC name/address and automatic auth.
- Connection status: verify connected, disconnected, lost connection, and failed auth states are visible.
- Notifications: on Android 13+, deny and allow `POST_NOTIFICATIONS`; verify the app handles both safely.
- Hardware volume buttons: verify default mode changes phone volume; PC mode sends PC volume only while `MainActivity` is active.
- Clipboard sync: verify disabled by default; when enabled, text copy to PC requires an authenticated connection and never appears in logs.
- Power actions: verify disabled setting blocks buttons; sleep, restart, and shutdown show confirmation; repeated taps are rejected or ignored safely.
- App switcher: open several desktop windows, tap Apps > Windows, choose a window, and verify the PC attempts to focus it. Close a listed window before selecting it to verify a graceful failure.
- Media remote: test play/pause, previous, next, volume, mute, seek, and fullscreen against a browser/media app.
- Web remote: test browser actions on Chrome, Firefox, and Opera where installed.
- Touchpad/mouse/keyboard: test click, drag, scroll, text entry, shortcuts, and reconnect after network loss.

## Windows Manual QA

- Pairing PIN: generate a PIN and confirm the log does not print the PIN value.
- Token auth: verify unauthenticated clipboard, app, power, media, and video requests are rejected.
- Logs: confirm raw JSON, tokens, PINs, and clipboard contents are not logged.
- Power: verify stale timestamped commands and fast repeated dangerous commands are rejected.
- Unsupported behavior: `video_list` should return `supported: false` and `fallback: "media_remote"`.

## Compatibility Notes

- Windows is the only implemented PC host target.
- LAN/TCP is the only implemented transport; Bluetooth is currently an unavailable/fallback state.
- Android supports the declared min/target SDKs in `app/build.gradle.kts`; Android 13+ notification permission must be handled at runtime.
- Automatic video detection is not implemented; use Media Remote controls as fallback.
