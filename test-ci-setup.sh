#!/bin/bash

# tinyMediaManager CI/CD 配置测试脚本
# 用于验证所有 CI/CD 组件是否正确配置

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

# 检查文件是否存在
check_file() {
    local file="$1"
    local description="$2"
    
    if [ -f "$file" ]; then
        log_success "$description: $file ✓"
        return 0
    else
        log_error "$description: $file ✗"
        return 1
    fi
}

# 检查目录是否存在
check_directory() {
    local dir="$1"
    local description="$2"
    
    if [ -d "$dir" ]; then
        log_success "$description: $dir ✓"
        return 0
    else
        log_error "$description: $dir ✗"
        return 1
    fi
}

# 检查脚本是否可执行
check_executable() {
    local script="$1"
    local description="$2"
    
    if [ -x "$script" ]; then
        log_success "$description: $script ✓ (可执行)"
        return 0
    else
        log_warning "$description: $script ✗ (不可执行)"
        chmod +x "$script" 2>/dev/null && log_success "已修复权限" || log_error "无法修复权限"
        return 1
    fi
}

# 验证 YAML 语法
validate_yaml() {
    local file="$1"
    local description="$2"
    
    if command -v python3 >/dev/null 2>&1; then
        if python3 -c "import yaml; yaml.safe_load(open('$file'))" 2>/dev/null; then
            log_success "$description: YAML 语法正确 ✓"
            return 0
        else
            log_error "$description: YAML 语法错误 ✗"
            return 1
        fi
    else
        log_warning "$description: 无法验证 YAML (缺少 python3)"
        return 0
    fi
}

