#!/bin/bash

# tinyMediaManager 启动脚本
# 简化版本，支持 DEBUG 日志和快速启动

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目路径
PROJECT_DIR="/Users/jonntd/tinyMediaManager/tinyMediaManager"

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示帮助信息
show_help() {
    echo "tinyMediaManager 启动脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help     显示此帮助信息"
    echo "  -c, --compile  重新编译后启动"
    echo "  -d, --debug    启用 DEBUG 日志级别"
    echo "  -j, --jar      使用 JAR 文件启动"
    echo "  -m, --maven    使用 Maven 启动（默认）"
    echo ""
    echo "示例:"
    echo "  $0              # 使用 Maven 启动"
    echo "  $0 -d           # 启用 DEBUG 日志"
    echo "  $0 -c           # 重新编译后启动"
    echo "  $0 -j           # 使用 JAR 文件启动"
    echo ""
}

# 编译项目
compile_project() {
    print_info "开始编译 tinyMediaManager..."
    cd "$PROJECT_DIR"

    if mvn clean compile package -DskipTests -q; then
        print_success "编译完成！"
        return 0
    else
        print_error "编译失败！"
        return 1
    fi
}

# 使用 JAR 文件启动
start_with_jar() {
    print_info "使用 JAR 文件启动 tinyMediaManager..."
    cd "$PROJECT_DIR"

    JAR_FILE="$PROJECT_DIR/target/tinyMediaManager-5.2.0-SNAPSHOT.jar"
    if [ ! -f "$JAR_FILE" ]; then
        print_error "JAR 文件不存在: $JAR_FILE"
        print_info "请先运行: $0 -c"
        exit 1
    fi

    if [ "$DEBUG_MODE" = "true" ]; then
        print_info "启用 DEBUG 模式..."
        java -Dtmm.consoleloglevel=DEBUG -jar "$JAR_FILE"
    else
        java -jar "$JAR_FILE"
    fi
}

# 使用 Maven 启动
start_with_maven() {
    print_info "使用 Maven 启动 tinyMediaManager..."
    cd "$PROJECT_DIR"

    if [ "$DEBUG_MODE" = "true" ]; then
        print_info "启用 DEBUG 日志级别..."
        mvn exec:java -Dexec.mainClass="org.tinymediamanager.TinyMediaManager" -Dtmm.consoleloglevel=DEBUG
    else
        mvn exec:java -Dexec.mainClass="org.tinymediamanager.TinyMediaManager"
    fi
}

# 主函数
main() {
    # 解析命令行参数
    FORCE_COMPILE=false
    USE_JAR=false
    DEBUG_MODE=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -c|--compile)
                FORCE_COMPILE=true
                shift
                ;;
            -j|--jar)
                USE_JAR=true
                shift
                ;;
            -m|--maven)
                # Maven 是默认方式，这个选项主要用于明确指定
                shift
                ;;
            -d|--debug)
                DEBUG_MODE=true
                shift
                ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # 检查项目目录
    if [ ! -d "$PROJECT_DIR" ]; then
        print_error "项目目录不存在: $PROJECT_DIR"
        exit 1
    fi

    print_info "tinyMediaManager 启动脚本"
    print_info "项目目录: $PROJECT_DIR"

    if [ "$DEBUG_MODE" = "true" ]; then
        print_warning "DEBUG 日志模式已启用"
    fi

    # 检查是否需要编译
    if [ "$FORCE_COMPILE" = "true" ]; then
        if ! compile_project; then
            exit 1
        fi
    fi

    # 选择启动方式
    if [ "$USE_JAR" = "true" ]; then
        start_with_jar
    else
        # 默认使用 Maven 启动（更稳定）
        start_with_maven
    fi
}

# 运行主函数
main "$@"
