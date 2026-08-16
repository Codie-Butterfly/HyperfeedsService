# Hyperfeeds collaboration handoff

You are the Antigravity implementation agent responsible for building the Hyperfeeds
Flutter mobile application and integrating it with this Spring Boot backend.

Read these files before changing anything:

1. `docs/ANTIGRAVITY_MOBILE_APP_BRIEF.md` — complete product, UX, asset, API, security,
   testing, and delivery requirements.
2. `README.md` — backend setup and feature tracker.
3. The controller and DTO source under `src/main/java/zw/co/hyperfeeds/` — authoritative
   request and response contracts.

Create the Flutter application as a sibling directory named `HyperfeedsMobile`, not
inside the Spring Boot source tree. Use the downloaded `hyperfeeds*` assets exactly as
mapped in the build brief. Build customer flows end-to-end before employee tools.

## Collaboration protocol

- The Codex agent owns backend changes in `HyperfeedsService`.
- Antigravity owns mobile changes in `HyperfeedsMobile`.
- Do not silently alter backend contracts from the mobile workspace.
- Record every backend gap or requested contract change in
  `docs/MOBILE_INTEGRATION_GAPS.md` using: date, screen/flow, endpoint, current
  behavior, required behavior, and blocking severity.
- Record implemented mobile/API coverage in `docs/MOBILE_INTEGRATION_STATUS.md`.
- Never add fake production data or client-side substitutes for missing backend APIs.
- Never commit credentials or copy the exposed credentials from ImbaService.
- Run `flutter analyze` and `flutter test` after each feature slice.

Start by scaffolding the Flutter project, copying assets, implementing the shared
theme and API client, then complete customer signup, OTP verification, branch
selection, and token refresh. Continue through the phases in the build brief without
waiting for confirmation unless a genuine backend contract gap blocks progress.
