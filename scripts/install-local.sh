#!/bin/sh
# Install artifacts into the Maven repository used by normal Maven and Gradle builds.
set -eu
PRECOGNITION_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PRECOGNITION_MAVEN_REPO="${PRECOGNITION_MAVEN_REPO:-$HOME/.m2/repository}" \
    "$PRECOGNITION_ROOT/scripts/maven.sh" -B -ntp install "$@"
