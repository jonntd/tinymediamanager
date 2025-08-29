#!/bin/bash

# tinyMediaManager 构建状态监控脚本
# 用于监控 GitHub Actions 构建状态

set -e

# 配置
REPO_OWNER="jonntd"
REPO_NAME="tinymediamanager"
API_BASE="https://api.github.com/repos/$REPO_OWNER/$REPO_NAME"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示帮助信息
show_help() {
    cat << EOF
tinyMediaManager 构建状态监控脚本

用法: $0 [选项] [命令]

命令:
    status          显示最新构建状态 (默认)
    list            列出最近的构建
    watch           实时监控构建状态
    workflows       显示所有工作流
    artifacts       显示构建产物

选项:
    -w, --workflow NAME     指定工作流名称
    -b, --branch BRANCH     指定分支
    -l, --limit NUMBER      限制结果数量 [默认: 10]
    -f, --format FORMAT     输出格式 (table|json|simple) [默认: table]
    -r, --refresh SECONDS   监控模式刷新间隔 [默认: 30]
    -h, --help              显示此帮助信息

示例:
    $0                              # 显示最新构建状态
    $0 list -l 5                    # 列出最近5次构建
    $0 watch -r 10                  # 每10秒刷新一次状态
    $0 status -w "CI/CD Pipeline"   # 显示特定工作流状态
    $0 artifacts                    # 显示构建产物

EOF
}

# 调用 GitHub API
call_api() {
    local endpoint="$1"
    local auth_header=""
    
    # 如果设置了 GITHUB_TOKEN，使用认证
    if [ -n "$GITHUB_TOKEN" ]; then
        auth_header="-H \"Authorization: token $GITHUB_TOKEN\""
    fi
    
    eval "curl -s $auth_header \"$API_BASE$endpoint\""
}

# 格式化时间
format_time() {
    local timestamp="$1"
    if command -v date >/dev/null 2>&1; then
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            date -j -f "%Y-%m-%dT%H:%M:%SZ" "$timestamp" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "$timestamp"
        else
            # Linux
            date -d "$timestamp" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "$timestamp"
        fi
    else
        echo "$timestamp"
    fi
}

# 获取状态图标
get_status_icon() {
    local status="$1"
    local conclusion="$2"
    
    case "$status" in
        "completed")
            case "$conclusion" in
                "success") echo "✅" ;;
                "failure") echo "❌" ;;
                "cancelled") echo "🚫" ;;
                "skipped") echo "⏭️" ;;
                *) echo "❓" ;;
            esac
            ;;
        "in_progress") echo "🔄" ;;
        "queued") echo "⏳" ;;
        *) echo "❓" ;;
    esac
}

