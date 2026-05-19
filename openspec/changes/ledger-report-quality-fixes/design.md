## Context

`report-document-generation` 落地后跑过一份真实月报（`r-616f3980`），E2E 暴露 4 个独立缺陷：TOC 空、cover author 水印、HTML 字体 CORS 拦截、validator fail-fast 让 AI 多轮 retry。

本 change 只在 `data-talk-application/report/` + `data-talk-adapter/report/` + ledger skill 资源目录内做局部修复，不引入新依赖、不动数据库 schema、不动前端。代码层面影响面：

- `ReportRenderer`（应用层，纯函数）
- `MarkdownRenderer`（应用层，纯函数）
- `ReportSchemaValidator`（应用层；API 签名变更，影响 `ReportArtifactService.promote` + `PromoteReportActionHandler`）
- `ReportController.serveAsset`（适配层，HTTP 端点头）
- `skills/ledger/SKILL.md` + `section-patterns.md`（资源文件）

资源同步走现有 `SkillResourceSyncer.syncSkill("ledger", ...)` SHA-256 marker 路径，无需新机制。

后端构建坑：编辑 `data-talk-application` 后必须 `mvn install -pl data-talk-application -am -DskipTests` 才能让 `spring-boot:run` 看到新 class（CLAUDE.md "Backend Run vs Compile" 已强调）。

## Goals / Non-Goals

**Goals:**

1. ledger 报告 HTML/PDF 目录区出现可点击的章节锚点列表。
2. cover 区不再出现 `DataTalk 自动生成` 这类生成器自指文案；旧报告重新渲染同样净化。
3. Report Viewer iframe（srcdoc，origin=null）中字体加载不再被 CORS 阻断，关闭 BUG-0073。
4. AI 一次 promote 失败的返回值能让 AI 一轮修齐所有问题，连续 2 次失败明确停下汇总。
5. 修复零回归：现有合法报告渲染结果文本/视觉差异仅限"TOC 多出锚点链接"与"cover author 黑名单时被去除"两点。

**Non-Goals:**

- TOC 页码（Chromium paged-media `target-counter` 兼容性差，v0 不做）。
- TOC 多级嵌套（当前 schema 只支持一级 chapter；未来加 sub-chapter 时再扩）。
- 全局 API CORS 策略调整（只动 `/_assets/**`）。
- ReportValidationException 类删除（保留作为内部 wrapper，避免外部依赖断裂）。
- 前端 Report Viewer 改造（iframe sandbox 模式不变，只靠后端 CORS 头解决）。
- BUG-0049（bezel 大屏中文乱码）顺手修 —— 不同 capability，单独 change。

## Decisions

### D1：TOC 用两遍渲染（预扫描 + 输出）

**选择**：`ReportRenderer.toHtml` 渲染前先做一次 `collectChapters(sections[])` 遍历，收集 `{idx, heading}` 列表，再传入 `renderToc(chapters)` 与 `renderChapter(chapter, idx)`。

**为什么**：
- 当前一次性 forward 渲染，遇到 `toc` block 时还不知道后面有多少 chapter；要么用占位符两遍 string replace，要么预扫描。预扫描更直白、可测，无字符串拼接陷阱。
- chapter id 用 1-based 索引（`chap-1`、`chap-2`）—— 简单稳定，不依赖 heading 文本（heading 中文/特殊字符做 slug 麻烦且不唯一）。
- TOC 的 `<a href="#chap-N">` 与 chapter 的 `<section id="chap-N">` 用相同索引锚定。

**Alternatives considered**:
- A. **占位符 + 两遍 string replace**：实现稍简单但易出错（占位符冲突、转义陷阱）。
- B. **JS 客户端生成 TOC**：依赖 DOM ready，PDF 截图前 LEDGER_READY 信号要等 TOC JS 跑完，复杂度高于价值。

### D2：Markdown TOC 输出 GFM 锚点链接

**选择**：`MarkdownRenderer.renderToc` 输出形如：

```
## 目录
- [业务总览](#业务总览)
- [区域分析](#区域分析)
```

**为什么**：
- GFM 自动给 heading 生成锚点（小写、连字符化），中文 heading 在 GitHub/Obsidian/Typora 都能正常跳转。
- 比"工具自动生成"更确定 —— 不依赖渲染工具的能力探测。

**Alternatives considered**:
- A. **保持空**（当前行为）：用户每次拿 .md 都还要自己加 TOC。否。
- B. **生成显式数字编号 `[1. 业务总览](#chap-1)`**：HTML 风格但 Markdown 不自带 id；如果显式锚点 GFM 也支持但需要 `<a name="chap-1"></a>`。冗余。

### D3：cover.author 水印按"黑名单 + sanitize"治理（不在 validator 拒绝）

