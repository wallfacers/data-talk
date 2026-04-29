import type { ExecResult, JsonPatchOp, PatchResult, UIObject } from '@/services/ui-router'

export class ErInspectorAdapter implements UIObject {
  type = 'er_inspector'
  objectId: string
  tabId: string
  title = 'ER Inspector'

  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId
    this.tabId = tabId
  }

  read(_mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    return null
  }

  patch(_ops: JsonPatchOp[], _reason?: string): PatchResult {
    return { status: 'error', message: 'ErInspectorAdapter.patch not yet implemented (Task 27)' }
  }

  exec(_action: string, _params?: unknown): ExecResult {
    return { success: false, error: 'ErInspectorAdapter.exec not yet implemented (Task 27)' }
  }
}
