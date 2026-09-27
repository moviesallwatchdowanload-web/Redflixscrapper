#!/bin/sh
DIR=$(cd "$(dirname "$0")" && pwd)
APP_HOME="$DIR"
GRADLE_VERSION=8.7
GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/gradle-$GRADLE_VERSION/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$GRADLE_HOME"
    cd "$GRADLE_HOME"
    if command -v curl >/dev/null 2>&1; then
        curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    else
        wget -q -O gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    fi
    unzip -q gradle.zip
    rm gradle.zip
fi

cd "$APP_HOME"
exec "$GRADLE_BIN" "$@"
