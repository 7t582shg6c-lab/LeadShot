#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! java -version 2>&1 | head -1 | grep -Eq '25([. +"]|$)'; then
  echo 'ERROR: JDK 25 required. Check java -version and JAVA_HOME.' >&2
  exit 2
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo 'ERROR: Gradle 9.8.2 unavailable. Use GitHub Actions workflow or install Gradle 9.8.2.' >&2
  exit 3
fi
exec gradle --no-daemon clean build
