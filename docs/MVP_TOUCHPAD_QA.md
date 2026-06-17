# MVP Touchpad QA Checklist

## Pairing and Auth

- Valid token: Android shows Connected and touchpad commands move/click/scroll the PC.
- Invalid or revoked token: Android shows Pair Required/Auth Failed and PC input is not executed.
- Missing token: touchpad controls stay disabled and no command is sent.

## Mouse Gestures

- Mouse mode appears as a dedicated remote section and is usable without opening Settings.
- Single tap on touchpad: sends left click.
- Double tap on touchpad: sends left double click using `mouse_click` with `clicks: 2`; it must not also send two delayed single clicks.
- Long press on touchpad: sends right click and does not send a left click on release.
- Left/Right buttons send primary and secondary clicks.
- Double button sends a primary double click.
- Drag button sends `mouse_button` down, changes to Drop, then sends `mouse_button` up.
- Drag stays tied to the button that started it even if left-handed mode changes before Drop.
- Network/auth loss while dragging clears Android drag UI and does not crash.
- One-finger move: cursor moves smoothly without jumps.
- Two-finger move: page scrolls and cursor does not move.
- Two-finger pinch out/in: browser zooms in/out and cursor does not move.
- After two-finger scroll, returning to one-finger movement does not jump the cursor.
- Cursor speed slider persists and affects one-finger movement without making tiny jitter move the cursor.
- Acceleration toggle persists and changes one-finger movement scaling.
- Left-handed toggle persists and swaps primary/secondary click behavior.
- Scroll sensitivity persists and changes two-finger scroll step size.
- Natural scroll toggle persists; turning it off reverses two-finger scroll direction.
- Feedback toggle persists; turning it off disables touchpad surface pulse feedback.
- Help opens a compact gesture reference.
- Gyro mouse is not enabled because no sensor input lifecycle is currently present in the Android app.

## Sensitivity

- Minimum sensitivity (`0.25x`): cursor moves slowly and remains controllable.
- Default sensitivity (`1.0x`): cursor movement feels natural.
- Maximum sensitivity (`2.0x`): cursor moves faster without unstable jumps.
- Sensitivity persists after closing and reopening the Android app.

## Reliability

- Network loss while touching the pad: Android does not crash and stops sending mouse commands.
- Reconnect after network returns: touchpad works again without manual Ping in Settings when the device is already paired.
- Saved PC change: choosing another paired/saved PC reconnects to that PC instead of keeping the old authorized socket.
- Desktop server restart while Android stays open: Android reconnects/authenticates and touchpad works again.
- Firewall or server unavailable: Android does not crash and touchpad remains disabled/offline.
- Android backgrounding: touch gestures, drag state, reconnect callbacks, local timer countdown, and timer polling stop while MainActivity is paused.

## Performance / Battery

- Low Power setting persists and lengthens heartbeat, reconnect, mouse-move, and timer-poll intervals.
- Android system Battery Saver also activates the low-power policy.
- Timer status polling runs only while a timer is active and MainActivity is foregrounded.
- Performance Diagnostics setting is off by default and logs command throughput, heartbeat latency, pointer throttling, and dropped move counts only when enabled.
- PC host sleep-timer UI polling starts only while a sleep/shutdown timer is active.
- PC host performance diagnostics require `DESKLY_PERF_DIAGNOSTICS=1` and include request throughput, high-frequency command counts, CPU delta, and working set.

## Protocol Hardening

- Bad JSON payload for `mouse_move`, `mouse_click`, `mouse_scroll`, or `mouse_button`: server returns an error response and keeps running.
- Huge `dx`/`dy`: server clamps movement and keeps running.
- Huge `deltaX`/`deltaY`: server clamps scroll and keeps running.
- Invalid `clicks`: server clamps to `1..3`.
- Invalid mouse button names are rejected by the PC host without executing input.

## Keyboard MVP

- Text input: text typed in Android is inserted into the active PC window.
- Send: sends the text box as one Unicode `keyboard_text` block.
- Chars: sends the same text as per-code-point `keyboard_text` chunks for apps that behave better with individual character input.
- Voice: focuses the Android text box and opens the soft keyboard; dictated text can be sent after the keyboard inserts it.
- Voice unavailable: Android shows fallback messaging because voice dictation depends on the installed OS keyboard/IME.
- Diacritics: common Slovak/Czech characters are inserted correctly.
- Special characters and emoji are serialized as Unicode text instead of lossy keycodes.
- Enter: sends Enter to the active PC window.
- Delete: sends Backspace to the active PC window.
- Esc: closes/cancels in apps that support Esc.
- Tab: moves focus in forms.
- Arrow buttons: Up/Left/Down/Right move focus, caret, or slides in apps that support arrow keys.
- Copy: sends `Ctrl+C`.
- Paste: sends `Ctrl+V`.
- All: sends `Ctrl+A`.
- Switch: sends `Alt+Tab`.
- Invalid token: no keyboard input is executed on the PC.

## Shortcut Slots MVP

