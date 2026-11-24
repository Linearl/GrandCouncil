# GC 代码问题台账

> 用途：记录在 GC（GrandCouncil）代码里发现的问题，供 GC 专员排期修复。
> 建立时间：2026-11-25（zcode 模拟器测试交接，tl1539）。用户指示：「如果发现 GC 代码问题，及时记录到文档」。
> 协作约定：GC 域任务的修复派子对话，不派 zcode（见 `AGENTS.md` 派单纪律）。本文件只记录，修复后请把状态改为「已修」并附 commit。

## 状态说明

| 状态 | 含义 |
|---|---|
| 🔴 待修 | 已确认是缺陷，未修 |
| 🟠 修复中 | 工作区已有改动但尚未提交；行号以当前工作区为准 |
| 🟡 待定 | 需要产品/负责人决定是否算问题、怎么改 |
| 🔵 观察 | 现象确凿但触发条件未完全查清，或依赖外部状态 |
| ✅ 已修 | 已修复，附 commit |

## 问题清单

> 最后核对：2026-11-25 02:25，基线 commit `1bca1f1`（v0.1.02）。行号为**当时工作区**的行号，提交后可能有小幅偏移。

| # | 状态 | 问题 | 严重度 | 位置 |
|---|---|---|---|---|
| GC-01 | ✅ 已修 | 扫码页「关闭」「相册」按钮被状态栏盖住点不动 | 高（功能不可用，真机同样复现） | `scan/ScannerActivity.kt:186`、`:193` |
| GC-02 | ✅ 已修 | 展开项目拉会话期间无任何加载反馈 | 中 | `ui/sessions/SessionListViewModel.kt:56`、`:446` |
| GC-03 | ✅ 已修 | 「能力探测 △（旧版 serve）」文案误导 | 低 | `connection/ConnectionTester.kt:261-270` |
| GC-04 | ✅ 已修 | 向导可保存无 token 连接，与编辑器「禁无 token」不一致 | 中（安全口径不一致） | `ui/wizard/WizardScreen.kt:68`、`:296-313`、`:129` |
| GC-05 | 🔵 观察 | 会话请求超时后是否显示「加载失败」未获实测证实 | 未知 | `api/HttpClientFactory.kt:41-43` |

---

## GC-01 扫码页「关闭」「相册」按钮被状态栏盖住点不动

**状态**：✅ 已修（commit `ddff33e`，v0.1.03 `ac1f55a`）　**发现**：2026-11-25　**发现时版本**：v0.1.02（commit `1bca1f1`）

**现象**：扫码页（相机预览）左上「🖼 相册」、右上「关闭」两个按钮点击无响应。编辑器对话框里的按钮都正常。

**根因**（针对 commit `1bca1f1` 的代码）：`targetSdk = 37`（`app/build.gradle.kts:14`）下系统强制 edge-to-edge，内容绘制到系统栏底下且不可关闭。`ScannerActivity` 没有任何 insets 处理：

- 根 `Box(Modifier.fillMaxSize().background(Color.Black))` 没有 `statusBarsPadding()`
- 两个 `TextButton` 用 `Modifier.align(Alignment.TopEnd / TopStart).padding(8.dp)` 贴顶

结果按钮 bounds 的 y 范围是 22~137，而状态栏高 128px——按钮主体嵌在状态栏区域，触摸被状态栏窗口接收，只有最下缘约 9px 可点。

对照：`MainActivity.kt:60` 调了 `enableEdgeToEdge()`，且各页面都用 Scaffold 自动避让，所以其它页正常。全仓 `setContent` 入口共 2 个，`ScannerActivity` 是唯一没有 insets 处理的。

**证据**（模拟器 grandcouncil_avd，1080x2400 / density 420，修复前）：

状态栏高度：`adb shell cmd overlay lookup android android:dimen/status_bar_height` → `128.0px`

