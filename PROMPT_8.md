# PROMPT_8 — Add Media Remote and Web Remote panels

You are Codex working inside an existing PC + Android remote-control application repository.

General rules:
- First inspect the repository structure and identify the Android client, PC/server app, shared protocol/models, tests, and build scripts.
- Do not rewrite unrelated systems. Make the smallest cohesive production-quality changes needed for this prompt.
- Keep existing public APIs and user data compatible unless a protocol change is unavoidable. If you change protocol messages, add versioning/backward-compatibility handling.
- Add or update tests for the behavior you change. Prefer unit tests for protocol/mapping logic and integration-style tests for end-to-end flows when the repo supports them.
- Run the relevant build/test/lint commands that exist in the repo. If a command cannot run because the environment lacks tooling, report that explicitly.
- Do not create commits, amend history, rebase, push, or force-push.
- At the end, provide: files changed, behavior implemented, tests run, and any known limitations.


## Goal

Add high-value remote-control panels for media playback and browser/web navigation.

## Tasks

1. Media Remote panel:
   - Add a dedicated Media Remote section/panel.
   - Support Play/Pause, Previous, Next, Volume Up/Down, Mute, Seek Forward/Backward, and Fullscreen.
   - Support common targets where the existing architecture allows it: YouTube, Netflix, Disney+, Apple TV+, VLC, Spotify, and presentation apps.
   - Prefer platform media keys and tested shortcut mappings over brittle UI automation.

2. Web Remote panel:
   - Add a dedicated Web Remote section/panel.
   - Support Chrome, Firefox, and Opera mappings.
   - Add Back, Forward, Refresh, New Tab, Close Tab, Switch Tabs, Page Scroll, and Fullscreen.
   - Reuse shortcut infrastructure from PROMPT_2.

3. UX:
   - Show clear panel labels and icons/buttons.
   - Disable or explain actions that are unsupported on the current platform.
   - Keep the layout usable on small Android screens.

4. PC-side dispatch:
   - Route media and web commands through semantic command IDs.
   - Add platform/browser-specific mapping only at the appropriate adapter layer.

5. Tests:
   - Test media command mapping.
   - Test browser command mapping for Chrome/Firefox/Opera.
   - Test unsupported-platform behavior.
   - Test Android UI command emission where UI tests exist.

## Acceptance criteria

- Media Remote and Web Remote are visible and usable.
- Commands are semantic and tested.
- Browser mappings reuse the fixed shortcut system.
- Unsupported actions fail visibly and safely, not silently.
