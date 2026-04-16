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

export type Part = {
  type: string
  id: string
  sessionID: string
  messageID: string
  [k: string]: unknown
}
