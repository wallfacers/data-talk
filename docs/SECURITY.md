# 安全指南

## 威胁模型

DataTalk 作为桌面应用，主要威胁面：

1. **用户数据库凭据泄露** — 连接密码在本地存储和传输中的安全
2. **SQL 注入** — AI 生成的 SQL 可能包含恶意内容
3. **OpenCode 通信安全** — 后端与 AI 服务间的数据传输
4. **本地数据安全** — SQLite 元数据库中的敏感信息

## 已实施措施

### 凭据管理

- 数据库密码通过 `SecretVault` 使用 AES-256-GCM 加密后存储
- `password_enc` 字段为 BLOB 类型，不可直接读取
- 加密密钥通过环境变量注入，不进入版本控制

### SQL 执行安全

- OpenCode 作为纯推理层，不直接连接数据库
- 所有 SQL 由后端 `SqlExecutionRepository` 代理执行
- 查询超时限制：`spring.jdbc.template.query-timeout=30`
- **DELETE 对话式确认**：AI 路径下 `datatalk_execute_sql` 检测到 `DELETE` 时不立即执行，返回 `requires_confirmation` + `confirmationId`，需用户在对话中明确确认后携 `confirmationId` 二次调用才执行（`ExecuteSqlAction`）
- **破坏性 DDL 拦截**：AI 路径下基于原始 SQL 关键词扫描拦截 `DROP` / `TRUNCATE` / `ALTER…DROP` / `GRANT` / `REVOKE` / `DENY` / `KILL` / `SHUTDOWN` / `PURGE` / `SET GLOBAL` / `INSERT OVERWRITE`，返回 `redirect_to_editor`，由用户在 query_editor 中手动确认执行（不依赖 `CalciteSqlRiskAnalyzer` 的 riskLevel，对 MySQL/PG/H2 的 DDL 其返回 null）

### 通信安全

- 桌面端 ↔ 后端：本地通信 (localhost)，Tauri IPC 沙箱隔离
- 后端 ↔ OpenCode：HTTP，生产环境应启用 TLS

## 待实施措施

| 措施 | 优先级 | 关联 Plan |
|------|--------|----------|
| 参数化查询优先：AI Prompt 中要求优先使用参数化 SQL | P0 | Plan B |
| OpenCode TLS 通信 | P1 | 部署配置 |
| CSP 头配置（Tauri WebView） | P2 | Plan C |
| 连接权限最小化提示：建议用户使用只读账号连接 | P2 | Plan C |

## 安全审计检查清单

新增代码时检查：

- [ ] 是否有硬编码密码/密钥/Token？
- [ ] SQL 拼接是否有注入风险？
- [ ] 用户输入是否经过校验？
- [ ] 敏感数据日志是否已脱敏？
- [ ] 新增依赖是否有已知漏洞？
