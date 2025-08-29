#!/bin/bash

# tinyMediaManager 构建工具脚本
# 用于 GitHub Actions 和本地开发

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# 检查必要的工具
check_prerequisites() {
    log_info "检查构建环境..."
    
    # 检查 Java
    if ! command -v java &> /dev/null; then
        log_error "Java 未安装或不在 PATH 中"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        log_error "需要 Java 17 或更高版本，当前版本: $JAVA_VERSION"
        exit 1
    fi
    log_success "Java 版本检查通过: $JAVA_VERSION"
    
    # 检查 Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven 未安装或不在 PATH 中"
        exit 1
    fi
    log_success "Maven 检查通过"
    
    # 检查 Git
    if ! command -v git &> /dev/null; then
        log_error "Git 未安装或不在 PATH 中"
        exit 1
    fi
    log_success "Git 检查通过"
}

# 获取项目版本
get_version() {
    mvn help:evaluate -Dexpression=project.version -q -DforceStdout
}

# 检查原生库
check_native_libs() {
    local platform=$1
    log_info "检查 $platform 平台的原生库..."
    
    case $platform in
        "windows")
            if [ -f "native/windows/MediaInfo.dll" ]; then
                log_success "Windows 原生库存在"
                return 0
            fi
            ;;
        "linux")
            if [ -f "native/linux/libtinyfiledialogs.so" ]; then
                log_success "Linux 原生库存在"
                return 0
            fi
            ;;
        "mac")
            if [ -f "native/mac/libmediainfo.dylib" ]; then
                log_success "macOS 原生库存在"
                return 0
            fi
            ;;
        "arm")
            if [ -f "native/arm/libtinyfiledialogs.so" ]; then
                log_success "ARM 原生库存在"
                return 0
            fi
            ;;
    esac
    
    log_warning "$platform 平台的原生库不存在或不完整"
    return 1
}

# 清理构建目录
clean_build() {
    log_info "清理构建目录..."
    mvn clean --batch-mode
    
    # 清理额外的目录
    rm -rf dist/ 2>/dev/null || true
    rm -rf target/assembly/ 2>/dev/null || true
    
    log_success "构建目录清理完成"
}

# 基础构建
basic_build() {
    log_info "执行基础构建..."
    mvn compile --batch-mode -DskipTests=true
    log_success "基础构建完成"
}

# 完整构建
full_build() {
    log_info "执行完整构建..."
    mvn package --batch-mode -DskipTests=true -DskipSign=true
    log_success "完整构建完成"
}

# 分发构建
dist_build() {
    log_info "执行分发构建..."
    mvn package -Pdist --batch-mode -DskipTests=true -DskipSign=true
    log_success "分发构建完成"
}

# 运行测试
run_tests() {
    log_info "运行测试..."
    mvn test --batch-mode
    log_success "测试完成"
}

# 验证构建产物
verify_artifacts() {
    local build_type=$1
    log_info "验证构建产物..."
    
    # 检查基本 JAR
    if [ ! -f target/*.jar ]; then
        log_error "未找到 JAR 文件"
        return 1
    fi
    log_success "JAR 文件存在"
    
    # 如果是分发构建，检查分发包
    if [ "$build_type" = "dist" ]; then
        if [ -d "dist" ] && [ "$(ls -A dist/)" ]; then
            log_success "分发包存在"
            log_info "分发包内容:"
            ls -la dist/
        else
            log_error "分发包不存在或为空"
            return 1
        fi
    fi
    
    return 0
}

# 生成构建报告
generate_build_report() {
    local version=$1
    local build_type=$2
    
    log_info "生成构建报告..."
    
    cat > build-report.md << EOF
# 构建报告

**版本**: $version
**构建类型**: $build_type
**构建时间**: $(date)
**构建环境**: $(uname -a)

## Java 环境
- **Java 版本**: $(java -version 2>&1 | head -n1)
- **Maven 版本**: $(mvn -version | head -n1)

## 构建产物
EOF

    if [ -f target/*.jar ]; then
        echo "- JAR 文件: $(ls target/*.jar | xargs basename)" >> build-report.md
        echo "- JAR 大小: $(ls -lh target/*.jar | awk '{print $5}')" >> build-report.md
    fi
    
    if [ -d "dist" ]; then
        echo "" >> build-report.md
        echo "## 分发包" >> build-report.md
        for file in dist/*; do
            if [ -f "$file" ]; then
                echo "- $(basename "$file"): $(ls -lh "$file" | awk '{print $5}')" >> build-report.md
            fi
        done
    fi
    
    log_success "构建报告已生成: build-report.md"
}

# 主函数
main() {
    local command=$1
    local platform=${2:-"all"}
    
    case $command in
        "check")
            check_prerequisites
            ;;
        "clean")
            clean_build
            ;;
        "build")
            check_prerequisites
            basic_build
            ;;
        "package")
            check_prerequisites
            full_build
            verify_artifacts "package"
            ;;
        "dist")
            check_prerequisites
            dist_build
            verify_artifacts "dist"
            ;;
        "test")
            check_prerequisites
            run_tests
            ;;
        "full")
            check_prerequisites
            clean_build
            run_tests
            dist_build
            verify_artifacts "dist"
            generate_build_report "$(get_version)" "full"
            ;;
        "native-check")
            if [ "$platform" = "all" ]; then
                check_native_libs "windows"
                check_native_libs "linux"
                check_native_libs "mac"
                check_native_libs "arm"
            else
                check_native_libs "$platform"
            fi
            ;;
        *)
            echo "用法: $0 {check|clean|build|package|dist|test|full|native-check} [platform]"
            echo ""
            echo "命令说明:"
            echo "  check        - 检查构建环境"
            echo "  clean        - 清理构建目录"
            echo "  build        - 基础构建"
            echo "  package      - 完整构建并打包"
            echo "  dist         - 分发构建"
            echo "  test         - 运行测试"
            echo "  full         - 完整流程（清理+测试+分发+报告）"
            echo "  native-check - 检查原生库 [windows|linux|mac|arm|all]"
            exit 1
            ;;
    esac
}

# 如果脚本被直接执行
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
