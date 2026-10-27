#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
javac --release 21 -encoding UTF-8 -d "$TMP" \
    "$ROOT/src/main/java/dev/leadshot/MotionShadowAudit.java" \
    "$ROOT/tools/MotionShadowAuditTest.java" \
    "$ROOT/tools/MotionShadowRecentTest.java"
java -cp "$TMP" dev.leadshot.MotionShadowAuditTest
java -cp "$TMP" dev.leadshot.MotionShadowRecentTest
