#!/bin/bash
set -e

echo "🍎 tinyMediaManager DMG 版本安全修复工具"
echo "========================================"
echo ""
echo "此脚本将修复 macOS 对未签名应用的常见问题："
echo "- 移除隔离属性 (quarantine)"
echo "- 修复文件权限"
echo "- 验证应用程序结构"
echo ""
echo "💡 提示：此脚本适用于从 DMG 安装的应用程序"
echo ""
read -p "按回车键继续，或按 Ctrl+C 取消..."

echo ""

# 获取脚本所在目录（DMG挂载点）
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo "📍 脚本目录: $SCRIPT_DIR"

# 检查是否在DMG中运行
if [[ "$SCRIPT_DIR" == *"/Volumes/tinyMediaManager"* ]]; then
    echo "✅ 检测到从DMG运行"
    APP_PATH="$SCRIPT_DIR/tinyMediaManager.app"
    
    # 提示用户先安装到应用程序文件夹
    echo ""
    echo "🎯 重要提示："
    echo "由于您正在从DMG运行，请先："
    echo "1. 将 tinyMediaManager.app 拖入 /Applications 文件夹"
    echo "2. 然后运行 /Applications/tinyMediaManager.app 中的修复脚本"
    echo ""
    
    # 提供复制命令
    echo "📋 复制命令："
    echo "cp -R \"$APP_PATH\" /Applications/"
    echo ""
    
    read -p "按回车键退出，然后请手动安装应用程序..."
    exit 0
fi

# 智能搜索应用程序位置
echo "🔍 正在搜索 tinyMediaManager.app..."

# 可能的安装位置
SEARCH_PATHS=(
    "/Applications/tinyMediaManager.app"
    "$HOME/Applications/tinyMediaManager.app"
    "$SCRIPT_DIR/tinyMediaManager.app"
    "$HOME/Downloads/tinyMediaManager.app"
    "$HOME/Desktop/tinyMediaManager.app"
    "$(find "$HOME" -name "tinyMediaManager.app" -type d 2>/dev/null | head -1)"
)

APP_PATH=""
for path in "${SEARCH_PATHS[@]}"; do
    if [[ -d "$path" ]]; then
        APP_PATH="$path"
        break
    fi
done

# 如果自动搜索失败，询问用户
if [[ -z "$APP_PATH" ]]; then
    echo "❌ 未找到 tinyMediaManager.app"
    echo ""
    echo "请选择应用程序位置："
    echo "1. /Applications/tinyMediaManager.app"
    echo "2. 其他位置"
    read -p "请输入选择 (1-2): " choice
    
    case $choice in
        1)
            APP_PATH="/Applications/tinyMediaManager.app"
            ;;
        2)
            read -p "请输入完整路径: " custom_path
            APP_PATH="$custom_path"
            ;;
        *)
            APP_PATH="/Applications/tinyMediaManager.app"
            ;;
    esac
fi

echo "📍 应用程序路径: $APP_PATH"

# 验证应用程序是否存在
if [[ ! -d "$APP_PATH" ]]; then
    echo "❌ 未找到应用程序: $APP_PATH"
    echo ""
    echo "请确认："
    echo "1. tinyMediaManager.app 是否已正确安装？"
    echo "2. 应用程序是否在正确的位置？"
    echo ""
    echo "解决方案："
    echo "- 从DMG将 tinyMediaManager.app 拖入 /Applications 文件夹"
    echo "- 或者使用 Spotlight 搜索 tinyMediaManager.app"
    echo ""
    read -p "按回车键退出..."
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

# 修复Java可执行文件（如果存在）
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

# 修复所有可执行脚本
find "$APP_PATH" -type f -name "*.sh" -o -name "*.command" | while read script; do
    chmod +x "$script" 2>/dev/null || sudo chmod +x "$script"
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

# 步骤4: 提供系统设置指导
echo ""
echo "🎯 步骤 4: 系统设置确认"
echo ""
echo "修复完成！现在可以尝试："
echo ""
echo "方法1: 双击打开"
echo "1. 打开 Finder"
echo "2. 导航到: $(dirname "$APP_PATH")"
echo "3. 双击 tinyMediaManager.app"
echo ""
echo "方法2: 右键打开"
echo "1. 右键点击 tinyMediaManager.app"
echo "2. 选择 '打开'"
echo "3. 在弹出的对话框中点击 '打开'"
echo ""
echo "方法3: 系统设置"
echo "1. 打开 系统偏好设置 > 安全性与隐私"
echo "2. 点击'通用'标签页"
echo "3. 查看底部是否有 'tinyMediaManager.app 被阻止使用' 的提示"
echo "4. 点击 '仍要打开'"
echo ""

# 检查macOS版本
MACOS_VERSION=$(sw_vers -productVersion 2>/dev/null || echo "未知")
echo "💡 系统信息: macOS $MACOS_VERSION"

if [[ "$ALL_GOOD" = true ]]; then
    echo "🎉 修复完成！"
    echo ""
    echo "下一步操作："
    echo "1. 现在可以双击 tinyMediaManager.app 启动"
    echo "2. 如果仍有问题，请尝试方法2或方法3"
    echo "3. 确保应用程序已正确安装到 /Applications"
else
    echo "⚠️  应用程序可能已损坏"
    echo "建议重新下载tinyMediaManager"
fi

echo ""
read -p "按回车键关闭此窗口..."