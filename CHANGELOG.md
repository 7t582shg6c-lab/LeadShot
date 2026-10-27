# LeadShot changelog

## v0.36.0-alpha.1 (2026-10-27)

- Added opt-in **locked-target aim assist**. While using a bow, with a valid manually locked target, a checked clear projectile route, enabled line-of-sight filtering, and consistent model status, adjust yaw/pitch toward the predicted solution by at most 1–4 degrees each client tick. Defaults OFF. No mouse input emulation or world-facing overlay.
- Added opt-in **assisted single-shot release** (right mouse button held + middle mouse press, Minecraft keybinding remappable). Requires manual lock, hostile mob target, drawn bow, clear inspected trajectory, line-of-sight filtering, and stable READY status. Defaults OFF. One action per click, 12-tick minimum spacing, no queued shots or repeat firing; no automatic firing at players.
- Added seventh settings page and J key for toggling aim assist; settings are persisted in the existing guide properties file, preserving old config compatibility.
- Added pure-math aim controller with wrapped yaw and maximum angular velocity, and pure release safety gate.
- Expanded local regressions for angular velocity, input/release gate truth table, and persistence. Mock Fabric API signature compilation passed; **not tested against real Fabric 26.3**.
- Source and root-upload bundles are supplied. No compiled Minecraft 26.3 JAR exists for v0.36 at this stage.

## Earlier releases

v0.35: READY notification, configurable text scale, CENTER/RADAR selection, lock stability.

v0.34: frame-time guide smoothing.

v0.33: occlusion-aware target acquisition, aim decoration and guide settings.

v0.32: first real compiled LeadShot Fabric 26.3 JAR (after key mapping/hideGui fixes).
