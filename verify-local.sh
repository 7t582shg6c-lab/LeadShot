#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{BallisticOcclusion,CoreBallistics,AimProjection,LeadShotOptions,ContactAudit,TargetStability,FlightGuidePoints,ProjectedBox,ProjectileContactWindow,MotionShadowAudit,ShotReleaseAudit,WorldGuideGeometry,ObservedArrowAudit}.java \
 tools/{BallisticOcclusionTest,IntegratedMathTest,PreferencesTest,ProjectedBoxTest,MotionShadowAuditTest,MotionShadowRecentTest,ProjectileContactWindowTest,ShotReleaseAuditTest,WorldGuideGeometryTest,ObservedArrowAuditTest}.java
java -cp "$TMP" dev.leadshot.BallisticOcclusionTest
java -cp "$TMP" dev.leadshot.IntegratedMathTest
java -cp "$TMP" dev.leadshot.PreferencesTest
java -cp "$TMP" dev.leadshot.ProjectedBoxTest
java -cp "$TMP" dev.leadshot.MotionShadowAuditTest
java -cp "$TMP" dev.leadshot.MotionShadowRecentTest
java -cp "$TMP" dev.leadshot.ShotReleaseAuditTest
java -cp "$TMP" dev.leadshot.ObservedArrowAuditTest
java -cp "$TMP" dev.leadshot.WorldGuideGeometryTest
java -cp "$TMP" dev.leadshot.ProjectileContactWindowTest
bash tools/test-v030.sh
python3 tools/static-port-check.py
python3 tools/mock-signatures.py

# v0.33 independent persistent UI config tests
javac --release 21 -encoding UTF-8 -d "$TMP" src/main/java/dev/leadshot/{FineTuneParameter,GuideControls}.java tools/GuideControlsTest.java
java -cp "$TMP" dev.leadshot.GuideControlsTest

# v0.34 elapsed-time screen-guide smoothing + backwards compatible config
javac --release 21 -encoding UTF-8 -d "$TMP" \
  src/main/java/dev/leadshot/{CoreBallistics,GuideFrameSmoother,FineTuneParameter,GuideControls}.java \
  tools/{GuideFrameSmootherTest,GuideControlsTest}.java
java -cp "$TMP" dev.leadshot.GuideFrameSmootherTest
java -cp "$TMP" dev.leadshot.GuideControlsTest

# v0.35 reliable lock-on readiness and new guide settings
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{CoreBallistics,ProjectileContactWindow,ContactAudit,BallisticOcclusion,TrajectoryConsensus,ShotConfidence,AimReadySignal,FineTuneParameter,GuideControls}.java \
 tools/{AimReadySignalTest,GuideControlsV35Test}.java
java -cp "$TMP" dev.leadshot.AimReadySignalTest
java -cp "$TMP" dev.leadshot.GuideControlsV35Test

# v0.36 steering, click-gated release, and opt-in persisted settings.
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{CoreBallistics,AimAssistController,AssistedReleaseGate,FineTuneParameter,GuideControls}.java \
 tools/{AimAssistControllerTest,AssistedReleaseGateTest,GuideControlsV36Test}.java
java -cp "$TMP" dev.leadshot.AimAssistControllerTest
java -cp "$TMP" dev.leadshot.AssistedReleaseGateTest
java -cp "$TMP" dev.leadshot.GuideControlsV36Test

# v0.37 full-draw accuracy and repeat-release policy.
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{CoreBallistics,ContinuousReleaseGate,TargetVelocityFilter,AimReadinessCriteria,ProjectileContactWindow,ContactAudit,BallisticOcclusion,TrajectoryConsensus,ShotConfidence,AimReadySignal}.java \
 tools/{ContinuousReleaseGateTest,TargetVelocityFilterTest,AimReadinessCriteriaTest}.java
java -cp "$TMP" dev.leadshot.ContinuousReleaseGateTest
java -cp "$TMP" dev.leadshot.TargetVelocityFilterTest
java -cp "$TMP" dev.leadshot.AimReadinessCriteriaTest

