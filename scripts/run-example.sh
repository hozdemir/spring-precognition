#!/bin/sh
set -eu
PRECOGNITION_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PRECOGNITION_PLATFORM=${1:-boot4}
if [ "$#" -gt 0 ]; then shift; fi
case "$PRECOGNITION_PLATFORM" in boot3|boot4) ;; *) echo 'Usage: run-example.sh [boot3|boot4] [Maven options]' >&2; exit 2 ;; esac
PRECOGNITION_MAVEN_REPO="${PRECOGNITION_MAVEN_REPO:-$HOME/.m2/repository}" \
    "$PRECOGNITION_ROOT/scripts/maven.sh" -f "$PRECOGNITION_ROOT/examples/$PRECOGNITION_PLATFORM/pom.xml" spring-boot:run "$@"
