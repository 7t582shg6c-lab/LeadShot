#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
javac --release 21 -encoding UTF-8 -d "$TMP" \
    "$ROOT/src/main/java/dev/leadshot/ProjectileContactWindow.java" \
    "$ROOT/tools/ProjectileContactWindowTest.java"
java -cp "$TMP" dev.leadshot.ProjectileContactWindowTest
