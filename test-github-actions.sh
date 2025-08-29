#!/bin/bash

# GitHub Actions 实时测试和错误修复脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# 快速诊断函数
quick_diagnosis() {
    echo "🔍 GitHub Actions 快速诊断"
    echo "=========================="
    
    # 检查工作流文件
    log_info "检查工作流文件..."
    if [ -d ".github/workflows" ]; then
        local workflow_count=$(find .github/workflows -name "*.yml" -o -name "*.yaml" | wc -l)
        log_success "✅ 找到 $workflow_count 个工作流文件"
        ls -la .github/workflows/
    else
        log_error "❌ .github/workflows 目录不存在"
    fi
    
    echo ""
    
    # 检查 Git 状态
    log_info "检查 Git 状态..."
    echo "当前分支: $(git branch --show-current)"
    echo "远程状态:"
    git status --porcelain
    
    echo ""
    
    # 检查最近提交
    log_info "最近的提交:"
    git log --oneline -3
    
    echo ""
    
    # 验证 YAML 语法
    log_info "验证 YAML 语法..."
    for workflow in .github/workflows/*.yml .github/workflows/*.yaml; do
        if [ -f "$workflow" ]; then
            if python3 -c "import yaml; yaml.safe_load(open('$workflow'))" 2>/dev/null; then
                log_success "✅ $(basename "$workflow") 语法正确"
            else
                log_error "❌ $(basename "$workflow") 语法错误"
            fi
        fi
    done
}

# 触发测试构建
trigger_test() {
    log_info "准备触发测试构建..."
    
    # 检查未提交的更改
    if [ -n "$(git status --porcelain)" ]; then
        log_warning "发现未提交的更改"
        git status --short
        
        read -p "是否提交并推送? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git add .
            git commit -m "Test: Trigger GitHub Actions build"
            git push origin $(git branch --show-current)
            log_success "✅ 更改已提交并推送"
        fi
    else
        # 创建一个空提交来触发构建
        git commit --allow-empty -m "Test: Trigger GitHub Actions build"
        git push origin $(git branch --show-current)
        log_success "✅ 空提交已推送，构建已触发"
    fi
    
    echo ""
    log_info "🚀 构建已触发！"
    log_info "请访问以下链接查看构建状态:"
    echo "https://github.com/jonntd/tinymediamanager/actions"
}

# 监控构建状态 (简化版)
monitor_simple() {
    log_info "开始简单监控..."
    log_info "请手动访问 GitHub Actions 页面查看详细状态"
    echo "https://github.com/jonntd/tinymediamanager/actions"
    
    # 简单的状态检查循环
    for i in {1..10}; do
        echo ""
        log_info "检查 #$i (每30秒检查一次)"
        echo "时间: $(date)"
        
        # 这里可以添加 API 调用来获取状态
        # 但为了简化，我们只是提醒用户手动检查
        
        if [ $i -lt 10 ]; then
            echo "等待30秒..."
            sleep 30
        fi
    done
    
    log_info "监控完成，请手动检查最终状态"
}

# 常见错误修复
fix_common_issues() {
    log_info "检查并修复常见问题..."
    
    # 检查权限问题
    if ! grep -q "permissions:" .github/workflows/*.yml .github/workflows/*.yaml 2>/dev/null; then
        log_warning "⚠️ 工作流可能缺少权限配置"
        echo "建议在工作流中添加:"
        echo "permissions:"
        echo "  contents: read"
        echo "  packages: write"
    fi
    
    # 检查 secrets 配置
    local secrets_used=$(grep -r "secrets\." .github/workflows/ 2>/dev/null | wc -l)
    if [ "$secrets_used" -gt 0 ]; then
        log_info "发现 $secrets_used 个 secrets 引用"
        log_warning "请确保在 GitHub 仓库设置中配置了相应的 secrets"
    fi
    
    # 检查 Java 版本
    if grep -q "java-version.*17" .github/workflows/*.yml .github/workflows/*.yaml 2>/dev/null; then
        log_success "✅ 使用 Java 17"
    else
        log_warning "⚠️ 建议使用 Java 17"
    fi
    
    log_success "常见问题检查完成"
}

# 显示帮助
show_help() {
    cat << EOF
GitHub Actions 测试脚本

用法: $0 [命令]

命令:
    diagnosis   快速诊断配置问题
    test        触发测试构建
    monitor     简单监控构建状态
    fix         修复常见问题
    help        显示此帮助

示例:
    $0 diagnosis    # 快速诊断
    $0 test         # 触发测试
    $0 monitor      # 监控状态

EOF
}

# 主函数
main() {
    local command="${1:-diagnosis}"
    
    case "$command" in
        diagnosis|diag)
            quick_diagnosis
            ;;
        test)
            quick_diagnosis
            echo ""
            trigger_test
            ;;
        monitor)
            monitor_simple
            ;;
        fix)
            fix_common_issues
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "未知命令: $command"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"
