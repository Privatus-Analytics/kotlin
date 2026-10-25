#!/bin/sh
# Runs the tests, ktlint and the package build in the official Gradle image.
# Usage: ./run-docker.sh
# Gradle's download cache lives in .gradle-docker/ (git ignored), so later runs are faster.
set -e
docker run --rm -v "$PWD":/app -w /app -e GRADLE_USER_HOME=/app/.gradle-docker gradle:8-jdk21 sh -c 'set -e
  if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then gradle wrapper --gradle-version 8.14.4 --no-daemon -q; fi
  ./gradlew --no-daemon --console=plain clean build publishToMavenLocal
'
