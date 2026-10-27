#!/usr/bin/env sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST="$HERE/target/smoke-classes"
mkdir -p "$DEST"
find "$HERE/src/main/java" "$HERE/src/test/java" -type f -name '*.java' > "$DEST/sources.txt"
javac --release 17 -d "$DEST" @"$DEST/sources.txt"
java -cp "$DEST" org.sakaiproject.devtools.cli.CliSmoke
