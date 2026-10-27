# Changelog

## 0.32.0-alpha.1 — Locally-observed arrow launch/flight audit

- Added `ObservedArrowAudit`: associates candidate bow-use stops with **actual client-visible owned arrow entities** within 8 ticks and 8 blocks. No claims of server acceptance without an observed arrow.
- Added bounded in-memory flight histories, predicted-vs-observed initial speed/angle, and per-tick flight trajectory error (mean and 256-sample P90). Invalid coordinates, impossible speeds, pre-existing visible arrows, stale arrows, and duplicate ticks are ignored.
- Connected `AbstractArrow` observation to the Fabric client tick loop using `Projectile.getOwner()`; exclude tridents and unrelated arrows.
- Opt-in under existing anonymous diagnostic setting. HUD `ANALYSIS` mode reports observed arrow count and estimated trajectory deviation, clearly labeled **not a hit/kill judgement**.
- Reset audit state on world switch, target filter switch or audit disable. Diagnostics do not log target names, arrow UUIDs or coordinates.
- New deterministic regression suite `ObservedArrowAuditTest` (48,046 assertions) and expanded mock signature tests. Full Fabric/Java 25 build and in-game run still pending.


## 0.31.0-alpha.1 — Observable release audit & renderer-ready world geometry

- Added `ShotReleaseAudit`: captures an expected target center when **bow use ends** and matches a later client-observed target center at the **exact** predicted flight tick. Tracks session-only error mean and nearest-rank P90; does **not** claim arrow spawn, arrow impact, damage, or hit/miss.
- Connected the audit to the opt-in `shadowAudit` option. Observed per-entity centers are fed into the audit even when bow draw is not active; samples are dropped if target data is missing on the exact due tick. Anonymous aggregate diagnostic summary appears on the ANALYSIS HUD page and in the existing 1200-tick diagnostic interval. No entity names, UUIDs, coordinates, or server addresses are exported.
- Added `WorldGuideGeometry`: immutable Java-only world-coordinate lines for recommended aim ring, future-target ring, future target AABB and flight trajectory (at most 56 total). Prepares a per-tick `Frame` in the existing aim controller. **No depth-tested world rendering has been implemented or registered.**
- Automatic reset on world change and diagnostic disabling; bounded release audit to 64 pending checks and 256 recent error samples.
- Added `ShotReleaseAuditTest` (12,104 assertions) and `WorldGuideGeometryTest` (283,642 assertions); all previous tests still pass.
- Kept Java 25 / Fabric 26.3 Gradle configuration and GitHub Actions, still requiring a real environment to validate compiler APIs.

## 0.30.0-alpha.1 — Three-model trajectory sensitivity and projected aiming rings

- Three-model collision-consistency audit, state-colored HUD, projected aim/future rings.
- Retained moving AABB, block occlusion and diagnostic controls; local tests passed.
