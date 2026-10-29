#!/usr/bin/env bash
# Builds a signed debug APK without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-36/android.jar
rm -rf build && mkdir -p build/classes build/dex

$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -I "$JAR" --manifest AndroidManifest.xml -o build/unsigned.apk \
  --java build/gen build/res.zip --version-code 1 --version-name 1.0
javac -source 11 -target 11 -classpath "$JAR" -d build/classes \
  $(find src build/gen -name '*.java')
$BT/d8 --lib "$JAR" --min-api 29 --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
$BT/apksigner sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
  --out build/nightstand.apk build/aligned.apk
echo "built build/nightstand.apk"
