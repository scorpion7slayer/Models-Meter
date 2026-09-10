#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION_NAME="1.0.2"
DIST="$ROOT/dist"
SIGNING_DIR="$ROOT/.local-signing"
KEYSTORE="$SIGNING_DIR/models-meter-release.p12"
PASS_FILE="$SIGNING_DIR/models-meter-password"

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
if [[ -z "${ANDROID_SDK_ROOT:-}" && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
fi

umask 077
mkdir -p "$DIST" "$SIGNING_DIR"
if [[ ! -f "$KEYSTORE" ]]; then
  if [[ "${CI:-}" == "true" && "${GITHUB_EVENT_NAME:-}" != "pull_request" ]]; then
    echo "Persistent Models Meter signing key is required for CI distribution builds." >&2
    exit 1
  fi
  openssl rand -hex 24 > "$PASS_FILE"
  chmod 600 "$PASS_FILE"
  "$JAVA_HOME/bin/keytool" -genkeypair \
    -storetype PKCS12 \
    -keystore "$KEYSTORE" -storepass:file "$PASS_FILE" -keypass:file "$PASS_FILE" \
    -alias modelsmeter -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Models Meter, OU=Android, O=scorpion7slayer" \
    >/dev/null 2>&1
fi

"$ROOT/gradlew" --project-dir "$ROOT" \
  :app:assembleRelease :wear:assembleRelease \
  --console=plain

SOURCE_APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
OUT="$DIST/ModelsMeter-$VERSION_NAME.apk"
cp "$SOURCE_APK" "$OUT"
WEAR_OUT="$DIST/ModelsMeter-Wear-$VERSION_NAME.apk"
cp "$ROOT/wear/build/outputs/apk/release/wear-release.apk" "$WEAR_OUT"

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -type f -name apksigner | sort | tail -1)"
"$APKSIGNER" verify --verbose --print-certs "$OUT"
"$APKSIGNER" verify --verbose --print-certs "$WEAR_OUT"
if [[ "${CI:-}" == "true" && "${GITHUB_EVENT_NAME:-}" != "pull_request" ]]; then
  EXPECTED_CERT="2561ff561e466bf4c64fd93057eb2d0d94e36ee0b512827c11ab917c3106d0cb"
  for apk in "$OUT" "$WEAR_OUT"; do
    ACTUAL_CERT="$("$APKSIGNER" verify --print-certs "$apk" | awk -F': ' '/certificate SHA-256 digest/ {print $NF}')"
    if [[ "$ACTUAL_CERT" != "$EXPECTED_CERT" ]]; then
      echo "Distribution APK does not use the persistent Models Meter signing certificate." >&2
      exit 1
    fi
  done
fi
(cd "$DIST" && sha256sum "$(basename "$OUT")" "$(basename "$WEAR_OUT")") | tee "$DIST/SHA256SUMS.txt"
echo "Built $OUT"
