# Chart Artifact Inline Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 当 AI 调用 `datatalk_render_chart` 工具生成图表时，图表直接内联展示在对话消息流中，用户无需点击眼睛图标跳转到 Workspace 才能看到。

**Architecture:** `ArtifactCreated` 组件（工具调用行渲染器）订阅 `useOntologyStore`，当 `kind=chart` 且 artifact 落库后（`ontology.updated` SSE 先于工具完成到达），直接在工具行下方渲染 `ChartRenderer` 内联预览。眼睛图标仍保留，用于在 Workspace Tab 中打开大图。

**Tech Stack:** React 19, Zustand (useOntologyStore), `ChartRenderer` (echarts-for-react), Tailwind CSS 语义 token, Vitest + Testing Library

**Design Inputs (client/DESIGN.md):**
- `border-[var(--dt-border-subtle)]` 图表外框
- `bg-[var(--dt-bg-panel)]` 工具行背景（继承 toolSurface）
- Density: `comfortable` for chat messages（16px padding, 无压缩布局）
- Motion: 无折叠动画（YAGNI），遵守 `prefers-reduced-motion`
- 不使用 raw primitive 颜色，仅用语义 CSS 变量

---

## 文件清单

| 文件 | 变更 |
|------|------|
| `client/src/features/chat/components/tools/renderers/artifact-created.tsx` | 修改：添加 ontology store 订阅 + 内联图表渲染 |
| `client/src/features/chat/components/tools/renderers/__tests__/artifact-created.test.tsx` | 新建：组件测试 |

---

## Task 1: 写失败测试

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/__tests__/artifact-created.test.tsx`

- [x] **Step 1.1: 创建测试文件，验证测试在无实现时失败**

```tsx
// client/src/features/chat/components/tools/renderers/__tests__/artifact-created.test.tsx
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ArtifactCreated } from '../artifact-created'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'

vi.mock('@/features/chat/components/markdown/chart-renderer', () => ({
  ChartRenderer: ({ option }: { option: Record<string, unknown> }) => (
    <div data-testid="chart-renderer-mock" data-option={JSON.stringify(option)} />
  ),
}))

vi.mock('@/stores/stage-store', () => ({
  useStageStore: Object.assign(
    () => ({}),
    { getState: () => ({ openArtifactPreviewTab: vi.fn(), openStage: vi.fn() }) },
  ),
}))

vi.mock('@/stores/ui-settings-store', () => ({
  getCurrentLanguage: () => 'zh-CN',
}))

vi.mock('@/i18n/messages', () => ({
  translateMessage: (_lang: string, key: string) => key,
}))

vi.mock('@/features/actions/registry', () => ({
  getRenderers: () => undefined,
}))

const CHART_OPTION = { series: [{ type: 'bar', data: [1, 2, 3] }] }

const CHART_ARTIFACT = {
  id: 'art-1',
  version: 1,
  kind: 'chart' as const,
  payload: { kind: 'chart', echartsOption: CHART_OPTION },
}

function makePart(overrides: Record<string, unknown> = {}) {
  return {
    id: 'part-1',
    sessionID: 'sess-1',
    messageID: 'msg-1',
    type: 'tool' as const,
    tool: 'datatalk_render_chart',
    state: {
      status: 'completed' as const,
      output: { artifactId: 'art-1', version: 1 },
      ...overrides,
    },
  }
}

const DESCRIPTOR = {
  id: 'datatalk.render_chart',
  executor: 'SERVER',
  description: '',
  inputSchema: {},
  outputSchema: {},
  produces: [],
  sideEffects: [],
  requiresConnection: false,
  timeoutMs: 5000,
}

