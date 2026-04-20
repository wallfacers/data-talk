# 质量标准

DataTalk 项目的质量评分维度和目标。

## 质量维度

| 维度 | 当前评分 | 目标 | 说明 |
|------|---------|------|------|
| 测试覆盖 | B | A | domain/application 单元测试覆盖率 > 80%；adapter 集成测试覆盖关键路径 |
| 类型安全 | A | A | Java sealed interface + TypeScript strict mode，编译期最大化捕获错误 |
| API 一致性 | C | B | 前后端类型定义手动同步，需引入自动生成机制 |
| 文档完整性 | B | A | 架构文档和计划已版本化；API 文档待补充 |
| 错误处理 | C | B | 缺少全局异常处理器，错误响应格式不统一 |
| 安全性 | B | A | 密码 AES-GCM 加密；SQL 注入防护待加强 |

## 测试质量标准

### 必须

- 每个 public 方法至少一个正向测试和一个负向测试
- 集成测试覆盖所有 API 端点的 happy path
- E2E smoke test 覆盖核心用户流程
- 后端完整门禁命令统一为 `cd server && mvn clean verify`
- `data-talk-adapter/src/test/**/**/*IT.java` 必须由 Maven failsafe 在 `verify` 阶段自动执行，不允许只留在 IDE / 手工命令里

### 应该

- 边界条件测试（空输入、超大输入、并发）
- 性能基准（关键路径响应时间 < 200ms）

### 可选

- 模糊测试（JSON 解析、SQL 生成）
- 压力测试（并发会话数）

## 代码质量规则

- 单个方法不超过 20 行（public）或 30 行（private）
- 单个类不超过 300 行
- 循环依赖零容忍
- 所有 sealed interface 必须有 exhaustive switch
- 所有 Magic Number 必须提取为常量或配置

## 评分更新

每个 Plan 完成后，评审各维度评分并更新本文件。评分变化记入 git 历史。
