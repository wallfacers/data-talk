## Requirements

### Requirement: Python REST API 分页遍历配方

data-collection skill SHALL 包含 Python 分页遍历 REST API 的配方，覆盖 page number、offset、cursor 三种分页模式。

#### Scenario: 配方文件存在且可被 AI 引用

- **WHEN** AI 在 data-collection skill 上下文中查找分页获取数据的代码模板
- **THEN** 存在 `recipes/python-rest-pagination.md` 文件
- **AND** 包含：requests GET 循环、page number 递增模式、offset/limit 模式、cursor/next 模式
- **AND** 每种模式包含完整的可运行代码（含错误处理和速率限制说明）

### Requirement: Python HTML 解析配方

data-collection skill SHALL 包含 Python BeautifulSoup HTML 解析配方。

#### Scenario: 配方文件存在且可被 AI 引用

- **WHEN** AI 需要抓取网页并解析 HTML 表格/列表数据
- **THEN** 存在 `recipes/python-html-parse.md` 文件
- **AND** 包含：BeautifulSoup 安装说明、HTML 表格提取、列表提取、CSS 选择器用法
- **AND** 包含解析后通过 `POST /api/script-data/write` 写入数据库的完整流程

### Requirement: Python CSV 文件解析配方

data-collection skill SHALL 包含 Python CSV 文件解析并写入数据库的配方。

#### Scenario: 配方文件存在且可被 AI 引用

- **WHEN** AI 需要读取 CSV 文件并导入数据库
- **THEN** 存在 `recipes/python-csv-parse.md` 文件
- **AND** 包含：csv.DictReader 逐行读取、类型推断、批量写入（每 1000 行）
- **AND** 包含流式写入模式（`POST /api/script-data/batch` + `batch/close`）

### Requirement: Python 认证模式配方

data-collection skill SHALL 包含常见 API 认证模式的配方。

#### Scenario: 配方文件存在且可被 AI 引用

- **WHEN** AI 需要访问需要认证的 REST API
- **THEN** 存在 `recipes/python-auth-patterns.md` 文件
- **AND** 包含：Bearer Token、API Key (header/query)、HTTP Basic Auth
- **AND** 每种模式包含从环境变量读取凭证的安全实践
- **AND** 明确禁止在脚本中硬编码凭证

### Requirement: Python 大文件流式下载配方

data-collection skill SHALL 包含大文件流式下载并分批写入的配方。

#### Scenario: 配方文件存在且可被 AI 引用

- **WHEN** AI 需要下载大文件（>50MB JSON/CSV）并通过流式写入导入数据库
- **THEN** 存在 `recipes/python-stream-download.md` 文件
- **AND** 包含：`requests.get(stream=True)`、`iter_lines()` 解析、分批 `POST /api/script-data/batch`
- **AND** 包含进度日志输出和错误处理

### Requirement: 配方文件精简约束

data-collection skill 的每个配方文件 SHALL 保持简洁，控制在 50-80 行。

#### Scenario: 配方文件长度

- **WHEN** 配方文件被读取
- **THEN** 每个配方文件总行数 ≤ 80 行
- **AND** 包含完整可运行的代码模板，不含冗余注释
- **AND** 包含环境变量说明（DT_BACKEND_URL, DT_SCRIPT_TOKEN, DT_CONNECTION_ID）
