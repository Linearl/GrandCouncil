<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# connections

## Purpose

连接管理页：多 serve 连接的列表、编辑表单（地址/认证方式/凭据）、三层连接测试与保存。App 的「配置」栏主页面，首次使用经 wizard 引导进入。

## Key Files

| File | Description |
|------|-------------|
| `ConnectionScreen.kt` | 连接列表 + 表单 UI：编辑/新建/测试/删除；连接失败提示引导启动 serve（引用 `bash scripts/serve-manager.sh start`） |
| `ConnectionViewModel.kt` | 连接页状态管理：档案 CRUD、测试流程驱动（调用 ConnectionTester） |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- 表单中凭据输入与保存必须走 `../../security/CredentialCipher.kt` 加密
- 测试按钮联动 ConnectionTester 三层诊断，展示每层结果与「人话」失败文案
- 删除连接是破坏性操作：删除前确认（连接内的会话数据在远端 serve，删除只移除本地档案）

### Testing Requirements

- 模拟器实测：添加连接（10.0.2.2:8787）→ 测试通过 → 保存 → 会话页可见（ui-auto.py 覆盖此路径）

### Common Patterns

- 表单状态本地持有，保存时才写 ConnectionStore

## Dependencies

### Internal

- `../../connection/` — ConnectionTester/ConnectionStore/ConnectionProfile/ConnectionGuide
- `../../security/` — CredentialCipher

### External

- Compose Material3、Navigation

<!-- MANUAL: -->
