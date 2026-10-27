#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -encoding UTF-8 -d "$TMP" \
 src/main/java/dev/leadshot/{CoreBallistics,ProjectileContactWindow,BallisticOcclusion,ContactAudit,TrajectoryConsensus,ShotConfidence,AimProjection,ProjectedBox,ProjectedAimRings}.java \
 tools/{TrajectoryConsensusTest,ShotConfidenceTest,ProjectedAimRingsTest}.java
java -cp "$TMP" dev.leadshot.TrajectoryConsensusTest
java -cp "$TMP" dev.leadshot.ShotConfidenceTest
java -cp "$TMP" dev.leadshot.ProjectedAimRingsTest
