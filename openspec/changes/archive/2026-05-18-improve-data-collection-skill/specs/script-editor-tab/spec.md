## MODIFIED Requirements

### Requirement: xterm.js 控制台面板

script_editor tab SHALL 内嵌 xterm.js 控制台面板，显示脚本运行输出。控制台 MUST 使用 semantic token 适配暗/亮主题，不得硬编码颜色值。

#### Scenario: 控制台布局

- **WHEN** script_editor tab 被渲染
- **THEN** 上半部分为 Monaco 编辑器，下半部分为可折叠的 xterm.js 控制台面板
- **AND** 面板高度可通过拖拽分隔条调整
- **AND** 控制台背景使用对应主题的 semantic token 颜色值

#### Scenario: 暗色主题

- **WHEN** app 处于 dark 主题
- **THEN** xterm.js theme 设置为：
  - `background`: `#1a1a19`（neutral.900）
  - `foreground`: `#f1f1ef`（neutral.100）
  - `cursor`: `#b9b9b7`（neutral.400）
  - `selectionBackground`: `#34322d`（neutral.800）
- **AND** ANSI Black → `#34322d`（neutral.800），ANSI Red → `#ef4444`（red.500），ANSI Green → `#22c55e`（green.500），ANSI Yellow → `#f59e0b`（amber.500），ANSI Blue → `#3b82f6`（cobalt.500），ANSI Magenta → 品红色，ANSI Cyan → `#0ea5e9`（sky.500），ANSI White → `#f1f1ef`（neutral.100）
- **AND** Bright 变体使用对应颜色的浅色调

#### Scenario: 亮色主题

- **WHEN** app 处于 light 主题
- **THEN** xterm.js theme 设置为：
  - `background`: `#fcfcfb`（neutral.25）
  - `foreground`: `#34322d`（neutral.800）
  - `cursor`: `#858481`（neutral.500）
  - `selectionBackground`: `#f1f1ef`（neutral.100）
- **AND** ANSI Black → `#d1d1cd`（neutral.300），ANSI Red → `#b91c1c`（red.700），ANSI Green → `#15803d`（green.700），ANSI Yellow → `#b45309`（amber.700），ANSI Blue → `#1d4ed8`（cobalt.700），ANSI Magenta → 品红色，ANSI Cyan → `#0369a1`（sky.700），ANSI White → `#34322d`（neutral.800）
- **AND** Bright 变体使用对应颜色的暗色调

#### Scenario: 主题切换无闪烁

- **WHEN** app 在 dark/light 主题间切换
- **THEN** xterm.js theme 通过 `term.options.theme = newTheme` 更新
- **AND** 不使用 `term.dispose()` + 重建
- **AND** 终端缓冲内容保留，不丢失
- **AND** 切换无明显闪烁

#### Scenario: 控制台交互

- **WHEN** 脚本运行中输出内容
- **THEN** xterm.js 自动滚动到底部
- **AND** 用户可向上滚动查看历史输出（scrollback ≥ 10000 行）
- **AND** 用户可选中文字并复制