beforeEach(() => {
  useSessionStore.setState({ activeSessionId: 'sess-1' } as any)
  useOntologyStore.setState({ artifactsBySession: new Map() } as any)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ArtifactCreated — inline chart preview', () => {
  it('renders ChartRenderer inline when chart artifact is in ontology store', () => {
    useOntologyStore.setState({
      artifactsBySession: new Map([
        ['sess-1', new Map([['art-1', CHART_ARTIFACT]])],
      ]),
    } as any)

    render(<ArtifactCreated part={makePart() as any} descriptor={DESCRIPTOR as any} />)

    expect(screen.getByTestId('chart-renderer-mock')).toBeInTheDocument()
  })

  it('does NOT render ChartRenderer when artifact is absent from store', () => {
    // store is empty (default beforeEach)
    render(<ArtifactCreated part={makePart() as any} descriptor={DESCRIPTOR as any} />)

    expect(screen.queryByTestId('chart-renderer-mock')).not.toBeInTheDocument()
  })

  it('does NOT render ChartRenderer for non-chart kind (table)', () => {
    useOntologyStore.setState({
      artifactsBySession: new Map([
        ['sess-1', new Map([['art-1', { ...CHART_ARTIFACT, kind: 'table' }]])],
      ]),
    } as any)

    const part = makePart()
    ;(part as any).tool = 'datatalk_build_table'

    render(<ArtifactCreated part={part as any} descriptor={DESCRIPTOR as any} />)

    expect(screen.queryByTestId('chart-renderer-mock')).not.toBeInTheDocument()
  })

  it('does NOT render ChartRenderer when artifact payload has no echartsOption', () => {
    useOntologyStore.setState({
      artifactsBySession: new Map([
        ['sess-1', new Map([['art-1', { ...CHART_ARTIFACT, payload: { kind: 'chart' } }]])],
      ]),
    } as any)

    render(<ArtifactCreated part={makePart() as any} descriptor={DESCRIPTOR as any} />)

    expect(screen.queryByTestId('chart-renderer-mock')).not.toBeInTheDocument()
  })

  it('passes echartsOption to ChartRenderer', () => {
    useOntologyStore.setState({
      artifactsBySession: new Map([
        ['sess-1', new Map([['art-1', CHART_ARTIFACT]])],
      ]),
    } as any)

    render(<ArtifactCreated part={makePart() as any} descriptor={DESCRIPTOR as any} />)

    const renderer = screen.getByTestId('chart-renderer-mock')
    expect(JSON.parse(renderer.getAttribute('data-option')!)).toEqual(CHART_OPTION)
  })

  it('still renders the tool row (BasicTool) alongside the inline chart', () => {
    useOntologyStore.setState({
      artifactsBySession: new Map([
        ['sess-1', new Map([['art-1', CHART_ARTIFACT]])],
      ]),
    } as any)

    render(<ArtifactCreated part={makePart() as any} descriptor={DESCRIPTOR as any} />)

    expect(screen.getByTestId('chart-renderer-mock')).toBeInTheDocument()
    // BasicTool renders the tool title
    expect(screen.getByText(/chart artifact/)).toBeInTheDocument()
  })
})
```

- [x] **Step 1.2: 运行测试，确认全部失败（ChartRenderer 未渲染）**

```bash
cd /home/wallfacers/project/data-talk/client
npx vitest run src/features/chat/components/tools/renderers/__tests__/artifact-created.test.tsx
```

Expected: 5 tests FAIL（"Unable to find an element by: [data-testid="chart-renderer-mock"]" 等）

---

## Task 2: 实现内联图表渲染

**Files:**
- Modify: `client/src/features/chat/components/tools/renderers/artifact-created.tsx`

- [x] **Step 2.1: 修改 artifact-created.tsx，添加 ontology store 订阅和内联图表**

> 实际实现相比计划有额外增强：i18n 翻译、string JSON output 解析、Tooltip 包裹眼睛图标、fallback title 本地化。

完整替换文件内容：

```tsx
import { useMemo } from 'react'
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { ChartRenderer } from '@/features/chat/components/markdown/chart-renderer'
import { EyeIcon } from 'lucide-react'

const KIND_ICONS: Record<string, string> = { table: '📊', chart: '📈', erd: '🔗' }

