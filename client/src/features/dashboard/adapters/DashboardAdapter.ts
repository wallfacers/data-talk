import type { ActionDef, ExecResult, JsonPatchOp, PatchCapability, PatchResult, UIObject } from '@/services/ui-router'
import { useDashboardTabsStore } from '../stores/dashboard-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { generateUuid } from '@/lib/uuid'
import type { Dashboard } from '../schema'

const PATCH_CAPABILITIES: PatchCapability[] = [
  { pathPattern: '/title', ops: ['replace'] },
  { pathPattern: '/description', ops: ['replace'] },
  { pathPattern: '/defaultConnectionId', ops: ['replace'] },
  { pathPattern: '/widgets/-', ops: ['add'] },
  { pathPattern: '/widgets[id=<id>]', ops: ['replace', 'remove'] },
  { pathPattern: '/widgets[id=<id>]/position', ops: ['replace'] },
  { pathPattern: '/widgets[id=<id>]/options', ops: ['replace'] },
  { pathPattern: '/widgets[id=<id>]/query', ops: ['replace'] },
  { pathPattern: '/parameters/-', ops: ['add'] },
  { pathPattern: '/parameters[id=<id>]', ops: ['replace', 'remove'] },
  { pathPattern: '/layout', ops: ['replace'] },
]

const ACTIONS: ActionDef[] = [
  {
    name: 'create',
    description: 'Create a new empty dashboard and open it as a tab',
    paramsSchema: {
      type: 'object',
      properties: {
        title: { type: 'string' },
      },
    },
  },
  {
    name: 'archive',
    description: 'Archive the dashboard (P1: deferred)',
    paramsSchema: { type: 'object', properties: {} },
  },
]

function buildEmptyDashboard(title: string): Dashboard {
  return {
    schemaVersion: 1,
    id: `dash_${generateUuid()}`,
    title,
    parameters: [],
    widgets: [],
    layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
    version: 1,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }
}

export class DashboardAdapter implements UIObject {
  type = 'dashboard'
  objectId: string
  tabId: string
  title = 'Dashboard'
  patchCapabilities = PATCH_CAPABILITIES

  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId
    this.tabId = tabId
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const tab = useDashboardTabsStore.getState().tabs.get(this.tabId)
    const payload = tab?.dashboard ?? null

    if (mode === 'state') return payload
    if (mode === 'schema') return { type: this.type, patchCapabilities: this.patchCapabilities }
    if (mode === 'actions') return ACTIONS
    return {
      state: payload,
      schema: { type: this.type, patchCapabilities: this.patchCapabilities },
      actions: ACTIONS,
    }
  }

  patch(ops: JsonPatchOp[], _reason?: string): PatchResult {
    try {
      useDashboardTabsStore.getState().applyPatchOps(this.tabId, ops)
      const tab = useDashboardTabsStore.getState().tabs.get(this.tabId)
      return {
        status: 'applied',
        message: `applied ${ops.length} op(s)`,
        newVersion: (tab?.dashboard.version ?? 0) + 1,
      }
    } catch (error) {
      return { status: 'error', message: (error as Error).message }
    }
  }

  async exec(action: string, params?: unknown): Promise<ExecResult> {
    switch (action) {
      case 'create': {
        const input = (params ?? {}) as { title?: string }
        const title = input.title ?? 'Untitled Dashboard'
        const dashboard = buildEmptyDashboard(title)
        const tabId = `dashboard_${generateUuid()}`

        useDashboardTabsStore.getState().hydrateTab(tabId, dashboard)
        useStageStore.getState().openTab({
          tabId,
          type: 'dashboard',
          title,
          payload: dashboard,
          createdAt: Date.now(),
        })
        useStageStore.getState().openStage()

        return { success: true, data: { tabId, dashboardId: dashboard.id } }
      }

      case 'archive': {
        // P1: archive is deferred — just acknowledge
        return { success: true, data: { status: 'deferred' } }
      }

      default:
        return { success: false, error: `unknown action: ${action}` }
    }
  }
}
