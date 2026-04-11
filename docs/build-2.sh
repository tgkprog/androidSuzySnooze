#!/bin/bash
echo "REACHME_HOME=$REACHME_HOME"
echo "RME=$RME"
# set -e  <-- Removed to allow custom error handling (sound playing)

# Resolve paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# S2REACHME_ROOT is the parent directory of android/
S2REACHME_ROOT_LOCAL="$(cd "$SCRIPT_DIR/.." && pwd)"

REACHME_ROOT="${REACHME_HOME:-$S2REACHME_ROOT_LOCAL}"
MISC_ROOT="${MISC_ROOT:-$REACHME_ROOT/misc}"
PROJECT_DIR="${PROJECT_DIR:-${ANDROID_ROOT:-$REACHME_ROOT/android}}"
SRVR1_DWNLDS_DIR="${SRVR1_DWNLDS_DIR:-$REACHME_ROOT/server1/public/downloads}"
REACHME_PARENT="$(cd "$REACHME_ROOT/.." && pwd)"
OUTPUT_DIR="${OUTPUT_DIR:-$REACHME_PARENT/other}"

LOG_FILE="$REACHME_ROOT/logs/andrBld.txt"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee "$LOG_FILE") 2>&1

GRADLE_LOG="$REACHME_ROOT/logs/androidBld.txt"
mkdir -p "$(dirname "$GRADLE_LOG")"
touch "$GRADLE_LOG"


# ---------------------------------------------------------
# Ensure Android SDK tools (apksigner, zipalign) are in PATH
# ---------------------------------------------------------
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME="/apps/android/Sdk"
fi

