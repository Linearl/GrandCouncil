#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-gc-boundary.py — GC 定位边界防线（tl967）

GC = Reasonix serve 远程控制客户端（2026-10-06 用户战略定案）：
不提供本地推理 / 本地聊天，不内嵌任何 LLM provider SDK / apiKey / provider 直连。
本脚本扫四道边界（963 报告 §4.1 规格），命中即非零退出：

  C1 依赖清单：app/build.gradle.kts + gradle/libs.versions.toml 不得出现 LLM provider SDK
  C2 凭据字段：app/src/main Kotlin 源码不得出现 apiKey / api_key / sk-… 字面量
     （白名单：token / password 是 serve 认证凭据，允许——本就不在扫描模式内）
  C3 直连域名：同 C2 范围，不得硬编码 LLM provider 端点域名
  C4 本地推理：依赖清单不得出现 on-device 推理运行时（onnxruntime/llama.cpp/mlc/…）

防假绿（2026-09 审计工具链三坑同型教训）：
  - 扫描器必须自证「真的扫到了文件」：每个检查面断言扫描文件数 ≥ 下限；
  - --self-test：在临时目录注入已知坏样本，守卫必须变红（非零退出），
    不变红 = 守卫无效 = 本脚本自身报错退出。

用法：
  python scripts/check-gc-boundary.py            # 常规检查（提交前人工跑）
  python scripts/check-gc-boundary.py --self-test# 反向测试（必须变红）
