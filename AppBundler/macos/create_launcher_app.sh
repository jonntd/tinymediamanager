#!/bin/bash

# Create a launcher app that automatically handles quarantine removal
# This creates a separate .app that launches the main tinyMediaManager.app

LAUNCHER_NAME="Launch tinyMediaManager"
LAUNCHER_APP="${LAUNCHER_NAME}.app"

echo "🚀 Creating launcher app: $LAUNCHER_APP"

# Create app bundle structure
mkdir -p "$LAUNCHER_APP/Contents/MacOS"
mkdir -p "$LAUNCHER_APP/Contents/Resources"

# Create Info.plist
cat > "$LAUNCHER_APP/Contents/Info.plist" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>launcher</string>
    <key>CFBundleIdentifier</key>
    <string>org.tinymediamanager.launcher</string>
    <key>CFBundleName</key>
    <string>Launch tinyMediaManager</string>
    <key>CFBundleVersion</key>
    <string>1.0</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleSignature</key>
    <string>????</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.9</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>LSUIElement</key>
    <true/>
</dict>
</plist>
EOF

# Create launcher executable
cat > "$LAUNCHER_APP/Contents/MacOS/launcher" << 'EOF'
#!/bin/bash

# Get the directory where this launcher is located
LAUNCHER_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
BUNDLE_DIR="$(dirname "$(dirname "$LAUNCHER_DIR")")"
MAIN_DIR="$(dirname "$BUNDLE_DIR")"
APP_PATH="$MAIN_DIR/tinyMediaManager.app"

# Function to show notification
show_notification() {
    osascript -e "display notification \"$2\" with title \"$1\""
}

# Function to show dialog
show_dialog() {
    osascript -e "display dialog \"$1\" with title \"tinyMediaManager Launcher\" buttons {\"OK\"} default button \"OK\""
}

# Function to remove quarantine silently
remove_quarantine_silent() {
    xattr -dr com.apple.quarantine "$APP_PATH" 2>/dev/null
    return $?
}

# Function to check if app exists
if [ ! -d "$APP_PATH" ]; then
    show_dialog "Error: tinyMediaManager.app not found at expected location."
    exit 1
fi

# Try to remove quarantine silently
if remove_quarantine_silent; then
    show_notification "tinyMediaManager" "Security fix applied, launching..."
else
    show_notification "tinyMediaManager" "Launching application..."
fi

# Launch the main app
open "$APP_PATH"

# Exit the launcher
exit 0
EOF

# Make launcher executable
chmod +x "$LAUNCHER_APP/Contents/MacOS/launcher"

echo "✅ Launcher app created: $LAUNCHER_APP"
echo "📋 This app will automatically handle quarantine removal and launch tinyMediaManager"
