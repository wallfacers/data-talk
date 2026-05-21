# Composer 数据源选择器设计

**状态**：approved
**日期**：2026-04-20
**作者**：wallfacers
**关联代码**：`client/src/features/session/`、`client/src/features/stage/`、`client/src/features/connection/`

## 1. 背景

当前 DataTalk 已有两套相关能力：

- Composer 底部已有模型选择器，承担“这次请求用哪个模型”的显式上下文选择
- 数据源连接已有设置页管理能力，但缺少会话主路径里的快速切换入口

现状的问题是：

1. 用户在聊天过程中会高频切换数据源，设置页入口过深，切换成本高
2. Stage 中每张卡片都必须绑定生成时的数据源，避免用户后续切换当前连接后出现“卡片来源对不上”的错觉
3. 若把主入口放进 Stage，会把“发送前上下文选择”放到结果区，割裂聊天主工作流

因此需要把“当前活动数据源”提升为 Composer 主工作流的一等上下文，和模型选择器并列，但同时保持 Stage 卡片对来源数据源的固化和可追溯。

## 2. 目标与非目标

**目标**：

- 在 Composer 中新增与模型选择器同级的数据源选择器，支持搜索和快速切换
- 数据源是会话请求的显式上下文，发送消息、开新会话、执行依赖数据库上下文的动作都读取当前活动数据源
- 无当前数据源时，不做“先报错再让用户重试”的阻断；应直接拉起选择弹框，选择后自动恢复原动作
- Stage 卡片、Tab 或其他工作区对象在生成时固化其 `connectionId` 和展示名，后续不受当前活动数据源变化影响
- 复用现有 UI Object Protocol 方向，提供前端可调用的“让用户选择数据源”适配器能力

**非目标**：

- 不把主数据源切换入口放进 `StageWindow`
- 不引入新的后端持久化字段保存“最近使用数据源”；最近使用仅做前端本地体验增强
- 不自动因为“查看旧卡片 / 切换 Tab / 聚焦 Stage”而隐式切换当前活动数据源
- 不在本期改造连接管理设置页的信息架构
- 不处理跨数据源单请求并发执行；当前一次请求仍只有一个活动数据源上下文

## 3. 设计结论

### 3.1 主入口位置

数据源主选择器放在 Composer 底部，与模型选择器并列，不放在 Stage 内。

理由：

- 模型和数据源都属于“本次请求的执行上下文”
- 数据源切换是高频动作，必须在发送主路径上完成
- Stage 是结果和工作区，不适合承担前置上下文配置

### 3.2 Stage 的职责

Stage 不负责主选择，只负责来源展示与按卡片回切：

- 每个卡片 / Tab 固化自己的 `connectionId`
- UI 上显示来源数据源 badge 或元信息
- 提供明确动作“用此数据源继续”，手动把当前活动数据源切到该卡片绑定的数据源
- 不允许仅因浏览卡片而自动切换当前活动数据源

## 4. 交互设计

### 4.1 Composer 底部布局

当前布局：

`模型 + Auto + Stage + Send`

调整为：

`模型 + 数据源 + Auto + Stage + Send`

其中“数据源”按钮视觉规格与模型选择器对齐：

- 高度 `h-7`
- `px-2`
- `text-xs`
- hover 态 `bg-accent/50`
- 未选中时显示占位文案“选择数据源”
- 已选中时显示连接名，右侧带 `ChevronDownIcon`

### 4.2 数据源选择弹框

入口点击后打开 `DataSourcePickerDialog`，交互风格与现有模型选择器对齐，采用对话框而非 popover。

弹框内容：

- 顶部标题：`选择数据源`
- 右上或标题区包含搜索框
- 主区为单列列表，不需要 provider 双栏结构
- 列表项展示三层信息：
  - 主标题：连接名
  - 次信息：`MySQL · prod-db.company · analytics`
  - 状态信息：最近测试结果、不可用状态或空

搜索维度：

- 连接名
- 数据库类型
- 主机
- 数据库名