if [ -d "$ANDROID_HOME/build-tools" ]; then
    # Find latest build-tools version
    LATEST_BUILD_TOOLS=$(ls -1 "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)
    if [ -n "$LATEST_BUILD_TOOLS" ]; then
        BUILD_TOOLS_PATH="$ANDROID_HOME/build-tools/$LATEST_BUILD_TOOLS"
        if [ -d "$BUILD_TOOLS_PATH" ]; then
            export PATH="$BUILD_TOOLS_PATH:$PATH"
            echo "🔧 Added build-tools to PATH: $BUILD_TOOLS_PATH"
            if command -v apksigner >/dev/null; then
                echo "   ✅ apksigner found: $(command -v apksigner)"
            else
                echo "   ⚠️ apksigner NOT found in $BUILD_TOOLS_PATH"
            fi
        fi
    fi
else
    echo "⚠️  Android SDK build-tools directory not found at $ANDROID_HOME/build-tools"
fi
# ---------------------------------------------------------

# Gradle Worker Configuration
GRADLE_WORKERS="${GRADLE_WORKERS:-2}"


run_gradle() {
    local extra_log="$1"
    shift
    local args=("$@")
    local status

    args+=("${COMMON_GRADLE_OPTS[@]}")

    if [ -n "$extra_log" ]; then
        mkdir -p "$(dirname "$extra_log")"
        nice -n 19 ./gradlew "${args[@]}" 2>&1 | tee -a "$GRADLE_LOG" | tee -a "$extra_log"
    else
        nice -n 19 ./gradlew "${args[@]}" 2>&1 | tee -a "$GRADLE_LOG"
    fi
    status=${PIPESTATUS[0]}
    return $status
}

# Source environment from misc/useEnv
if [ -f "$S2REACHME_ROOT_LOCAL/misc/useEnv" ]; then
    source "$S2REACHME_ROOT_LOCAL/misc/useEnv"
else
    echo "❌ ERROR: misc/useEnv not found at $S2REACHME_ROOT_LOCAL/misc/useEnv"
    exit 1
fi

# Source optional local signing configuration
# Each developer can create their own signing config without sharing production keys
# File: ~/.reachme_signing.env (or REACHME_SIGNING_ENV to override path)
#
# Example ~/.reachme_signing.env contents:
#   export REACHME_KEYSTORE_PATH=/path/to/your/keystore.jks
#   export REACHME_KEYSTORE_PASS=your_keystore_password
#   export REACHME_KEY_ALIAS=your_key_alias
#   export REACHME_KEY_PASS=your_key_password  # Optional, defaults to keystore password
#
SIGNING_ENV_FILE="${REACHME_SIGNING_ENV:-$HOME/.reachme_signing.env}"
if [ -f "$SIGNING_ENV_FILE" ]; then
    source "$SIGNING_ENV_FILE"
    echo "🔐 Loaded signing config from: $SIGNING_ENV_FILE"
fi

# Source project-specific signing configuration (bld.env)
# This allows keeping signing config in the repo (if using relative paths or ignored files)
BLD_ENV_FILE="$PROJECT_DIR/scripts/bld.env"
if [ -f "$BLD_ENV_FILE" ]; then
    source "$BLD_ENV_FILE"
    echo "🔐 Loaded signing config from: $BLD_ENV_FILE"
    
    # Debug/Verify signing config
    if [ -n "$REACHME_KEYSTORE_PATH" ]; then
        echo "   🔑 Keystore Path: $REACHME_KEYSTORE_PATH"
        if [ -f "$REACHME_KEYSTORE_PATH" ]; then
            echo "   ✅ Keystore file exists."
            # Verify credentials
            if keytool -list -keystore "$REACHME_KEYSTORE_PATH" -storepass "$REACHME_KEYSTORE_PASS" -alias "$REACHME_KEY_ALIAS" > /dev/null 2>&1; then
                echo "   ✅ Credentials verified with keytool."
            else
                echo "   ❌ KEYTOOL CHECK FAILED! Password or Alias might be wrong."
                echo "      Pass: ${REACHME_KEYSTORE_PASS:0:2}****"
                echo "      Alias: $REACHME_KEY_ALIAS"
            fi
        else
            echo "   ❌ Keystore file NOT FOUND at this path!"
        fi
    fi
fi

# Source shared error/success handling functions
if [ -f "$MISC_ROOT/scripts/handle.sh" ]; then
    source "$MISC_ROOT/scripts/handle.sh"
else
    # Fallback if handle.sh not found
    handle_error() {
        echo "❌ ERROR: $1"
        # Play error sound
        #if [ -f "/data/pr/media/errInBuild.wav" ]; then
            #aplay /data/pr/media/errInBuild.wav 2>/dev/null || true
        #fi
        nohup /b/donePersistentNotify.py  "Build err" 103 /data/pr/media/errInBuild.wav >/dev/null 2>&1 &
        exit "${2:-1}"
    }

    handle_success() {
        echo "✅ ${1:-Done!}"
        # Play success sound
        #if [ -f "$HOME/Videos/done.wav" ]; then
            #aplay "$HOME/Videos/done.wav" 2>/dev/null || true
            #/data/pr/audio/bldDone.wav           
        #fi
        nohup /b/donePersistentNotify.py  "Build done" 180 /data/pr/audio/bldDone.wav >/dev/null 2>&1 &
    }
fi

# ReachMe Native Android - Fast Build & Deploy Script
# 
# Usage: ./build.sh [BUILD_TYPE] [OPTIONS]
#
# BUILD TYPE (use exactly one, default: no param = Production APK):
#   (none)     - Production APK (CPU-friendly, auto-increment version)
#   d          - Debug APK (standard mode, fast iteration)
#   a          - Release APK (non-debug assembleRelease)
#   r          - Release AAB (for Play Store, auto-increment)
#   p          - Production AAB (CPU-friendly, auto-increment, for Play Store)
#   h or ?     - Show usage and exit
#
# OPTIONS (can be combined with any build type):
#   c          - Clean build (gradlew clean before build)
#   o          - Online mode (download new dependencies, disable --offline)
#   m          - Minified/obfuscated (enables R8, generates mapping.txt)
#                Mapping file: app/build/outputs/mapping/release/mapping.txt
#
# Examples:
#   ./build.sh           # Production APK (CPU-friendly, default)
#   ./build.sh d         # Debug APK
#   ./build.sh d o       # Debug APK, online mode
#   ./build.sh p c m     # Production AAB, clean, minified
#   ./build.sh a m       # Release APK, minified

echo -e "╔════════════════════════════════════════════════════════════╗"
echo -e "║ BUILD TYPE (exactly one):                                  ║"
echo -e "║   (none) : Production APK (CPU-friendly, default)          ║"
echo -e "║   d      : Debug APK                                       ║"
echo -e "║   a      : Release APK (non-debug)                         ║"
echo -e "║   p      : Production AAB (CPU-friendly, Play Store)       ║"
echo -e "╠════════════════════════════════════════════════════════════╣"
echo -e "║ OPTIONS (combinable):                                      ║"
echo -e "║   c      : Clean build                                     ║"
echo -e "║   o      : Online mode (fetch dependencies)                ║"
echo -e "║   m      : Minified (R8 obfuscation + mapping.txt)         ║"
echo -e "║   s      : Select environment (interactive)                ║"
echo -e "║   t1..t4 : Set number of Gradle workers/threads to 1..4    ║"
echo -e "║   l1     : Enable standard linting (removes -x lint)       ║"
echo -e "║   l2     : Enable strict linting (adds --warning-mode all) ║"
echo -e "╠════════════════════════════════════════════════════════════╣"
echo -e "║ Other type (only one):                                     ║"
echo -e "║   h or ? : Show usage and exit                             ║"
echo -e "║   ver    : Show version and build date                     ║"
echo -e "╚════════════════════════════════════════════════════════════╝"
echo -e "║ ENVIRONMENT SHORTCUTS (use instead of 's'):                ║"
echo -e "║   sr     : Select 'production' environment                 ║"
echo -e "║   srq4   : Select 'rq4' environment                        ║"
echo -e "║   srq    : Select 't7rq' environment                       ║"
echo -e "╚════════════════════════════════════════════════════════════╝"

# ...

# Parse parameters
SHOW_HELP=false
DO_CLEAN=false
DO_STANDARD=false
DO_RELEASE=false
DO_ONLINE=false
DO_PRODUCTION=false
DO_PRODUCTION_APK=true # Default to Production APK
DO_RELEASE_APK=false
DO_MINIFY=false
DO_LINT=0
SHOW_VERSION=false
ASK_ENV=false
CLI_ENV_OVERRIDE=""

for arg in "$@"; do
    case "$arg" in
        c) DO_CLEAN=true ;;
        C) DO_CLEAN=true ;;
        d) DO_STANDARD=true; DO_PRODUCTION_APK=false ;; # d for Debug
        D) DO_STANDARD=true; DO_PRODUCTION_APK=false ;;
        s) ASK_ENV=true ;;
        S) ASK_ENV=true ;;
        release|p|P) DO_PRODUCTION=true; DO_PRODUCTION_APK=false ;; # p for Production AAB
        o) DO_ONLINE=true ;;
        O) DO_ONLINE=true ;;
        a) DO_RELEASE_APK=true; DO_PRODUCTION_APK=false ;; # Release APK (non-debug)
        A) DO_RELEASE_APK=true; DO_PRODUCTION_APK=false ;;
        m) DO_MINIFY=true ;; # Enable R8 minification/obfuscation
        M) DO_MINIFY=true ;;
        sr)
            CLI_ENV_OVERRIDE=production
            ASK_ENV=false
            ;;
        SR)
            CLI_ENV_OVERRIDE=production
            ASK_ENV=false
            ;;
        srq4)
            CLI_ENV_OVERRIDE=rq4
            ASK_ENV=false
            ;;
        SRQ4)
            CLI_ENV_OVERRIDE=rq4
            ASK_ENV=false
            ;;
        srq)
            CLI_ENV_OVERRIDE=t7rq
            ASK_ENV=false
            ;;
        SRQ)
            CLI_ENV_OVERRIDE=t7rq
            ASK_ENV=false
            ;;
        h) SHOW_HELP=true ;;
        ?) SHOW_HELP=true ;;
        H) SHOW_HELP=true ;;
        help) SHOW_HELP=true ;;
        ver) SHOW_VERSION=true ;;
        version) SHOW_VERSION=true ;;
        t1|T1) GRADLE_WORKERS=1 ;;
        t2|T2) GRADLE_WORKERS=2 ;;
        t3|T3) GRADLE_WORKERS=3 ;;
        t4|T4) GRADLE_WORKERS=4 ;;
        l1|L1) DO_LINT=1 ;;
        l2|L2) DO_LINT=2 ;;
    esac
