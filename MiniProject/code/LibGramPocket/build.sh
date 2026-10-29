#!/bin/sh
set -eu
cd "$(dirname "$0")"
sh test/run-tests.sh
mkdir -p out/app
find src -name '*.java' -print0 | xargs -0 javac -Xlint:all -d out/app
jar cfe LibGramPocket.jar Main -C out/app .
printf '%s\n' 'Built LibGramPocket.jar'