| 按钮 | bounds | 点击 y | 结果 |
|---|---|---|---|
| 关闭 | `[907,22]–[1059,148]` | 85（语义树中心） | 无响应 |
| 关闭 | 同上 | 130（下缘） | 回到 MainActivity |
| 相册 | `[21,22]–[213,148]` | 85（语义树中心） | 无响应 |
| 相册 | 同上 | 130（下缘） | 弹出系统照片选择器 |

**修法**（已提交 `ddff33e`）：给两个按钮的 modifier 各加 `.statusBarsPadding()`——

- `ScannerActivity.kt:186` 关闭按钮：`Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)`
- `ScannerActivity.kt:193` 相册按钮：`Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)`

只避让按钮、不避让根 Box，相机预览仍然全屏铺底。

**遗留的小问题**：页面底部居中的提示文字「将连接二维码对准取景框…」仍是 `Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)`，没有避让导航栏。因为留了 96dp，在常见机型上应该不会被手势条盖住，但不是由 insets 保证的。

**验收标准**：扫码页两个按钮的语义树 `bounds.top` ≥ 状态栏高度（本机 128px）；点语义树中心即生效。

**排除的假设**：不是 PreviewView 消费触摸，也不是 Compose hit-test 问题——语义树里两个按钮的点击容器都是 `clickable: true`，事件绑定正常。

---

## GC-02 展开项目拉会话期间无任何加载反馈

**状态**：✅ 已修（commit `ddff33e`，v0.1.03 `ac1f55a`）　**发现**：2026-11-25　**发现时版本**：v0.1.02

**现象**：在会话栏点开一个项目后，展开标记（▾）立刻出现，但在会话列表返回之前，项目下方是空白的——没有「加载中」提示。

**代码事实**（针对 commit `1bca1f1`）：

- `SessionListViewModel.loadProjectSessions`：进入时只设置 `expandedProject = key`，没有设置任何 loading 状态
- `SessionListScreen` 展开后三选一——有错误显示「⚠ 加载会话失败」；无会话显示「该项目暂无会话」；否则显示会话行。没有「正在加载」这一态
- `SessionRepository.kt:43-47`：会话列表请求用 45 秒专属超时（注释说明是为慢速链路预留）

**影响**：慢速链路或服务端响应慢时，用户点开项目后最长会看到约 45 秒空白，无法区分「在加载」和「出错了」。

**实测**（修复前）：

| 项目状态 | 表现 | 证实方式 |
|---|---|---|
| 有会话 | 4~6 秒内展开并显示会话行（实测 7 条） | 实测 |
| 0 会话 | 显示「该项目暂无会话」 | 实测 |
| 请求进行中 | 空白，无提示 | 代码事实 |
| 请求超时/失败 | 代码上应显示「⚠ 加载会话失败」 | **未实测**，见 GC-05 |

**修法**（已提交 `ddff33e`）：

- `SessionListUiState` 新增 `sessionsLoading: Set<String>`（**采纳下文边界情况建议改 Set**，并发拉取互不覆盖）
- 进入时 `+ key`，成功/失败时 `- key`
- `SessionListScreen` 读取 `key in state.sessionsLoading`，在错误分支之后、空会话分支之前显示「加载会话中…」

**验收标准**：点开任意项目后立即出现加载提示；请求结束后（成功 / 失败 / 空）提示消失并被对应内容替换；加载期间切换到另一个项目，两个项目的状态互不干扰。

**⚠️ 当前修法里的一个边界情况**（读代码推演得出，未实测；提交前建议处理）：

`sessionsLoading` 是单个 `String?`，同一时刻只能记一个项目。在「A 加载中 → 点 B → B 先返回 → A 后返回」的顺序下会出问题：

| 步骤 | 动作 | `sessionsLoading` |
|---|---|---|
| 1 | 点 A | `"A"` |
| 2 | A 未返回，点 B | `"B"`（覆盖了 `"A"`） |
| 3 | B 先返回，`:453` 清掉 `"B"` | `null` |
| 4 | A 的请求仍在进行，但已无标记 | `null` |
| 5 | 用户切回 A，`:440` 发现未加载，重新请求 | `"A"`（自愈） |