export function ArtifactCreated(props: ToolRendererProps) {
  const { part } = props
  const language = getCurrentLanguage()
  const output = part.state.output as { kind?: string; title?: string; artifactId?: string } | undefined
  const kind = (() => {
    if (output?.kind) return output.kind
    const metaKind = part.state.metadata?.kind as string | undefined
    if (metaKind) return metaKind
    if (part.tool === 'datatalk_render_chart') return 'chart'
    if (part.tool === 'datatalk_layout_erd') return 'erd'
    return 'table'
  })()
  const title =
    output?.title ??
    (part.state.metadata?.title as string | undefined) ??
    `${kind} artifact`
  const artifactId = output?.artifactId ?? null
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const sessionId = activeSessionId?.trim().length
    ? activeSessionId
    : part.sessionID.trim().length > 0
      ? part.sessionID
      : null

  const artifact = useOntologyStore((s) => {
    if (!sessionId || !artifactId || kind !== 'chart') return null
    return s.artifactsBySession.get(sessionId)?.get(artifactId) ?? null
  })

  const echartsOption = useMemo(() => {
    const payload = artifact?.payload as { echartsOption?: unknown } | undefined
    const opt = payload?.echartsOption
    if (!opt || typeof opt !== 'object') return null
    return opt as Record<string, unknown>
  }, [artifact])

  const openArtifact = () => {
    if (!sessionId) return
    if (artifactId && part.state.status === 'completed') {
      useStageStore.getState().openArtifactPreviewTab(sessionId, artifactId, title)
    } else {
      useStageStore.getState().openStage(sessionId)
    }
  }

  return (
    <>
      <BasicTool
        icon="artifact"
        risk="L1"
        status={part.state.status}
        trigger={{
          title: `${KIND_ICONS[kind] ?? '📦'} ${title}`,
          action: (
            <button
              type="button"
              aria-label={translateMessage(language, 'chat.openStage')}
              title={translateMessage(language, 'chat.viewInStage')}
              disabled={!sessionId || !artifactId || part.state.status !== 'completed'}
              onClick={(event) => {
                event.stopPropagation()
                openArtifact()
              }}
              className="inline-flex size-6 items-center justify-center rounded-md text-foreground hover:bg-muted hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
            >
              <EyeIcon className="size-3.5" />
            </button>
          ),
        }}
        hideDetails
      />
      {echartsOption && (
        <div className="mb-2 overflow-hidden rounded-lg border border-[var(--dt-border-subtle)]">
          <ChartRenderer option={echartsOption} />
        </div>
      )}
    </>
  )
}
```

- [x] **Step 2.2: 类型检查**

```bash
cd /home/wallfacers/project/data-talk/client
npx tsc --noEmit
```

Expected: 无错误

---

## Task 3: 运行测试 + commit

- [x] **Step 3.1: 运行新增测试，确认全部通过**

```bash
cd /home/wallfacers/project/data-talk/client
npx vitest run src/features/chat/components/tools/renderers/__tests__/artifact-created.test.tsx
```

Result: 6 tests PASS（比计划多 1 个）

- [x] **Step 3.2: 运行完整测试套件，确认无回归**

```bash
cd /home/wallfacers/project/data-talk/client
npx vitest run --reporter=verbose 2>&1 | tail -30
```

Expected: 全部通过，无新失败

- [x] **Step 3.3: commit** — 代码已实现但尚未 commit，待提交。

```bash
git add client/src/features/chat/components/tools/renderers/artifact-created.tsx \
        client/src/features/chat/components/tools/renderers/__tests__/artifact-created.test.tsx
git commit -m "feat(client): render chart artifact inline below tool row"
```

---

## Self-Review

**Spec coverage:**
- ✅ 图表内联展示（Task 2）
- ✅ 仅 chart kind 触发渲染（测试 case 3）
- ✅ artifact 未就绪时不渲染（测试 case 2）
- ✅ 眼睛图标仍保留（Task 2 代码中保留，额外加了 Tooltip）
- ✅ 语义 token（`var(--dt-border-subtle)`）
- ✅ 无 raw primitive 颜色

**实现差异（相对计划）：**
- i18n 翻译替代硬编码 `${kind} artifact`
- string JSON output 解析兼容（`typeof rawOutput === 'string'`）
- 眼睛图标包裹 `Tooltip` 组件
- 测试 6 条（计划 5 条），多 1 条 i18n mock 完善

**Placeholder scan:** 无 TBD / TODO

**Type consistency:** 
- `echartsOption: Record<string, unknown>` 在 `useMemo` 和 `ChartRenderer` 的 `option` prop 类型一致
- `artifact?.payload as { echartsOption?: unknown }` 与 ontology store 的 `payload: unknown` 一致

**Out of scope（后续迭代）：**
- 图表折叠/展开 toggle（YAGNI）
- 全屏展开按钮（YAGNI）
- 内联图表自身的独立 ErrorBoundary（`ToolErrorBoundary` 已兜底）