"""
import argparse
import os
import re
import shutil
import sys
import tempfile

# Windows 控制台 GBK 坑：非 GBK 字符 print 可能抛异常，先重配 stdout
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 每个检查面的扫描文件数下限（防「路径写错→零扫描→静默绿」）
KT_MIN = 30      # app/src/main 下 .kt 文件数下限（当前基线 45）
DEP_MIN = 2      # 依赖清单文件数下限（build.gradle.kts + libs.versions.toml）

# ---- C1/C4：依赖清单扫描（目标：app/build.gradle.kts, gradle/libs.versions.toml）----
DEP_FILES = [os.path.join("app", "build.gradle.kts"),
             os.path.join("gradle", "libs.versions.toml")]

# C1：LLM provider SDK（依赖坐标 / 别名，不区分大小写）
C1_PATTERNS = [
    r"com\.openai[:\w.-]*",            # openai-java / openai-kotlin
    r"com\.anthropic[:\w.-]*",
    r"com\.google\.ai\.edge[:\w.-]*",  # Google AI Edge / generative SDK
    r"com\.google\.genai[:\w.-]*",
    r"ai\.djl[:\w.-]*",                # Deep Java Library（LLM 推理面）
    r"openai[-_]?(kotlin|java|android)",
    r"anthropic[-_]?(kotlin|java|android)",
    r"google[-_]?genai",
    r"\bgemini\b.*sdk",
    r"dashscope[-_]?(sdk|java)?",
    r"moonshot[-_]?(sdk|java)?",
    r"zhipu[-_]?(sdk|java|ai)?",
    r"deepseek[-_]?(sdk|java)?",
    r"cohere[-_]?(java|sdk)?",
    r"mistral[-_]?(java|sdk)?",
]

# C4：on-device 推理运行时
C4_PATTERNS = [
    r"onnxruntime",
    r"llama[-.]?(cpp|jvm|android)",
    r"\bmlc\b",
    r"mediapipe[-_]?(llm|genai|tasks)",
    r"\bggml\b",
    r"\bgguf\b",
    r"tensorflow[-_]?lite.*(?:llm|genai)",
    r"litert",
    r"executorch",
    r"mnn[-_]?(llm|android)",
]

# ---- C2/C3：Kotlin 源码扫描（目标：app/src/main/**/*.kt）----
KT_ROOT = os.path.join("app", "src", "main")

# C2：凭据字段（\b 前缀锚定；api key 家族任何形态/后缀都报——GC 不该有此类命名）
# 注意：不查 Kotlin 标识符形态的 SecretKey（javax.crypto JCA 标准类型，
# GC 现有 serve 凭据加密存储在用，属白名单语义）；只查字符串字面量形态。
C2_PATTERNS = [
    r"\bapi_?key\w*",           # apiKey / ApiKey / api_key2 / apiKeyAlias …
    r'"sk-[A-Za-z0-9]{16,}"',   # 硬编码 sk- 密钥字面量
    r'["\']secret[_ ]?key["\']',  # provider 请求体字段名字符串（JCA SecretKey 类型不在扫描面）
]

# C3：LLM provider 直连端点
C3_PATTERNS = [
    r"api\.openai\.com",
    r"api\.anthropic\.com",
    r"generativelanguage\.googleapis\.com",
    r"api\.deepseek\.com",
    r"api\.moonshot\.cn",
    r"dashscope\.aliyuncs\.com",
    r"open\.bigmodel\.cn",
    r"api\.x\.ai",
    r"api\.cohere\.(com|ai)",
    r"api\.mistral\.ai",
    r"api\.together\.xyz",
    r"openrouter\.ai",
    r"api\.siliconflow\.cn",
]


def iter_files(base, exts):
    """递归收集 base 下指定扩展名的文件（绝对路径）。"""
    out = []
    for dirpath, _dirnames, filenames in os.walk(base):
        for fn in filenames:
            if os.path.splitext(fn)[1].lower() in exts:
                out.append(os.path.join(dirpath, fn))
    return sorted(out)


def scan(check_id, title, files, patterns, min_files):
    """返回 (命中列表, 扫描文件数)。命中列表元素 = (相对路径, 行号, 行内容, 模式)。"""
    if len(files) < min_files:
        print(f"  [FAIL] {check_id} 扫描面自证失败：仅扫到 {len(files)} 个文件（下限 {min_files}）——路径错误或文件缺失，拒绝给绿")
        sys.exit(2)
    hits = []
    compiled = [(re.compile(p, re.IGNORECASE), p) for p in patterns]
    for f in files:
        rel = os.path.relpath(f, REPO_ROOT)
        try:
            with open(f, encoding="utf-8", errors="replace") as fh:
                for lineno, line in enumerate(fh, 1):
                    for cre, raw in compiled:
                        if cre.search(line):
                            hits.append((rel, lineno, line.strip()[:160], raw))
        except OSError as e:
            print(f"  [FAIL] {check_id} 读取失败 {rel}: {e}")
            sys.exit(2)
    return hits, len(files)


def report(check_id, title, hits, n_files):
    print(f"[{check_id}] {title}（扫描 {n_files} 个文件，{len(hits)} 处命中）")
    if hits:
        for rel, lineno, line, pat in hits:
            print(f"    {rel}:{lineno}  [pattern: {pat}]")
            print(f"      > {line}")
    return len(hits) == 0


def main():
    ap = argparse.ArgumentParser(description="GC 定位边界防线（C1-C4）")
    ap.add_argument("--self-test", action="store_true",
                    help="反向测试：注入坏样本必须变红")
    args = ap.parse_args()

    if args.self_test:
        sys.exit(self_test())

    kt_files = iter_files(os.path.join(REPO_ROOT, KT_ROOT), {".kt"})
    dep_files = [os.path.join(REPO_ROOT, f) for f in DEP_FILES if os.path.isfile(os.path.join(REPO_ROOT, f))]

    print(f"GC 边界防线：仓库根 {REPO_ROOT}")
    results = []
    h1, n1 = scan("C1", "依赖清单 provider SDK", dep_files, C1_PATTERNS, DEP_MIN)
    results.append(report("C1", "依赖清单禁 LLM provider SDK", h1, n1))
    h2, n2 = scan("C2", "Kotlin 凭据字段", kt_files, C2_PATTERNS, KT_MIN)
    results.append(report("C2", "源码禁 apiKey/api_key/sk- 字面量（token/password 白名单外）", h2, n2))
    h3, n3 = scan("C3", "provider 直连域名", kt_files, C3_PATTERNS, KT_MIN)
    results.append(report("C3", "源码禁硬编码 LLM provider 端点", h3, n3))
    h4, n4 = scan("C4", "本地推理运行时", dep_files, C4_PATTERNS, DEP_MIN)
    results.append(report("C4", "依赖禁 on-device 推理运行时", h4, n4))

    if all(results):
        print(f"\n边界检查 PASS：四道全绿（.kt×{n2}，依赖×{n1}）")
        return 0
    print("\n边界检查 FAIL：见上方命中明细——GC 不得引入本地推理/provider 直连能力（2026-10-06 用户战略定案）")
    return 1


def self_test():
    """反向测试：在临时目录构造带坏样本的最小树，守卫必须全数命中。

    做法：把 REPO_ROOT 指向伪造树跑同一套扫描函数（不碰真实仓库文件）。
    任何一道没变红 → 守卫无效 → 本函数返回非零。
    """
    print("== self-test：注入坏样本，四道必须全红 ==")
    tmp = tempfile.mkdtemp(prefix="gc-boundary-selftest-")
    try:
        # 伪造树：<tmp>/app/src/main/Kt.kt + <tmp>/app/build.gradle.kts + <tmp>/gradle/libs.versions.toml
        os.makedirs(os.path.join(tmp, "app", "src", "main"), exist_ok=True)
        os.makedirs(os.path.join(tmp, "gradle"), exist_ok=True)
        with open(os.path.join(tmp, "app", "src", "main", "Bad.kt"), "w", encoding="utf-8") as f:
            f.write('val apiKey = "x"\n'
                    'val api_key2 = "y"\n'
                    'val sk = "sk-abcdefghijklmnopqrstuvwx"\n'
                    'val url = "https://api.openai.com/v1"\n')
        # 补足 KT_MIN 下限所需文件数（自证机制要求 ≥KT_MIN 个 .kt）
        for i in range(KT_MIN):
            with open(os.path.join(tmp, "app", "src", "main", f"Pad{i}.kt"), "w", encoding="utf-8") as f:
                f.write("// pad\n")
        with open(os.path.join(tmp, "app", "build.gradle.kts"), "w", encoding="utf-8") as f:
            f.write('implementation("com.openai:openai-java:1.0")\nimplementation("com.microsoft.onnxruntime:onnxruntime:1.0")\n')
        with open(os.path.join(tmp, "gradle", "libs.versions.toml"), "w", encoding="utf-8") as f:
            f.write("onnxruntime = \"1.0\"\n")

        fake_kt = iter_files(os.path.join(tmp, "app", "src", "main"), {".kt"})
        fake_dep = iter_files(os.path.join(tmp, "app"), {".kts", ".toml"}) + [os.path.join(tmp, "gradle", "libs.versions.toml")]

        global REPO_ROOT
        saved_root = REPO_ROOT
        REPO_ROOT = tmp
        try:
            checks = [
                ("C1", scan("C1", "selftest", fake_dep, C1_PATTERNS, DEP_MIN)),
                ("C2", scan("C2", "selftest", fake_kt, C2_PATTERNS, KT_MIN)),
                ("C3", scan("C3", "selftest", fake_kt, C3_PATTERNS, KT_MIN)),
                ("C4", scan("C4", "selftest", fake_dep, C4_PATTERNS, DEP_MIN)),
            ]
        finally:
            REPO_ROOT = saved_root

        failed = False
        expected_min = {"C1": 1, "C2": 3, "C3": 1, "C4": 1}
        for cid, (hits, n) in checks:
            ok = len(hits) >= expected_min[cid]
            print(f"  {cid}: {len(hits)} 命中（预期 ≥{expected_min[cid]}）→ {'红 ✓' if ok else '未变红 ✗'}")
            if not ok:
                failed = True
        if failed:
            print("self-test FAIL：守卫对已知坏样本未全部变红，防线无效")
            return 1
        print("self-test PASS：四道对坏样本全部变红")
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
