#!/usr/bin/env bash
# GrandCouncil — 模拟器自动调试脚本
# 用法: bash scripts/dev-emulator.sh [--headless]
#   1) 启动 reasonix serve（若 8787 未占用）
#   2) 创建/复用 AVD（system-images;android-36.1;google_apis;x86_64）
#   3) 启动模拟器并等待系统就绪
#   4) 安装 app-debug.apk 并启动 App
#   5) 验证: 模拟器→宿主 serve 连通性 + 建一个测试会话 + 截图
set -euo pipefail

SDK="/c/Users/yinji/AppData/Local/Android/Sdk"
ADB="$SDK/platform-tools/adb.exe"
EMU="$SDK/emulator/emulator.exe"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager.bat"
AVD_NAME="grandcouncil_avd"
AVD_IMAGE="system-images;android-36.1;google_apis;x86_64"
APK="/d/1.workspace/GrandCouncil/app/build/outputs/apk/debug/app-debug.apk"
SERVE_ADDR="0.0.0.0:8787"
HEADLESS="${1:-}"
OUT_DIR="temp/debug-out"

mkdir -p "$OUT_DIR"

# ---------- 1. reasonix serve ----------
if netstat -ano | grep -q ":8787.*LISTENING"; then
    echo "[1/5] serve 已在 8787 运行"
else
    echo "[1/5] 启动 reasonix serve ($SERVE_ADDR)"
    KEY=$(grep "^DEEPSEEK_API_KEY=" "$APPDATA/reasonix/.env" | cut -d= -f2-)
    (
        cd "$APPDATA/reasonix/global-workspace" || exit 1
        DEEPSEEK_API_KEY="$KEY" "/c/Users/yinji/AppData/Local/Programs/Reasonix/reasonix-cli.exe" serve --addr "$SERVE_ADDR" > /tmp/gc-serve.log 2>&1 &
    )
    sleep 5
    curl -s -o /dev/null -w "[serve] HTTP %{http_code}\n" --max-time 5 http://127.0.0.1:8787/status || echo "[serve] 未就绪，见 /tmp/gc-serve.log"
fi

# ---------- 2. AVD（手动创建，绕开 avdmanager 的远程包验证——代理下会失败） ----------
AVD_DIR="$HOME/.android/avd"
if [ ! -f "$AVD_DIR/grandcouncil_avd.ini" ]; then
    echo "[2/5] 创建 AVD: $AVD_NAME"
    mkdir -p "$AVD_DIR/grandcouncil_avd.avd"
    printf 'avd.ini.encoding=UTF-8\npath=C:/Users/yinji/.android/avd/grandcouncil_avd.avd\npath.rel=avd/grandcouncil_avd.avd\ntarget=android-36.1\n' > "$AVD_DIR/grandcouncil_avd.ini"
    printf 'avd.ini.displayname=GrandCouncil AVD\nhw.ramSize=2048\nhw.cpu.arch=x86_64\nimage.sysdir.1=system-images/android-36.1/google_apis/x86_64/\ntag.id=google_apis\ntag.display=Google APIs\nabi.type=x86_64\nhw.device.name=pixel_6\nhw.device.manufacturer=Google\nhw.lcd.width=1080\nhw.lcd.height=2400\nhw.lcd.density=420\nhw.keyboard=yes\nhw.gpu.enabled=yes\n' > "$AVD_DIR/grandcouncil_avd.avd/config.ini"
else
    echo "[2/5] AVD 已存在: $AVD_NAME"
fi

# ---------- 3. 启动模拟器 ----------
echo "[3/5] 启动模拟器"
"$ADB" kill-server > /dev/null 2>&1 || true
if [ -n "$HEADLESS" ]; then
    "$EMU" -avd "$AVD_NAME" -no-snapshot -no-boot-anim -no-window -gpu swiftshader_indirect > /tmp/gc-emu.log 2>&1 &
else
    "$EMU" -avd "$AVD_NAME" -no-snapshot -no-boot-anim -gpu auto > /tmp/gc-emu.log 2>&1 &
fi

echo "    等待系统启动…"
"$ADB" wait-for-device
for i in $(seq 1 120); do
    BOOT=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$BOOT" = "1" ] && break
    sleep 2
done
if [ "$BOOT" != "1" ]; then echo "模拟器启动超时，日志: /tmp/gc-emu.log"; exit 1; fi
echo "    系统就绪"

# ---------- 4. 安装并启动 App ----------
echo "[4/5] 安装 APK + 启动 App"
"$ADB" install -r "$APK"
"$ADB" shell am start -n com.grandcouncil.remote/.MainActivity
sleep 6

# ---------- 5. 验证 ----------
echo "[5/5] 验证"
# 模拟器访问宿主 serve（10.0.2.2 = 宿主机回环）
"$ADB" shell "ping -c 1 -W 2 10.0.2.2" > /dev/null 2>&1 && echo "    [OK] 模拟器→宿主网络通" || echo "    [WARN] ping 10.0.2.2 失败（防火墙可能拦截 ICMP，HTTP 未必受影响）"
# 在 serve 建一个测试会话（App 会话列表可显示）
curl -s --max-time 10 -X POST -H "Content-Type: application/json" -d '{}' http://127.0.0.1:8787/new > /dev/null 2>&1 && echo "    [OK] serve 已创建测试会话"
# 截图
"$ADB" exec-out screencap -p > "$OUT_DIR/screen-$(date +%H%M%S).png"
echo "    截图: $OUT_DIR/screen-*.png"

# UI 自动化：添加连接(10.0.2.2:8787) → 三层诊断 → 会话列表验证
if command -v python > /dev/null 2>&1; then
    echo "[6/6] UI 自动化（scripts/ui-auto.py）"
    PYTHONIOENCODING=utf-8 python "scripts/ui-auto.py" || echo "    UI 自动化未完全通过（可手动调试）"
fi
echo "完成。App 包名 com.grandcouncil.remote；serve 地址 http://10.0.2.2:8787"
