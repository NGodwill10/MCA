#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
find src -name '*.java' -print0 | xargs -0 javac -Xlint:all -d out
./test/run-tests.sh
jar cfe LibGram.jar Main -C out .
echo "Built LibGram.jar"