**选择**：
- 渲染层（`ReportRenderer.renderCover` + `MarkdownRenderer` cover 处理）：trim 后用正则 `(?i)^(datatalk[\s·-]?(自动生成|生成|auto[\s-]?generated)|ai\s?生成|自动生成|系统生成|generated\s+by\s+.*)$` 匹配，命中视为空。warn 日志记录原始值（便于回溯哪些 prompt 触发）。
- prompt 层（`section-patterns.md` cover 字段说明 + `SKILL.md`）：显式列出禁用词清单 + "author 不确定就留空"。
- validator 不拦截：保留对历史/兼容 promote 的友好。

**为什么**：
- 根因在 AI 提示，sanitize 是兜底。两层都做，避免任一被绕过。
- 黑名单只作用在 cover（视觉影响最大），不作用在 meta.author（meta 不渲染到页面，仅入库）—— 防止过度拦截。
- 不在 validator 报错：旧报告（meta/cover author = "DataTalk 自动生成"）重新 promote 仍能成功，渲染时自动清除。

**Alternatives considered**:
- A. **validator 拒绝**：对历史 promote 有破坏性。
- B. **删除 cover.author 字段**：breaking change，影响 section-patterns / templates / 已发布报告。

**Risks**：黑名单会漏判（如新文案 "Powered by DataTalk"）。
**Mitigation**：正则可扩展；warn 日志反向监控触发率。

### D4：`_assets/**` 单端点加 CORS 头，不开全局

**选择**：`ReportController.serveAsset` 返回 `ResponseEntity` 时显式加：
```
Access-Control-Allow-Origin: *
Vary: Origin
```

**为什么**：
- 字体 always 走 CORS（即使 same-origin），iframe srcdoc origin=null 必须 wildcard。
- 端点只服务静态资产（字体/CSS/JS），无敏感数据，wildcard 安全。
- `Vary: Origin` 防止下游 cache 把不同 Origin 请求的响应串错。
- 不开 `@CrossOrigin` 全局或 `WebMvcConfigurer` 路径前缀过滤 —— 避免把 CORS 策略扩散到无关 API。

**Alternatives considered**:
- A. **改 iframe `srcdoc` → `src`**：Tauri prod 下 webview origin (`tauri://localhost`) ≠ backend origin (`http://127.0.0.1:8080`)，CORS 依然要解。复杂度高且不彻底。
- B. **WebMvcConfigurer 全局 CORS for `/api/reports/_assets/**`**：等价但不如单端点显式。

### D5：Validator collect-all 错误反馈

**选择**：
- `ReportSchemaValidator.validate(JsonNode)` 签名改为 `List<Violation> validate(JsonNode)`，不抛 `ReportValidationException`。
- `Violation` record：`{ String code, String path, String message }`，path 形如 `meta.title` / `sections[3].rows[201]`。
- 顶层校验失败（如 `root.isObject() == false`）直接返回 single-element list（语义清晰）。
- `ReportArtifactService.promote`：若 violations 非空，包装抛 `ReportValidationException` 携带 `List<Violation>`（保留异常路径，方便上层捕获）。
- `PromoteReportActionHandler` 错误返回结构：
  ```json
  {
    "error": "validation failed: meta.title is required; sections array must be non-empty",
    "errorCode": "REPORT_META_MISSING",   // 第一个 violation code，向后兼容
    "errorCodes": ["REPORT_META_MISSING", "REPORT_SECTIONS_MISSING"],
    "violations": [
      { "code": "REPORT_META_MISSING", "path": "meta.title", "message": "meta.title is required" },
      { "code": "REPORT_SECTIONS_MISSING", "path": "sections", "message": "sections array must be non-empty" }
    ],
    "recoveryHints": {
      "REPORT_META_MISSING": "把 title/templateId/generatedAt 放进顶层 meta 对象",
      "REPORT_SECTIONS_MISSING": "顶层用 sections 数组而非 blocks"
    }
  }
  ```
- `recoveryHints` 是 `static final Map<String, String>` 写死，code → 修复提示。AI 不依赖即可工作，但有了能一次修齐。

**为什么**：
- 保留 `errorCode` 单值字段：已部署的 AI / OpenCode 客户端仍按旧字段消费，零回归。
- `path` 字段帮 AI 精确定位错误位置，避免对整个 report.json 通搜。
- `recoveryHints` 是 well-known map，code 集合稳定，维护成本低。

**Alternatives considered**:
- A. **保留 fail-fast 但 SKILL.md 改用"先 dry-run check"流程**：dry-run 需要额外端点；且 fail-fast 本身的多轮 retry 是核心问题。
- B. **violations 改 array of strings**：丢失结构化 code，AI 解析难。

**Risks**：`recoveryHints` 中文文案与未来翻译/i18n 冲突。
**Mitigation**：v0 中文，后续按需移到 message bundle。

### D6：SKILL.md 增加"promote 失败兜底"指引

