# PROMPT_13 — Final QA, compatibility, and security hardening

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

Create or improve a comprehensive validation layer for the PC + Android remote app after implementing the feature prompts.

## Tasks

1. Functional test coverage:
   - Mouse mode.
   - Touchpad mode.
   - Keyboard mode.
   - Media Remote.
   - Web Remote.
   - Bluetooth connection if implemented.
   - Wi-Fi/LAN connection.
   - App/web shortcuts.
   - Physical volume buttons.
   - Clipboard Sync.

2. Compatibility validation:
   - Windows behavior.
   - macOS behavior if supported.
   - Supported Android versions.
   - Different Android screen sizes/resolutions.
   - Different keyboard layouts/languages.
   - Different network conditions: stable LAN, reconnect, timeout, lost connection.

3. Security validation:
   - Password/PIN pairing.
   - Unknown device rejection.
   - Behavior after connection loss.
   - Clipboard Sync protection.
   - Same-network unauthorized access attempts.
   - Privileged actions such as power commands requiring authentication.

4. Test harness improvements:
   - Add mocks/fakes for PC input injection if missing.
   - Add protocol-level tests for all command types.
   - Add Android UI tests where feasible.
   - Add CI-friendly scripts or documented local commands.

5. Regression checklist:
   - Verify zoom and shortcuts remain fixed.
   - Verify touchpad gestures do not conflict.
   - Verify power-saving changes do not add input lag.
   - Verify no sensitive data is logged.

## Acceptance criteria

- There is a documented and runnable validation path for the app.
- Critical features have automated coverage where practical.
- Manual test gaps are explicitly documented.
- Security-sensitive flows have regression tests.