`:452` 的注释「期间切换到其他项目时不清别人的」只覆盖了「A 的回调晚于 B 的赋值」这一种顺序，没有覆盖「B 的回调早于 A 的回调」。后果是步骤 4 的窗口期里 A 没有加载标记，且 A 会被重复请求一次（步骤 5）；不影响数据正确性，属于体验瑕疵。

**可选处理**：**已采纳**——`sessionsLoading` 改为 `Set<String>`，进入时 `+ key`，结束时 `- key`，UI 判断 `key in state.sessionsLoading`（commit `ddff33e`）。

---

## GC-03 「能力探测 △（旧版 serve）」文案误导

**状态**：✅ 已修（commit `ddff33e`，v0.1.03 `ac1f55a`）　**发现**：2026-11-25

**现象**：连接测试第四层显示「△ 能力探测：服务端未提供能力宣告（旧版 serve）——新增能力入口将隐藏，既有功能不受影响」，但服务端其实是新版。

**根因**（针对 commit `1bca1f1`）：`ConnectionTester.kt` 里 `GET /capabilities` 返回 404 或 405 时，一律显示「旧版 serve」。

**实际情况**：本机 18789 是 servepool **网关根**（`servepool.json` 里 `enabled: true`，进程 `reasonix-desktop v1.38.3-20261124-2208`）。网关根只提供 `/manifest`、`/status` 和 `/p/<id>/…`；`/capabilities` 在根上不存在。宿主机侧实测（同一 token）：

| 路径 | 网关根 | 带 `/p/<id>/` 前缀 |
|---|---|---|
| `/manifest` | 200 | — |
| `/status` | 200 | 501 |
| `/capabilities` | 404 | **501** |
| `/sessions` | 404 | 200 |

所以 404 的含义是「连到了网关根、端点不在这里」，而不是「服务端版本旧」。

**修法**（已提交 `ddff33e`）：按 `profile.projectId` 是否为空分流文案，**已按建议删掉「填项目 ID 后可按项目探测」引导**（网关对 `/p/<id>/capabilities` 返 501，走不通）。`projectId` 为空时显示：

> 服务端未提供能力宣告（旧版 serve，或当前连的是 servepool 网关根）——新增能力入口将隐藏，既有功能不受影响

~~（此段历史建议已全部采纳，见上「修法」）~~ 原建议三要点：①去掉「填项目 ID 后可按项目探测」（已删）②不再把网关 404 归因为「旧版 serve」（已分流）③网关场景能力探测需 serve 侧代理 `/p/<id>/capabilities`（属 reasonix serve 侧，未做，客户端暂不引导）。

---

## GC-04 向导可保存无 token 连接，与编辑器「禁无 token」不一致

**状态**：✅ 已修（commit `ddff33e`，v0.1.03 `ac1f55a`，采口径方案）　**发现**：2026-11-25

**背景**：提交 `8e2249b`（tl1539③）的说明是「认证固定 TOKEN 禁无 token……删 authMode/密码选择器」，实际只改了编辑器（`ProfileEditorDialog`）这一侧。

**代码事实**：

- 编辑器 `ui/connections/ConnectionScreen.kt:244` 注释「认证固定 TOKEN（不再提供无 token/密码方式）」，保存按钮需名称、URL、token 均非空才可点
- 向导 `ui/wizard/WizardScreen.kt:68` 默认 `AuthMode.TOKEN`，但第 2 步仍可选认证方式；第 3 步 `:296-313` 按选择显示 Token 框或「serve 密码」框，选「无认证」则都不显示
- 向导第 4 步 `:129` 保存的唯一条件是测试各层 `success` 均为 true——而能力探测层是「信息层永不判失败」，所以只要前三层通过就会保存

**实测**：向导第 2 步界面显示「无认证 / Token / 密码」三个选项（模拟器 v0.1.02）。

**影响**：两条「新建连接」入口的安全口径不一致。向导路径可以存下无 token 或密码认证的连接。

