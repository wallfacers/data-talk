import type { ActionDef, ExecResult, JsonPatchOp, PatchCapability, PatchResult, UIObject } from '@/services/ui-router'
import { useDashboardTabsStore } from '../stores/dashboard-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { generateUuid } from '@/lib/uuid'
import type { Dashboard } from '../schema'
import { promoteDashboard, updateDashboard } from '../services/dashboard-api'
import { translateMessage, type MessageKey } from '@/i18n/messages'
import { getCurrentLanguage } from '@/stores/ui-settings-store'

function t(key: MessageKey, values?: Record<string, string | number>): string {
  return translateMessage(getCurrentLanguage(), key, values)
}

const PATCH_CAPABILITIES: PatchCapability[] = [
  { pathPattern: '/title', ops: ['replace'] },
  { pathPattern: '/description', ops: ['replace'] },
  { pathPattern: '/defaultConnectionId', ops: ['replace'] },
  { pathPattern: '/widgets/-', ops: ['add'] },
  { pathPattern: '/widgets[id=<id>]', ops: ['replace', 'remove'] },
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
    schemaVersion: 3,
    id: `dash_${generateUuid().replace(/-/g, '')}`,
    title,
    theme: 'industry-default',
    renderer: 'bezel',
    parameters: [],
    widgets: [],
    layout: { engine: 'free', template: 'grid-equal' },
    version: 1,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }
}

export class DashboardAdapter implements UIObject {
  type = 'dashboard'
  objectId: string
  tabId: string
  title = t('dashboard.defaultTitle')
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

  async patch(ops: JsonPatchOp[], _reason?: string): Promise<PatchResult> {
    const tab = useDashboardTabsStore.getState().tabs.get(this.tabId)
    if (!tab) return { status: 'error', message: t('dashboard.error.tabNotFound') }

    const currentVersion = tab.dashboard.version
    const snapshot = tab.dashboard

    try {
      useDashboardTabsStore.getState().applyPatchOps(this.tabId, ops)

      const updatedTab = useDashboardTabsStore.getState().tabs.get(this.tabId)
      if (!updatedTab) return { status: 'error', message: t('dashboard.error.tabNotFound') }

      const result = await updateDashboard(updatedTab.dashboard.id, updatedTab.dashboard, currentVersion)
      if (!result) {
        useDashboardTabsStore.getState().hydrateTab(this.tabId, snapshot)
        return { status: 'error', message: 'Update failed' }
      }

      const store = useDashboardTabsStore.getState()
      if (result.changes && Array.isArray(result.changes) && result.changes.length > 0) {
        store.setPendingChanges(this.tabId, result.changes as Array<{ widgetId: string; baseOption: Record<string, unknown>; html?: string }>)
      } else if (result.html) {
        store.consumePendingChanges(this.tabId)
        store.bumpReloadKey(this.tabId)
      }

      return {
        status: 'applied',
        message: t('dashboard.patch.applied', { count: ops.length }),
        newVersion: result.version,
      }
    } catch (error) {
      return { status: 'error', message: (error as Error).message }
    }
  }

  async exec(action: string, params?: unknown): Promise<ExecResult> {
    switch (action) {
      case 'create': {
        const input = (params ?? {}) as { title?: string }
        const title = input.title ?? t('dashboard.untitled')
        const dashboard = buildEmptyDashboard(title)

        const result = await promoteDashboard(dashboard)
        if (!result) {
          return { success: false, error: t('dashboard.error.promotionFailed') }
        }

        const tabId = `dashboard_${generateUuid()}`
        dashboard.id = result.id
        dashboard.version = result.version

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
        return { success: true, data: { status: 'deferred' } }
      }

      default:
        return { success: false, error: t('dashboard.error.unknownAction', { action }) }
    }
  }
}
