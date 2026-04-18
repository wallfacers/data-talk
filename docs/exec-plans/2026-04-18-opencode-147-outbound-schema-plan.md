# OpenCode 1.4.7 Outbound Schema Fix Plan

**Status:** 已完成（2026-04-18）

**Execution notes:**
- **初版（规范化填字段）无效**：加了 `prt_` 前缀 + `synthetic`/`ignored` 默认 + `time` 对象后，smoke 不再报 Zod error，但 OpenCode 也**不生成任何响应**。静默 pass → 静默吞。
- **根因（最终版）**：参考 `~/project/open-db-studio/src-tauri/src/agent/stream.rs:395-414` 的 Rust 实现——正确做法是**parts 只传 `{type, text}` 两个字段**。所有其它字段（id / sessionID / messageID / synthetic / ignored / time / metadata）都由 OpenCode 服务端生成；客户端传了就必须通过严格 schema 校验。
- 已改 `ChannelService.partForWire`：白名单 emit。TextPart → `{type:"text", text}`；FilePart → `{type:"file", mime?, filename?, url?, source?}`；其它类型打 warn + 丢弃。
- 单测 `ChannelServiceModelParamTest` 5/5 绿；全量回归 100/100 绿。

**Goal:** 修复 DataTalk → OpenCode 1.4.7 `POST /session/:id/message` 出站 body 的 schema 不匹配问题——1.4.7 对请求体开启了严格 Zod 校验，当前 body 会被 reject，AI 根本收不到用户消息。

**Architecture:** 在 `ChannelService.sendMessage` 转发前对 body 做规范化：
1. `model` 字符串 `"provider/modelID"` 拆成 `{providerID, modelID}` 对象。
2. 每个 outbound part 规范化：`id` 必须以 `prt_` 开头；`synthetic`/`ignored` 补齐 boolean 默认值；`time` 必须是合法对象。

**Tech Stack:** Jackson、JUnit 5、AssertJ、Mockito；依赖不变。

---

## 背景

envelope adapter plan 完成后实机 smoke 发消息，OpenCode 1.4.7 返回 schema error：

```
{"expected":"object","path":["model"],"message":"Invalid input: expected object, received string"}
{"path":["parts",0,"id"],"message":"Invalid string: must start with \"prt\""}
{"expected":"boolean","path":["parts",0,"synthetic"]}
{"expected":"boolean","path":["parts",0,"ignored"]}
{"expected":"object","path":["parts",0,"time"]}
```

触发点：`ChannelService.sendMessage` 把 `userPrefs.getCurrentModel()` 直接作为字符串塞给 body；把前端传入的 `TextPart`（`id` = UUID，`synthetic`/`ignored`/`time` 都是 null）直接 `om.convertValue(..., Map.class)` 转发。

OpenCode 1.4.7 的请求 schema 现在是：

```ts
{
  model: { providerID: string, modelID: string },
  parts: [{
    id: string 形如 "prt_xxx",
    type: "text" | "file" | ...,
    text: string,
    synthetic: boolean,
    ignored: boolean,
    time: { start: number, end?: number },
    messageID: string,
    sessionID: string,
    ...
  }]
}
```

---

## 范围

**改动文件：**

- `server/data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java` — sendMessage 里加一个 `normalizeForOpenCode` 流程
- `server/data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceModelParamTest.java` — 更新 model 断言（对象而非字符串），新增 part schema 锁定测试

**不改：**

- `Part` domain 模型（DataTalk 内部持久化 schema 保持向后兼容）
- `IdGenerator`（仍用 UUID；prefix 在 outbound 规范化里加）
- 前端（出站规范化是后端内部行为，对前端透明）
- `OpenCodeGateway`（仅接受 `Map<String, Object> body`，对形状不 opinionated）

---

## Task 1：ChannelService 出站规范化

**Files:**

- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java`

- [x] **Step 1：加 `normalizeModel(String)` 私有方法**：
    - 输入：`"provider/modelID"`（或更多斜杠的情况，按首个 `/` 拆分左右）
    - 输出：`Map.of("providerID", provider, "modelID", modelID)`
    - 无 `/`：`modelID` 为全量字符串，`providerID` 空串（让 OpenCode 拿自己的默认）
- [x] **Step 2：加 `normalizePartForWire(Part)` 私有方法**，返回 `Map<String, Object>`：
    - 先 `om.convertValue(part, Map.class)` 得基础结构
    - `id`：如果不以 `prt_` 开头，重写成 `"prt_" + 原 id 去连字符`
    - `synthetic`：为 null 时写 `false`
    - `ignored`：为 null 时写 `false`
    - `time`：为 null 时写 `{"start": <当前 ms>}`（end 可选，不塞）
- [x] **Step 3：sendMessage 替换现有转发段**：
    ```java
    body.put("parts", stamped.stream().map(this::normalizePartForWire).toList());
    String model = userPrefs.getCurrentModel();
    if (model != null && !model.isBlank()) {
        body.put("model", normalizeModel(model));
    }
    ```
- [x] **Step 4：id 规范化同时也 stamp 回到持久化/事件流的 Part 上**——不，暂不改本地 id。理由：DataTalk 内部 id 是 UUID 形态已被 MessageRepository/事件流 & 前端引用；只在出 OpenCode 前换。可选：把 prt_ 版本 id 也记进 OpenCodeSessionMap 做 partId 反查（留 TODO，暂时不做，后续 delta 事件里的 partID 是 OpenCode 生成的）。

---

## Task 2：更新单测 + 新增 schema 锁定测试

**Files:**

- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceModelParamTest.java`

- [x] **Step 1**：现有 `sendMessage_forwards_current_model_when_set` 的断言从 `containsEntry("model", "openai/gpt-5")` 改成：
    ```java
    assertThat(cap.getValue().get("model"))
        .isEqualTo(Map.of("providerID", "openai", "modelID", "gpt-5"));
    ```
- [x] **Step 2**：新增 `sendMessage_normalizes_part_for_opencode_wire`：
    - 输入 TextPart(`id="abc-123"`, `synthetic=null`, `ignored=null`, `time=null`)
    - 断言捕获 body 里 parts[0]：
        - `id` startsWith "prt_"
        - `synthetic` == false
        - `ignored` == false
        - `time` 是 Map 且含 `start` key
- [x] **Step 3**：新增 `sendMessage_preserves_existing_prt_prefix_id`：
    - 输入 TextPart(`id="prt_keepme"`, …)
    - 断言捕获 body 里 parts[0].id == "prt_keepme"（不加第二次前缀）
- [x] **Step 4**：新增 `sendMessage_model_without_slash_uses_modelID_only`：
    - 输入 model="gpt-5"（无斜杠）
    - 断言 `providerID` == ""，`modelID` == "gpt-5"

---

## Task 3：回归 + 重装

- [x] **Step 1**：`cd server && mvn -pl data-talk-application test`
- [x] **Step 2**：`mvn -pl data-talk-application install -am -DskipTests`
- [x] **Step 3**：`mvn -pl data-talk-adapter compile`（确认引用没断）

---

## Task 4：实机 smoke（最终验证，AI 真能响应）

- [x] **Step 1**：`mvn -pl data-talk-adapter spring-boot:run`
- [x] **Step 2**：前端发"你好"
- [x] **Step 3**：观察
    - 后端 log 不再出现 `session.error` 含 schema error
    - 前端 EventSource 收到 `message.part.updated` 的 assistant 气泡，内容逐字填充
- [x] **Step 4**：如仍有 schema error，按报错路径继续精修（可能有更多字段）

---

## 验收

- [x] Task 2 所有新/改单测绿
- [x] `mvn -pl data-talk-application test` 全绿
- [x] 实机发"你好" → 前端看到 AI 气泡内容
- [x] `docs/exec-plans/index.md` 把本计划从活跃搬到已完成

## 风险与回滚

- **风险 1**：OpenCode 1.4.7 可能对 part 还有其他必填字段（如 `version`、`agent` 等未在当前 smoke error 报出的）。如果 Task 4 smoke 报新错，回到 Task 1 按真实报错追加字段，成本低。
- **风险 2**：`time.start` 用 wall-clock ms 可能与 OpenCode 期望的单调时钟不一致。实测不行时改成 `System.currentTimeMillis()` 或 `clock.millis()`，再看。
- **回滚**：单 commit `git revert` 即可；回滚到 envelope 仅修完的状态（AI 发出 400，但不崩后端）。
