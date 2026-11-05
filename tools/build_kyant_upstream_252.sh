#!/bin/sh
set -eu
cd "$(dirname "$0")/../kyant-backdrop-engine"
echo 'Building genuine Kyant upstream catalog and all transitive Compose/Shapes dependencies...'
./gradlew :androidApp:assembleDebug --stacktrace
APK=androidApp/build/outputs/apk/debug/androidApp-debug.apk
[ -s "$APK" ] || { echo "Missing built upstream APK: $APK"; exit 1; }
echo "Built upstream APK: $APK"
