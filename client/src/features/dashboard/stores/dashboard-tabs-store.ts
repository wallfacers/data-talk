import { create } from 'zustand'
import { applyPatch } from '@/services/ui-router/jsonPatch'
import type { Dashboard } from '../schema'
import type { JsonPatchOp } from '@/services/ui-router/types'

export interface WidgetChange {
  widgetId: string
  baseOption: Record<string, unknown>
  html?: string
}

export interface TabState {
  dashboard: Dashboard
  dirtySinceVersion: number | null
}

export interface DashboardTabsState {
  tabs: Map<string, TabState>
  pendingChanges: Map<string, WidgetChange[]>
  reloadKeys: Map<string, number>

  hydrateTab: (tabId: string, dashboard: Dashboard) => void
  applyPatchOps: (tabId: string, ops: JsonPatchOp[]) => void
  removeTab: (tabId: string) => void
  setPendingChanges: (tabId: string, changes: WidgetChange[]) => void
  consumePendingChanges: (tabId: string) => WidgetChange[] | undefined
  bumpReloadKey: (tabId: string) => void
}

export const useDashboardTabsStore = create<DashboardTabsState>((set, get) => ({
  tabs: new Map(),
  pendingChanges: new Map(),
  reloadKeys: new Map(),

  hydrateTab(tabId, dashboard) {
    set((state) => {
      const tabs = new Map(state.tabs)
      tabs.set(tabId, { dashboard, dirtySinceVersion: null })
      return { tabs }
    })
  },

  applyPatchOps(tabId, ops) {
    const tab = get().tabs.get(tabId)
    if (!tab) throw new Error(`tab not found: ${tabId}`)

    const patched = applyPatch(
      tab.dashboard as unknown as Record<string, unknown>,
      ops,
    ) as unknown as Dashboard

    set((state) => {
      const tabs = new Map(state.tabs)
      tabs.set(tabId, {
        dashboard: patched,
        dirtySinceVersion: tab.dirtySinceVersion ?? tab.dashboard.version,
      })
      return { tabs }
    })
  },

  removeTab(tabId) {
    set((state) => {
      const tabs = new Map(state.tabs)
      tabs.delete(tabId)
      return { tabs }
    })
  },

  setPendingChanges(tabId, changes) {
    set((state) => {
      const pendingChanges = new Map(state.pendingChanges)
      pendingChanges.set(tabId, changes)
      return { pendingChanges }
    })
  },

  consumePendingChanges(tabId) {
    const changes = get().pendingChanges.get(tabId)
    set((state) => {
      const pendingChanges = new Map(state.pendingChanges)
      pendingChanges.delete(tabId)
      return { pendingChanges }
    })
    return changes
  },

  bumpReloadKey(tabId) {
    set((state) => {
      const reloadKeys = new Map(state.reloadKeys)
      const next = (reloadKeys.get(tabId) ?? 0) + 1
      reloadKeys.set(tabId, next)
      return { reloadKeys }
    })
  },
}))
