#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""GrandCouncil 模拟器 UI 自动化：添加连接(10.0.2.2:8787) → 测试 → 会话列表验证。
依赖 scripts/dev-emulator.sh 已启动的模拟器与 serve（或等价环境）。
用法: python scripts/ui-auto.py
"""
import re
import subprocess
import sys
import time

ADB = r"C:\Users\yinji\AppData\Local\Android\Sdk\platform-tools\adb.exe"


def sh(*args):
    return subprocess.run([ADB] + list(args), capture_output=True).stdout


def dump() -> str:
    for _ in range(4):
        sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        raw = sh("shell", "cat", "/sdcard/ui.xml")
        text = raw.decode("utf-8", "replace")
        if "<node" in text:
            return text
        time.sleep(2)
    return ""


def find(xml: str, text: str, y_min: int = 0, y_max: int = 10000):
    """按 text/content-desc 匹配：先精确相等，再包含匹配，返回节点中心坐标"""
    nodes = re.findall(
        r'<node[^>]*?(?:text="([^"]*)"|content-desc="([^"]*)")[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    )

    def in_region(t, d, x1, y1, x2, y2):
        s = t or d
        cy = (int(y1) + int(y2)) // 2
        return s == text and y_min <= cy <= y_max

    for t, d, x1, y1, x2, y2 in nodes:
        if in_region(t, d, x1, y1, x2, y2):
            return (int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2
    for t, d, x1, y1, x2, y2 in nodes:
        s = t or d
        cy = (int(y1) + int(y2)) // 2
        if text in s and y_min <= cy <= y_max:
            return (int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2
    return None


def tap(x: int, y: int):
    sh("shell", "input", "tap", str(x), str(y))


def input_text(s: str):
    sh("shell", "input", "text", s)


def wait_for(text, timeout=20, y_min=0, y_max=10000):
    for _ in range(timeout):
        xml = dump()
        pos = find(xml, text, y_min, y_max)
        if pos:
            return pos, xml
        time.sleep(1)
    return None, ""


def main():
    # 用法: python scripts/ui-auto.py [baseUrl]，默认 10.0.2.2:8787
    base_url = sys.argv[1] if len(sys.argv) > 1 else "http://10.0.2.2:8787"

    # 0. 重置：仅当对话框残留时处理（先收键盘再点取消；
    #    无条件按 BACK 会把 App 退回桌面）
    xml = dump()
    if "取消" in xml:
        sh("shell", "input", "keyevent", "4")
        time.sleep(1)
        xml = dump()
        pos = find(xml, "取消")
        if pos:
            tap(*pos); time.sleep(1)
            print("[i] 已关闭残留对话框")

    # 1. 切「连接」tab（底部导航 y>2000）
    xml = dump()
    pos = find(xml, "连接", y_min=2000)
    if not pos:
        print("FAIL: 找不到「连接」tab"); sys.exit(1)
    tap(*pos); time.sleep(2)
    xml = dump()
    if "远端连接" not in xml and "暂无连接配置" not in xml:
        print("FAIL: 未进入连接页"); sys.exit(1)
    print("[OK] 已进入连接页")

    # 2. FAB「添加连接」（语义标签可能延迟，兜底右下角坐标）
    pos = find(xml, "添加连接")
    if not pos:
        pos = (964, 2074)  # 1080x2400 布局 FAB 固定位置
        print("[i] FAB 无语义标签，用兜底坐标")
    tap(*pos); time.sleep(2)

    # 3. 填 Base URL
    pos, xml = wait_for("Base URL")
    if not pos:
        print("FAIL: 找不到 Base URL 字段"); sys.exit(1)
    tap(*pos); time.sleep(1)
    input_text(base_url); time.sleep(1)
    # 收起软键盘（IME 遮挡下方按钮会导致 tap 误触键盘键）
    sh("shell", "input", "keyevent", "4")
    time.sleep(1)
    print("[OK] 已输入 Base URL")

    # 4. 保存（重试：IME 遮挡可能导致 tap 落空）
    saved = False
    for _ in range(3):
        pos, xml = wait_for("保存", timeout=8)
        if not pos:
            break
        tap(*pos); time.sleep(2)
        xml = dump()
        # 对话框关闭 = 「保存」按钮消失（FAB 的 desc 含"添加连接"会干扰标题判断，勿用）
        if "保存" not in xml:
            saved = True
            break
    if not saved:
        print("FAIL: 保存连接失败（对话框未关闭）"); sys.exit(1)
    print("[OK] 已保存连接")

    # 5. 点「测试连接」（语义标签可能延迟出现，轮询等待；兜底按卡片/固定位）
    xml = dump()
    pos = find(xml, "测试连接")
    if not pos:
        pos, xml = wait_for("测试连接", timeout=20)
    if not pos:
        card = find(xml, base_url)
        if card:
            cx, cy = card
            pos = (cx - 140, cy)
            print("[i] 测试按钮无语义标签，用卡片兜底坐标")
        else:
            print("FAIL: 找不到连接卡片"); sys.exit(1)
    tap(*pos)
    time.sleep(12)  # 三层诊断（TCP + HTTP）

    # 6. 验证测试结果
    xml = dump()
    ok = False
    for c in ["网络可达", "认证", "协议握手"]:
        if c in xml:
            print(f"[OK] 测试项可见: {c}")
            ok = True
        else:
            print(f"[?] 未找到: {c}")
    if "连接成功" in xml:
        print("[OK] 协议握手成功（已确认 serve）")
        ok = True
    if not ok:
        print("FAIL: 未检测到任何测试结果"); sys.exit(1)

    # 7. 切「会话」tab 验证列表（底部导航 y>2000）
    pos = find(xml, "会话", y_min=2000)
    if pos:
        tap(*pos); time.sleep(4)
    xml = dump()
    if "暂无会话" in xml:
        print("[WARN] 会话列表为空")
    elif "轮" in xml:
        print("[OK] 会话列表有数据（GET /sessions 链路通）")
    else:
        print("[WARN] 会话页状态未知")
    print("DONE")


if __name__ == "__main__":
    main()