done

COMMON_GRADLE_OPTS=(--max-workers="$GRADLE_WORKERS" "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m" --no-daemon --stacktrace)

echo "||| *****"
echo "GRADLE_WORKERS: $GRADLE_WORKERS"
echo "||| *****"

# Build minify flag for gradle
MINIFY_FLAG=""
if [ "$DO_MINIFY" = true ]; then
    MINIFY_FLAG="-PminifyEnabled=true"
    echo "🔒 Minification enabled - mapping.txt will be generated"
fi

# Export Env Vars for Gradle to pick up if needed, but we will pass explicitly via -P
export SERVER_URL="${SERVER_URL:-https://rq4.sel2in.com}"
# Try to source WEB_CLIENT_ID if not set (e.g. from a separate secrets file if it exists, or rely on it being in env)
# For now, we rely on it being set or we'll pass an empty string and let Gradle/Kotlin handle defaults.


# Construct Signing Arguments
SIGNING_ARGS=()
if [ -n "$REACHME_KEYSTORE_PATH" ]; then
    SIGNING_ARGS+=("-PreachmeKeystorePath=$REACHME_KEYSTORE_PATH")
fi
if [ -n "$REACHME_KEYSTORE_PASS" ]; then
    SIGNING_ARGS+=("-PreachmeKeystorePass=$REACHME_KEYSTORE_PASS")
