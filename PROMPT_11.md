# PROMPT_11 — Add automatic video detection and mobile video list

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

Add an advanced video-detection feature so the Android app can show videos currently playing or available on the PC and provide remote playback controls.

## Important constraint

This feature is complex and platform-dependent. Avoid brittle screen scraping unless the repo already uses it. Prefer browser/media APIs, OS media session APIs, browser extensions, app integrations, or existing PC-side media abstractions.

## Tasks

1. Discovery design:
   - Inspect current media-control architecture.
   - Identify safe ways to detect media from browser tabs, local apps, or OS media sessions.
   - Support YouTube, Netflix, Disney+, Apple TV+, VLC, Spotify, and other services only where technically feasible.

2. PC-side video detection:
   - Detect whether media is playing in a browser or local app.
   - Extract available metadata where possible: title, source/app, playback state.
   - Add fallback when automatic detection is impossible: show generic active media session or manual Media Remote controls.

3. Android video list:
   - Display detected title.
   - Display source, e.g. YouTube, VLC, Netflix.
   - Display playback state.
   - Add Play/Pause.
   - Add Start/Open on computer if the integration supports it.
   - Add refresh.

4. Playback controls:
   - Add pause/play, seek, and volume control through existing media command infrastructure.
   - Avoid duplicating Media Remote command logic.

5. Tests:
   - Unit-test video metadata models and serialization.
   - Test unsupported/no-video fallback.
   - Test refresh behavior.
   - Add mock media-session/browser-source tests if possible.

## Acceptance criteria

- Android app can show a list of detected media when supported.
- Unsupported sources degrade to generic media controls.
- No fragile or privacy-invasive implementation is introduced.
- Playback controls reuse existing Media Remote infrastructure.
