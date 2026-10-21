#!/bin/sh
# Small local launcher; this is not the Apache Maven Wrapper.
set -eu
PRECOGNITION_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if command -v mvn >/dev/null 2>&1; then
    PRECOGNITION_MAVEN=$(command -v mvn)
elif [ -x "$PRECOGNITION_ROOT/.tools/apache-maven-3.10.0/bin/mvn" ]; then
    PRECOGNITION_MAVEN="$PRECOGNITION_ROOT/.tools/apache-maven-3.10.0/bin/mvn"
else
    echo 'Maven 3.6.3+ is required. Install Maven, then run this script again.' >&2
    exit 1
fi
cd "$PRECOGNITION_ROOT"
exec "$PRECOGNITION_MAVEN" "-Dmaven.repo.local=${PRECOGNITION_MAVEN_REPO:-$PRECOGNITION_ROOT/.tools/m2}" "$@"
