import { generateUuid } from '@/lib/uuid'

export type StreamEvent = {
  id: number
  event: string
  data: unknown
}

export type RpcRequest =
  | { jsonrpc: '2.0'; id: string; method: 'send_message';  params: { parts: unknown[] } }
  | { jsonrpc: '2.0'; id: string; method: 'action_result'; params: { callId: string; ok: boolean; output?: unknown; error?: unknown } }
  | { jsonrpc: '2.0'; id: string; method: 'abort';         params: Record<string, never> }
  | { jsonrpc: '2.0'; id: string; method: 'hello';         params: { clientRev: number; lastEventId?: number } }

export type PartTime = { start?: number; end?: number }

export type BasePart = {
  id: string
  sessionID: string
  messageID: string
  metadata?: Record<string, unknown>
}

export type TextPart = BasePart & {
  type: 'text'
  text: string
  time?: PartTime
  synthetic?: boolean
}

export type ReasoningPart = BasePart & {
  type: 'reasoning'
  text: string
  time?: PartTime
}

export type ToolState = {
  status: 'pending' | 'running' | 'completed' | 'error'
  input?: Record<string, any>
  output?: unknown
  metadata?: Record<string, any>
  error?: string
  title?: string
}

export type ToolPart = BasePart & {
  type: 'tool'
  tool: string
  state: ToolState
  callID?: string
}

export type StepStartPart = BasePart & { type: 'step-start' }
export type StepFinishPart = BasePart & {
  type: 'step-finish'
  reason?: string
  tokens?: Record<string, unknown>
  cost?: number
}

export type CompactionPart = BasePart & { type: 'compaction' }

export type FilePart = BasePart & {
  type: 'file'
  url?: string
  filename?: string
  source?: { text?: { start: number; end: number } }
}

export type AgentPart = BasePart & {
  type: 'agent'
  source?: { start: number; end: number }
}

export type Part =
  | TextPart | ReasoningPart | ToolPart
  | StepStartPart | StepFinishPart | CompactionPart
  | FilePart | AgentPart
  | (BasePart & { type: string; [k: string]: unknown })

export type MessageInfo = {
  id: string
  role: 'user' | 'assistant' | 'system'
  sessionID: string
  time: { created: number; completed?: number }
  providerID?: string
  modelID?: string
  tokens?: Record<string, unknown>
  parentID?: string
  agent?: string
  mode?: string
  path?: Record<string, unknown>
  error?: { name: string; data?: { message?: string } }
  finish?: string
  summary?: { diffs?: Array<{ file: string; before: string; after: string }> }

  __pending?: boolean
  __failed?: boolean
  __failReason?: string
  __retrying?: boolean
  __renderKey?: string
}

export function createTextPart(sessionId: string, text: string): TextPart {
  return {
    type: 'text',
    id: generateUuid(),
    sessionID: sessionId,
    messageID: '',
    text,
    metadata: {},
  }
}
