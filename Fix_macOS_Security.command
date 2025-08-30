#!/bin/bash
set -e

echo "🍎 tinyMediaManager.app 正式版本安全修复工具"
echo "=========================================="
echo ""
echo "此脚本将修复 /Applications/tinyMediaManager.app 的macOS安全限制"
echo ""
echo "修复内容："
echo "- 移除应用程序隔离属性"
echo "- 修复.app包内所有可执行文件权限"
echo "- 验证应用程序完整性"
echo ""
read -p "按回车键继续修复，或按 Ctrl+C 取消..."

echo ""

# 设置应用程序路径
APP_PATH="/Applications/tinyMediaManager.app"

echo "📍 目标应用: $APP_PATH"

# 检查应用程序是否存在
if [ ! -d "$APP_PATH" ]; then
    echo "❌ 未找到应用程序: $APP_PATH"
    echo ""
    echo "请确认："
    echo "1. tinyMediaManager.app 是否已移动到应用程序文件夹？"
    echo "2. 或者使用 Finder 找到应用程序的准确位置"
    echo ""
    read -p "按回车键退出..."
    exit 1
fi

echo "✅ 找到应用程序"
echo ""

# 步骤1: 移除隔离属性
echo "🔧 步骤 1: 移除隔离属性..."
if sudo xattr -dr com.apple.quarantine "$APP_PATH" 2>/dev/null; then
    echo "✅ 隔离属性移除成功"
else
    echo "⚠️  隔离属性移除失败 (可能不需要或权限不足)"
fi

# 步骤2: 修复.app包内所有可执行文件权限
echo ""
echo "🔧 步骤 2: 修复可执行文件权限..."

# 修复主可执行文件
MAIN_EXEC="$APP_PATH/Contents/MacOS/tinyMediaManager"
if [ -f "$MAIN_EXEC" ]; then
    sudo chmod +x "$MAIN_EXEC"
    echo "✅ 主执行文件权限已修复"
else
    echo "❌ 未找到主执行文件: $MAIN_EXEC"
fi

# 修复Java可执行文件
JAVA_EXEC="$APP_PATH/Contents/MacOS/jre/bin/java"
if [ -f "$JAVA_EXEC" ]; then
    sudo chmod +x "$JAVA_EXEC"
    echo "✅ Java执行文件权限已修复"
fi

# 修复所有可执行脚本
if [ -d "$APP_PATH/Contents/MacOS" ]; then
    sudo find "$APP_PATH/Contents/MacOS" -type f -exec chmod +x {} \; 2>/dev/null
    echo "✅ MacOS目录下所有文件权限已修复"
fi

# 修复整个.app包权限
sudo chmod -R u+rw,go+r "$APP_PATH"
sudo find "$APP_PATH" -type d -exec chmod 755 {} \;
echo "✅ 应用程序包权限已统一修复"

# 步骤3: 验证应用程序完整性
echo ""
echo "🔧 步骤 3: 验证应用程序结构..."

REQUIRED_FILES=(
    "$APP_PATH/Contents/Info.plist"
    "$APP_PATH/Contents/MacOS/tinyMediaManager"
    "$APP_PATH/Contents/MacOS"
)

ALL_GOOD=true
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -e "$file" ]; then
        echo "❌ 缺少关键文件: $file"
        ALL_GOOD=false
    fi
done

if [ "$ALL_GOOD" = true ]; then
    echo "✅ 应用程序结构完整"
else
    echo "⚠️  应用程序可能已损坏，建议重新下载"
fi

# 步骤4: 提供系统设置指导
echo ""
echo "🎯 步骤 4: 系统设置确认"
echo ""
echo "如果仍然无法打开，请按以下步骤操作："
echo ""
echo "方法1: 右键打开"
echo "1. 在 Finder 中找到 /Applications/tinyMediaManager.app"
echo "2. 右键点击应用程序图标"
echo "3. 选择 '打开'"
echo "4. 在弹出的对话框中点击 '打开'"
echo ""
echo "方法2: 系统偏好设置"
echo "1. 打开 系统偏好设置 > 安全性与隐私"
echo "2. 点击'通用'标签页"
echo "3. 查看底部是否有 'tinyMediaManager.app 被阻止使用' 的提示"
echo "4. 点击 '仍要打开'"
echo ""
echo "方法3: 终端命令 (终极方案)"
echo "运行: sudo spctl --master-disable"
echo "⚠️  此命令会降低系统安全性，仅建议临时使用"
echo ""

if [ "$ALL_GOOD" = true ]; then
    echo "🎉 修复完成！"
    echo ""
    echo "现在可以尝试："
    echo "1. 双击 /Applications/tinyMediaManager.app"
    echo "2. 或使用方法1/2中的步骤"
    echo ""
else
    echo "⚠️  应用程序可能已损坏"
    echo "建议重新下载tinyMediaManager"
fi

read -p "按回车键关闭此窗口..."