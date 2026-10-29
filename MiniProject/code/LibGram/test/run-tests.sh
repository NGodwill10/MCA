#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p out
find src -name '*.java' -print0 | xargs -0 javac -Xlint:all -d out
javac -cp out -d out test/*.java
failed=0
for test in AccountServiceTest CatalogServiceTest LendingServiceTest MemberServiceTest ChargeCalculatorTest CsvUtilTest RepositoryRecoveryTest PermissionMatrixTest MigrationTest InvariantSequenceTest PhaseOneSmokeTest PhaseSixBillingTest PhaseSixWorkflowTest PhaseEightNotificationTest PhaseNineHardeningTest ReservationServiceTest PerformanceSmokeTest StoragePathsTest; do
  if java -cp out "$test" "$(pwd)"; then echo "PASS: $test"; else failed=1; echo "FAIL: $test"; fi
done
if [ "$failed" -eq 0 ]; then echo 'PASS: all LibGram tests'; else echo 'FAIL: one or more LibGram tests failed'; exit 1; fi
