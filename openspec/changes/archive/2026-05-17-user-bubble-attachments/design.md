## Context

**现状回顾**：

```
发送前（prompt-composer）            发送后（UserBubble）            历史会话回看
─────────────────────────           ──────────────────────         ────────────────
FileAttachmentChip (横向单行)        FileUploadCard (气泡内纵向)      FileUploadCard
├ 含 File 对象                       ├ 仅 metadata                   ├ 仅 metadata
├ 缩略图 + 进度 + X 删除              ├ analysis metrics              ├ 不可点
├ 单击 → FilePreviewDialog          ├ Preview 可折叠（文本）         ├ 无预览闭环
                                    └ 占满气泡宽度
```

三个生命周期视觉/交互不一致，且**历史无法预览**是断开的最关键环节。

**关键既有契约**：

- `FileUploadPart` 协议已有 `fileId / filename / mimeType / sizeBytes / analysis` 全部 metadata（`client/src/services/channel/types.ts:75`）—— 无需扩展协议。
- 后端 `UploadedFile.physicalPath` 字段存绝对路径，`UploadedFileRepository.findById(id)` 已存在 —— 后端只需补一个 GET endpoint，不动 domain / application / infra。
- 上传时文件落到 `~/.data-talk/uploads/{fileId}/{filename}`（`FileUploadController:77-79`）—— 路径根固定可控。
- 现有 `FilePreviewDialog` 已实现 image / markdown / code / text 四类预览 + maximize/restore + close —— UI 与状态机完整，只是契约绑死了内存 `File`。

**约束**：

- DataTalk 4 层架构：domain ← application ← infrastructure ← adapter。新增 GET 端点只动 adapter 层，无需新建 domain/application 抽象。
- `client/DESIGN.md` 五态规则、`accent.primary` 仅留 focus/selection/primary action。
- 不破坏现有输入框 chip 行为（删除、上传进度、错误态）。

## Goals / Non-Goals

**Goals:**

- 用户气泡上方以**独立横向 chip 列表**回显附件，与输入框 chip 视觉一致。
- 当条新发消息 / 历史回看消息 / 输入框预发消息**共用同一 `FilePreviewDialog`**。
- 后端补 `GET /api/files/{fileId}/content`，让历史附件可下载/可预览。
- `FileChip` 五态显式映射 DESIGN tokens，无颜色作为唯一状态信号。
- `FilePreviewDialog` 数据源抽象解耦，未来其他来源（如分享链接、剪贴板）可插入新 `PreviewSource` variant 而不动 dialog 本体。

**Non-Goals:**

- 不修改 `FileUploadPart` 协议字段或 OpenCode 协议。
- 不修改 `UploadedFile` domain、不动 Flyway schema。
- 不引入文件版本控制 / 多版本预览。
- 不实现"附件批量下载 / 打包"。
- 不实现 Dialog 内的图片缩放、平移、旋转等高级查看器特性（保持现有 maximize/restore 即可）。
- 不实现"附件搜索"或附件库列表（独立于本次范围）。

## Decisions

### Decision 1: `FilePreviewDialog` 数据源抽象 → `PreviewSource` 联合类型

**方案**：把 `FilePreviewDialog` 的 prop 从 `attachment: FileAttachment | null` 改为 `source: PreviewSource | null`。

```ts
export type PreviewSource =
  | { kind: 'local';  file: File }
  | { kind: 'remote'; fileId: string; filename: string; mimeType: string; sizeBytes: number }
```

Dialog 内部统一通过一个 `useBlob(source)` hook 屏蔽来源差异：

- `local` → `URL.createObjectURL(source.file)` / `source.file.text()`
- `remote` → `fetch('/api/files/{fileId}/content')` → `Response.blob()`，从 blob 走 `URL.createObjectURL` 或 `Blob.text()`

Dialog 现有的"image 用 objectURL / 文本用 readAsText"分支逻辑无需改动，只是字节来源不同。`useEffect` cleanup 中 `URL.revokeObjectURL` 仍按现状执行。

**Alternatives considered**：

| 方案 | 优点 | 缺点 | 决策 |
|---|---|---|---|
| A. `PreviewSource` 联合类型 | dialog 集中处理两种来源，类型穷举安全 | 多一层间接 | ✅ 选 |
| B. 把 Dialog 拆成 `LocalPreviewDialog` + `RemotePreviewDialog` | 单一职责清晰 | UI/maximize 逻辑重复，违反 DRY | ✗ |
| C. Dialog 完全 source-agnostic，外面塞 `getBlob: () => Promise<Blob>` callback | 最干净的依赖倒置 | 调用方需要管 blob 生命周期，dialog 失去 cleanup 时机 | ✗ |