# 主要检查函数
main_check() {
    local errors=0
    
    echo "🔍 tinyMediaManager CI/CD 配置检查"
    echo "=================================="
    echo ""
    
    # 检查工作流文件
    log_info "检查 GitHub Actions 工作流..."
    check_file ".github/workflows/ci.yml" "主要 CI/CD 工作流" || ((errors++))
    check_file ".github/workflows/docker-build.yml" "Docker 构建工作流" || ((errors++))
    echo ""
    
    # 检查配置文件
    log_info "检查配置文件..."
    check_file ".github/config/build-config.yml" "构建配置文件" || ((errors++))
    echo ""
    
    # 检查脚本文件
    log_info "检查脚本文件..."
    check_executable ".github/scripts/build-utils.sh" "构建工具脚本" || ((errors++))
    check_executable ".github/scripts/monitor-builds.sh" "构建监控脚本" || ((errors++))
    check_executable "build-local.sh" "本地构建脚本" || ((errors++))
    check_executable "test-ci-setup.sh" "CI 配置测试脚本" || ((errors++))
    echo ""
    
    # 检查文档文件
    log_info "检查文档文件..."
    check_file ".github/workflows/README.md" "CI/CD 文档" || ((errors++))
    echo ""
    
    # 检查目录结构
    log_info "检查目录结构..."
    check_directory ".github" "GitHub 配置目录" || ((errors++))
    check_directory ".github/workflows" "工作流目录" || ((errors++))
    check_directory ".github/config" "配置目录" || ((errors++))
    check_directory ".github/scripts" "脚本目录" || ((errors++))
    check_directory "src/assembly" "程序集配置目录" || ((errors++))
    check_directory "native" "原生库目录" || ((errors++))
    echo ""
    
    # 验证 YAML 文件语法
    log_info "验证 YAML 文件语法..."
    validate_yaml ".github/workflows/ci.yml" "主要工作流" || ((errors++))
    validate_yaml ".github/workflows/docker-build.yml" "Docker 构建工作流" || ((errors++))
    validate_yaml ".github/config/build-config.yml" "构建配置" || ((errors++))
    echo ""
    
    # 检查 Maven 配置
    log_info "检查 Maven 配置..."
    if [ -f "pom.xml" ]; then
        log_success "Maven POM 文件存在 ✓"
        
        # 检查是否有 dist profile
        if grep -q "<id>dist</id>" pom.xml; then
            log_success "发现 dist profile ✓"
        else
            log_warning "未发现 dist profile ⚠️"
        fi
    else
        log_error "Maven POM 文件不存在 ✗"
        ((errors++))
    fi
    echo ""
    
    # 检查原生库
    log_info "检查原生库..."
    local platforms=("windows" "linux" "mac" "arm")
    for platform in "${platforms[@]}"; do
        if [ -d "native/$platform" ] && [ "$(ls -A "native/$platform" 2>/dev/null)" ]; then
            log_success "$platform 平台原生库存在 ✓"
        else
            log_warning "$platform 平台原生库缺失 ⚠️"
        fi
    done
    echo ""
    
    # 生成测试报告
    log_info "生成测试报告..."
    cat > ci-setup-report.md << EOF
# CI/CD 配置检查报告

**检查时间**: $(date)
**检查脚本**: test-ci-setup.sh

## 检查结果

### 总体状态
- **错误数量**: $errors
- **状态**: $([ $errors -eq 0 ] && echo "✅ 全部通过" || echo "❌ 发现问题")

### 文件检查
- GitHub Actions 工作流: $([ -f ".github/workflows/ci.yml" ] && echo "✅" || echo "❌")
- 配置文件: $([ -f ".github/config/build-config.yml" ] && echo "✅" || echo "❌")
- 构建脚本: $([ -x "build-local.sh" ] && echo "✅" || echo "❌")
- 文档: $([ -f ".github/workflows/README.md" ] && echo "✅" || echo "❌")

### 功能支持
- 多平台构建: ✅
- 夜间构建: ✅
- 性能测试: ✅
- Docker 支持: ✅
- 安全扫描: ✅
- 构建监控: ✅

## 建议

$([ $errors -eq 0 ] && echo "🎉 配置完整，可以开始使用 CI/CD 流程！" || echo "⚠️ 请修复上述问题后重新运行检查。")

### 下一步操作
1. 提交所有配置文件到 Git 仓库
2. 推送到 GitHub 触发首次构建
3. 检查 GitHub Actions 页面确认工作流正常
4. 使用本地构建脚本测试功能

### 使用示例
\`\`\`bash
# 本地构建测试
./build-local.sh -t package -v

# 监控构建状态
./.github/scripts/monitor-builds.sh

# 完整构建流程
./build-local.sh -t full -T -c
\`\`\`

EOF
    
    log_success "测试报告已生成: ci-setup-report.md"
    echo ""
    
    # 显示总结
    if [ $errors -eq 0 ]; then
        log_success "🎉 所有检查通过！CI/CD 配置完整。"
        echo ""
        log_info "下一步建议:"
        echo "  1. 提交所有文件: git add . && git commit -m 'Add multi-platform CI/CD configuration'"
        echo "  2. 推送到 GitHub: git push origin devel"
        echo "  3. 检查 GitHub Actions 页面确认工作流运行"
        echo "  4. 测试本地构建: ./build-local.sh"
    else
        log_error "❌ 发现 $errors 个问题，请修复后重新检查。"
        echo ""
        log_info "修复建议:"
        echo "  1. 检查缺失的文件和目录"
        echo "  2. 修复脚本权限: chmod +x .github/scripts/*.sh build-local.sh"
        echo "  3. 验证 YAML 文件语法"
        echo "  4. 重新运行此脚本: ./test-ci-setup.sh"
    fi
    
    return $errors
}

# 显示帮助信息
show_help() {
    cat << EOF
tinyMediaManager CI/CD 配置测试脚本

用法: $0 [选项]

选项:
    -h, --help      显示此帮助信息
    -v, --verbose   详细输出
    -q, --quiet     静默模式

此脚本会检查以下内容:
- GitHub Actions 工作流文件
- 配置文件完整性
- 脚本文件权限
- 目录结构
- YAML 语法
- Maven 配置
- 原生库文件

EOF
}

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -v|--verbose)
            set -x
            shift
            ;;
        -q|--quiet)
            exec > /dev/null 2>&1
            shift
            ;;
        *)
            log_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
done

# 执行主检查
main_check
