#!/usr/bin/env bash
# GrandCouncil — 本机多项目 serve 管理器（懒加载：按需 start/stop，无需一次全起）
#
# 背景：一个 serve = 一个项目（serve 协议无项目端点）。desktop 本地直读全部项目，
#       手机必须走 serve。此脚本让「识别全部项目 → 按需启动指定项目 serve」可操作。
#
# 用法:
#   bash scripts/serve-manager.sh list              # 列出全部项目 + 端口 + 运行状态
#   bash scripts/serve-manager.sh start <项目名>     # 启动该项目 serve（如 GrandCouncil）
#   bash scripts/serve-manager.sh stop <项目名>      # 停止
#   bash scripts/serve-manager.sh status             # 全部项目状态
#   bash scripts/serve-manager.sh start-all          # 全部启动（一般不推荐，负荷大）
#
# 项目来源：desktop-projects.json 的 projects 清单（与桌面版侧栏一致）；
# 端口分配：按清单顺序从 8788 起（可被 PORT_BASE 覆盖）；日志在 temp/serve-logs/。

set -euo pipefail
PORT_BASE="${PORT_BASE:-8788}"
CONF="$APPDATA/reasonix/desktop-projects.json"
LOG_DIR="/d/1.workspace/GrandCouncil/temp/serve-logs"
KEY=$(grep "^DEEPSEEK_API_KEY=" "$APPDATA/reasonix/.env" | cut -d= -f2-)
REASONIX_CLI="/c/Users/yinji/AppData/Local/Programs/Reasonix/reasonix-cli.exe"

mkdir -p "$LOG_DIR"

# 读取 desktop 项目清单（root 路径数组）
read_projects() {
    PORT_BASE="$PORT_BASE" python -c "
import json, os
base = int(os.environ['PORT_BASE'])
p = os.path.expandvars(r'$CONF')
d = json.load(open(p, encoding='utf-8'))
for i, proj in enumerate(d.get('projects', [])):
    root = proj.get('root') if isinstance(proj, dict) else proj
    if root:
        print(f'{base + i}|{root}')
"
}

project_exists() {
    local target="$1" result=""
    while IFS='|' read -r port root; do
        if [ "$(basename "$root")" = "$target" ] || [ "$root" = "$target" ]; then
            result="$port|$root"
            break
        fi
    done < <(read_projects)
    if [ -n "$result" ]; then
        echo "$result"
        return 0
    fi
    return 1
}

port_status() {
    local port="$1"
    if netstat -ano | grep -q ":$port.*LISTEN"; then
        curl -s --max-time 3 "http://127.0.0.1:$port/status" -o /dev/null -w "运行中(HTTP %{http_code})" 2>/dev/null || echo "端口占用(非 serve)"
    else
        echo "未运行"
    fi
}

cmd_list() {
    echo "项目名                端口   状态"
    echo "------------------------------------------"
    read_projects | while IFS='|' read -r port root; do
        name=$(basename "$root")
        printf "%-22s %s   %s\n" "$name" "$port" "$(port_status "$port")"
    done
}

cmd_start() {
    local target="$1"
    local entry
    entry=$(project_exists "$target") || { echo "未找到项目: $target（用 list 查看）"; exit 1; }
    local port root
    IFS='|' read -r port root <<< "$entry"
    if netstat -ano | grep -q ":$port.*LISTEN"; then
        echo "[$target] 已在 $port 端口运行"
        return 0
    fi
    echo "[$target] 启动 serve → http://0.0.0.0:$port （项目: $root）"
    # 注意：在 agent 自动化环境中调用本脚本需保持进程组（preserve_background_processes），
    # 否则 serve 随调用 shell 退出被清理；用户终端直接运行无此问题。
    (
        cd "$root" || exit 1
        DEEPSEEK_API_KEY="$KEY" "$REASONIX_CLI" serve --addr "0.0.0.0:$port" > "$LOG_DIR/$target.log" 2>&1 &
    )
    sleep 8
    if curl -s --max-time 3 "http://127.0.0.1:$port/status" -o /dev/null; then
        echo "[$target] ✓ 启动成功，手机连接 http://<电脑IP>:$port"
    else
        echo "[$target] ✗ 启动失败，日志: $LOG_DIR/$target.log"
        tail -5 "$LOG_DIR/$target.log" 2>/dev/null || true
        exit 1
    fi
}

cmd_stop() {
    local target="$1"
    local entry
    entry=$(project_exists "$target") || { echo "未找到项目: $target"; exit 1; }
    local port
    IFS='|' read -r port _ <<< "$entry"
    local pid
    pid=$(netstat -ano | grep ":$port.*LISTEN" | head -1 | awk '{print $5}')
    if [ -n "$pid" ]; then
        taskkill //F //PID "$pid" > /dev/null 2>&1
        echo "[$target] 已停止（端口 $port）"
    else
        echo "[$target] 未在运行"
    fi
}

cmd_status() {
    cmd_list
}

case "${1:-list}" in
    list|status) cmd_list ;;
    start) cmd_start "$2" ;;
    stop) cmd_stop "$2" ;;
    start-all)
        read_projects | while IFS='|' read -r port root; do
            cmd_start "$(basename "$root")"
        done
        ;;
    *) echo "用法: serve-manager.sh list|status|start <项目>|stop <项目>|start-all"; exit 1 ;;
esac