**理由**：联合类型让 dialog 内部 cleanup 仍能基于 `kind` 区分（local 不需 fetch、remote 需要 fetch + loading state），且 TypeScript 的 discriminated union 给后续新增 source 提供穷举检查。

### Decision 2: 组件三分

```
┌──────────────────────────────────────────────────────────────┐
│ FileChip (纯展示, 新)                                          │
│  - props: { filename, sizeBytes, mimeType, onClick?, disabled?}│
│  - 含缩略图（图片）/ 图标（其他）                                │
│  - 五态: hover / active / focus-visible / selected(N/A) /      │
│           disabled                                              │
│  - 不带 X、不带进度条、不带 File 对象                            │
│  - 单击触发 onClick（外部决定是否打开 Dialog）                   │
└──────────────────────────────────────────────────────────────┘
            ▲                              ▲
            │                              │
┌───────────┴────────────┐       ┌────────┴─────────────────┐
│ FileAttachmentChip      │       │ BubbleAttachmentList     │
│  (输入框场景)            │       │  (用户气泡上方场景, 新)    │
│  - FileChip + 进度条 + X │       │  - 横滚容器 + FileChip[]  │
│  - 内部管 previewOpen   │       │  - 内部管 previewOpen +   │
│  - source = local       │       │    activePreviewSource    │
└────────────────────────┘       │  - source = remote        │
                                  └──────────────────────────┘
```

**理由**：

- `FileChip` 是最小复用单元，零业务依赖；输入框场景和气泡场景**只在外壳上扩展不同附加能力**（输入框要进度/删除，气泡要批量与右对齐布局），符合 DataTalk 客户端"组合优于继承"惯例。
- 删除 `FileUploadCard`：它的所有职能在新架构下被 `FileChip`（密度更低、视觉更现代、点击可预览）取代。`AnalysisBadge` / `AnalysisMetrics` / `PreviewSection` 三段 metadata 渲染**不迁移**到 chip —— 这部分高密度信息属于"详细预览"层，应该出现在 Dialog 而非气泡列表中。如有用户在历史 chip 上需要看 metrics，已可通过单击进入 Dialog 看到（Dialog 后续可补 metadata 区，但非本次必须）。

**Alternatives considered**：

- **不拆 `FileChip`，让 `FileAttachmentChip` 通过 `mode='preview' | 'edit'` prop 切换** —— 拒绝：会让单一组件同时承载两种数据契约（File vs metadata），prop 矩阵爆炸。
- **保留 `FileUploadCard` 并新增 `BubbleAttachmentList` 用旧卡片** —— 拒绝：与"风格统一"目标直接冲突；用户明确要求复用输入框 chip 风格。

### Decision 3: 后端 `GET /api/files/{fileId}/content` 端点设计

**路由**：`@GetMapping("/{fileId}/content")` 挂在现有 `FileUploadController`（`@RequestMapping("/api/files")`）下，不新建 controller。

**响应**：

```
200 OK
Content-Type: {uploadedFile.mimeType}
Content-Length: {uploadedFile.sizeBytes}
Content-Disposition: inline; filename*=UTF-8''{percentEncode(filename)}
Cache-Control: private, max-age=300
Body: <file bytes stream>

404 Not Found  → {"error": "File not found"}        当 findById 返回 empty 或物理文件不在
410 Gone       → {"error": "File no longer available"} 物理文件被清理但 DB 记录还在（cleanup race）
```

**安全防御**：

1. **Path traversal 硬绑定**：从 `UploadedFile.physicalPath` 直接读，**不接受**任何来自请求路径的拼接。fileId 仅作为数据库查询键，与文件系统操作完全解耦。
2. **路径白名单校验**：读文件前断言 `physicalPath.startsWith(uploadBase.toAbsolutePath().toString())`，防御数据被篡改后指向 uploads 目录之外的文件。
3. **MIME 不可被请求覆盖**：响应的 `Content-Type` 严格来自 DB 中持久化的 `mimeType`（上传时由 `FileAnalysisService.detectMime` 计算）。
4. **`Content-Disposition: inline`**：浏览器内嵌渲染，避免被引导成下载；filename 用 RFC 5987 编码以支持中文文件名（如截图中"效果图.png"）。
5. **不做鉴权**：DataTalk 当前是单用户桌面应用，所有 controller 均未引入 user/session 鉴权层。新增本端点遵循同一原则，**不强制 sessionId 校验**，避免引入与现有架构不一致的鉴权碎片。后续若引入多用户，本端点需同步加 sessionId 验证（已在 Risks 列出）。

**实现方式**：

