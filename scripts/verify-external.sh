#!/bin/sh
# Full source, artifact-consumer and browser verification; requires network and loopback sockets.
set -eu
PRECOGNITION_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PRECOGNITION_ROOT"
./scripts/maven.sh -B -ntp test
npm ci --prefix validation/client --cache .tools/npm-cache --ignore-scripts --no-audit --no-fund --legacy-peer-deps
for PRECOGNITION_PLATFORM in boot3 boot4; do
    PRECOGNITION_FIXTURES="../../target/validation/$PRECOGNITION_PLATFORM-laravel-fixtures.json" \
        npm test --prefix validation/client --cache .tools/npm-cache
done
./scripts/maven.sh -B -ntp -Dtest=PrecognitionHttpIT test
for PRECOGNITION_PLATFORM in boot3 boot4; do
    PRECOGNITION_REPORT="$PRECOGNITION_ROOT/examples/$PRECOGNITION_PLATFORM/target/surefire-reports/TEST-dev.precognition.example.PrecognitionIntegrationTest.xml" \
        npm run test:http --prefix validation/client --cache .tools/npm-cache
done
./scripts/maven.sh -B -ntp -Psecurity-verification test
./scripts/verify-browser.sh
./scripts/maven.sh -B -ntp -DskipTests install
python3 scripts/verify-artifacts.py
./scripts/verify-consumer.sh