**选择**：`SKILL.md` 报告生成流程末尾增加段落：

> 如果 `datatalk_promote_report` 返回 `error` 字段：
> 1. 读 `errorCodes[]` + `violations[]`，**一轮** 内修齐所有违规后重试。
> 2. **连续失败 2 次** 不要继续 retry —— 停下，把完整 violations 列表告诉用户，请用户决策（修数据 / 改模板 / 放弃）。
> 3. retry 时不带 `groupId`（首次 promote 失败的 retry，之前没有 record，沿用旧 groupId 会被服务端拒绝为孤立 version）。

**为什么**：D5 给了一轮修齐的工具，但 AI 还是可能因数据问题死循环。明确兜底条件比单纯增强反馈更稳。

### D7：不动 spring-boot:run 行为；测试用 `mvn install -pl data-talk-application -am -DskipTests`

`ReportSchemaValidator` 在 `data-talk-application` 模块。本 change 改其 API 签名后，必须 `mvn install` 到本地 m2 才能让 `mvn spring-boot:run -pl data-talk-adapter` 加载新 class（CLAUDE.md "Backend Run vs Compile" 提醒）。tasks.md 验证步骤会显式列。

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| `report-document-generation` 未 archive 时本 change 的 delta spec 无法 base merge | tasks.md 显式声明依赖；要么先 archive `report-document-generation` 再 apply 本 change，要么并行 apply 时手动协调 spec merge 顺序 |
| `PromoteReportActionHandler` 错误返回结构扩展可能被 AI 提示词解析逻辑误读 | 保留 `errorCode` 单值兼容；OpenSpec scenario 同时断言两套字段并存 |
| TOC 预扫描需重写 `ReportRenderer.toHtml` 主流程一行（增加 chapters 收集步骤），有破坏 chart bootstrap script 的风险 | 单元测试覆盖 "report 仅含 narrative" / "report 含 5 个 chapter" / "report 含 chart + chapter 嵌套" 多场景 |
| 黑名单正则可能误杀合法 author（如真人姓名"DataTalk 团队张三"） | 正则锚定 `^...$`，仅匹配纯水印短文本；姓名通常包含中文姓+名，不会匹配 |
| CORS wildcard 是否被安全审计标记为风险 | _assets 仅服务字体/CSS/JS 静态资产，无身份/敏感数据；docs/SECURITY.md 中标注该端点为静态资产白名单 |
| Markdown TOC 锚点对中文 heading 跳转依赖渲染工具支持 GFM-style slug | GitHub/Obsidian/Typora 均支持；不支持的工具退化为不可点击但不报错 |

## Migration Plan

1. **代码 + 资源改动一次性合入** —— 修复彼此独立无依赖，但都在 ledger report capability 内，单 PR/单 commit 串。
2. **后端验证**：`cd server && mvn -pl data-talk-application -am test`（含 ReportRenderer/ReportSchemaValidator 单元测试） + `mvn -pl data-talk-adapter test`（含 ReportController + PromoteReportActionHandler）。
3. **本地集成**：`mvn install -pl data-talk-application -am -DskipTests` → `mvn spring-boot:run -pl data-talk-adapter`，启动后跑一个旧报告的 GET `/api/reports/{id}/download/html` + GET `/api/reports/_assets/fonts/NotoSerifSC-Regular.otf` 确认 CORS 头。
4. **E2E（playwright-cli）**：
   - 进入 Report Viewer，打开任一已生成报告。
   - 断言：iframe 内 console 无 CORS 错误；目录区出现可点击章节链接；cover 区无 "DataTalk 自动生成"；点击章节锚点滚动到对应位置。
   - 删除/不删除 `r-616f3980` 由用户决定 —— 不在本 change 自动处理。
5. **BUG 收尾**：`docs/bugs/BUG-0073-report-font-cors-blocked-in-iframe.md` status `open → fixed`，回填 `fixCommit`；同步 `docs/bugs/index.md` 行。
6. **回滚**：单 commit revert；validator 行为回滚后旧 `errorCode` 字段消费方向后兼容字段读取无影响。

## Open Questions

1. **是否在本 change 同步派生重新触发**：旧报告（已 promote 的 HTML/PDF）仍保留旧 cover author / 旧 TOC 空白。是否要在本 change 提供一个"重派生"端点 `POST /api/reports/{id}/rederive`？**当前决定**：不做（用户可"重新生成"走新 promote 流程），未来根据需求再加。
2. **`recoveryHints` 是否要做成多语言**：v0 中文写死；当前 SKILL.md / data-contract.md 也全中文。**当前决定**：跟着 skill 走中文，未来 i18n 时一并处理。
3. **CORS `Access-Control-Allow-Origin: *` vs 显式列 `null` + `tauri://localhost`**：wildcard 简单，列举可能漏。**当前决定**：wildcard，静态资产无敏感。
