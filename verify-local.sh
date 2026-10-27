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
