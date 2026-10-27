# LeadShot for Minecraft Java 26.3 / Fabric — v0.36.0-alpha.1

LeadShot is an experimental **client-side bow prediction and guided manual target selection** mod. This source tree adds a deliberate opt-in aim-assist and single-action assisted release on top of the v0.35 HUD, READY cue, radar-mode target acquisition, tracking and projected target guides.

**No v0.36 compiled JAR is included**. This version was tested with local Java 21 math tests and synthetic API stubs, **not** with the real Fabric 26.3 development/runtime environment. Use GitHub Actions Java 25 with Fabric 26.3 to confirm compilation.

- **O** options; **I** lock; **P** cycle target; **K** clear; **J** toggle autoaim.
- **Right mouse held + mouse-wheel click**: one attempted assisted bow shot only if the opt-in setting is enabled and stringent READY conditions are met. No auto-repeat; no auto attack against players.
- Bow must be drawn before aim assist steers; steering uses a max 1–4° each tick and must see clear checked trajectory.
- See [MIGRATION_NOTES_v036.md](MIGRATION_NOTES_v036.md) for all safety conditions and build instructions.
- [INTEGRATION_STATUS.md](INTEGRATION_STATUS.md) records exactly what is and isn't verified.

Existing Fabric API and Java 25 requirements are in `gradle.properties`, `build.gradle` and `fabric.mod.json`. The GitHub root-upload ZIP flattens `src/main/java/dev/leadshot/*.java` and asset JSON at repository root to match the user repository's existing Actions staging script.
