#!/usr/bin/env bash
set -e

SDK_DIR="${ANDROID_SDK_ROOT:-/tmp/android-sdk}"
BUILD_TOOLS="$SDK_DIR/build-tools/33.0.2"
PLATFORM="$SDK_DIR/platforms/android-33/android.jar"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$DIR/build"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/dex" "$OUT/res"

echo "[1/5] Compiling resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$DIR/res" -o "$OUT/res/compiled.zip"

echo "[2/5] Linking APK & generating R.java..."
"$BUILD_TOOLS/aapt2" link \
  -I "$PLATFORM" \
  --manifest "$DIR/AndroidManifest.xml" \
  --java "$OUT/gen" \
  -o "$OUT/unsigned.apk" \
  "$OUT/res/compiled.zip"

echo "[3/5] Compiling Java sources..."
javac -source 11 -target 11 \
  -cp "$PLATFORM" \
  -d "$OUT/obj" \
  "$OUT/gen/com/nanodesk/agentlink/R.java" \
  "$DIR/src/com/nanodesk/agentlink/MainActivity.java"

echo "[4/5] Converting bytecode to DEX with d8..."
"$BUILD_TOOLS/d8" --lib "$PLATFORM" --output "$OUT/dex" $(find "$OUT/obj" -name "*.class")

echo "[5/5] Packaging, aligning, and signing APK..."
cd "$OUT/dex"
zip -u "$OUT/unsigned.apk" classes.dex
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KEYSTORE="$OUT/debug.keystore"
KEYTOOL=$(command -v keytool || echo "/usr/lib/jvm/jdk-11/bin/keytool")
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkey -v -keystore "$KEYSTORE" -storepass android -alias androiddebugkey \
    -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=NanoDesk,O=AgentLink,C=US" >/dev/null 2>&1
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$DIR/AgentLink-v1.0.0.apk" \
  "$OUT/aligned.apk"

echo "Built: $DIR/AgentLink-v1.0.0.apk"
ls -lh "$DIR/AgentLink-v1.0.0.apk"
