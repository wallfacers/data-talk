# Chat Tool System Arg Folding Design

## 背景

聊天区工具调用卡片会把工具输入参数直接显示在 trigger 区。这里既包括 `__dtOpenCodeSessionId`、`__dtCallId`、`__dtBridgeNonce` 这类系统 bridge 参数，也包括 `object=workspace`、`action=open` 这种对普通用户意义有限的执行参数。即使已经解决了水平溢出，这些内容仍然会污染聊天消息的主阅读路径。

本次问题不是“值太长”，而是“参数本身不该默认展示”。用户明确要求：trigger 只保留工具名称，所有参数都放到展开内容里。

## 目标

1. 工具调用 trigger 默认只展示工具名称，不展示任何输入参数
2. 完整 `key=value` 仍可在工具卡片展开内容中查看，避免丢失调试信息
3. 不改变工具卡片的展开/收起模型、风险样式和双主题语义

## 非目标

- 不重做 `BasicTool` 的整体视觉样式
- 不引入新的交互入口或独立“更多”按钮
- 不修改后端 action schema 或 tool payload
- 不新增独立“参数”按钮或额外交互状态

## 设计约束

来源：[`client/DESIGN.md`](../../client/DESIGN.md)

- 聊天工具卡片继续使用既有 `message.toolSurface` 语义，不新增视觉层级
- 聊天区保持 `comfortable` 密度，修复重点是信息层级而不是增加装饰
- 文本继续沿用现有 UI/mono 字体体系
- 双主题语义保持不变，不新增 theme-specific 分支

## 方案选择

采用“trigger 只显示工具名称，所有参数完整下沉到展开内容”的方案。

不采用的方案：

- trigger 继续显示一部分可读参数，例如 `object=workspace` / `action=open`：这些参数本质上仍然是执行细节，会继续干扰主阅读路径
- trigger 只显示“3 个参数”：过于抽象，用户无法知道卡片携带了哪些上下文
- 继续在 trigger 中显示完整值但做截断：仍然把噪声留在主阅读路径，只是缩短了噪声

## 详细行为

### 1. Trigger 展示规则

- trigger 中不显示任何输入参数
- trigger 只保留工具名称，以及已有 subtitle / 状态 / 展开箭头等通用外壳信息
- 无论参数是系统 bridge 参数还是普通短参数，都不再进入 trigger args

### 2. 展开内容规则

当存在输入参数时，`GenericTool` 的 body 需要在输出区之前补一段 input detail：

- 内容为完整 `key=value`
- 所有参数都进入这段 detail，不再在 trigger 里重复展示
- 这样用户平时不被执行细节打断，但在展开卡片后仍可查看完整上下文

### 3. 组件责任

- `BasicTool` 继续只负责通用 trigger/body 壳层，不承担“系统参数”判断
- `GenericTool` 负责输入摘要策略：trigger 不再输出任何 args，所有输入参数直接下沉到 body detail

这样可以把策略保持在 generic renderer 层，避免把“隐藏参数”的规则污染到所有工具卡片基类。

## 测试策略

新增前端回归测试，覆盖：

1. trigger 中只显示工具名称，不显示任何参数
2. 展开工具卡片后能看到完整 `key=value`
3. 系统参数与普通短参数都下沉到展开内容