fi
if [ -n "$REACHME_KEY_ALIAS" ]; then
    SIGNING_ARGS+=("-PreachmeKeyAlias=$REACHME_KEY_ALIAS")
fi
# Key pass defaults to keystore pass if not set, but pass it if it exists
if [ -n "$REACHME_KEY_PASS" ]; then
    SIGNING_ARGS+=("-PreachmeKeyPass=$REACHME_KEY_PASS")
elif [ -n "$REACHME_KEYSTORE_PASS" ]; then
     SIGNING_ARGS+=("-PreachmeKeyPass=$REACHME_KEYSTORE_PASS")
fi

echo "================================"
echo "ReachMe Android Build Script"
echo "Env: $USE_ENV"
echo "================================"

cd "$PROJECT_DIR" || handle_error "Could not cd to project dir: $PROJECT_DIR" 1

if [ "$SHOW_VERSION" = true ]; then
    echo "Version: $(cat version.txt)"
    echo "Build Date: $(cat buildDate.txt)"
    echo "Downloads directory listing: $SRVR1_DWNLDS_DIR"
    ls -al "$SRVR1_DWNLDS_DIR"
    exit 0
fi

if [ "$SHOW_HELP" = true ]; then
    echo ""
    echo "  Usage only."
    echo ""
    exit 0
fi

# Auto-increment version for release builds
if [ "$DO_PRODUCTION" = true ] || [ "$DO_PRODUCTION_APK" = true ] || [ "$DO_RELEASE_APK" = true ]; then
    echo "🔢 Auto-incrementing version for release build..."
    PYTHON_CMD=$(command -v python3 || command -v python)
    if [ -z "$PYTHON_CMD" ]; then
        echo "⚠️  Warning: Python not found. Skipping version increment."
        echo "   Install Python 3 to enable auto-versioning."
    else
        VERSION_SCRIPT="$PROJECT_DIR/scripts/version_manager.py"
        if [ -f "$VERSION_SCRIPT" ]; then
            OLD_VERSION=$($PYTHON_CMD "$VERSION_SCRIPT" get)
            $PYTHON_CMD "$VERSION_SCRIPT" increment patch
            NEW_VERSION=$($PYTHON_CMD "$VERSION_SCRIPT" get)
            echo "   Version: $OLD_VERSION -> $NEW_VERSION"
        else
            echo "⚠️  Warning: version_manager.py not found at $VERSION_SCRIPT"
            echo "   Skipping version increment."
        fi
    fi
fi

# Update buildDate.txt with current timestamp
echo "📅 Updating build date..."
date '+%Y-%m-%d %H:%M:%S %:z' > buildDate.txt
echo "   Build date: $(cat buildDate.txt)"

