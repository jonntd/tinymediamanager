# macOS GitHub Builds - User Guide

## 🍎 Important Notice for macOS Users

GitHub Actions builds for macOS are **unsigned** due to technical limitations. This means you'll encounter security warnings when trying to run the application.

## 🚨 Security Warning You'll See

When you try to open tinyMediaManager from GitHub builds, you'll see:
- "访达没有权限打开(null)" (Finder doesn't have permission to open)
- "App can't be opened because it is from an unidentified developer"
- "macOS cannot verify the developer of this app"

## ✅ How to Run GitHub macOS Builds

### Method 1: Remove Quarantine Attribute (Recommended)

1. **Download and extract** the DMG or ZIP file
2. **Open Terminal** and navigate to the extracted folder
3. **Remove quarantine attribute**:
   ```bash
   xattr -dr com.apple.quarantine tinyMediaManager.app
   ```
4. **Run the application** - it should now open normally

### Method 2: System Preferences Override

1. **Try to open** tinyMediaManager.app (it will fail)
2. **Go to System Preferences** > Security & Privacy > General
3. **Click "Open Anyway"** next to the blocked app message
4. **Confirm** when prompted

### Method 3: Right-Click Override

1. **Right-click** on tinyMediaManager.app
2. **Select "Open"** from context menu
3. **Click "Open"** in the security dialog
4. **The app will run** and be remembered as safe

## 🔒 Why This Happens

### GitHub vs GitLab Builds

| Feature | GitHub Builds | GitLab Builds |
|---------|---------------|---------------|
| **Code Signing** | ❌ Not available | ✅ Full signing |
| **Apple Notarization** | ❌ Not possible | ✅ Complete |
| **DMG Creation** | ⚠️ Basic only | ✅ Professional |
| **Security** | ⚠️ Manual override needed | ✅ Fully trusted |

### Technical Limitations

1. **No Developer Certificate**: GitHub Actions can't access Apple Developer certificates
2. **No Notarization**: Apple notarization requires paid developer account
3. **Security Policy**: macOS blocks unsigned apps by default since 10.15

## 🎯 Recommended Approach

### For Regular Users
- **Use GitLab builds** from [official releases](https://release.tinymediamanager.org)
- GitLab builds are fully signed and notarized
- No security warnings or manual overrides needed

### For Developers/Testers
- **GitHub builds** are perfect for testing latest features
- Use the quarantine removal method above
- Understand the security implications

## 🔧 Technical Details

### What GitHub Builds Include
```
tinyMediaManager-5.2.0-macos-x86_64-unsigned.dmg
├── tinyMediaManager.app/
│   ├── Contents/
│   │   ├── Info.plist
│   │   ├── MacOS/
│   │   └── Resources/
│   └── (unsigned - no code signature)
```

### What GitLab Builds Include
```
tinyMediaManager-5.2.0-macos-x86_64.dmg
├── tinyMediaManager.app/
│   ├── Contents/
│   │   ├── Info.plist
│   │   ├── MacOS/
│   │   └── Resources/
│   └── (fully signed + notarized)
```

## 🛡️ Security Considerations

### Is it Safe?
- **GitHub builds are safe** - they contain the same code as GitLab builds
- **Source is identical** - both use the same repository
- **Only difference** - GitHub builds lack Apple's cryptographic signature

### What You're Bypassing
- **Gatekeeper protection** - macOS's first line of defense
- **Automatic malware scanning** - Apple's notarization includes malware checks
- **Developer verification** - Apple's verification of developer identity

### Best Practices
1. **Only download** from official GitHub repository
2. **Verify checksums** if provided
3. **Use antivirus** if you're concerned
4. **Prefer GitLab builds** for production use

## 🚀 Future Improvements

We're working on:
- **Self-hosted runners** with signing capabilities
- **Alternative distribution** methods
- **Better user experience** for GitHub builds

## 📞 Support

If you encounter issues:
1. **Check this guide** first
2. **Try GitLab builds** as alternative
3. **Report issues** on GitHub with detailed error messages
4. **Include macOS version** and security settings

## 📋 Quick Reference

### Command to Allow App
```bash
xattr -dr com.apple.quarantine tinyMediaManager.app
```

### Check if App is Quarantined
```bash
xattr -l tinyMediaManager.app
```

### Verify App Signature (will fail for GitHub builds)
```bash
codesign -dv --verbose=4 tinyMediaManager.app
```

---

**Remember**: GitLab builds are recommended for regular use. GitHub builds are excellent for testing and development!
