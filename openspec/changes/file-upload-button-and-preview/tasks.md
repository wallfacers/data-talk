## 1. 前端：上传按钮与布局重排

Design Inputs (from client/DESIGN.md): ghost 按钮 `text-text-muted`，`size="icon-xs"` + `rounded-full`，`aria-label` 可访问性，compact density 工具栏，semantic tokens only。

- [x] 1.1 `prompt-composer.tsx`: 在工具栏发送按钮左侧添加 Paperclip 图标按钮（`<Button variant="ghost" size="icon-xs">` + `Paperclip` icon），`aria-label="Attach files"`，点击触发隐藏 `<input type="file" multiple>` ref
- [x] 1.2 `prompt-composer.tsx`: 将附件 chips 区域从 textarea 与工具栏之间迁移到 `InputGroup` 内部顶部（`InputGroupTextarea` 之前），容器改为 `flex-row overflow-x-auto` 水平布局
- [x] 1.3 `file-attachment-chip.tsx`: 适配紧凑横向卡片样式（调整 flex、padding、max-width）
- [x] 1.4 前端验证: `cd client && npx tsc --noEmit`

## 2. 前端：图片文件支持

- [x] 2.1 `useFileUpload.ts`: `ALLOWED_EXTENSIONS` 新增 `.png .jpg .jpeg .gif .webp .bmp`，排除 `.svg`
- [x] 2.2 `file-attachment-chip.tsx`: 当文件 MIME 以 `image/` 开头时，使用 `URL.createObjectURL(file)` 渲染 32×32 缩略图替代文件类型图标
- [x] 2.3 前端验证: `cd client && npx tsc --noEmit`
- [x] 2.4 前端测试: 添加 vitest 测试覆盖图片扩展名白名单、SVG 拒绝、缩略图渲染条件

## 3. 后端：图片 MIME 检测与元数据提取

- [x] 3.1 `FileAnalysisService.java`: 新增 `image/png, image/jpeg, image/gif, image/webp, image/bmp` MIME 检测分支，使用 `javax.imageio.ImageIO` 提取 `{ width, height, format }` 元数据
- [x] 3.2 后端验证: `mvn install -pl data-talk-application -am -DskipTests`（application 层改动）
- [x] 3.3 后端测试: 添加 JUnit 测试验证图片 MIME 检测和尺寸提取（使用小尺寸测试图片）

## 4. 后端：图片二进制读取

- [x] 4.1 `FileReadActionHandler.java`: 新增二进制读取路径——当文件 MIME 以 `image/` 开头时，读取完整文件字节并返回 `data:{mimeType};base64,{encoded}` 字符串，忽略 `offset/limit` 参数
- [x] 4.2 后端验证: `mvn install -pl data-talk-adapter -am -DskipTests`（adapter 层改动）
- [x] 4.3 后端测试: 添加 JUnit 测试验证图片文件 base64 读取、非图片文件仍走原有文本路径

## 5. AI 路由与集成

- [x] 5.1 `skills/file-upload-routing/SKILL.md`: 新增图片类型路由规则，图片文件路由为上下文附件（提供元数据 + base64 内容给 AI）
- [ ] 5.2 集成验证: 手动端到端验证——上传图片 → AI 收到图片上下文 → 基于图片内容回答

## 6. 最终验证

- [x] 6.1 后端全量编译: `cd server && mvn compile -q`
- [x] 6.2 前端全量类型检查: `cd client && npx tsc --noEmit`
- [x] 6.3 后端测试: `cd server && mvn test -pl data-talk-application,data-talk-adapter`（8 failures 均为 pre-existing `InverseSqlGeneratorTest`，与本次变更无关）
- [x] 6.4 前端测试: `cd client && npx vitest run`（13 failures 均为 pre-existing，新增测试 7/7 全部通过）
