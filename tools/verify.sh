#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/verify_offline.py
if command -v kotlinc >/dev/null && command -v java >/dev/null; then
  mkdir -p tools/build
  kotlinc app/src/main/java/com/raghava/unlockaudit/domain/{LegacyHistoryParser,EventReducer,CsvWriter,RetentionPolicy}.kt \
    tools/DomainSmokeTest.kt -include-runtime -d tools/build/domain-smoke.jar
  java -jar tools/build/domain-smoke.jar
else
  echo 'Kotlin JVM smoke tests SKIPPED: kotlinc/java missing'
fi
