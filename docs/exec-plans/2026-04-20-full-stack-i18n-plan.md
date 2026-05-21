# Full-Stack I18n — 实施计划

**Goal:** 为当前 DataTalk 前端页面和后端 API/默认文案增加中英双语支持，覆盖按钮、标题、空态、表单、toast、错误消息、默认名称，并让前端语言选择通过 `Accept-Language` 驱动后端返回语言。

**Architecture:** 前端引入轻量级应用内 i18n 基础设施（字典 + provider + hook + 本地持久化语言状态），把现有设置页语言选项接入全局语言切换，并在 HTTP 客户端统一附带 `Accept-Language`。后端使用 Spring `MessageSource` + `AcceptHeaderLocaleResolver` + `LocaleContextHolder` 解析当前语言，把异常处理、默认标题、连接测试结果、Action 描述等用户可见文案改为 message key 驱动。

**Tech Stack:** React 19 / Zustand / ky / Spring Boot 3.5 / Java 21 / JUnit 5 / Vitest

**非目标:**
- 不处理代码注释、测试示例数据、设计文档本身的国际化
- 不引入多语种内容编辑后台
- 不做超出 `zh-CN` / `en-US` 的更多语言扩展

**依赖关系:**
- T1 为基础设施，T2/T3/T4 依赖 T1
- T2（前端底座）完成后，T3（前端页面替换）可推进
- T4（后端 i18n）与 T3 可并行
- T5（测试与收尾）依赖以上全部

---

## Task 1: 计划登记与改造清单冻结

**Files:**
- Create: `docs/exec-plans/2026-04-20-full-stack-i18n-plan.md`
- Update: `docs/exec-plans/index.md`

- [x] 记录目标、边界、分任务和验证方式
- [x] 在 `docs/exec-plans/index.md` 的活跃计划中登记本计划

---

## Task 2: 前端国际化基础设施

**Files:**
- Create: `client/src/i18n/messages.ts`
- Create: `client/src/i18n/provider.tsx`
- Create: `client/src/i18n/use-i18n.ts`
- Update: `client/src/main.tsx`
- Update: `client/src/routes/__root.tsx`
- Update: `client/src/stores/ui-settings-store.ts`
- Update: `client/src/services/http.ts`

- [x] 定义 `zh-CN` / `en-US` 字典、`LanguageOption` 和基础 `t()` 能力
- [x] 将语言设置持久化到本地 store，并暴露给全局 provider
- [x] 在根节点挂载 i18n provider，支持运行时切换
- [x] HTTP 客户端统一发送 `Accept-Language`
- [x] 为格式化分组标签、动态插值、默认回退提供最小必要能力

---

## Task 3: 前端页面与交互文案替换

**Files:**
- Update: `client/src/services/http-error.ts`
- Update: `client/src/features/settings/**/*.tsx`
- Update: `client/src/features/session/**/*.tsx`
- Update: `client/src/features/workspace/components/*.tsx`
- Update: `client/src/features/chat/components/**/*.tsx`
- Update: `client/src/features/stage/components/**/*.tsx`
- Update: `client/src/features/data-grid/components/data-grid.tsx`
- Update: `client/src/features/settings/shared/recommended-providers.ts`

- [x] 替换设置中心、数据源、提供商、模型页面的按钮/标题/占位符/空态
- [x] 替换聊天区、模型选择器、空白欢迎态、会话侧栏、Stage 面板文案
- [x] 替换 toast、错误提示、确认弹窗、动态文案拼接
- [x] 保留 provider/model 等服务端原始名称，不做翻译

---

## Task 4: 后端国际化基础设施与用户可见文案

**Files:**
- Create: `server/data-talk-adapter/src/main/resources/messages.properties`
- Create: `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/I18nConfig.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/i18n/Translator.java`
- Update: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/GlobalExceptionHandler.java`
- Update: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- Update: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- Update: `server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Update: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/*.java`
- Update: `server/data-talk-infrastructure/src/main/resources/db/migration/V7__connection_name.sql`

- [x] 配置 `MessageSource`、默认 locale、`Accept-Language` 解析
- [x] 提供统一 `Translator`，在 application/adapter 层读取消息
- [x] 将异常返回、默认会话标题、连接测试结果、未知资源提示改为 message key
- [x] 将 Action 描述改为 message key 驱动，保证前端消费时可得到当前语言文本
- [x] 兼容数据库默认名称生成规则的国际化
  说明：未修改历史 migration `V7__connection_name.sql`，改为在运行时服务层生成本地化默认名称，避免按当前 locale 重写旧数据。

---

## Task 5: 测试、验证与文档收尾

**Files:**
- Update: `client/src/**/*.test.ts*`
- Update: `server/**/src/test/**`
- Update: `docs/exec-plans/2026-04-20-full-stack-i18n-plan.md`
- Update: `docs/exec-plans/index.md`

- [x] 为前端 i18n provider / 语言切换 / 关键页面文案增加或更新测试
  说明：更新了受影响的前端测试夹具与断言，并执行关键改动覆盖用例。
- [x] 为后端 locale 解析、异常消息、默认文案和 Action 描述增加或更新测试
- [x] 运行 `cd client && npx tsc --noEmit`
- [x] 运行 `cd server && mvn compile -q`
- [x] 更新计划 checklist 状态并在完成后把索引从 Active 移到 Completed

## 完成说明

- 前端实际在 `client/src/main.tsx` 挂载 `I18nProvider`，未改动 `client/src/routes/__root.tsx`，因为现有根入口已足够承载全局 provider。
- 校验命令：
  - `cd client && npx tsc --noEmit`
  - `cd client && npm test -- --run src/features/session/model-picker/__tests__/model-picker.test.tsx src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx src/features/stage/components/stage-window.test.tsx`
  - `export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 && export PATH="$JAVA_HOME/bin:$PATH" && cd server && mvn compile -q`
  - `export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 && export PATH="$JAVA_HOME/bin:$PATH" && cd server && mvn -q -pl data-talk-application test -Dtest=ActionRegistryTest`
  - `export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 && export PATH="$JAVA_HOME/bin:$PATH" && cd server && mvn -q -pl data-talk-adapter -am test -Dtest=SessionControllerIT,ConnectionControllerIT,ChannelControllerIT,FlywayMigrationIT,SupersedeArtifactActionTest -Dsurefire.failIfNoSpecifiedTests=false`
