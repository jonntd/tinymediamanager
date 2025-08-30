#!/bin/bash

# 创建带有 FIX 按钮的 DMG 背景图片
# 这个脚本会在背景图上添加一个可点击的 FIX 按钮区域

set -e

echo "🎨 Creating DMG background with FIX button..."

# 检查是否有 ImageMagick 或其他图像处理工具
if command -v convert >/dev/null 2>&1; then
    echo "✅ Found ImageMagick"
    
    # 复制原始背景图
    cp background.png background_with_fix.png
    
    # 在背景图上添加 FIX 按钮
    # 位置：右下角 (500, 300) 附近
    convert background_with_fix.png \
        -fill "#FF6B35" \
        -stroke "#FFFFFF" \
        -strokewidth 2 \
        -draw "roundrectangle 450,280 580,320 10,10" \
        -fill "#FFFFFF" \
        -font "Arial-Bold" \
        -pointsize 16 \
        -gravity center \
        -draw "text 515,300 '🔧 FIX'" \
        background_with_fix.png
    
    echo "✅ Created background with FIX button"
    
elif command -v sips >/dev/null 2>&1; then
    echo "✅ Found sips (macOS built-in)"
    
    # 使用 sips 创建简单的按钮效果
    cp background.png background_with_fix.png
    
    # 注意：sips 功能有限，我们需要用其他方法
    echo "⚠️  sips has limited drawing capabilities"
    echo "💡 Consider using ImageMagick or creating the button image separately"
    
else
    echo "❌ No image processing tool found"
    echo "💡 Please install ImageMagick: brew install imagemagick"
    echo "📋 Or create the button image manually"
    
    # 复制原始图片作为备用
    cp background.png background_with_fix.png
fi

# 创建一个简单的 HTML 说明，展示按钮位置
cat > fix_button_layout.html << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>DMG FIX Button Layout</title>
    <style>
        .dmg-preview {
            position: relative;
            width: 660px;
            height: 400px;
            background: url('background_with_fix.png') no-repeat;
            border: 1px solid #ccc;
            margin: 20px;
        }
        .fix-button {
            position: absolute;
            left: 450px;
            top: 280px;
            width: 130px;
            height: 40px;
            background: linear-gradient(135deg, #FF6B35, #F7931E);
            border: 2px solid #fff;
            border-radius: 10px;
            color: white;
            font-weight: bold;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            box-shadow: 0 4px 8px rgba(0,0,0,0.3);
            font-family: Arial, sans-serif;
        }
        .fix-button:hover {
            background: linear-gradient(135deg, #E55A2B, #E8851A);
            transform: translateY(-2px);
            box-shadow: 0 6px 12px rgba(0,0,0,0.4);
        }
        .app-icon {
            position: absolute;
            left: 160px;
            top: 180px;
            width: 80px;
            height: 80px;
            background: #4A90E2;
            border-radius: 15px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-size: 24px;
        }
        .applications-link {
            position: absolute;
            left: 500px;
            top: 175px;
            width: 80px;
            height: 80px;
            background: #7ED321;
            border-radius: 15px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-size: 12px;
            text-align: center;
        }
    </style>
</head>
<body>
    <h1>🎨 DMG Layout with FIX Button</h1>
    <div class="dmg-preview">
        <div class="app-icon">🎬</div>
        <div class="applications-link">📂<br>Apps</div>
        <div class="fix-button" onclick="alert('🔧 FIX Button Clicked!')">🔧 FIX</div>
    </div>
    <p><strong>Layout coordinates:</strong></p>
    <ul>
        <li>tinyMediaManager.app: (160, 180)</li>
        <li>Applications link: (500, 175)</li>
        <li>🔧 FIX Button: (450, 280) - 130x40px</li>
    </ul>
    <p><strong>Button specs:</strong></p>
    <ul>
        <li>Background: Orange gradient (#FF6B35 to #F7931E)</li>
        <li>Border: 2px white</li>
        <li>Border radius: 10px</li>
        <li>Text: 🔧 FIX (white, bold)</li>
    </ul>
</body>
</html>
EOF

echo "✅ Created layout preview: fix_button_layout.html"
echo "📋 Open fix_button_layout.html in browser to see the layout"
echo ""
echo "🎯 Next steps:"
echo "1. Use the coordinates to position the Fix App in DMG"
echo "2. Update create-dmg arguments with button position"
echo "3. Test the DMG layout"
