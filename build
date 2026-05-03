#!/bin/bash
# Suzy Snooze - Android Build Script
# Usage: ./build.sh [BUILD_TYPE] [OPTIONS]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR"
GRADLE_LOG="$LOG_DIR/gradle-build.log"
touch "$GRADLE_LOG"

# ---------------------------------------------------------
# Ensure Android SDK tools (apksigner, zipalign) are in PATH
# ---------------------------------------------------------
if [ -z "${ANDROID_HOME:-}" ]; then
    export ANDROID_HOME="/lib/android/sdk"
fi

if [ -d "$ANDROID_HOME/build-tools" ]; then
    LATEST_BUILD_TOOLS=$(ls -1 "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)
    if [ -n "$LATEST_BUILD_TOOLS" ]; then
        BUILD_TOOLS_PATH="$ANDROID_HOME/build-tools/$LATEST_BUILD_TOOLS"
        if [ -d "$BUILD_TOOLS_PATH" ]; then
            export PATH="$BUILD_TOOLS_PATH:$PATH"
        fi
    fi
fi

VERSION_FILE="$PROJECT_ROOT/version.txt"
BUILD_DATE_FILE="$PROJECT_ROOT/buildDate.txt"

increment_version() {
    if [ ! -f "$VERSION_FILE" ]; then
        echo "1.0.0" > "$VERSION_FILE"
    fi
    local current
    current=$(tr -d ' \t\r\n' < "$VERSION_FILE")
    if [[ ! $current =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        current="1.0.0"
    fi
    IFS='.' read -r major minor patch <<<"$current"
    patch=$((patch + 1))
    local new_version="${major}.${minor}.${patch}"
    echo "$new_version" > "$VERSION_FILE"
    echo "$new_version"
}

increment_minor_version() {
    if [ ! -f "$VERSION_FILE" ]; then
        echo "1.0.0" > "$VERSION_FILE"
    fi
    local current
    current=$(tr -d ' \t\r\n' < "$VERSION_FILE")
    if [[ ! $current =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        current="1.0.0"
    fi
    IFS='.' read -r major minor patch <<<"$current"
    minor=$((minor + 1))
    patch=0
    local new_version="${major}.${minor}.${patch}"
    echo "$new_version" > "$VERSION_FILE"
    echo "$new_version"
}

update_build_date() {
    local stamp
    stamp=$(date '+%Y-%m-%d %H:%M:%S %Z%z')
    echo "$stamp" > "$BUILD_DATE_FILE"
    echo "$stamp"
}

# Signing setup:
#   $S2N_SIGN_FILE      = path to the .jks keystore file (binary, NOT sourced)
#   Passwords/alias come from env vars or an optional env file.
#
# Source password env file if it exists (NOT the keystore!)
SIGNING_SECRETS_FILE="${REACHME_SIGNING_ENV:-$HOME/.reachme_signing.env}"
if [ -f "$SIGNING_SECRETS_FILE" ]; then
    # shellcheck disable=SC1090
    source "$SIGNING_SECRETS_FILE"
    echo "🔐 Loaded signing secrets from: $SIGNING_SECRETS_FILE"
fi

# Use $S2N_SIGN_FILE as keystore path if REACHME_KEYSTORE_PATH is not already set
if [ -n "${S2N_SIGN_FILE:-}" ] && [ -z "${REACHME_KEYSTORE_PATH:-}" ]; then
    export REACHME_KEYSTORE_PATH="$S2N_SIGN_FILE"
    echo "🔑 Keystore: $REACHME_KEYSTORE_PATH"
fi

handle_error() {
    echo "❌ ERROR: $1"
    if [ -x "/b/donePersistentNotify.py" ]; then
        nohup /b/donePersistentNotify.py "Build err" 103 /data/pr/media/errInBuild.wav >/dev/null 2>&1 &
    fi
    exit "${2:-1}"
}

handle_success() {
    echo "✅ ${1:-Done!}"
    if [ -x "/b/donePersistentNotify.py" ]; then
        nohup /b/donePersistentNotify.py "Build done" 180 /data/pr/audio/bldDone.wav >/dev/null 2>&1 &
    fi
}

show_help() {
    cat <<'USAGE'
╔════════════════════════════════════════════════════════════╗
║ BUILD TYPE (exactly one):                                  ║
║   d (default) : Debug APK                                  ║
║   a           : Release APK                                ║
║   p           : Production AAB (Play Store, signed)        ║
╠════════════════════════════════════════════════════════════╣
║ OPTIONS (combinable):                                      ║
║   c           : Clean build                                ║
║   m           : Minified (R8 obfuscation)                  ║
║   o           : Online mode (fetch dependencies)           ║
║   t1..t4      : Set Gradle workers/threads (default: 2)   ║
║   l1          : Enable standard linting                    ║
║   l2          : Enable strict linting                      ║
║   1           : Bump minor version (reset patch to 0)      ║
║   h or ?      : Show usage and exit                        ║
╚════════════════════════════════════════════════════════════╝

Examples:
  ./build.sh             # Debug APK (default)
  ./build.sh a           # Release APK
  ./build.sh p           # Production AAB (signed for Play Store)
  ./build.sh p c m       # Production AAB, clean, minified
  ./build.sh d o t4      # Debug APK, online mode, 4 threads
  ./build.sh a m l1      # Release APK, minified, with linting

Signing:
  Set $S2N_SIGN_FILE to point to your signing env file.
  The env file should export:
    REACHME_KEYSTORE_PATH, REACHME_KEYSTORE_PASS,
    REACHME_KEY_ALIAS, REACHME_KEY_PASS
USAGE
}

# --- Defaults ---
DO_CLEAN=false
ONLINE_MODE=false
DO_MINIFY=false
BUMP_MINOR=false
GRADLE_WORKERS=2
DO_LINT=0
BUILD_TASK="assembleDebug"
BUILD_LABEL="Debug APK"
BUILD_FORMAT="apk"

# --- Parse args ---
for arg in "$@"; do
    case "$arg" in
        d|D) BUILD_TASK="assembleDebug";   BUILD_LABEL="Debug APK";            BUILD_FORMAT="apk" ;;
        a|A) BUILD_TASK="assembleRelease"; BUILD_LABEL="Release APK";          BUILD_FORMAT="apk" ;;
        p|P) BUILD_TASK="bundleRelease";   BUILD_LABEL="Production AAB";       BUILD_FORMAT="aab" ;;
        c|C) DO_CLEAN=true ;;
        o|O) ONLINE_MODE=true ;;
        m|M) DO_MINIFY=true ;;
        1)   BUMP_MINOR=true ;;
        t1|T1) GRADLE_WORKERS=1 ;;
        t2|T2) GRADLE_WORKERS=2 ;;
        t3|T3) GRADLE_WORKERS=3 ;;
        t4|T4) GRADLE_WORKERS=4 ;;
        l1|L1) DO_LINT=1 ;;
        l2|L2) DO_LINT=2 ;;
        h|H|help|\?) show_help; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; show_help; exit 1 ;;
    esac
