# Stage Window Redesign Mockups

评审图：

- [三案对比图](./stage-window-options.png)
- [推荐方案放大图](./stage-window-recommended.png)
- [HTML 原型](/home/wushengzhou/workspace/github/data-talk/client/public/prototypes/stage-window-data-tool.html)

方案摘要：

- `A Focused Sidebar`
  - 延续当前单层 sidebar 结构，改动最小
  - 风险最低，但工具层和资源层还是挤在一起
- `B Rail + Explorer`
  - 拆成 `mode rail + explorer + workspace`
  - 最适合当前 `SplitView -> StageWindow` 架构，Tab 层级也更清楚
- `C Stacked Context`
  - 更偏画布式工作台，适合以后报表 / dashboard / AI 工作流
  - 对当前 SQL/ER 场景来说变化偏大

当前推荐：

- 先走 `B Rail + Explorer`
- 保留现有 store / session / tab 模型，只重做视觉层级和容器组织
- 后续如果要加 `report` / `dashboard`，这套结构也还能继续扩

HTML 原型说明：

- 原型文件放在 `client/public/prototypes/stage-window-data-tool.html`
- 在当前前端环境中可直接通过 `/prototypes/stage-window-data-tool.html` 访问
- SQL `analytics.public.events` 场景已改为单卡片内嵌结果区：编辑器在上、`Result Grid` 在容器底部，中间用分割线隔开
- 原型控制区支持 `2 Sets / 1 Set / 0 Set`
  - `2 Sets`：显示多结果集切换条
  - `1 Set`：只显示一个结果集按钮和一个结果表
  - `0 Set`：整个结果区隐藏，只保留 SQL 编辑器
- 结构映射当前真实组件边界：
  - `SplitView`：左 chat / 右 stage
  - `StageWindow`：titlebar + stage body
  - `StageSidebar`：rail + explorer + tool row + resource browser
  - `StageTabBar`：SQL / ER / Bang Query / Empty State 切换
  - `StageTabContent`：QueryEditorTab / ER Canvas / BangQueryTab 的高保真静态原型