```java
@GetMapping("/{fileId}/content")
public ResponseEntity<Resource> getContent(@PathVariable String fileId) throws IOException {
    UploadedFile uf = uploadedFileRepo.findById(fileId).orElse(null);
    if (uf == null) return ResponseEntity.status(NOT_FOUND).build();

    Path uploadBase = Path.of(System.getProperty("user.home"), ".data-talk", "uploads")
                          .toAbsolutePath().normalize();
    Path filePath = Path.of(uf.physicalPath()).toAbsolutePath().normalize();
    if (!filePath.startsWith(uploadBase) || !Files.exists(filePath)) {
        return ResponseEntity.status(NOT_FOUND).build();
    }

    Resource resource = new FileSystemResource(filePath);
    String encodedName = URLEncoder.encode(uf.filename(), StandardCharsets.UTF_8).replace("+", "%20");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(uf.mimeType()))
        .contentLength(uf.sizeBytes())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .body(resource);
}
```

**为什么 `FileSystemResource` 而不是 `Files.newInputStream` + `StreamingResponseBody`**：当前最大文件 50MB，Spring 的 `FileSystemResource` 已自动走零拷贝传输；`StreamingResponseBody` 适合"按需生成"场景，对已落盘文件反而引入异步复杂度。

### Decision 4: 视觉 token 与五态映射（`FileChip`）

| 状态 | Token | Rationale |
|---|---|---|
| 默认 | `border-border-default bg-bg-soft text-text-base` | 与 `FileAttachmentChip` 输入框态完全一致 |
| hover | `bg-bg-subtle border-border-strong` | 不使用 `accent.primary`，遵守 DESIGN.md 第 353 行 |
| focus-visible | `outline-none ring-2 ring-accent-primary/40 ring-offset-1 ring-offset-bg-canvas` | 使用 `interaction.focusRing`（accent 在 focus 是允许语义） |
| active | `bg-accent-primary/8 border-accent-primary/40` | active 是"engaged control state"，可携带轻量 primary 提示 |
| disabled | `opacity-60 cursor-not-allowed` (无 onClick) | a11y：不仅依赖颜色，并加 `aria-disabled="true"` |

**主题色出现规则**：

- 默认/hover 全程中性 → 在用户气泡这种"承载主色气泡 + 主色发送按钮"的视觉密集区域不再贡献额外饱和度。
- 仅在 focus / active 引入 `accent.primary` —— 用户用键盘 Tab 到 chip 或鼠标按下时给明确反馈，符合 DESIGN.md 第 279 行"accent.primary reserved for current object, primary action, and selected emphasis"。

**Alternatives considered**：

- **hover 加 `border-primary/40`**：之前 explore 倾向方案。最终拒绝 —— 用户气泡区已有 `bg-primary` 气泡 + 主色发送按钮，hover 再加主色描边会造成视觉碎片化。

### Decision 5: `BubbleAttachmentList` 布局

```css
/* 容器 */
ml-auto                  /* 右贴齐 */
max-w-[85%]              /* 与气泡 max-w-[85%] 同宽 */
flex flex-row gap-1.5
overflow-x-auto
justify-end              /* 内容少时贴右；overflow 时浏览器退化为 justify-start 行为 */
pb-1                     /* 给气泡留 4px 间距 */

/* chip 单体 */
max-w-[180px]            /* 与输入框 chip 一致 */
shrink-0                 /* 横滚容器内必须 */
```

气泡上方与气泡间距 = `pb-1`（来自容器 padding-bottom）+ `mb-2`（来自气泡 margin-top，复用现有 `UserBubble` 容器 `my-2` 中的 `mt-2`，但因为 chip 容器与气泡处于同一 `flex flex-col items-end gap-1` 父容器下，实际间距由父容器 `gap-1` 控制）。

**渲染锚点**：在 `UserBubble` 函数返回的根 `div` 内部，**气泡 `<div>` 之上、`items-end` 父容器之内**插入 `<BubbleAttachmentList />`。父容器已有 `flex flex-col items-end gap-1` —— chip 列表天然右对齐。

```tsx
<div className="my-2 flex flex-col items-end gap-1">
  {fileParts.length > 0 && <BubbleAttachmentList parts={fileParts} />}  {/* 新位置 */}
  <div className={cn('relative max-w-[85%] rounded-lg ...')}>           {/* 气泡 */}
    {/* 删除：气泡内部的 fileParts 渲染块 */}
    {isBangQueryUser ? ... : <Markdown ... />}
  </div>
  {/* meta / actions */}
</div>
```

**为什么 chip 容器作为兄弟节点而非气泡子节点**：保持 chip 与气泡视觉上"独立的两个气块"，且利用既有 `items-end + gap-1` 父容器节省一层 wrapper。

### Decision 6: 图片缩略图加载策略

`FileChip` 在图片类型 chip 上显示 24×24 缩略图（与输入框 chip 一致）：

