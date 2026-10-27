# LeadShot v0.36.0 integration status

- Version: 0.36.0-alpha.1
- Source: all v0.35 code + new aim assist, release gate, UI/settings and tests.
- Local independent regression suite: PASSED (log: `LeadShot_v0.36.0_tests.log`).
- Local synthetic Minecraft API javac: PASSED. This does **not** establish real Minecraft compilation or runtime behavior.
- GitHub repository changes: NOT COMMITTED; GitHub connector returned HTTP 403 for contents write. User upload needed.
- Java runtime here: JDK 21; real Minecraft 26.3/Fabric build needs JDK 25, therefore NOT BUILT.
- v0.36 JAR: NOT GENERATED / NOT VERIFIED.
- Backward-compatible guide config read/write. New assist toggles default OFF.
- Auto aim and click-fired releases must be confirmed in a dedicated single-player test world before wider use.
