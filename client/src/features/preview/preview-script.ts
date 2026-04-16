// 预览模式的"假事件流"。通过 buildEventSink 把事件喂给真实的 stores，
// 从而复用生产组件渲染（TableArtifact / ChartArtifact / ToolPartRenderer 等）。

import { buildEventSink } from '@/services/channel/use-channel'
import type { StreamEvent } from '@/services/channel/types'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { useActionRegistryStore } from '@/stores/action-registry-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import type { ActionDescriptor } from '@/features/actions/registry'
import { generateUuid } from '@/lib/uuid'
import {
  MOCK_ACTION_DESCRIPTIONS,
  MOCK_ACTION_IDS,
  MOCK_CONNECTION_ID,
  MOCK_GREEN_PROMPT,
  MOCK_SESSION_ID,
  MOCK_TABLE_COLUMNS,
  MOCK_TABLE_ROWS,
  MOCK_USER_PROMPT,
  makeEchartsOption,
} from './preview-mock-data'

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
const uid = (prefix: string) => `${prefix}-${generateUuid().slice(0, 8)}`
const evt = (event: string, data: unknown): StreamEvent => ({ id: 0, event, data })

// ---- 种子/清理 ----------------------------------------------------------

export function seedPreview() {
  // 预置一条"虚拟连接"，让依赖 activeConnectionId 的 UI 不抱怨
  useConnectionStore.setState({
    activeConnectionId: MOCK_CONNECTION_ID,
    connections: [
      {
        id: MOCK_CONNECTION_ID,
        name: 'preview-postgres',
        dbType: 'postgres',
        host: 'localhost',
        port: 5432,
        database: 'preview',
        username: 'demo',
      },
    ],
  })

  // 预置 action descriptors（正常由 GET /api/actions 注入）
  const descs: Record<string, ActionDescriptor> = {}
  for (const id of MOCK_ACTION_IDS) {
    descs[id] = {
      id,
      executor: 'SERVER',
      description: MOCK_ACTION_DESCRIPTIONS[id] ?? id,
      inputSchema: {},
      outputSchema: {},
      produces: [],
      sideEffects: [],
      requiresConnection: true,
      timeoutMs: 30_000,
    }
  }
  useActionRegistryStore.setState({ descriptors: descs })

  // 强制进入 HERO：清空缓存，openSession(hasEverSent=false)
  useSessionStore.setState({
    activeSessionId: null,
    modeBySession: new Map(),
    hasEverSentBySession: new Map(),
    pendingPrompt: null,
    pendingConnectionPrompt: false,
  })
  useSessionStore.getState().openSession(MOCK_SESSION_ID, false)

  // 清掉可能残留的 parts/artifacts/timeline
  useChatPartsStore.getState().clearSession(MOCK_SESSION_ID)
  useOntologyStore.getState().clearSession(MOCK_SESSION_ID)
  useTimelineStore.getState().clear(MOCK_SESSION_ID)
}

export function teardownPreview() {
  useChatPartsStore.getState().clearSession(MOCK_SESSION_ID)
  useOntologyStore.getState().clearSession(MOCK_SESSION_ID)
  useTimelineStore.getState().clear(MOCK_SESSION_ID)
  // 把 activeSessionId 清掉，回到 NOSESS，避免污染后续真实会话
  useSessionStore.setState({
    activeSessionId: null,
    modeBySession: new Map(),
    hasEverSentBySession: new Map(),
    pendingPrompt: null,
    pendingConnectionPrompt: false,
  })
  // 不动 connection/action-registry，避免其他页面需要 re-fetch
}

export function resetPreview() {
  seedPreview()
}

// ---- 事件辅助 ------------------------------------------------------------

function sink() {
  // 第二个参数是 ChannelClient，仅在 action.invoke 事件里用到；脚本不发 action.invoke
  return buildEventSink(MOCK_SESSION_ID, null)
}

function emit(event: string, data: unknown) {
  sink()(evt(event, data))
}

function emitUserText(messageId: string, text: string) {
  emit('message.created', {
    message: { id: messageId, role: 'user', createdAt: Date.now() },
  })
  emit('message.part.created', {
    part: {
      type: 'text',
      id: uid('p'),
      sessionID: MOCK_SESSION_ID,
      messageID: messageId,
      text,
      metadata: {},
    },
  })
}

function emitAssistantHeader(messageId: string) {
  emit('message.created', {
    message: { id: messageId, role: 'assistant', createdAt: Date.now() },
  })
}

async function emitReasoning(messageId: string, chunks: string[]) {
  const partId = uid('p-reason')
  emit('message.part.created', {
    part: {
      type: 'reasoning',
      id: partId,
      sessionID: MOCK_SESSION_ID,
      messageID: messageId,
      text: '',
      metadata: {},
    },
  })
  for (const chunk of chunks) {
    await sleep(220)
    emit('message.part.delta', { partId, field: 'text', delta: chunk })
  }
}

async function emitTool(messageId: string, tool: string, durationMs: number) {
  const partId = uid('p-tool')
  emit('message.part.created', {
    part: {
      type: 'tool',
      id: partId,
      sessionID: MOCK_SESSION_ID,
      messageID: messageId,
      tool,
      state: { status: 'running' },
      metadata: {},
    },
  })
  await sleep(durationMs)
  emit('message.part.updated', {
    part: {
      type: 'tool',
      id: partId,
      sessionID: MOCK_SESSION_ID,
      messageID: messageId,
      tool,
      state: { status: 'completed' },
      metadata: {},
    },
  })
}

