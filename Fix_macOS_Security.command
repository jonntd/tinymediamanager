#!/bin/bash
set -e

echo "🎯 tinyMediaManager 直接路径修复工具"
echo "================================"
echo ""

# 直接指定您要的路径
APP_PATH="/Applications/tinyMediaManager.app"

echo "📍 目标路径: $APP_PATH"

# 验证应用程序是否存在
if [[ ! -d "$APP_PATH" ]]; then
    echo "❌ 未找到应用程序: $APP_PATH"
    echo ""
    echo "请确认："
    echo "1. tinyMediaManager.app 是否已安装到 /Applications？"
    echo "2. 应用程序名称是否正确？"
    echo ""
    echo "解决方案："
    echo "- 从DMG将 tinyMediaManager.app 拖入 /Applications 文件夹"
    echo "- 或使用 Spotlight 搜索 tinyMediaManager.app"
    exit 1
fi

echo "✅ 找到应用程序"
echo ""

# 步骤1: 移除隔离属性
echo "🔧 步骤 1: 移除隔离属性..."
if xattr -dr com.apple.quarantine "$APP_PATH" 2>/dev/null; then
    echo "✅ 隔离属性移除成功"
elif sudo xattr -dr com.apple.quarantine "$APP_PATH" 2>/dev/null; then
    echo "✅ 隔离属性移除成功 (使用sudo)"
else
    echo "⚠️  隔离属性移除失败 (可能不需要)"
fi

# 步骤2: 修复文件权限
echo ""
echo "🔧 步骤 2: 修复文件权限..."

# 修复主可执行文件
MAIN_EXEC="$APP_PATH/Contents/MacOS/tinyMediaManager"
if [[ -f "$MAIN_EXEC" ]]; then
    chmod +x "$MAIN_EXEC" 2>/dev/null || sudo chmod +x "$MAIN_EXEC"
    echo "✅ 主执行文件权限已修复"
else
    echo "❌ 未找到主执行文件: $MAIN_EXEC"
fi

# 修复Java可执行文件
JAVA_EXECS=(
    "$APP_PATH/Contents/MacOS/jre/bin/java"
    "$APP_PATH/Contents/MacOS/jre/Contents/Home/bin/java"
)

for java_exec in "${JAVA_EXECS[@]}"; do
    if [[ -f "$java_exec" ]]; then
        chmod +x "$java_exec" 2>/dev/null || sudo chmod +x "$java_exec"
        echo "✅ Java执行文件权限已修复: $(basename "$java_exec")"
    fi
done

# 修复整个应用包权限
chmod -R u+rw,go+r "$APP_PATH" 2>/dev/null || sudo chmod -R u+rw,go+r "$APP_PATH"
find "$APP_PATH" -type d -exec chmod 755 {} \; 2>/dev/null || sudo find "$APP_PATH" -type d -exec chmod 755 {} \;
echo "✅ 应用包权限已统一修复"

# 步骤3: 验证应用程序完整性
echo ""
echo "🔧 步骤 3: 验证应用程序结构..."

REQUIRED_FILES=(
    "$APP_PATH/Contents/Info.plist"
    "$APP_PATH/Contents/MacOS/tinyMediaManager"
)

ALL_GOOD=true
for file in "${REQUIRED_FILES[@]}"; do
    if [[ ! -e "$file" ]]; then
        echo "❌ 缺少关键文件: $file"
        ALL_GOOD=false
    fi
done

if [[ "$ALL_GOOD" = true ]]; then
    echo "✅ 应用程序结构完整"
else
    echo "⚠️  应用程序可能已损坏，建议重新下载"
fi

echo ""
echo "🎉 修复完成！"
echo ""
echo "现在可以："
echo "1. 双击 /Applications/tinyMediaManager.app 启动"
echo "2. 或右键点击选择 '打开'"
echo ""
read -p "按回车键关闭此窗口..."