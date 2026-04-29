// UI Object Protocol — types
// Ported from /home/wushengzhou/workspace/github/open-db-studio/src/mcp/ui/types.ts

export interface JsonPatchOp {
  op: 'add' | 'remove' | 'replace'
  path: string
  value?: unknown
  baseVersion?: number | 'auto'
  expectedVersion?: number | 'auto'
}

export interface UIRequest {
  tool: 'ui_read' | 'ui_patch' | 'ui_exec'
  object: string
  target: string
  payload: unknown
}

export interface UIResponse {
  data?: unknown
  error?: string
  status?: 'applied' | 'pending_confirm'
  confirm_id?: string
}

export interface PatchCapability {
  pathPattern: string
  ops: ('replace' | 'add' | 'remove')[]
  description?: string
  addressableBy?: string[]
}

export interface PatchResult {
  status: 'applied' | 'pending_confirm' | 'error'
  confirm_id?: string
  preview?: JsonPatchOp[]
  message?: string
  // Maps each `add` op path that was assigned an ID by the store to the new ID.
  // Stable handles for follow-up ops on the same item — see ER designer/inspector
  // store assignedIds semantics.
  assignedIds?: Record<string, string>
  newVersion?: number
}

export interface ExecResult {
  success: boolean
  data?: unknown
  error?: string
}

export interface JsonSchema {
  type: 'object'
  properties: Record<string, unknown>
  required?: string[]
}

export interface ActionDef {
  name: string
  description: string
  paramsSchema: JsonSchema
}

export interface UIObjectInfo {
  objectId: string
  type: string
  title: string
  connectionId?: string
  database?: string
}

export interface UIObject {
  type: string
  objectId: string
  title: string
  connectionId?: string
  database?: string
  tabId?: string
  patchCapabilities?: PatchCapability[]

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown
  patch(ops: JsonPatchOp[], reason?: string): PatchResult | Promise<PatchResult>
  exec(action: string, params?: unknown): ExecResult | Promise<ExecResult>
}
