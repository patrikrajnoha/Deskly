# PROMPT_10 — Add physical volume button control, notifications, settings, and UI clarity

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

Improve daily usability: physical Android volume buttons can control PC volume, users receive connection-state notifications, and settings/UI are clearer.

## Tasks

1. Physical volume buttons:
   - Add setting to choose whether Android hardware volume buttons control the phone volume or PC volume while the app is active.
   - When PC volume mode is enabled, send Volume Up/Down commands to the PC.
   - Do not hijack volume buttons outside the app.
   - Test behavior on Android versions supported by the repo.

2. Push/local notifications:
   - Notify on PC connected, PC disconnected, lost connection, and failed connection where appropriate.
   - Show connected computer name and connection type when available.
   - Add setting to disable these notifications.
   - Use Android notification best practices and permission handling.

3. General settings:
   - Add settings for mouse sensitivity, touchpad sensitivity, gyro sensitivity, scroll behavior, hardware volume behavior, security, notifications, and appearance if not already present.
   - Persist settings.
   - Keep defaults conservative.

4. UI clarity:
   - Unify design across Mouse, Touchpad, Keyboard, Media Remote, and Web Remote.
   - Add clear descriptions of core functions.
   - Add visible connection status on the main screen.
   - Add simple onboarding for new users.
   - Add swipe navigation between main sections if it does not interfere with touchpad gestures.
   - Add current-section indicator.

5. Profiles:
   - Add user profiles if the settings architecture supports it: general PC control, presentation, media remote, gaming/TV setup.
   - If profiles are too large for one change, add the data model and one default profile plus TODOs.

## Acceptance criteria

- Hardware volume behavior is configurable and app-scoped.
- Connection notifications are useful and configurable.
- Main UI clearly shows connection state.
- Settings are centralized and persisted.
- Swipe navigation does not break touchpad gestures.
