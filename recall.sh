#!/bin/bash
JAR="target/recall-1.0.0.jar"
if [ ! -f "$JAR" ]; then
  echo "Build first: mvn package" >&2
  exit 1
fi
exec java -jar "$JAR" "$@"
