# Workspace And Backend I18n Design

## 背景

DataTalk 已具备前端 `client/src/i18n/messages.ts` 与后端 Spring `MessageSource + Translator` 的双端国际化基础设施，支持 `zh-CN` 与 `en-US`。但当前工作台相关页面和后端用户可见文本仍存在明显漏网：

- 前端 `workspace / stage / 关联 session` 组件仍有按钮、标签、空状态、菜单、ARIA label、placeholder、badge 文本直接硬编码在组件中
- 后端仍有一批会透出给前端的异常文本、静态说明、对象显示名、SQL 结果标题直接写死在 Java 代码中

本设计目标不是重做 i18n 体系，而是在现有体系上完成一次“用户可见文本全量补齐”。

## 目标

1. 前端工作台相关页面与组件的所有用户可见静态文案均接入现有 `t()` 翻译体系
2. 后端所有用户可见静态文案均接入 `Translator`
3. 后端错误与异常返回在 `Accept-Language` 为 `zh-CN` / `en-US` 时返回对应语言
4. 不改变现有协议结构、字段名、枚举值、事件名、日志语义

## 范围

### 前端纳入范围

覆盖 `client/src/features/workspace/**`、`client/src/features/stage/**`、相关 `client/src/features/session/**` 中所有用户可见静态文本，包括但不限于：

- 页面标题、按钮文案、菜单项、空状态、说明文字
- tooltip、badge、placeholder、辅助提示
- `aria-label`、`sr-only` 等辅助访问文案
- 当前扫描出的工作台硬编码，例如 `SQL 编辑器`、`High risk`、`Execution limit`、`Tab override`、`Session context`、结果面板拖拽条 aria label 等

### 后端纳入范围

覆盖所有会被用户或前端直接消费的静态文本与错误文本，包括但不限于：

- `action.*.description`
- Ontology / object type 显示名
- SQL 执行结果标题，例如 `Result Set {n}`、`Error {n}`、`DML Summary {n}` / `{start}-{end}`
- controller / action / service 抛出的、最终会进入 API 错误响应的异常消息
- 数据上下文解析、连接缺失、请求体缺失、未知 action / object type、SQL 风险拦截、SQL 执行失败等用户可见报错

## 非目标

以下内容不在本次设计范围内：

- 动态业务数据本身的翻译，例如数据库名、schema 名、表名、用户输入、AI 生成内容
- JSON 字段名、协议方法名、SSE event 名、`kind/type/status` 等机器消费常量
- 日志、埋点、调试输出、内部异常上下文
- 大规模异常体系重构，例如全面引入新的错误码层级或领域异常继承树

## 设计方案

### 方案选择

采用“分层补齐，顺手收口错误出口”的中等强度方案：

- 前端继续沿用 `useI18n()/t()` 与 `messages.ts`
- 后端继续沿用 `Translator` 与 `messages.properties`
- 不引入第二套 i18n 机制
- 不重写协议与错误响应结构
- 对现有用户可见文本逐步替换为 key 驱动

该方案比“只修当前扫描项”的最小补丁更完整，也比“重做异常体系”的方案更可控，适合本次需求。

### 前端设计

前端所有新增或替换文案统一收口到 `client/src/i18n/messages.ts`，并保持 `zh-CN` / `en-US` 双语同步。key 继续沿用现有分组约定，重点扩展：

- `workspace.*`
- `stage.*`
- 必要的 `common.*`

实现要求：

- 组件内禁止继续出现用户可见硬编码字符串
- 所有按钮、菜单、提示、badge、空状态统一使用 `t()`
- 所有辅助访问文案也必须走 `t()`
- 测试不得再依赖“默认一定是中文硬编码”，应通过 `translateMessage()` 或 `I18nContext` 注入断言

### 后端静态文案设计

后端静态用户可见文本统一落到：

- `server/data-talk-adapter/src/main/resources/messages.properties`
- `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`

建议 key 分组：

- `error.*`：错误与异常消息
- `action.*`：action 描述
- `object.*`：对象显示名
- `sql.*`：SQL 结果标题、SQL 相关静态标签
- `label.*`：少量其他后端静态展示字段

实现要求：

- 现有 `action.*.description` 全部改由 `Translator` 提供
- 对象类型显示名不再直接返回英文硬编码
- SQL 结果标题与汇总标题通过翻译模板生成，参数采用 `{0}`、`{1}` 等 `MessageFormat` 占位

### 后端错误出口设计

本次不做全仓库异常体系重写，但会把“会透出给前端的异常路径”收口到可翻译文本。具体策略：

- 保留现有 API 返回结构
- 对现有 `IllegalArgumentException`、`NoSuchElementException`、`RuntimeException` 的用户可见分支，改为使用 `Translator` 生成消息
- 优先处理 controller、action、service 中会直接进入 `GlobalExceptionHandler` 或等价错误出口的路径
- 对局部需要区分语义的错误，可补充轻量包装异常或翻译辅助方法，但不引入大规模异常类层级

重点覆盖路径包括：

- SQL 执行与风险拦截
- 数据上下文解析与自动定位表
- 请求体缺失、连接缺失、无活动连接
- unknown action / unknown object type
- 其他当前已知会直接返回裸字符串的用户可见异常

## 数据流与行为

1. 用户在前端切换语言，现有设置持久化与 `I18nProvider` 行为保持不变
2. 前端渲染时，工作台相关所有静态文本通过 `t()` 按当前语言展示
3. 前端请求继续自动附带 `Accept-Language`
4. 后端通过 `AcceptHeaderLocaleResolver` 解析 locale
5. 所有用户可见静态文本与错误消息经 `Translator` 输出对应语言
6. 前端无需更改响应结构解析逻辑，只消费已本地化的 message/title/description

## 测试与验收

### 前端

- 静态扫描工作台相关目录，确认无新增用户可见硬编码残留
- 组件测试覆盖按钮、菜单、空状态、ARIA label、结果面板标签
- 至少补一组 `en-US` 断言，确认新 key 可切换
- 维持 `npx tsc --noEmit` 通过

### 后端

- 为关键错误路径补单测或集成测试
- 在 `zh-CN` 与 `en-US` 请求头下分别断言错误消息切换
- 验证静态元数据文案与 SQL 结果标题走 `Translator`
- 维持 `mvn compile -q` 通过

## 风险与控制

### 风险 1：误改协议语义

控制措施：只替换用户可见文本，不改 JSON 字段名、对象 `kind/type/status`、SSE / RPC method 名。

### 风险 2：异常出口分散

控制措施：优先改会透出给前端的路径，不追求一次性重构所有内部异常；必要时新增最小包装。

### 风险 3：测试耦合固定语言

控制措施：同步改测试工具与断言，避免直接依赖中文硬编码。

## 实施拆分

### 任务 1：前端工作台静态文案补齐

- 盘点 `workspace / stage / 关联 session` 组件中的所有用户可见硬编码
- 补齐 `messages.ts` key
- 更新对应组件与测试

### 任务 2：后端静态元数据文案补齐

- 补齐 action 描述国际化
- 补齐对象显示名国际化
- 补齐 SQL 结果标题国际化

### 任务 3：后端错误出口国际化

- 将用户可见错误路径切到 `Translator`
- 补充 `Accept-Language` 相关测试
- 完成前后端编译校验

## 成功标准

- 工作台相关前端页面在 `zh-CN` 与 `en-US` 下均无明显硬编码漏项
- 后端 API 错误消息在双语下可切换
- 静态元数据、对象显示名、SQL 结果标题可切换
- 不引入协议回归，不影响现有前后端数据结构
