## ADDED Requirements

### Requirement: pattern-catalog.yaml SHALL 是编译器与文档的唯一真源

`server/data-talk-application/src/main/resources/dashboard/pattern-catalog.yaml` SHALL 是人手维护的唯一契约,同时驱动:运行时 Java 编译器(加载为不可变 `PatternCatalog` 模型,强制执行)与构建期 AI 文档(由脚本生成 `.md`)。编译器与 AI 文档 SHALL NOT 各自维护重复的 pattern/chartType 定义。

#### Scenario: 编译器从 YAML 加载 pattern
- **GIVEN** `pattern-catalog.yaml` 声明 `patterns: { ecommerce.gmv-trend: { renderKind: chart, ... } }`
- **WHEN** 编译器编译一个 `patternId: 'ecommerce.gmv-trend'` 的 widget
- **THEN** 编译器按 YAML 的 `renderKind: chart` 处理(init ECharts),不依赖任何硬编码 pattern 表

#### Scenario: 文档由 YAML 生成
- **WHEN** 运行 `scripts/generate-bezel-docs.sh`
- **THEN** 生成的 `references/patterns-catalog.md` 内容 MUST 与 `pattern-catalog.yaml` 的 patterns 一致(同名、同 supportedChartTypes)

### Requirement: 启动期 SHALL 校验 catalog 自洽

应用启动加载 `pattern-catalog.yaml` 时 SHALL 校验:每个 pattern 的 `defaultChartType` ∈ 其 `supportedChartTypes` ⊆ 全局 `chartTypes`;`defaultColorScheme` ∈ `supportedColorSchemes`;每个 pattern 的 `renderKind` ∈ `{chart, html}`;每个 template 的 slot `id` 唯一。任一不满足 SHALL 启动失败并报告违例条目。

#### Scenario: defaultChartType 不在 supported 列表启动失败
- **GIVEN** 某 pattern `defaultChartType: pie` 但 `supportedChartTypes: [bar, line]`
- **WHEN** 应用启动加载 catalog
- **THEN** 启动失败,错误指明该 pattern 的 defaultChartType 不在 supportedChartTypes

#### Scenario: 合法 catalog 启动成功
- **GIVEN** 一份所有约束均满足的 catalog
- **WHEN** 启动
- **THEN** `PatternCatalog` 加载成功,无校验错误

### Requirement: CI SHALL 拦截 YAML 与生成文档的漂移

CI SHALL 执行 `generate-bezel-docs.sh` 后跑 `git diff --exit-code`;若生成产物与已提交 `.md` 不一致 SHALL 失败。`mvn package` SHALL NOT 重新生成文档(仅 dev/CI 运行生成)。

#### Scenario: YAML 改了但没重生成文档 CI 失败
- **GIVEN** 有人修改了 `pattern-catalog.yaml` 但未运行生成脚本、未提交更新后的 `.md`
- **WHEN** CI 跑 `generate-bezel-docs.sh && git diff --exit-code`
- **THEN** `git diff` 检出差异,CI 失败

#### Scenario: YAML 与文档一致时 CI 通过
- **GIVEN** YAML 与已提交 `.md` 同步
- **WHEN** CI 跑生成 + diff 校验
- **THEN** 无 diff,CI 通过