# 显示构建状态
show_status() {
    local workflow_filter="$1"
    
    log_info "获取最新构建状态..."
    
    local endpoint="/actions/runs?per_page=1"
    if [ -n "$workflow_filter" ]; then
        endpoint="/actions/runs?per_page=1&workflow=$workflow_filter"
    fi
    
    local response=$(call_api "$endpoint")
    
    if [ -z "$response" ]; then
        log_error "无法获取构建状态"
        return 1
    fi
    
    # 解析 JSON 响应
    local run_data=$(echo "$response" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    if data.get('workflow_runs'):
        run = data['workflow_runs'][0]
        print(f\"{run['id']}|{run['run_number']}|{run['status']}|{run['conclusion'] or 'running'}|{run['workflow_name']}|{run['head_branch']}|{run['created_at']}|{run['html_url']}\")
    else:
        print('no_runs')
except:
    print('error')
")
    
    if [ "$run_data" = "no_runs" ]; then
        log_warning "没有找到构建记录"
        return 0
    elif [ "$run_data" = "error" ]; then
        log_error "解析构建数据失败"
        return 1
    fi
    
    # 解析数据
    IFS='|' read -r run_id run_number status conclusion workflow_name branch created_at html_url <<< "$run_data"
    
    # 显示状态
    local icon=$(get_status_icon "$status" "$conclusion")
    local formatted_time=$(format_time "$created_at")
    
    echo ""
    echo "🏗️  最新构建状态"
    echo "=================="
    echo "状态: $icon $status"
    if [ "$status" = "completed" ]; then
        echo "结果: $conclusion"
    fi
    echo "工作流: $workflow_name"
    echo "分支: $branch"
    echo "构建号: #$run_number"
    echo "时间: $formatted_time"
    echo "链接: $html_url"
    echo ""
}

# 列出构建历史
list_builds() {
    local limit="$1"
    local workflow_filter="$2"
    local format="$3"
    
    log_info "获取构建历史..."
    
    local endpoint="/actions/runs?per_page=$limit"
    if [ -n "$workflow_filter" ]; then
        endpoint="/actions/runs?per_page=$limit&workflow=$workflow_filter"
    fi
    
    local response=$(call_api "$endpoint")
    
    if [ -z "$response" ]; then
        log_error "无法获取构建历史"
        return 1
    fi
    
    case "$format" in
        "json")
            echo "$response" | python3 -m json.tool
            ;;
        "simple")
            echo "$response" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for run in data.get('workflow_runs', []):
    icon = '✅' if run['conclusion'] == 'success' else '❌' if run['conclusion'] == 'failure' else '🔄'
    print(f\"{icon} #{run['run_number']} {run['workflow_name']} ({run['head_branch']}) - {run['status']}\")
"
            ;;
        *)
            # 表格格式
            echo ""
            printf "%-4s %-8s %-20s %-15s %-10s %-15s\n" "状态" "构建号" "工作流" "分支" "状态" "时间"
            echo "$(printf '%.0s-' {1..80})"
            
            echo "$response" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for run in data.get('workflow_runs', []):
    icon = '✅' if run['conclusion'] == 'success' else '❌' if run['conclusion'] == 'failure' else '🔄'
    workflow = run['workflow_name'][:18] + '..' if len(run['workflow_name']) > 20 else run['workflow_name']
    branch = run['head_branch'][:13] + '..' if len(run['head_branch']) > 15 else run['head_branch']
    created = run['created_at'][:10]
    print(f\"{icon:<4} #{run['run_number']:<7} {workflow:<20} {branch:<15} {run['status']:<10} {created:<15}\")
"
            echo ""
            ;;
    esac
}

# 监控构建状态
watch_builds() {
    local refresh_interval="$1"
    local workflow_filter="$2"
    
    log_info "开始监控构建状态 (每 $refresh_interval 秒刷新一次)"
    log_info "按 Ctrl+C 停止监控"
    
    while true; do
        clear
        echo "🔍 tinyMediaManager 构建监控"
        echo "刷新间隔: $refresh_interval 秒"
        echo "时间: $(date)"
        echo ""
        
        show_status "$workflow_filter"
        
        echo "下次刷新: $(date -d "+$refresh_interval seconds" 2>/dev/null || date)"
        
        sleep "$refresh_interval"
    done
}

# 显示工作流列表
show_workflows() {
    log_info "获取工作流列表..."
    
    local response=$(call_api "/actions/workflows")
    
    if [ -z "$response" ]; then
        log_error "无法获取工作流列表"
        return 1
    fi
    
    echo ""
    echo "📋 可用工作流"
    echo "=============="
    
    echo "$response" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for workflow in data.get('workflows', []):
    state_icon = '✅' if workflow['state'] == 'active' else '❌'
    print(f\"{state_icon} {workflow['name']} ({workflow['path']})\")
"
    echo ""
}

# 显示构建产物
show_artifacts() {
    log_info "获取最新构建产物..."
    
    # 获取最新成功的构建
    local response=$(call_api "/actions/runs?status=success&per_page=1")
    
    if [ -z "$response" ]; then
        log_error "无法获取构建信息"
        return 1
    fi
    
    local run_id=$(echo "$response" | python3 -c "
import json, sys
data = json.load(sys.stdin)
if data.get('workflow_runs'):
    print(data['workflow_runs'][0]['id'])
else:
    print('')
")
    
    if [ -z "$run_id" ]; then
        log_warning "没有找到成功的构建"
        return 0
    fi
    
    # 获取构建产物
    local artifacts_response=$(call_api "/actions/runs/$run_id/artifacts")
    
    echo ""
    echo "📦 构建产物 (构建 #$run_id)"
    echo "=========================="
    
    echo "$artifacts_response" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for artifact in data.get('artifacts', []):
    size_mb = round(artifact['size_in_bytes'] / 1024 / 1024, 2)
    print(f\"📄 {artifact['name']} ({size_mb} MB) - {artifact['created_at'][:10]}\")
    print(f\"   下载: {artifact['archive_download_url']}\")
    print()
"
}

# 主函数
main() {
    local command="status"
    local workflow_filter=""
    local branch_filter=""
    local limit=10
    local format="table"
    local refresh_interval=30
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            status|list|watch|workflows|artifacts)
                command="$1"
                shift
                ;;
            -w|--workflow)
                workflow_filter="$2"
                shift 2
                ;;
            -b|--branch)
                branch_filter="$2"
                shift 2
                ;;
            -l|--limit)
                limit="$2"
                shift 2
                ;;
            -f|--format)
                format="$2"
                shift 2
                ;;
            -r|--refresh)
                refresh_interval="$2"
                shift 2
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 执行命令
    case "$command" in
        status)
            show_status "$workflow_filter"
            ;;
        list)
            list_builds "$limit" "$workflow_filter" "$format"
            ;;
        watch)
            watch_builds "$refresh_interval" "$workflow_filter"
            ;;
        workflows)
            show_workflows
            ;;
        artifacts)
            show_artifacts
            ;;
    esac
}

# 错误处理
trap 'log_error "脚本执行过程中发生错误"' ERR

# 执行主函数
main "$@"