function emitTableArtifact(id: string) {
  emit('ontology.updated', {
    objectType: 'datatalk.artifact',
    id,
    op: 'upsert',
    patch: {
      version: 1,
      kind: 'table',
      columns: MOCK_TABLE_COLUMNS,
      preview: MOCK_TABLE_ROWS,
      rowCount: MOCK_TABLE_ROWS.length,
      durationMs: 82,
    },
  })
}

function emitChartArtifact(id: string, color: string, supersedesId?: string) {
  emit('ontology.updated', {
    objectType: 'datatalk.artifact',
    id,
    op: 'upsert',
    patch: {
      version: 1,
      kind: 'chart',
      supersedesId,
      echartsOption: makeEchartsOption(color),
    },
  })
}

function emitAssistantText(messageId: string, text: string) {
  emit('message.part.created', {
    part: {
      type: 'text',
      id: uid('p'),
      sessionID: MOCK_SESSION_ID,
      messageID: messageId,
      text,
      metadata: {},
    },
  })
}

// ---- 主脚本 --------------------------------------------------------------

export async function playMainScript(userText?: string) {
  const text = (userText && userText.trim()) || MOCK_USER_PROMPT
  const userMsgId = uid('m-user')
  const aiMsgId = uid('m-ai')

  // 1) 用户消息先落入左列
  emitUserText(userMsgId, text)

  // 2) 立刻切 SPLIT（触发 FLIP + clip-path）
  await sleep(80)
  useSessionStore.getState().enterSplit(MOCK_SESSION_ID)
  await sleep(320)

  // 3) assistant 开启推理
  emitAssistantHeader(aiMsgId)
  await emitReasoning(aiMsgId, [
    '先看一下 users 表',
    '的结构…',
    '\n发现 created_at 字段，',
    '按天分桶 COUNT(*) 就可以。',
  ])

  // 4) read_schema 工具
  await sleep(180)
  await emitTool(aiMsgId, 'datatalk.read_schema', 520)

  // 5) execute_sql：工具先挂起，随后广播表 artifact，再完成
  await sleep(220)
  const sqlPartId = uid('p-tool')
  emit('message.part.created', {
    part: {
      type: 'tool',
      id: sqlPartId,
      sessionID: MOCK_SESSION_ID,
      messageID: aiMsgId,
      tool: 'datatalk.execute_sql',
      state: { status: 'running' },
      metadata: {},
    },
  })
  await sleep(400)
  emitTableArtifact('art-table-1')
  await sleep(120)
  emit('message.part.updated', {
    part: {
      type: 'tool',
      id: sqlPartId,
      sessionID: MOCK_SESSION_ID,
      messageID: aiMsgId,
      tool: 'datatalk.execute_sql',
      state: { status: 'completed' },
      metadata: {},
    },
  })

  // 6) render_chart：同样 running → artifact → completed
  await sleep(280)
  const chartPartId = uid('p-tool')
  emit('message.part.created', {
    part: {
      type: 'tool',
      id: chartPartId,
      sessionID: MOCK_SESSION_ID,
      messageID: aiMsgId,
      tool: 'datatalk.render_chart',
      state: { status: 'running' },
      metadata: {},
    },
  })
  await sleep(380)
  emitChartArtifact('art-chart-1', '#3b82f6')
  await sleep(120)
  emit('message.part.updated', {
    part: {
      type: 'tool',
      id: chartPartId,
      sessionID: MOCK_SESSION_ID,
      messageID: aiMsgId,
      tool: 'datatalk.render_chart',
      state: { status: 'completed' },
      metadata: {},
    },
  })

  // 7) assistant 总结
  await sleep(200)
  emitAssistantText(
    aiMsgId,
    '\n过去 7 天注册趋势上行，周末峰值 107。若要换色或切换成柱状图，直接再追问即可。',
  )
}

// ---- 追问脚本："换成绿色" -----------------------------------------------

export async function playGreenFollowup(userText?: string) {
  const text = (userText && userText.trim()) || MOCK_GREEN_PROMPT
  const userMsgId = uid('m-user')
  const aiMsgId = uid('m-ai')

  emitUserText(userMsgId, text)
  await sleep(220)

  emitAssistantHeader(aiMsgId)
  await emitReasoning(aiMsgId, ['好的，重新渲染，', '只改 color 字段。'])

  const partId = uid('p-tool')
  emit('message.part.created', {
    part: {
      type: 'tool',
      id: partId,
      sessionID: MOCK_SESSION_ID,
      messageID: aiMsgId,
      tool: 'datatalk.render_chart',
      state: { status: 'running' },
      metadata: {},
    },
  })
  await sleep(380)

  // 关键：新 artifact 带 supersedesId → ArtifactCanvas 跟随 active, ChartArtifact 触发 180ms 变色
  emitChartArtifact('art-chart-2', '#22c55e', 'art-chart-1')
  await sleep(120)

  emit('message.part.updated', {
    part: {
      type: 'tool',
      id: partId,
      sessionID: MOCK_SESSION_ID,
      messageID: aiMsgId,
      tool: 'datatalk.render_chart',
      state: { status: 'completed' },
      metadata: {},
    },
  })

  await sleep(200)
  emitAssistantText(aiMsgId, '\n已替换为绿色。旧的蓝色版本已在时间线中褪色。')
}