if [ -n "$CLI_ENV_OVERRIDE" ]; then
    export USE_ENV_OVERRIDE="$CLI_ENV_OVERRIDE"
    echo "   ✅ CLI-selected environment: $CLI_ENV_OVERRIDE"
    unset SERVER_URL SERVER_PORT CLIENT_URL CLIENT_PORT FLUTTER_FLAVOR
    source "$S2REACHME_ROOT_LOCAL/misc/useEnv"
    echo "   🌍 Environment configured: $USE_ENV (SERVER_URL=$SERVER_URL)"
    echo ""
elif [ "$ASK_ENV" = true ]; then
    # Show environment selection menu
    echo ""
    echo "╔════════════════════════════════════════╗"
    echo "║   Select Target Environment:           ║"
    echo "╠════════════════════════════════════════╣"
    echo "║  1) production (r.sel2in.com)          ║"
    echo "║  2) rq4 (rq4.sel2in.com)               ║"
    echo "║  3) t7rq (rq.sel2in.com)               ║"
    echo "║  4) Other (custom environment)         ║"
    echo "╚════════════════════════════════════════╝"
    echo ""
    read -p "Enter choice [1-4]: " env_choice
    
    case "$env_choice" in
        1)
            export USE_ENV_OVERRIDE=production
            echo "   ✅ Selected: production"
            ;;
        2)
            export USE_ENV_OVERRIDE=rq4
            echo "   ✅ Selected: rq4"
            ;;
        3)
            export USE_ENV_OVERRIDE=t7rq
            echo "   ✅ Selected: t7rq"
            ;;
        4)
            read -p "Enter custom environment name: " custom_env
            if [ -z "$custom_env" ]; then
                echo "   ❌ Error: Environment name cannot be empty"
                exit 1
            fi
            export USE_ENV_OVERRIDE="$custom_env"
            echo "   ✅ Selected: $custom_env"
            ;;
        *)
            echo "   ❌ Invalid choice. Defaulting to production."
            export USE_ENV_OVERRIDE=production
            ;;
    esac

    unset SERVER_URL SERVER_PORT CLIENT_URL CLIENT_PORT FLUTTER_FLAVOR
    source "$S2REACHME_ROOT_LOCAL/misc/useEnv"
    echo "   🌍 Environment configured: $USE_ENV (SERVER_URL=$SERVER_URL)"
    echo ""
elif [ "$DO_RELEASE_APK" = true ]; then
    export USE_ENV_OVERRIDE=rq4
    echo "   ✅ Auto-selected environment: rq4 (Build Type: a)"
    
    unset SERVER_URL SERVER_PORT CLIENT_URL CLIENT_PORT FLUTTER_FLAVOR
    source "$S2REACHME_ROOT_LOCAL/misc/useEnv"
    echo "   🌍 Environment configured: $USE_ENV (SERVER_URL=$SERVER_URL)"
    echo ""
elif [ "$DO_PRODUCTION" = true ]; then
    export USE_ENV_OVERRIDE=production
    echo "   ✅ Auto-selected environment: production (Build Type: p)"
    
    unset SERVER_URL SERVER_PORT CLIENT_URL CLIENT_PORT FLUTTER_FLAVOR
    source "$S2REACHME_ROOT_LOCAL/misc/useEnv"
    echo "   🌍 Environment configured: $USE_ENV (SERVER_URL=$SERVER_URL)"
    echo ""
fi

# Handle clean
if [ "$DO_CLEAN" = true ]; then
    echo "🧹 Cleaning previous build..."
    if ! run_gradle "" clean; then
        handle_error "Clean failed!" 1
    fi
    echo ""
fi

