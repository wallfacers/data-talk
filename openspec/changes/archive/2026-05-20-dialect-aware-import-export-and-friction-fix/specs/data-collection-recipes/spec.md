## ADDED Requirements

### Requirement: 脚本 SHALL NOT 直接连接目标数据库

`data-collection` skill 的 `SKILL.md` SHALL 在顶部"❗ DO NOT"段显式禁止 Python / Node.js 脚本通过任何直连库（`pymysql`, `mysql.connector`, `psycopg2`, `sqlite3`, `pyodbc`, `mysql2`, `pg`, `sequelize`, 等）连接目标数据库。脚本 SHALL 通过 `POST {DT_BACKEND_URL}/api/script-data/write` 或 `POST {DT_BACKEND_URL}/api/script-data/batch` API 写入数据，由后端代为执行 JDBC 写入。

#### Scenario: SKILL.md 顶部含 DO NOT 段

- **WHEN** 任何调用方读取 `server/data-talk-adapter/src/main/resources/skills/data-collection/SKILL.md`
- **THEN** 文件在 `# Data Collection Skill` 标题之后 / `## Tool Surface` 之前的章节包含一个明显的"DO NOT" / "❗ DO NOT"段
- **AND** 该段显式列出至少 4 种被禁止的直连库名称（`pymysql`, `mysql.connector`, `psycopg2`, `sqlite3`）
- **AND** 该段明确指明唯一支持的写入路径是 `POST {DT_BACKEND_URL}/api/script-data/write`

#### Scenario: 合同测试 DataCollectionSkillContractTest 防止文档退化

- **GIVEN** `DataCollectionSkillContractTest` 在 `cd server && mvn test` 中运行
- **WHEN** 测试加载 `data-collection/SKILL.md` 资源
- **THEN** 断言文件包含字符串 "DO NOT"
- **AND** 断言文件包含 "pymysql" 与 "psycopg2"（至少两个示例直连库）
- **AND** 断言文件包含 "/api/script-data/write"
- **AND** 任一断言失败 SHALL 中断 CI