done

cd "$PROJECT_ROOT"

# --- Version bump ---
if [ "$BUMP_MINOR" = true ]; then
    NEW_VERSION=$(increment_minor_version)
    echo "🔢 Minor version bumped to $NEW_VERSION"
else
    NEW_VERSION=$(increment_version)
    echo "🔢 Version bumped to $NEW_VERSION"
fi
BUILD_TIMESTAMP=$(update_build_date)

echo "════════════════════════════════════════"
echo " Suzy Snooze Build"
echo " Task   : $BUILD_TASK ($BUILD_LABEL)"
echo " Version: $NEW_VERSION"
echo " Workers: $GRADLE_WORKERS"
echo " Date   : $BUILD_TIMESTAMP"
echo "════════════════════════════════════════"

# --- Clean ---
if [ "$DO_CLEAN" = true ]; then
    echo "🧹 Cleaning project..."
    ./gradlew clean --no-daemon || handle_error "Clean failed" 1
fi

# --- Build Gradle args ---
GRADLE_ARGS=("$BUILD_TASK"
    --max-workers="$GRADLE_WORKERS"
    "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m"
    --no-daemon
    --stacktrace
)
[ "$ONLINE_MODE" = false ] && GRADLE_ARGS+=(--offline)
[ "$DO_MINIFY" = true ]    && GRADLE_ARGS+=("-PminifyEnabled=true")