echo "USE_ENV_OVERRIDE : $USE_ENV_OVERRIDE  slp 5"
sleep 5
# Build AAB for release, APK for debug
if [ "$DO_PRODUCTION" = true ]; then
    echo "🔨 Building PRODUCTION AAB (CPU-friendly mode)..."
    echo "   ℹ️  AAB format required for Google Play Store uploads"
    echo "   🌡️  Using CPU-friendly settings to prevent overheating"
    echo "   📁 Output will be redirected to: $OUTPUT_DIR"

    
    # CPU-friendly gradle options:
    # --max-workers=2: Limit parallel tasks to reduce CPU load
    # -Dorg.gradle.jvmargs: Limit heap memory to reduce CPU usage
    # --no-daemon: Avoid keeping daemon running (saves memory)
    # -x lint: Skip linting to save CPU cycles
    BUILD_LOG="$OUTPUT_DIR/build_output_$(date +%Y%m%d_%H%M%S).log"
    GRADLE_ARGS=(bundleRelease "-PserverUrl=$SERVER_URL" "${SIGNING_ARGS[@]}")
    if [ -n "$MINIFY_FLAG" ]; then
        GRADLE_ARGS+=("$MINIFY_FLAG")
    fi
    if [ "$DO_LINT" -eq 0 ]; then
        GRADLE_ARGS+=(-x lint)
    elif [ "$DO_LINT" -eq 2 ]; then
        GRADLE_ARGS+=(--warning-mode all)
    fi
    if ! run_gradle "$BUILD_LOG" "${GRADLE_ARGS[@]}"; then
        handle_error "Gradle Production Bundle Build failed!" 1
    fi
    AAB_PATH="app/build/outputs/bundle/release/reachme_release.aab"
    BUILD_TYPE="production"
    BUILD_FORMAT="aab"
elif [ "$DO_PRODUCTION_APK" = true ]; then
    echo "🔨 Building PRODUCTION APK (CPU-friendly mode)..."
    echo "   ℹ️  APK format for direct installation or distribution"
    echo "   🌡️  Using CPU-friendly settings to prevent overheating"
    echo "   📁 Output will be redirected to: $OUTPUT_DIR"
    
    # CPU-friendly gradle options (same as production AAB)
    BUILD_LOG="$OUTPUT_DIR/build_output_$(date +%Y%m%d_%H%M%S).log"
    GRADLE_ARGS=(assembleRelease "-PserverUrl=$SERVER_URL" "${SIGNING_ARGS[@]}")
    if [ -n "$MINIFY_FLAG" ]; then
        GRADLE_ARGS+=("$MINIFY_FLAG")
    fi
    if [ "$DO_LINT" -eq 0 ]; then
        GRADLE_ARGS+=(-x lint)
    elif [ "$DO_LINT" -eq 2 ]; then
        GRADLE_ARGS+=(--warning-mode all)
    fi
    if ! run_gradle "$BUILD_LOG" "${GRADLE_ARGS[@]}"; then
        handle_error "Gradle Production APK Build failed!" 1
    fi
    APK_PATH="app/build/outputs/apk/release/reachme_release.apk"
    BUILD_TYPE="production"
    BUILD_FORMAT="apk"
elif [ "$DO_RELEASE_APK" = true ]; then
    echo "🔨 Building RELEASE APK (non-debug, CPU-friendly)..."
    echo "   🌡️  Using CPU-friendly settings to prevent overheating"
    
    GRADLE_ARGS=(assembleRelease "-PserverUrl=$SERVER_URL" "${SIGNING_ARGS[@]}")
    if [ -n "$MINIFY_FLAG" ]; then
        GRADLE_ARGS+=("$MINIFY_FLAG")
    fi
    if [ "$DO_LINT" -eq 0 ]; then
        GRADLE_ARGS+=(-x lint)
    elif [ "$DO_LINT" -eq 2 ]; then
        GRADLE_ARGS+=(--warning-mode all)
    fi
    if ! run_gradle "" "${GRADLE_ARGS[@]}"; then
        handle_error "Gradle Release APK Build failed!" 1
    fi
    APK_PATH="app/build/outputs/apk/release/reachme_release.apk"
    BUILD_TYPE="release"
    BUILD_FORMAT="apk"
elif [ "$DO_STANDARD" = true ]; then
    echo "🔨 Building DEBUG APK (standard mode, CPU-friendly)..."
    echo "   🌡️  Using CPU-friendly settings"
    
    GRADLE_ARGS=(assembleDebug "-PserverUrl=$SERVER_URL")
    if ! run_gradle "" "${GRADLE_ARGS[@]}"; then
        handle_error "Gradle Debug Build (Standard) failed!" 1
    fi
    APK_PATH="app/build/outputs/apk/debug/reachme_debug.apk"
    BUILD_TYPE="debug"
    BUILD_FORMAT="apk"
