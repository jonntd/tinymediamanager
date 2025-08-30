#!/bin/bash
set -e

echo "🍎 tinyMediaManager macOS 权限修复工具"
echo "========================================"
echo ""
echo "此脚本将修复 macOS 对未签名应用的常见问题："
echo "- 移除隔离属性 (quarantine)"
echo "- 修复文件权限"
echo "- 验证应用程序结构"
echo ""
read -p "按回车键继续，或按 Ctrl+C 取消..."
echo ""

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
APP_PATH="$SCRIPT_DIR/tinyMediaManager.app"

# 如果在同一目录找不到，尝试常见位置
if [ ! -d "$APP_PATH" ]; then
    echo "🔍 在当前目录未找到 tinyMediaManager.app，正在搜索..."

    # 检查 DMG 挂载点 (最优先 - 用户通常在 DMG 中运行此脚本)
    if [ -d "/Volumes/tinyMediaManager/tinyMediaManager.app" ]; then
        APP_PATH="/Volumes/tinyMediaManager/tinyMediaManager.app"
        echo "✅ 在 DMG 挂载点找到应用程序"
    # 检查下载文件夹中的 DMG 内容
    elif [ -d "$HOME/Downloads/tinyMediaManager.app" ]; then
        APP_PATH="$HOME/Downloads/tinyMediaManager.app"
        echo "✅ 在下载文件夹找到应用程序"
    # 检查桌面
    elif [ -d "$HOME/Desktop/tinyMediaManager.app" ]; then
        APP_PATH="$HOME/Desktop/tinyMediaManager.app"
        echo "✅ 在桌面找到应用程序"
    # 检查应用程序文件夹 (已安装的情况)
    elif [ -d "/Applications/tinyMediaManager.app" ]; then
        APP_PATH="/Applications/tinyMediaManager.app"
        echo "✅ 在应用程序文件夹找到应用程序"
    else
        echo "❌ 未找到 tinyMediaManager.app"
        echo ""
        echo "请确保 tinyMediaManager.app 位于以下位置之一："
        echo "- 与此脚本相同的文件夹 (DMG 挂载状态)"
        echo "- DMG 挂载点 (/Volumes/tinyMediaManager/)"
        echo "- 下载文件夹 (~/Downloads/)"
        echo "- 桌面 (~/Desktop/)"
        echo "- 应用程序文件夹 (/Applications/)"
        echo ""
        read -p "按回车键关闭此窗口..."
        exit 1
    fi
fi

echo "📍 应用程序路径: $APP_PATH"
echo ""

if [ -d "$APP_PATH" ]; then
    echo "🔧 步骤 1: 移除隔离属性..."
    echo "⚠️  需要管理员权限来执行此操作"

    if sudo xattr -dr com.apple.quarantine "$APP_PATH" 2>/dev/null; then
        echo "✅ 隔离属性移除成功"
    else
        echo "⚠️  隔离属性移除失败 (可能不需要或权限不足)"
    fi

    echo ""
    echo "🔧 步骤 2: 修复文件权限..."

    # 修复主执行文件权限
    MAIN_EXEC="$APP_PATH/Contents/MacOS/tinyMediaManager"
    if [ -f "$MAIN_EXEC" ]; then
        sudo chmod +x "$MAIN_EXEC"
        echo "✅ 主执行文件权限已修复"
    else
        echo "❌ 未找到主执行文件: $MAIN_EXEC"
    fi

    # 修复 Info.plist 权限
    INFO_PLIST="$APP_PATH/Contents/Info.plist"
    if [ -f "$INFO_PLIST" ]; then
        sudo chmod 644 "$INFO_PLIST"
        echo "✅ Info.plist 权限已修复"
    else
        echo "❌ 未找到 Info.plist: $INFO_PLIST"
    fi

    # 修复整个应用包权限
    sudo chmod -R u+rw,go+r "$APP_PATH"
    sudo find "$APP_PATH" -type d -exec chmod 755 {} \;
    echo "✅ 应用包权限已修复"
    
    echo ""
    echo "🔧 步骤 3: 验证应用程序结构..."
    
    # 检查关键文件
    if [ -f "$MAIN_EXEC" ] && [ -f "$INFO_PLIST" ]; then
        echo "✅ 应用程序结构正常"
        echo ""
        echo "🎉 所有修复已成功完成！"
        echo ""
        echo "现在你可以双击 tinyMediaManager.app 来启动它。"
        echo ""
        echo "如果仍然遇到错误，请尝试："
        echo "1. 右键点击 tinyMediaManager.app 并选择 '打开'"
        echo "2. 前往 系统偏好设置 > 安全性与隐私 > 通用"
        echo "3. 点击 '仍要打开' (如果出现提示)"
        echo ""
    else
        echo "❌ 应用程序结构似乎已损坏"
        echo "请重新下载应用程序"
        echo ""
    fi
    
    read -p "按回车键关闭此窗口..."
else
    echo "❌ 在指定路径未找到 tinyMediaManager.app"
    echo "请确保此脚本与 tinyMediaManager.app 在同一文件夹中"
    echo ""
    read -p "按回车键关闭此窗口..."
    exit 1
fi