**入口关系**：首次使用的「➕ 添加连接」按钮和会话栏底部「🧭 向导」走向导；「远端连接」页右下角的 FAB「添加连接」走编辑器。两个入口的按钮文案相同（都是「添加连接」），走的是不同界面。

**处理**（已提交 `ddff33e`）：按用户 20261122 口径「不再允许无 token 认证」统一——向导 `authMode` 改 `val = AuthMode.TOKEN`（不可变），第 2 步删认证方式选择器（改为提示「认证方式：令牌（在下一步粘贴）」），第 3 步「下一步」加 token 非空校验。与编辑器口径一致。

---

## GC-05 会话请求超时后的表现未获实测证实

**状态**：🔵 观察　**发现**：2026-11-25

**说明**：这一条记录的是一个**没有查清**的点，不是已确认的缺陷。

**已知**（代码事实）：
- `SessionRepository.kt:43-47` `listSessionsForProject` 用 `runCatching`，超时会进 `onFailure` 并写入 `projectErrors`，UI 应显示「⚠ 加载会话失败：…」
- `HttpClientFactory.kt:41-43` 超时配置是 connect / read / write 各取 `timeoutMs`（会话列表为 45 秒），**没有 `callTimeout`**（总时长上限）

**没查清的**：2026-11-25 凌晨的一次测试里，曾观察到某个项目展开后长时间空白、没有出现失败提示。但事后复查发现：
- 当时**没有核实点中的是哪个项目**，实验对象不确定，不能作为证据
- 当时宿主机侧有 5 个实例的 `/p/<id>/sessions` 请求超时不返回（18 个实例里 17 个处于 stopped 状态）；几小时后复测，18 个实例全部正常返回（17 个 running）。**那次的超时与实例处于 stopped 状态同时出现，之后没能复现**

所以「超时后失败提示不出现」目前既没有被证实，也没有被否定。

**一个未经证实的可能机制**：`readTimeout` 计的是相邻两次收到数据之间的间隔。如果服务端在请求挂住期间仍有数据包陆续到达，`readTimeout` 不会被触发，请求可以一直挂着。是否真的发生，需要网络层日志才能确认。

**建议的验证方法**：用调试代理让 `/p/<id>/sessions` 的响应延迟超过 45 秒（分别试「完全不回」和「周期性发少量字节」两种），点开该项目，记录从点击到出现「加载会话失败」的时间。如果远超 45 秒或永不出现，再考虑加 `callTimeout`。

**附：服务端侧的关联观察（非 GC 问题，已另报 reasonix 协调线）**：实例处于 stopped 状态时，网关对 `/p/<id>/sessions` 的请求曾表现为 TCP 连上后不返回，而不是快速给出错误码。GC-02 修复合入后，这种情况用户至少能看到「加载会话中…」。

---

## 附：测试方法备注

供后续在模拟器上复测这些问题的人参考。

- **读界面**：用 UI Automator 语义树（`uiautomator dump`）。GC 页面里 emoji 在语义树中是 HTML 实体（如 `&#128247;`）。
- **系统栏高度**：`adb shell cmd overlay lookup android android:dimen/status_bar_height`
- **输入框回读**：长输入（如 64 位 token）后必须 dump 回读并逐字比对；命令返回成功不代表输入正确。分两段各 32 字符输入实测无丢失。
- **键盘遮挡**：输完内容直接点对话框底部按钮（如「保存」）会点不到——输入法还开着，键盘盖住了下半屏。先按 BACK 收键盘（输入法开着时 BACK 被输入法消费，不会关闭对话框），再点按钮。可用 `dumpsys input_method` 里的 `mInputShown` 判断键盘状态。
- **同地址连接会合并**：`ConnectionProfile.mergeByIdentity`（任务 542）按 scheme + host + port 合并，同地址新建的连接会更新原条目而不是新增。
- **ScannerActivity 未导出**：`android:exported="false"`，`adb shell am start` 会被拒绝，只能从界面进入（远端连接 → 添加连接 → 扫码）。
