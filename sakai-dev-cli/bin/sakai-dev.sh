#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$SCRIPT_DIR/../target/sakai-dev-cli.jar"
if [ ! -f "$JAR" ]; then
  echo "Sakai CLI JAR not built; run: mvn -f $SCRIPT_DIR/../pom.xml package" >&2
  exit 1
fi
exec java -jar "$JAR" "$@"