排序规则：

1. 最近使用优先
2. 其余按名称排序

最近使用仅存前端本地，例如 `localStorage` 保存连接 id 顺序。

### 4.3 选择后的行为

点击某个数据源后：

1. 更新 `activeConnectionId`
2. 记录最近使用顺序
3. 关闭弹框
4. 若这次弹框是由某个待恢复动作触发，则自动继续该动作

切换成功后给轻量反馈，例如按钮附近瞬时提示“已切换到 orders-prod”，不使用强打断 toast。

### 4.4 无当前数据源时的体验

这是本设计与“简单阻断”方案的核心差异。

当用户发送消息、创建依赖数据库上下文的会话、执行 SQL 或触发需要数据源的 UI 动作时，如果当前没有 `activeConnectionId`：

- 不先弹错误 toast
- 不要求用户手动回去点一次数据源选择器
- 直接打开 `DataSourcePickerDialog`
- 用户选完后自动恢复原动作
- 用户取消后，本次动作中止，但原输入内容保留

也就是说，体验是“即时补全缺失上下文”，不是“先失败一次再让用户修正”。

### 4.5 Stage 卡片上的数据源行为

每张 Stage 卡片、Tab 或工作区对象在生成时写入：

- `connectionId`
- `connectionName` 或可稳定展示的快照名称

后续规则：

- 用户切换当前活动数据源，不影响既有卡片的来源展示
- 卡片可显示 badge，如 `orders-prod`
- 卡片可提供“用此数据源继续”按钮，本质等价于 `setActive(card.connectionId)`
- 点击该快捷动作只切当前活动数据源，不自动重放请求，不改历史卡片元数据

## 5. 状态模型

### 5.1 当前活动数据源

直接复用现有连接 store：

- `client/src/features/connection/store.ts`
- `activeConnectionId: string | null`
- `setActive(id: string | null)`

不新增第二套“current data source”状态，避免会话、Composer、Stage 三处各自记一份。

### 5.2 待恢复动作

为实现“缺失上下文时自动选库并恢复”，新增一套前端待恢复状态，语义对齐现有 `pendingModelPrompt`，但行为不同：

- `pendingConnectionPrompt`
- `pendingActionAfterConnectionPick`

它记录“当前有一个动作因缺少数据源需要先选库”，并在选择完成后恢复。

待恢复动作至少覆盖：

- 发送当前 Composer 输入
- 创建新会话并继续发送
- 需要数据库上下文的 Stage / workspace 执行动作

### 5.3 最近使用顺序

新增轻量前端本地状态，例如：

- `recentConnectionIds: string[]`

写入 `localStorage`，不落后端，不影响连接实体定义。

## 6. 协议与适配器设计

### 6.1 本地用户路径

用户直接在前端点击发送时，Composer 可以直接基于本地状态判断：

- 有数据源：正常继续
- 无数据源：打开 `DataSourcePickerDialog`，并记录待恢复动作

这是最快路径，不需要每次都经由 AI Action。

### 6.2 UI Object Protocol 扩展

为了让 AI、Stage 和未来更多前端动作都能复用“让用户选择数据源”这一能力，本期同步在 UI Object Protocol 中增加一个前端适配器动作。

推荐形式：

- `ui_exec(workspace, choose_connection, params?)`

返回约定：

```json
{ "success": true, "data": { "connectionId": "c1", "connectionName": "orders-prod" } }
```

或

```json
{ "success": true, "data": { "cancelled": true } }
```

前端 adapter 负责：

- 打开数据源选择弹框
- 等待用户选择或取消
- 回传结果

这样后续任一需要“显式向用户索取数据源”的动作，都可以统一走 `ui_exec choose_connection`，而不是各处散落各自的弹框逻辑。

### 6.3 与已有 Stage UI Object Protocol 的关系

本设计与 [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md) 一致：

