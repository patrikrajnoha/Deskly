# PROMPT_9 — Add Clipboard Sync, App Switcher, and Power Options

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

Implement advanced but contained remote-control panels: clipboard sync, app switching, and power actions.

## Tasks

1. Clipboard Sync:
   - Add opt-in clipboard synchronization between Android and PC.
   - Support text clipboard first.
   - Investigate image clipboard support; implement only if the repo/platform abstractions make it safe and reliable.
   - Add an enable/disable setting.
   - Warn before syncing potentially sensitive content when appropriate.
   - Do not log clipboard contents.

2. App Switcher:
   - Add a panel showing open PC applications if the PC app has the needed OS access.
   - Allow switching between detected applications.
   - Allow launching selected/favorite apps if existing platform abstractions support it.
   - Add favorite apps and search where practical.

3. Power Options:
   - Add panel/actions for shutdown, sleep, and restart.
   - Require explicit confirmation before executing.
   - Add a setting to disable power actions entirely.
   - Prevent accidental activation from repeated taps or stale network commands.

4. Security:
   - Require authenticated connection for clipboard, app switcher, and power actions.
   - Treat these commands as privileged on the PC side.

5. Tests:
   - Test clipboard text sync and disabled state.
   - Test that clipboard content is not logged.
   - Test power action confirmation flow.
   - Test app switcher command models and failure states.

## Acceptance criteria

- Clipboard Sync is opt-in and secure.
- App Switcher handles unavailable OS permissions gracefully.
- Power Options require confirmation and can be disabled.
- Privileged actions require authenticated connection.
