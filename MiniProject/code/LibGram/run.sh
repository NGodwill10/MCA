#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -f LibGram.jar ]; then ./build.sh; fi
exec java -jar LibGram.jar
