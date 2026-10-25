#!/usr/bin/env bash
set -euo pipefail

# Navigate to the repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Parse optional arguments
BUILD_VARIANT="debug"
for arg in "$@"; do
    case "$arg" in
        --release|release)
            BUILD_VARIANT="release"
            ;;
        --debug|debug)
            BUILD_VARIANT="debug"
            ;;
        --help|-h)
            echo "Usage: $0 [--debug|--release]"
            echo "Builds, installs, and launches the app on the first connected Android phone."
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg"
            echo "Usage: $0 [--debug|--release]"
            exit 1
            ;;
    esac
done

# Locate adb binary
find_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return 0
    fi
    if command -v adb.exe >/dev/null 2>&1; then
        command -v adb.exe
        return 0
    fi

    # Check ANDROID_HOME and ANDROID_SDK_ROOT environment variables
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        if [ -n "$sdk" ]; then
            if [ -x "$sdk/platform-tools/adb" ]; then
                echo "$sdk/platform-tools/adb"
                return 0
            fi
            if [ -x "$sdk/platform-tools/adb.exe" ]; then
                echo "$sdk/platform-tools/adb.exe"
                return 0
            fi
        fi
    done

    # Check local.properties if present
    if [ -f "$SCRIPT_DIR/local.properties" ]; then
        local sdk_dir
        sdk_dir=$(grep '^sdk.dir=' "$SCRIPT_DIR/local.properties" 2>/dev/null | head -n 1 | cut -d'=' -f2- | sed -e 's/\\:/:/g' -e 's/\\\\/\//g' -e 's/\\/\//g' -e 's/\r//g')
        if [ -n "$sdk_dir" ]; then
            if [ -x "$sdk_dir/platform-tools/adb" ]; then
                echo "$sdk_dir/platform-tools/adb"
                return 0
            fi
            if [ -x "$sdk_dir/platform-tools/adb.exe" ]; then
                echo "$sdk_dir/platform-tools/adb.exe"
                return 0
            fi
            if command -v cygpath >/dev/null 2>&1; then
                local unix_sdk
                unix_sdk=$(cygpath -u "$sdk_dir" 2>/dev/null || true)
                if [ -n "$unix_sdk" ]; then
                    if [ -x "$unix_sdk/platform-tools/adb" ]; then
                        echo "$unix_sdk/platform-tools/adb"
                        return 0
                    fi
                    if [ -x "$unix_sdk/platform-tools/adb.exe" ]; then
                        echo "$unix_sdk/platform-tools/adb.exe"
                        return 0
                    fi
                fi
            fi
        fi
    fi

    # Common standard installation paths
    local candidates=(
        "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
        "${HOME}/AppData/Local/Android/Sdk/platform-tools/adb.exe"
        "${HOME}/Android/Sdk/platform-tools/adb"
        "${HOME}/Library/Android/sdk/platform-tools/adb"
        "/mnt/c/Users/${USER:-}/AppData/Local/Android/Sdk/platform-tools/adb.exe"
    )

    for cand in "${candidates[@]}"; do
        if [ -f "$cand" ]; then
            echo "$cand"
            return 0
        fi
    done

    return 1
}

ADB="$(find_adb || true)"
if [ -z "$ADB" ]; then
    echo "Error: 'adb' executable could not be found." >&2
    echo "Please ensure the Android SDK platform-tools are installed and 'adb' is on your PATH," >&2
    echo "or configure sdk.dir in local.properties." >&2
    exit 1
fi

echo "Using adb: $ADB"

# Retrieve list of ready devices (state == "device")
devices=()
while IFS=$'\t ' read -r serial state rest; do
    if [ "$state" = "device" ]; then
        devices+=("$serial")
    fi
done < <("$ADB" devices | grep -v "^List of devices attached" | tr -d '\r')

TARGET_DEVICE=""

# Find the first physical phone (non-emulator device)
for dev in "${devices[@]}"; do
    if [[ ! "$dev" =~ ^emulator- ]]; then
        TARGET_DEVICE="$dev"
        break
    fi
done

# If no physical phone found, fallback to the first connected device/emulator
if [ -z "$TARGET_DEVICE" ] && [ ${#devices[@]} -gt 0 ]; then
    TARGET_DEVICE="${devices[0]}"
    echo "Notice: No physical phone found, using connected device/emulator: $TARGET_DEVICE"
fi

if [ -z "$TARGET_DEVICE" ]; then
    echo "Error: No connected Android device in ready state found." >&2
    raw_devices=$("$ADB" devices | grep -v "^List of devices attached" | tr -d '\r' | grep -v '^[[:space:]]*$' || true)
    if echo "$raw_devices" | grep -q "unauthorized"; then
        echo "Found device in 'unauthorized' state. Please check your phone screen and confirm 'Allow USB debugging'." >&2
    elif echo "$raw_devices" | grep -q "offline"; then
        echo "Found device in 'offline' state. Please disconnect and reconnect your phone." >&2
    else
        echo "Please connect your phone via USB or wireless adb and ensure USB debugging is enabled." >&2
    fi
    exit 1
fi

# Query phone info
MANUFACTURER=$("$ADB" -s "$TARGET_DEVICE" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r' || true)
MODEL=$("$ADB" -s "$TARGET_DEVICE" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)
ANDROID_VER=$("$ADB" -s "$TARGET_DEVICE" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || true)

DEVICE_LABEL="$TARGET_DEVICE"
if [ -n "$MANUFACTURER" ] || [ -n "$MODEL" ]; then
    DEVICE_LABEL="$MANUFACTURER $MODEL ($TARGET_DEVICE, Android $ANDROID_VER)"
fi
echo "Target device: $DEVICE_LABEL"

# Configure build variables
if [ "$BUILD_VARIANT" = "release" ]; then
    GRADLE_TASK="assembleRelease"
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
    PACKAGE_NAME="com.pereviader.backgroundswitcher"
else
    GRADLE_TASK="assembleDebug"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    PACKAGE_NAME="com.pereviader.backgroundswitcher.debug"
fi
ACTIVITY_NAME="com.pereviader.backgroundswitcher.ui.MainActivity"

echo "Building application ($GRADLE_TASK)..."
if [ -f "./gradlew" ]; then
    sh ./gradlew "$GRADLE_TASK"
elif [ -f "./gradlew.bat" ]; then
    ./gradlew.bat "$GRADLE_TASK"
else
    gradle "$GRADLE_TASK"
fi

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH" >&2
    exit 1
fi

echo "Installing $APK_PATH to $DEVICE_LABEL..."
"$ADB" -s "$TARGET_DEVICE" install -r -d -t "$APK_PATH"

echo "Launching $PACKAGE_NAME on $DEVICE_LABEL..."
"$ADB" -s "$TARGET_DEVICE" shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

echo "Successfully installed and launched on $DEVICE_LABEL!"
