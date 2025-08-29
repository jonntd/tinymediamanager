#!/bin/bash
echo "🤖 GitHub Actions 实时监控开始..."
echo "=================================="

while true; do
    # 获取最新的运行状态
    response=$(curl -s "https://api.github.com/repos/jonntd/tinymediamanager/actions/runs?per_page=1")
    
    # 提取关键信息
    status=$(echo "$response" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    conclusion=$(echo "$response" | grep -o '"conclusion":"[^"]*"' | head -1 | cut -d'"' -f4)
    updated_at=$(echo "$response" | grep -o '"updated_at":"[^"]*"' | head -1 | cut -d'"' -f4)
    run_number=$(echo "$response" | grep -o '"run_number":[0-9]*' | head -1 | cut -d':' -f2)
    
    # 显示状态
    current_time=$(date '+%H:%M:%S')
    echo "[$current_time] 运行 #$run_number - 状态: $status"
    
    if [ "$conclusion" != "null" ] && [ "$conclusion" != "" ]; then
        echo "🎉 构建完成! 结果: $conclusion"
        echo "🔗 查看详情: https://github.com/jonntd/tinymediamanager/actions/runs/17327553190"
        break
    fi
    
    if [ "$status" = "completed" ]; then
        echo "✅ 构建已完成!"
        break
    fi
    
    # 等待 30 秒后再次检查
    sleep 30
done

echo "=================================="
echo "监控结束"
