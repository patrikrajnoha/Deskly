# Architecture Decisions

## Separate Apps

Android and Windows apps remain separate. Do not merge them into a shared framework, monorepo-style runtime, or generated cross-platform app unless explicitly approved.

## Local Network Protocol

Local network communication stays TCP JSON plus UDP discovery unless explicitly changed.

- TCP control channel: newline-delimited JSON.
- UDP discovery: `DESKLY_DISCOVER` broadcast and `discover_response`.
- Default ports remain `5050` TCP and `5051` UDP.

## Pairing/Auth Security Model

Pairing/token auth remains the security model unless a stronger design is planned and reviewed.

- PIN pairing creates a persisted token.
- Secure control messages carry the token in JSON payload.
- Future security changes should account for migration, token revocation, and both Android/Windows compatibility.

## Reliability Before Rewrites

Avoid large rewrites before connection reliability is fixed.

Priority should be:

1. Keep Android auto-connect/auth centralized around the existing `DesklyClient` state and `MainActivity.ensureConnectedAndAuthed`.
2. Use bounded reconnect backoff for transient network/host restarts.
3. Stop reconnect retries on explicit auth rejection and require pairing/token repair.
4. Then consider extracting router/services or reducing duplicated Android token logic.

## Display And Cooling Limits

- Use Windows-supported projection modes for display output switching.
- Do not add fake controls for HDMI/DisplayPort monitor input switching; document it as unsupported unless a stable monitor-specific API is introduced.
- Do not implement generic fan speed control without explicit hardware support. Prefer Windows power plans and the existing quiet mode behavior for safe thermal/noise control.

## Windows Target Framework

DesklyPC currently stays on `net10.0-windows`. Do not silently downgrade the target framework just to satisfy an older local SDK; use a .NET 10-capable SDK for the official PC build. A temporary `net8.0-windows` compile check is useful only for source compatibility.