if [ "$DO_LINT" -eq 0 ]; then
    GRADLE_ARGS+=(-x lint)
elif [ "$DO_LINT" -eq 2 ]; then
    GRADLE_ARGS+=(--warning-mode all)
fi

# --- Signing (passed as Gradle properties) ---
# Password fallback chain: REACHME_KEYSTORE_PASS -> S2n_Jks (backward compat)
REACHME_KEYSTORE_PASS="${REACHME_KEYSTORE_PASS:-${S2n_Jks:-}}"
REACHME_KEY_ALIAS="${REACHME_KEY_ALIAS:-sel2in_upload}"
REACHME_KEY_PASS="${REACHME_KEY_PASS:-$REACHME_KEYSTORE_PASS}"

[ -n "${REACHME_KEYSTORE_PATH:-}" ] && GRADLE_ARGS+=("-PreachmeKeystorePath=$REACHME_KEYSTORE_PATH")
[ -n "${REACHME_KEYSTORE_PASS:-}" ] && GRADLE_ARGS+=("-PreachmeKeystorePass=$REACHME_KEYSTORE_PASS")
[ -n "${REACHME_KEY_ALIAS:-}"     ] && GRADLE_ARGS+=("-PreachmeKeyAlias=$REACHME_KEY_ALIAS")
[ -n "${REACHME_KEY_PASS:-}"      ] && GRADLE_ARGS+=("-PreachmeKeyPass=$REACHME_KEY_PASS")

echo "🚀 Running Gradle..."
if ! ./gradlew "${GRADLE_ARGS[@]}" 2>&1 | tee -a "$GRADLE_LOG"; then
    handle_error "Gradle build failed" 1
fi

# --- Artifact discovery ---
declare -A OUTPUTS
OUTPUT_BASE="$PROJECT_ROOT/app/build/outputs"
OUTPUTS["assembleDebug"]="$OUTPUT_BASE/apk/debug/sel2in_snooze_debug.apk"
OUTPUTS["assembleRelease"]="$OUTPUT_BASE/apk/release/sel2in_snooze_release.apk"
OUTPUTS["bundleRelease"]="$OUTPUT_BASE/bundle/release/sel2in_snooze_release.aab"

BUILD_OUTPUT="${OUTPUTS[$BUILD_TASK]:-}"
if [ -n "$BUILD_OUTPUT" ] && [ -f "$BUILD_OUTPUT" ]; then
    FILE_SIZE=$(du -h "$BUILD_OUTPUT" | cut -f1)
    echo "✅ Build succeeded: $BUILD_OUTPUT ($FILE_SIZE)"

    # Verify signature on release / production builds
    if [ "$BUILD_FORMAT" = "aab" ] || [ "$BUILD_TASK" = "assembleRelease" ]; then
        echo ""
        echo "🔐 Verifying signature..."
        IS_SIGNED=false
        if [ "$BUILD_FORMAT" = "apk" ] && command -v apksigner &>/dev/null; then
            apksigner verify "$BUILD_OUTPUT" 2>/dev/null && IS_SIGNED=true
        else
            keytool -printcert -jarfile "$BUILD_OUTPUT" 2>&1 | grep -q "SHA1:" && IS_SIGNED=true
        fi
        if [ "$IS_SIGNED" = true ]; then
            echo "   ✅ Signature verified"
        else
            echo "   🔴 WARNING: Build is NOT signed!"
            echo "   Set \$S2N_SIGN_FILE pointing to your signing env."
        fi
    fi

    # ADB install if APK and device connected
    if [ "$BUILD_FORMAT" = "apk" ]; then
        if command -v adb >/dev/null && adb devices | grep -q "device$"; then
            echo "📱 Found device, installing..."
            adb install -r "$BUILD_OUTPUT" || echo "⚠️ Install failed"
        fi
    fi
    handle_success "Build Complete!"
else
    handle_error "Build task finished but artifact not found: $BUILD_OUTPUT" 1
fi