else
    if [ "$DO_ONLINE" = true ]; then
        echo "🔨 Building DEBUG APK (fast mode, online, CPU-friendly)..."
        # Removing --parallel as it contradicts max-workers=2 intent for heat reduction
        GRADLE_ARGS=(assembleDebug "-PserverUrl=$SERVER_URL" --configure-on-demand)
        if [ "$DO_LINT" -eq 0 ]; then
            GRADLE_ARGS+=(-x lint)
        elif [ "$DO_LINT" -eq 2 ]; then
            GRADLE_ARGS+=(--warning-mode all)
        fi
        if ! run_gradle "" "${GRADLE_ARGS[@]}"; then
            handle_error "Gradle Debug Build (Online) failed!" 1
        fi
    else
        echo "🔨 Building DEBUG APK (fast mode, offline, CPU-friendly)..."
        echo "   ℹ️  Running in offline mode. Use './build.sh o' to download new dependencies."
        GRADLE_ARGS=(assembleDebug "-PserverUrl=$SERVER_URL" --offline --configure-on-demand)
        if [ "$DO_LINT" -eq 0 ]; then
            GRADLE_ARGS+=(-x lint)
        elif [ "$DO_LINT" -eq 2 ]; then
            GRADLE_ARGS+=(--warning-mode all)
        fi
        if ! run_gradle "" "${GRADLE_ARGS[@]}"; then
             handle_error "Gradle Debug Build (Offline) failed!" 1
        fi
    fi
    APK_PATH="app/build/outputs/apk/debug/reachme_debug.apk"
    BUILD_TYPE="debug"
    BUILD_FORMAT="apk"
fi

# Check if build succeeded
if [ "$BUILD_FORMAT" = "aab" ]; then
    if [ ! -f "$AAB_PATH" ]; then
        handle_error "Build failed! AAB not found at $AAB_PATH" 1
    fi
    BUILD_OUTPUT="$AAB_PATH"
    echo "✅ Build successful!"
    echo "📦 AAB: $AAB_PATH"
else
    if [ ! -f "$APK_PATH" ]; then
        handle_error "Build failed! APK not found at $APK_PATH" 1
    fi
    BUILD_OUTPUT="$APK_PATH"
    echo "✅ Build successful!"
    echo "📦 APK: $APK_PATH"
fi

# Get file size
FILE_SIZE=$(du -h "$BUILD_OUTPUT" | cut -f1)
echo "📏 Size: $FILE_SIZE"
echo ""

# Copy to public downloads directory
echo "📂 Copying to public downloads..."
mkdir -p "$SRVR1_DWNLDS_DIR"
cp "$BUILD_OUTPUT" "$SRVR1_DWNLDS_DIR/reachme-${BUILD_TYPE}.${BUILD_FORMAT}"
echo "✅ Copied to $SRVR1_DWNLDS_DIR/reachme-${BUILD_TYPE}.${BUILD_FORMAT}"

# Copy to output directory if production build
if [ "$DO_PRODUCTION" = true ] || [ "$DO_PRODUCTION_APK" = true ]; then
    echo "📂 Copying to output directory..."
    mkdir -p "$OUTPUT_DIR"
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    cp "$BUILD_OUTPUT" "$OUTPUT_DIR/reachme_production_${TIMESTAMP}.${BUILD_FORMAT}"
    echo "✅ Copied to $OUTPUT_DIR/reachme_production_${TIMESTAMP}.${BUILD_FORMAT}"
fi
echo ""

# Install via adb if device connected (APK only)
if [ "$BUILD_FORMAT" = "apk" ]; then
    echo "📱 Checking for connected devices..."
    DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)
    
    if [ "$DEVICE_COUNT" -gt 0 ]; then
        echo "✅ Found $DEVICE_COUNT devices"
        echo "🚀 Installing APK..."
        adb install -r "$APK_PATH"
        
        if [ $? -eq 0 ]; then
            echo "✅ Installation successful!"
            echo ""
            echo "🎉 Build deployed to device!"
        else
            echo "⚠️ Installation failed - device might be locked or USB debugging disabled"
        fi
    else
        echo "ℹ️ No devices connected via adb"
        echo "   - Enable USB debugging on your device"
        echo "   - Connect via USB and authorize computer"
        echo "   - Or manually install: adb install $APK_PATH"
    fi