- 输入框场景（`local`）：`URL.createObjectURL(file)` —— 现有方式。
- 气泡 / 历史场景（`remote`）：直接用 `<img src={'/api/files/' + fileId + '/content'} />` —— 浏览器原生 HTTP 缓存（响应已设 `Cache-Control: private, max-age=300`），无需 JS 层做 Blob 缓存。

**Alternatives considered**：

- **chip 不显示缩略图，仅图标**：拒绝，图片附件失去最关键的视觉识别度。
- **后端新增 thumbnail 端点（resize 到 64×64）**：拒绝 —— 引入额外依赖（ImageIO/ImageMagick）；附件上限 50MB 内浏览器解码 24×24 不显著卡顿。如未来发现性能问题，本端点可独立增量上线。

### Decision 7: Dialog `onOpenChange` 与 `source` 解耦

`BubbleAttachmentList` 维护两个 state：

```ts
const [previewOpen, setPreviewOpen] = useState(false)
const [activePart, setActivePart] = useState<FileUploadPart | null>(null)
```

单击某 chip → `setActivePart(p); setPreviewOpen(true)`，把 `activePart` 映射为 `{ kind: 'remote', ... }` 传给 dialog。`onOpenChange(false)` 时只关闭 dialog，**不清空 `activePart`** —— Dialog close 动画期间仍需 source 渲染；下次单击会覆盖。

输入框 `FileAttachmentChip` 维护单个 `attachment` 的 dialog，每个 chip 独立持有 `[previewOpen, setPreviewOpen]`（现状），无需改动。

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| GET 端点未来需要鉴权时，需回头加 sessionId 校验 | 在 Decision 3 显式注释"single-user assumption"，并在 BUG/tech-debt 追踪里登记一个 P3 项 |
| 物理文件被用户手动清理后 chip 仍可点 → Dialog 显示 404 | Dialog 内部 fetch 失败时显示 "文件不可用" 友好态（复用现有 `readError` 渲染分支） |
| 大图（接近 50MB）通过 `<img src>` 加载时阻塞气泡渲染 | chip 上的 24×24 缩略图本质是浏览器解码，与气泡渲染异步；Dialog 内大图打开有 maximize 已是渐进式 |
| 旧会话 `FileUploadPart` 万一缺 `fileId`（理论不存在） | chip 渲染前 `if (!part.fileId) → 灰态不可点 + tooltip "无法预览"`；Type 层 fileId 为 `string`，运行期 spot-check 一条最早消息即可 |
| 删除 `FileUploadCard` 后是否还有引用方？ | grep 显示仅 `user-bubble.tsx` 引用，迁移即可下线；保险起见保留组件文件直到 archive 阶段，再随归档清理 |
| 用户气泡上方插入 chip 后，气泡的"右下角 hover 操作条"（copy / timestamp）受影响？ | 不受影响 —— 操作条独立在气泡同级下方，chip 在气泡上方，三者垂直 stack，互不干涉 |
| 横滚区出现滚动条影响视觉密度 | 容器 `overflow-x-auto` 在多 chip 时才出现；样式上加 `scrollbar-thin` 或保持系统默认（与输入框一致），不引入自定义滚动条样式 |

## Migration Plan

**单次发布即可**，无需 feature flag：

1. **后端先行**：合入 `GET /api/files/{fileId}/content` —— 旧前端不调用此端点，零影响。
2. **前端跟进**：合入组件拆分（`FileChip`、`BubbleAttachmentList`）+ Dialog 数据源重构 + `UserBubble` 调整。
3. **下线 `FileUploadCard`**：在同一个 PR 中删除其引用；保留文件直到 OpenSpec archive 阶段，方便 spot-check 没有遗漏调用方。
4. **验证**：在 Tauri 环境真实启动，依次验证：
   - 输入框场景预览仍正常（回归）
   - 新发消息气泡上方出现 chip 并可点击预览
   - 刷新页面后历史消息上方 chip 仍可预览
   - 中文文件名编码正常
   - 单 chip / 5 chip / 20 chip 多种密度下横滚正常
   - 删除 `~/.data-talk/uploads/{fileId}/` 物理文件后 chip 点击显示友好 404

**Rollback 策略**：直接 revert PR。后端 GET 端点保留无副作用；前端组件回退即可。

## Open Questions

- **缩略图加载失败兜底**：当 `<img src="/api/files/{fileId}/content">` 失败（如断网）时，是否切回图标占位？倾向加 `onError` → 切图标。低优先级，可在实现期决定，不阻塞 spec。
- **Dialog 在 remote 场景的 loading 态**：fetch 期间是否显示骨架？现有 dialog 已有 "loading" 文案分支（第 217-219 行），复用即可，但样式细节（spinner vs 文案）可在实现期对齐设计语感。
- **是否给 `GET` 端点加 ETag**：浏览器 max-age=300 已够日常场景。ETag 在文件不可变假设下意义有限（fileId 一变文件就一变），不引入。
