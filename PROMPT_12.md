# PROMPT_12 — Add Bluetooth connection support

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

Add Bluetooth connectivity as an alternative or fallback to Wi-Fi/LAN, with pairing, connection status, and latency validation.

## Tasks

1. Feasibility audit:
   - Identify current transport abstraction.
   - Check Android Bluetooth permission handling for supported Android versions.
   - Check PC-side Bluetooth support for Windows/macOS if the app targets both.
   - Determine whether Bluetooth Classic, BLE, or another profile is appropriate.

2. Transport abstraction:
   - Add Bluetooth as a transport implementation without breaking Wi-Fi/LAN.
   - Reuse shared connection state, authentication, and command protocol.
   - Add fallback to Wi-Fi/LAN when Bluetooth is unsupported or disconnected.

3. Pairing:
   - Add Bluetooth pairing flow.
   - Detect already paired devices.
   - Auto-recognize trusted paired devices.
   - Integrate with secure pairing/authentication from PROMPT_3.

4. UI:
   - Show connection type: Wi-Fi, LAN, Bluetooth.
   - Add Bluetooth-specific error messages and permissions flow.
   - Keep UI behavior consistent with normal connection flow.

5. Testing:
   - Add unit tests for transport selection and fallback.
   - Add tests for Bluetooth unavailable/permission denied states.
   - Add latency diagnostic logging for Bluetooth input commands.
   - Document any manual test steps required for real hardware.

## Acceptance criteria

- Bluetooth is implemented through the same transport abstraction as existing connections.
- Unsupported Bluetooth states are handled gracefully.
- User can see whether connection is Bluetooth, Wi-Fi, or LAN.
- Security/pairing is consistent with other transports.