# v0.37 immediate dead-target handoff logic (LOS loss is NOT death).
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/RadarLockHandoff.java tools/RadarLockHandoffTest.java
java -cp "$TMP" dev.leadshot.RadarLockHandoffTest

# v0.38 four-tier full-draw warming + backward-compatible settings.
javac --release 21 -encoding UTF-8 -d "$TMP" \
  src/main/java/dev/leadshot/{CoreBallistics,FullDrawWarmCache,PrecomputePolicy,FineTuneParameter,GuideControls}.java \
  tools/{FullDrawWarmCacheTest,GuideControlsV38Test}.java
java -cp "$TMP" dev.leadshot.FullDrawWarmCacheTest
java -cp "$TMP" dev.leadshot.GuideControlsV38Test

# v0.42 same 64 targets; 4 compute depths and fair round-robin scheduling.
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{CoreBallistics,ProjectileContactWindow,ContactAudit,BallisticOcclusion,TrajectoryConsensus,FullDrawWarmCache,PrecomputePolicy,RoundRobinPrecompute,StagedPrecomputeCache}.java \
 tools/{FullDrawWarmCacheTest,PrecomputeDepthV42Test}.java
java -cp "$TMP" dev.leadshot.FullDrawWarmCacheTest
java -cp "$TMP" dev.leadshot.PrecomputeDepthV42Test

# v0.39 tactical HUD layout/palette, persistence and stable UI settings.
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{FineTuneParameter,GuideControls,FlightHudLayout,FlightHudTheme}.java \
 tools/FlightHudV39Test.java
java -cp "$TMP" dev.leadshot.FlightHudV39Test

# v0.39 avoid wasting GPU quads on off-screen projected lines.
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/ScreenLineClipper.java tools/ScreenLineClipperTest.java
java -cp "$TMP" dev.leadshot.ScreenLineClipperTest

# v0.40 numeric range/precision/legacy preferences and safety isolation.
javac --release 21 -encoding UTF-8 -d "$TMP" \
  src/main/java/dev/leadshot/{FineTuneParameter,GuideControls}.java \
  tools/FineTuneV40Test.java
java -cp "$TMP" dev.leadshot.FineTuneV40Test

# v0.41 systematic performance, tracking-budget, and dynamic HUD tests.
javac --release 21 -encoding UTF-8 -d "$TMP" \
  src/main/java/dev/leadshot/{CoreBallistics,TrackingBudget,FlightHudLayout}.java \
  tools/{TrackingBudgetTest,BallisticCoefficientV41Test,HudDensityV41Test,HudTextFitV41Test}.java
for test in TrackingBudgetTest BallisticCoefficientV41Test HudDensityV41Test HudTextFitV41Test; do
  java -cp "$TMP" "dev.leadshot.$test"
done

# v0.43 steering latency guard, acquisition priority and persistent contact overlay
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{CoreBallistics,TargetStability,TargetSelectionV43,MultiplayerAimSync,ScanOverlayPreference}.java \
 tools/{TargetSelectionV43Test,MultiplayerAimSyncTest,ScanOverlayPreferenceTest}.java
for test in TargetSelectionV43Test MultiplayerAimSyncTest ScanOverlayPreferenceTest; do
  java -cp "$TMP" "dev.leadshot.$test"
done

# v0.43 safe separation of visual steering from strict release qualification.
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/AssistEligibilityV43.java tools/AssistEligibilityV43Test.java
java -cp "$TMP" dev.leadshot.AssistEligibilityV43Test

# v0.44 gliding-target sub-tick ballistics, high-speed motion and optional PvP settings.
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{CoreBallistics,AimAssistController,TargetVelocityFilter,FlightTargetPredictor,AdvancedAimOptions}.java \
 tools/{FlightPrecisionV44Test,FlightTargetPredictorTest,AdvancedAimOptionsV44Test}.java
for test in FlightPrecisionV44Test FlightTargetPredictorTest AdvancedAimOptionsV44Test; do
    java -cp "$TMP" "dev.leadshot.$test"
done
