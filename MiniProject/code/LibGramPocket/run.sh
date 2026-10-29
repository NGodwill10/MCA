#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -f LibGramPocket.jar ]; then
	echo 'LibGramPocket.jar is missing. Run ./build.sh first.' >&2
	exit 1
fi
exec java -jar LibGramPocket.jar "$@"