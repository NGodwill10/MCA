#!/bin/sh
set -u
cd "$(dirname "$0")/.."
mkdir -p out
if ! find src test -name '*.java' -print0 | xargs -0 javac -Xlint:all -d out; then
    echo 'FAIL compilation'
    exit 1
fi
passed=0
failed=0
for file in test/*Test.java; do
    test_name=$(basename "$file" .java)
    if [ "$test_name" = StoragePathsTest ]; then
        java -cp out "$test_name" "$PWD"
    else
        java -cp out "$test_name"
    fi
    if [ "$?" -eq 0 ]; then
        echo "PASS $test_name"
        passed=$((passed + 1))
    else
        echo "FAIL $test_name"
        failed=$((failed + 1))
    fi
done
echo "Summary: $passed passed, $failed failed"
[ "$failed" -eq 0 ]