- Keys tab: Shortcuts section is visible under Keyboard.
- Set: each slot can be assigned one preset action from Android.
- Slot action persists after closing and reopening Android.
- Show Desktop: sends `Win+D`.
- Task Manager: sends `Ctrl+Shift+Esc`.
- Close Window: sends `Alt+F4`.
- Back: sends `Alt+Left`.
- Forward: sends `Alt+Right`.
- New Tab: sends `Ctrl+T`.
- Close Tab: sends `Ctrl+W`.
- Next Tab: sends `Ctrl+Tab`.
- Prev Tab: sends `Ctrl+Shift+Tab`.
- Refresh: sends `Ctrl+R`.
- Address Bar: sends `Ctrl+L`.
- Fullscreen: sends `F11`.
- Page Up/Down: sends `PageUp` / `PageDown`.
- Zoom In/Out: sends browser-friendly `Ctrl` + mouse wheel.
- Reset Zoom: sends `Ctrl+0`.
- Empty slots stay disabled.
- Invalid token: no shortcut action is executed on the PC.

## Display Settings MVP

- Restore Brightness off: closing Android does not change monitor brightness.
- Restore Brightness on: after an initial `brightness_get`, closing Android sends the first remembered brightness values back to the PC.
- Restore Brightness on, disconnected/unauthorized: Android does not crash and does not send unauthenticated brightness commands.

## Clipboard Send MVP

- Keys tab: Clipboard section is visible under Shortcuts.
- Copy to PC: explicit button copies Android text into the Windows clipboard.
- Empty text does not send a command.
- Long text is capped before sending.
- Clipboard text is not logged.
- Invalid token: PC clipboard is not changed.

## Media Remote MVP

- Media mode shows Play, Prev/Next, Volume Down/Up, Mute, Seek Back/Forward, and Fullscreen.
- Play: sends semantic `media_action` `play_pause` and toggles play/pause in the active media app or browser when that app supports Windows media keys.
- Prev: skips to the previous track when the active app supports it.
- Next: skips to the next track when the active app supports it.
- Volume Down/Up and Mute use Windows volume media keys.
- Seek Back/Forward use left/right key actions for active-player seek behavior.
- Fullscreen sends `F11` for active browser/player fullscreen behavior.
- Unsupported media actions return `media_response` failure and do not execute input.
- Legacy `media_key` requests still work for older Android clients.
- Invalid token: no media key is executed on the PC.
- Offline/reconnect: media buttons stay disabled while unauthorized and work again after reconnect/auth.

## Web Remote MVP

- Keys tab: Web Remote is visible under Keyboard.
- Back: sends `Alt+Left`.
- Forward: sends `Alt+Right`.
- Refresh: sends `Ctrl+R`.
- New Tab: sends `Ctrl+T`.
- Close Tab: sends `Ctrl+W`.
- Prev Tab: sends `Ctrl+Shift+Tab`.
- Next Tab: sends `Ctrl+Tab`.
- Page Up/Down: sends `PageUp` / `PageDown`.
- Fullscreen: sends `F11`.
- Chrome, Firefox, and Opera-compatible mappings reuse `shortcut_action` and the shared shortcut protocol.
- Browser shortcuts act on the focused browser/window; app-specific custom shortcut changes may affect behavior.
- Invalid token: no browser shortcut is executed on the PC.
- Offline/reconnect: web remote buttons stay disabled while unauthorized and work again after reconnect/auth.

## Presentation Remote MVP

- Slides tab is visible in the Android remote mode switcher.
- Start: starts slideshow with `F5` in PowerPoint/compatible apps.
- Next: advances one slide.
- Prev: goes back one slide.
- Black: toggles black screen with `B`.
- Exit: exits slideshow with `Esc`.
- Invalid token: no presentation key is executed on the PC.
- Offline/reconnect: slide buttons stay disabled while unauthorized and work again after reconnect/auth.

## App Shortcuts MVP

- PC host: `App Shortcuts` opens a local configuration dialog.
- PC host: each slot can be set with Browse and cleared with Clear.
- Android: `Set` opens a PC-provided app catalog from Start Menu shortcuts.
- Android: selecting an app saves it into the chosen PC slot.
- Android: configured slots show the PC-provided label.
- Android: empty slots stay disabled.
- Slot 1-5: each configured slot opens the selected local PC app/shortcut.
- App catalog does not expose arbitrary path entry from Android.
- Invalid token: no app shortcut is executed on the PC.
- Invalid slot: server returns an error and keeps running.
- Offline/reconnect: app shortcut buttons stay disabled while unauthorized and work again after reconnect/auth.

## Connection Security MVP

- Discovery: PC replies include protocol version 1; Android ignores explicit unsupported protocol versions and still accepts legacy replies without a version.
- Pairing: wrong PIN does not create a token and cannot authorize commands.
- Auth: missing, unknown, or forgotten token returns Unauthorized for control commands.
- Status: Android shows PC name/address and LAN connection type when a PC is saved.
- Logs: TCP debug traffic logs summarize metadata and do not include tokens, PINs, or clipboard contents.
