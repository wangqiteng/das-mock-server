#!/bin/bash

# 设置环境变量
export MOCK_HENGNAO_API_KEY="MOCK_HENGNAO_API_KEY"
export MOCK_DASHSCOPE_API_KEY="MOCK_DASHSCOPE_API_KEY"
export MOCK_SERVER_PORT="9000"

# 应用配置
JAR_FILE="das-mock-server-1.0.0.jar"
APP_NAME="das-mock-server"
PID_FILE="/tmp/${APP_NAME}.pid"
LOG_FILE="main.log"

# 颜色输出定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印带颜色的信息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查JAR文件是否存在
check_jar() {
    if [ ! -f "$JAR_FILE" ]; then
        print_error "找不到JAR文件: $JAR_FILE"
        exit 1
    fi
}

# 获取应用进程ID
get_pid() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE"
    else
        pgrep -f "$JAR_FILE"
    fi
}

# 检查应用是否在运行
is_running() {
    local pid=$(get_pid)
    if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# 启动应用
start() {
    check_jar

    if is_running; then
        local pid=$(get_pid)
        print_warn "$APP_NAME 已在运行 (PID: $pid)"
        return 0
    fi

    print_info "启动 $APP_NAME..."
    nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
    local pid=$!
    echo $pid > "$PID_FILE"

    # 等待应用启动
    sleep 3

    if ps -p "$pid" > /dev/null 2>&1; then
        print_info "$APP_NAME 启动成功 (PID: $pid)"
        print_info "日志文件: $LOG_FILE"
        print_info "查看日志: tail -f $LOG_FILE"
    else
        print_error "$APP_NAME 启动失败"
        if [ -f "$LOG_FILE" ]; then
            print_info "最后20行日志:"
            tail -20 "$LOG_FILE"
        fi
        rm -f "$PID_FILE"
        exit 1
    fi
}

# 停止应用
stop() {
    if ! is_running; then
        print_warn "$APP_NAME 未在运行"
        return 0
    fi

    local pid=$(get_pid)
    print_info "停止 $APP_NAME (PID: $pid)..."

    # 优雅停止
    kill "$pid"

    # 等待最多30秒
    local count=0
    while [ $count -lt 30 ] && ps -p "$pid" > /dev/null 2>&1; do
        sleep 1
        count=$((count + 1))
    done

    # 如果还没停止，强制杀死
    if ps -p "$pid" > /dev/null 2>&1; then
        print_warn "强制停止 $APP_NAME..."
        kill -9 "$pid"
    fi

    rm -f "$PID_FILE"
    print_info "$APP_NAME 已停止"
}

# 重启应用
restart() {
    stop
    sleep 2
    start
}

# 查看状态
status() {
    if is_running; then
        local pid=$(get_pid)
        print_info "$APP_NAME 正在运行 (PID: $pid)"

        # 显示启动时间
        if [ -n "$pid" ]; then
            local start_time=$(ps -o lstart -p "$pid" | tail -1)
            print_info "启动时间: $start_time"
        fi

        # 显示日志文件信息
        if [ -f "$LOG_FILE" ]; then
            local log_size=$(du -h "$LOG_FILE" 2>/dev/null | cut -f1)
            print_info "日志文件大小: ${log_size:-0B}"
        fi
    else
        print_warn "$APP_NAME 未在运行"
    fi
}

# 查看日志
logs() {
    if [ "$1" = "-f" ]; then
        print_info "实时查看日志 (按 Ctrl+C 停止):"
        tail -f "$LOG_FILE"
    else
        print_info "查看最后100行日志:"
        tail -100 "$LOG_FILE"
    fi
}

# 显示帮助信息
show_help() {
    echo "用法: $0 {start|stop|restart|status|logs|help}"
    echo ""
    echo "命令:"
    echo "  start     启动应用"
    echo "  stop      停止应用"
    echo "  restart   重启应用"
    echo "  status    查看应用状态"
    echo "  logs      查看日志 (使用 logs -f 实时查看)"
    echo "  help      显示此帮助信息"
    echo ""
    echo "默认行为: 如果不带参数，则执行 start 命令"
}

# 主逻辑
case "${1:-start}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    logs)
        logs "$2"
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "未知命令: $1"
        show_help
        exit 1
        ;;
esac
