#!/bin/bash

# tinyMediaManager Auto-Launch Script for macOS
# This script automatically handles quarantine removal and launches the app

set -e

echo "🍎 tinyMediaManager Auto-Launch for macOS"
echo "========================================="

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
APP_PATH="$SCRIPT_DIR/tinyMediaManager.app"

# Function to check if app is quarantined
check_quarantine() {
    if xattr -l "$APP_PATH" 2>/dev/null | grep -q "com.apple.quarantine"; then
        return 0  # App is quarantined
    else
        return 1  # App is not quarantined
    fi
}

# Function to remove quarantine
remove_quarantine() {
    echo "🔧 Removing macOS quarantine attribute..."
    if xattr -dr com.apple.quarantine "$APP_PATH" 2>/dev/null; then
        echo "✅ Quarantine attribute removed successfully"
        return 0
    else
        echo "❌ Failed to remove quarantine attribute"
        return 1
    fi
}

# Function to launch app
launch_app() {
    echo "🚀 Launching tinyMediaManager..."
    open "$APP_PATH"
}

# Main logic
if [ ! -d "$APP_PATH" ]; then
    echo "❌ Error: tinyMediaManager.app not found at: $APP_PATH"
    echo "Please make sure this script is in the same directory as tinyMediaManager.app"
    exit 1
fi

echo "📍 Found tinyMediaManager.app at: $APP_PATH"

# Check if app is quarantined
if check_quarantine; then
    echo "⚠️  App is quarantined by macOS security"
    echo "🔧 Attempting to fix automatically..."
    
    if remove_quarantine; then
        echo "✅ Security issue fixed!"
        launch_app
    else
        echo "❌ Automatic fix failed"
        echo ""
        echo "Please run this command manually in Terminal:"
        echo "xattr -dr com.apple.quarantine \"$APP_PATH\""
        echo ""
        echo "Then try launching tinyMediaManager.app again"
        exit 1
    fi
else
    echo "✅ App is not quarantined, launching directly..."
    launch_app
fi

echo "🎉 tinyMediaManager should now be running!"
