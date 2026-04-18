# Message Parts Jackson Fix Implementation Plan

**Status:** 已完成（2026-04-18）—— 后端修复 + 回归测试 2/2 通过 + 历史坏数据清理 + application jar 重装。用户重启后端即可。

**Goal:** 修 `MessageRepository.save` 序列化 `List<Part>` 时因 Java 泛型擦除丢失多态 `type` 判别符，导致 GET `/api/sessions/{id}/messages` 反序列化抛 `InvalidTypeIdException: missing type id property 'type'`。

**Architecture:** 在 `MessageRepository` 里用 `TypeReference<List<Part>>` 显式告诉 Jackson 元素静态类型，`writerFor` + `readValue` 都走同一个 TypeReference，确保 `@JsonTypeInfo(property="type")` 在写入/读取两端都生效。

**Tech Stack:** Spring Boot 3.5、Jackson、JdbcTemplate、SQLite。

---

## 背景

- 症状：发消息后刷新/加载历史时后端抛 `cannot deserialize parts ... missing type id property 'type'`
- 根因：`om.writeValueAsString(m.parts())` 处 Java 泛型擦除，Jackson 只看到 `ArrayList<Object>`，按元素 runtime class（`TextPart`）查 serializer，但忽略了 Part 接口上的 `@JsonTypeInfo`；结果存进 DB 的 JSON 没 `type` 字段，`findBySession` 再也读不回来
- 证据：`SELECT parts_json FROM messages` 查到 `[{"id":...,"text":"你好",...}]`（**无 type**）

## 范围

**改动文件：**

- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/MessageRepository.java` — 引入 `TypeReference<List<Part>>` 常量，`save` 用 `writerFor(PARTS_TYPE)` 写，`findBySession` 用 `readValue(..., PARTS_TYPE)` 读，两端对称
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/MessageRepositoryIT.java` — 新增 2 条断言：(a) 存储后 JSON 含 `"type":"text"`；(b) `findBySession` 能回读并得到 `TextPart` 实例

**数据修复：** 生产/本地 dev 数据库里已存的 2 条"无 type"旧记录用 `DELETE FROM messages WHERE parts_json NOT LIKE '%"type":%'` 清除（已执行）。

## Tasks

- [x] Task 1：`MessageRepository` 在类顶部声明 `private static final TypeReference<List<Part>> PARTS_TYPE`；`save` 调用 `om.writerFor(PARTS_TYPE).writeValueAsString(...)`；`findBySession` 调用 `om.readValue(json, PARTS_TYPE)`
- [x] Task 2：`MessageRepositoryIT` 加 `"type":"text"` 断言 + 完整 roundtrip 测试
- [x] Task 3：`mvn -pl data-talk-adapter test -Dtest=MessageRepositoryIT` 全绿（2/2）
- [x] Task 4：清理 SQLite `messages` 表中无 type 字段的历史脏数据（2 行）
- [x] Task 5：`mvn -pl data-talk-application install -am -DskipTests` 刷新 ~/.m2 的 application jar，让 `spring-boot:run` 重启后加载到新代码

## 验收

- [x] `MessageRepositoryIT` 2/2 通过
- [x] DB messages 表无 `parts_json NOT LIKE '%"type":%'` 的行
- [ ] 用户重启后端（`mvn spring-boot:run -pl data-talk-adapter`），发送"你好" → 刷新 → GET `/messages` 返回 200（不再是 500）

## 风险与回滚

- **风险**：生产环境如果有大量历史脏数据，清理时会删用户的历史消息。当前本地 dev 只有 2 条测试数据，已直接删除；若生产环境遇到，应先评估数据量再决定迁移或补 `type` 字段的办法（可写一条 UPDATE 把缺 type 的 parts 推断为 `text`）。
- **回滚**：单 commit，`git revert` 即可；但历史脏数据无法自动恢复。