else
    echo "ℹ️ AAB files cannot be directly installed on devices"
    echo "   📤 Upload to Google Play Console for distribution"
    echo "   🔧 Or use bundletool to generate APK for local testing:"
    echo "      bundletool build-apks --bundle=$AAB_PATH --output=app.apks --mode=universal"
fi

if [ "$DO_MINIFY" = true ]; then
    echo ""
    echo "Mapping file: $PROJECT_DIR/app/build/outputs/mapping/release/mapping.txt"
fi

echo ""
echo "================================"
echo "✅ Build Complete!"
echo "================================"
if [ "$BUILD_FORMAT" = "aab" ]; then
    echo "AAB Location:"
    echo "  Local:     $PROJECT_DIR/$AAB_PATH"
    echo "  S1 downloads:   $SRVR1_DWNLDS_DIR/reachme-${BUILD_TYPE}.${BUILD_FORMAT}"
    echo ""
    echo "Upload to Play Store:"
    echo "  Use Google Play Console to upload the AAB file"
else
    echo "APK Location:"
    echo "  Local:     $PROJECT_DIR/$APK_PATH"
    echo "  S1 downloads:   $SRVR1_DWNLDS_DIR/reachme-${BUILD_TYPE}.${BUILD_FORMAT}"
    echo ""
    echo "Quick Install:"
    echo "  adb install -r $PROJECT_DIR/$APK_PATH"
fi
echo "================================"
echo "Environment Summary:"
echo "  USE_ENV_OVERRIDE: ${USE_ENV_OVERRIDE:-${USE_ENV:-unknown}}"
echo "  SERVER_URL: ${SERVER_URL:-unknown}"
echo "================================"

# Remote Deployment for APKs
if [ "$BUILD_FORMAT" = "apk" ]; then
    SERVERS_DIR="$S2REACHME_ROOT_LOCAL/server1/src/servers"
    UPLOAD_SCRIPT="$S2REACHME_ROOT_LOCAL/misc/scripts/move/upload.sh"

    if [ ! -x "$UPLOAD_SCRIPT" ]; then
        echo "ℹ️  Upload script not found or not executable at $UPLOAD_SCRIPT; skipping remote deployment."
    elif [ ! -d "$SERVERS_DIR" ]; then
        echo "ℹ️  Server configuration directory not found at $SERVERS_DIR; skipping remote deployment."
    else
        echo ""
        echo "🌐 Checking for remote deployment targets..."
        "$UPLOAD_SCRIPT" "$PROJECT_DIR/$APK_PATH" "$SERVERS_DIR" "server1/public/downloads"
    fi
fi

echo "================================"
echo "Environment Summary:"
echo "  USE_ENV_OVERRIDE: ${USE_ENV_OVERRIDE:-${USE_ENV:-unknown}}"
echo "  SERVER_URL: ${SERVER_URL:-unknown}"
echo "================================"
echo "Build date:" 
cat "$PROJECT_DIR/buildDate.txt"
echo ""
# Play success sound
handle_success "Build Complete!"
echo ""
cat ./version.txt

echo ""
echo "🔐 Verifying signature..."
IS_SIGNED=false


# Check if apksigner is available and applicable
# apksigner only verifies APKs (not AABs)
if [[ "$BUILD_FORMAT" == "apk" ]] && command -v apksigner &> /dev/null; then
    if apksigner verify "$BUILD_OUTPUT" 2>/dev/null; then
        IS_SIGNED=true
    fi
else
    # Fallback to keytool: check if it prints certificate info
    # Works for both APKs and AABs (since they are ZIPs of JARs/etc)
    if keytool -printcert -jarfile "$BUILD_OUTPUT" 2>&1 | grep -q "SHA1:"; then
        IS_SIGNED=true
    fi
fi

if [ "$IS_SIGNED" = false ]; then
    echo ""
    echo "🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴"
    echo "🔴 !!! WARNING: BUILD IS NOT SIGNED !!! 🔴"
    echo "🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴"
    echo ""
    # Only fail if it was supposed to be a release build
    if [ "$DO_PRODUCTION" = true ] || [ "$DO_PRODUCTION_APK" = true ] || [ "$DO_RELEASE_APK" = true ]; then
        echo "⚠️  This is a RELEASE build but it appears unsigned."
        echo "   Check your signing verification variables."
    fi
else
    echo "✅ Signature verification passed."
fi 