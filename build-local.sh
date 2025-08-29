#!/bin/bash

# tinyMediaManager 本地快速构建脚本
# 用于本地开发和测试

set -e

# 导入构建工具
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/.github/scripts/build-utils.sh"

# 默认参数
BUILD_TYPE="package"
CLEAN_BUILD=false
RUN_TESTS=false
VERBOSE=false
PLATFORM="current"
SKIP_CHECKS=false

# 显示帮助信息
show_help() {
    cat << EOF
tinyMediaManager 本地构建脚本

用法: $0 [选项]

选项:
    -t, --type TYPE         构建类型 (test|package|dist|full) [默认: package]
    -c, --clean             执行清理构建
    -T, --test              运行测试
    -p, --platform PLATFORM 目标平台 (current|windows|linux|mac|all) [默认: current]
    -v, --verbose           详细输出
    -s, --skip-checks       跳过环境检查
    -h, --help              显示此帮助信息

构建类型说明:
    test        - 编译和测试
    package     - 创建 JAR 包 (默认)
    dist        - 创建分发包
    full        - 完整构建流程

平台说明:
    current     - 当前平台 (默认)
    windows     - Windows x64
    linux       - Linux x64
    mac         - macOS
    all         - 所有平台 (仅限 dist 类型)

示例:
    $0                          # 基础 JAR 构建
    $0 -t dist -c               # 清理并创建分发包
    $0 -t full -T -v            # 完整构建，包含测试，详细输出
    $0 -t dist -p all           # 构建所有平台的分发包

EOF
}

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -t|--type)
                BUILD_TYPE="$2"
                shift 2
                ;;
            -c|--clean)
                CLEAN_BUILD=true
                shift
                ;;
            -T|--test)
                RUN_TESTS=true
                shift
                ;;
            -p|--platform)
                PLATFORM="$2"
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -s|--skip-checks)
                SKIP_CHECKS=true
                shift
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
}

# 验证参数
validate_args() {
    case $BUILD_TYPE in
        test|package|dist|full)
            ;;
        *)
            log_error "无效的构建类型: $BUILD_TYPE"
            exit 1
            ;;
    esac
    
    case $PLATFORM in
        current|windows|linux|mac|all)
            ;;
        *)
            log_error "无效的平台: $PLATFORM"
            exit 1
            ;;
    esac
    
    if [[ "$PLATFORM" == "all" && "$BUILD_TYPE" != "dist" ]]; then
        log_error "多平台构建只支持 dist 类型"
        exit 1
    fi
}

# 设置详细输出
setup_verbose() {
    if [[ "$VERBOSE" == "true" ]]; then
        set -x
        export MAVEN_OPTS="$MAVEN_OPTS -X"
    fi
}

# 执行构建
execute_build() {
    log_info "开始 $BUILD_TYPE 构建..."
    log_info "目标平台: $PLATFORM"
    
    # 环境检查
    if [[ "$SKIP_CHECKS" != "true" ]]; then
        check_prerequisites
    fi
    
    # 清理构建
    if [[ "$CLEAN_BUILD" == "true" ]]; then
        clean_build
    fi
    
    # 执行构建
    case $BUILD_TYPE in
        test)
            basic_build
            if [[ "$RUN_TESTS" == "true" ]]; then
                run_tests
            fi
            ;;
        package)
            full_build
            if [[ "$RUN_TESTS" == "true" ]]; then
                run_tests
            fi
            verify_artifacts "package"
            ;;
        dist)
            if [[ "$RUN_TESTS" == "true" ]]; then
                run_tests
            fi
            
            if [[ "$PLATFORM" == "all" ]]; then
                log_info "构建所有平台的分发包..."
                dist_build
            else
                log_info "构建 $PLATFORM 平台的分发包..."
                dist_build
            fi
            verify_artifacts "dist"
            ;;
        full)
            run_tests
            dist_build
            verify_artifacts "dist"
            generate_build_report "$(get_version)" "full"
            ;;
    esac
}

# 显示构建结果
show_results() {
    log_success "构建完成!"
    
    echo ""
    log_info "构建结果:"
    
    # 显示 JAR 文件
    if [ -f target/*.jar ]; then
        echo "📦 JAR 文件:"
        ls -lh target/*.jar
    fi
    
    # 显示分发包
    if [ -d "dist" ] && [ "$(ls -A dist/)" ]; then
        echo ""
        echo "📦 分发包:"
        ls -lh dist/
    fi
    
    # 显示构建报告
    if [ -f "build-report.md" ]; then
        echo ""
        echo "📋 构建报告: build-report.md"
    fi
    
    echo ""
    log_info "构建类型: $BUILD_TYPE"
    log_info "目标平台: $PLATFORM"
    log_info "项目版本: $(get_version)"
}

# 主函数
main() {
    log_info "tinyMediaManager 本地构建脚本"
    log_info "================================"
    
    parse_args "$@"
    validate_args
    setup_verbose
    
    # 记录开始时间
    START_TIME=$(date +%s)
    
    # 执行构建
    execute_build
    
    # 计算构建时间
    END_TIME=$(date +%s)
    BUILD_TIME=$((END_TIME - START_TIME))
    
    # 显示结果
    show_results
    
    log_success "总构建时间: ${BUILD_TIME} 秒"
    
    # 提供下一步建议
    echo ""
    log_info "下一步建议:"
    case $BUILD_TYPE in
        test)
            echo "  - 运行 '$0 -t package' 创建 JAR 包"
            ;;
        package)
            echo "  - 运行 'java -jar target/*.jar' 测试应用"
            echo "  - 运行 '$0 -t dist' 创建分发包"
            ;;
        dist)
            echo "  - 检查 dist/ 目录中的分发包"
            echo "  - 解压并测试分发包"
            ;;
        full)
            echo "  - 查看 build-report.md 了解详细信息"
            echo "  - 测试生成的分发包"
            ;;
    esac
}

# 错误处理
trap 'log_error "构建过程中发生错误，退出码: $?"' ERR

# 执行主函数
main "$@"
