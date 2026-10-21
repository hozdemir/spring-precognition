#!/bin/sh
set -eu
PRECOGNITION_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PRECOGNITION_ROOT"
npm ci --prefix validation/browser --cache .tools/npm-cache --ignore-scripts --no-audit --no-fund
for PRECOGNITION_PLATFORM in boot3 boot4; do
    PRECOGNITION_REPORT="$PRECOGNITION_ROOT/examples/$PRECOGNITION_PLATFORM/target/surefire-reports/TEST-dev.precognition.example.PrecognitionIntegrationTest.xml" \
        npm test --prefix validation/browser
done