- Global / Session / Tab 三层连接绑定模型继续保留
- 当前活动数据源属于前端全局上下文入口
- Session 在创建时固化 `connectionId`
- Tab / 卡片在生成时自持 `connectionId`
- `choose_connection` 是补全“用户交互式选择连接”的协议能力，不改变三层绑定模型

## 7. 组件与文件结构

**新增**

- `client/src/features/session/data-source-picker/data-source-picker.tsx`
- `client/src/features/session/data-source-picker/data-source-picker-dialog.tsx`
- `client/src/features/session/data-source-picker/__tests__/data-source-picker.test.tsx`
- `client/src/features/session/data-source-picker/__tests__/data-source-picker-dialog.test.tsx`

**修改**

- `client/src/features/session/prompt-composer.tsx`
  - 接入 `DataSourcePicker`
  - 缺失数据源时改为“拉起选择器并恢复原动作”
- `client/src/features/connection/store.ts`
  - 视需要补充最近使用辅助方法，但不改 `activeConnectionId` 语义
- `client/src/features/stage/*`
  - 为卡片 / Tab 元数据增加来源数据源展示和“用此数据源继续”动作
- `client/src/services/ui-router/*`
  - 注册 `ui_exec choose_connection` 处理能力
- `client/src/features/actions/ui-handlers.ts`
  - 若 AI 侧需要显式桥接，接入对应 handler

## 8. 错误处理与边界

### 8.1 当前选中数据源后来失效

如果当前按钮显示的数据源后来被删除、断连或测试失败：

- 不静默清空当前选择
- 选择器 trigger 保留原名称，但显示降级状态
- 用户下一次真正需要数据库上下文时，再显式引导其重新选择

这样可以保留用户对“我刚才在用哪个库”的认知，避免上下文突然丢失。

### 8.2 用户取消选择

若弹框由待恢复动作触发，而用户取消：

- 本次动作中止
- Composer 输入保留
- 不弹错误 toast

### 8.3 Stage 卡片回切到已失效数据源

若用户点击某张历史卡片的“用此数据源继续”，但该连接已失效：

- 仍先尝试把目标连接设为当前上下文
- 若实际执行动作时发现不可用，再通过正常的连接异常或重新选择流程处理
- 不在“点击回切”这一步偷偷替用户改成别的数据源

## 9. 测试规划

前端测试至少覆盖：

1. `DataSourcePicker`
   - 未选中时显示占位文案
   - 已选中时显示连接名
   - 点击 trigger 打开对话框

2. `DataSourcePickerDialog`
   - 搜索能按名称 / 主机 / 数据库名过滤
   - 最近使用排序优先
   - 点击某项后调用选中回调并关闭
   - 取消时只关闭，不改当前数据源

3. `PromptComposer`
   - 无当前数据源时，发送消息会打开数据源选择弹框
   - 选择完成后自动恢复原发送动作
   - 取消选择后输入内容保留

4. 会话与上下文
   - 新建会话使用当前 `activeConnectionId`
   - 切换数据源后，后续新动作读新值，旧会话 / 旧卡片元数据不被回写

5. Stage
   - 卡片显示来源数据源
   - 点击“用此数据源继续”只更新当前活动数据源，不改历史卡片元数据

6. UI Router / Action Handler
   - `ui_exec choose_connection` 能等待用户选择并返回 `{ connectionId, connectionName }`
   - 用户取消时返回 `{ cancelled: true }`

## 10. 验收清单

- [ ] Composer 底部出现与模型选择器同级的数据源选择器
- [ ] 数据源选择器支持搜索和快速切换
- [ ] 无当前数据源时，发送或相关动作会直接拉起选择器，而不是先报错
- [ ] 用户选择数据源后，原动作自动恢复，不要求再次点击发送
- [ ] Stage 卡片固化来源数据源，后续切换当前活动数据源不影响历史卡片
- [ ] 历史卡片可通过“用此数据源继续”显式回切当前活动数据源
- [ ] `ui_exec choose_connection` 适配器能力可用
- [ ] `client/` 下 `npx tsc --noEmit` 零错
- [ ] 新增及更新的 vitest 全部通过
