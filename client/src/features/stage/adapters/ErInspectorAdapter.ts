import type { ActionDef, ExecResult, JsonPatchOp, PatchCapability, PatchResult, UIObject } from '@/services/ui-router'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'

const PATCH_CAPABILITIES: PatchCapability[] = [
  { pathPattern: '/selection', ops: ['replace'] },
  { pathPattern: '/neighborDepth', ops: ['replace'] },
  { pathPattern: '/positions', ops: ['replace'] },
  { pathPattern: '/positions/<table>', ops: ['replace', 'remove'] },
  { pathPattern: '/collapsed', ops: ['replace'] },
  { pathPattern: '/virtualRelations', ops: ['replace'] },
  { pathPattern: '/virtualRelations/-', ops: ['add'] },
  { pathPattern: '/virtualRelations[id=<id>]', ops: ['replace', 'remove'] },
  { pathPattern: '/notes', ops: ['replace'] },
  { pathPattern: '/notes/<table>', ops: ['replace', 'remove'] },
  { pathPattern: '/viewport', ops: ['replace'] },
]

const ACTIONS: ActionDef[] = [
  {
    name: 'refresh',
    description: 'Re-read tables from the connection',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'auto_layout',
    description: 'Recompute node positions via dagre',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'fit_view',
    description: 'Reset viewport to fit all nodes',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'add_neighbors',
    description: 'Pull direct FK neighbors of a table into selection',
    paramsSchema: {
      type: 'object',
      required: ['table'],
      properties: { table: { type: 'string' } },
    },
  },
  {
    name: 'fork_to_designer',
    description: 'Create a designer tab seeded from this inspector',
    paramsSchema: { type: 'object', properties: {} },
  },
]

export class ErInspectorAdapter implements UIObject {
  type = 'er_inspector'
  patchCapabilities = PATCH_CAPABILITIES
  objectId: string
  tabId: string
  title = 'ER Inspector'

  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId
    this.tabId = tabId
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const payload = useErTabsStore.getState().inspectors.get(this.tabId) ?? null
    if (mode === 'state') return payload
    if (mode === 'schema') {
      return {
        type: this.type,
        patchCapabilities: this.patchCapabilities,
      }
    }
    if (mode === 'actions') return ACTIONS
    return {
      state: payload,
      schema: { type: this.type, patchCapabilities: this.patchCapabilities },
      actions: ACTIONS,
    }
  }

  patch(ops: JsonPatchOp[], _reason?: string): PatchResult {
    try {
      const { newVersion } = useErTabsStore.getState().applyInspectorPatch(this.tabId, ops)
      return { status: 'applied', message: `applied ${ops.length} op(s); new version ${newVersion}` }
    } catch (error) {
      return { status: 'error', message: (error as Error).message }
    }
  }

  exec(_action: string, _params?: unknown): ExecResult {
    return { success: false, error: 'ErInspectorAdapter.exec not yet implemented (Task 27)' }
  }
}
