import { create } from 'zustand'
import { applyPatch } from '@/services/ui-router/jsonPatch'
import type { Dashboard } from '../schema'
import type { JsonPatchOp } from '@/services/ui-router/types'

export interface TabState {
  dashboard: Dashboard
  dirtySinceVersion: number | null
}

export interface DashboardTabsState {
  tabs: Map<string, TabState>

  hydrateTab: (tabId: string, dashboard: Dashboard) => void
  applyPatchOps: (tabId: string, ops: JsonPatchOp[]) => void
  removeTab: (tabId: string) => void
}

export const useDashboardTabsStore = create<DashboardTabsState>((set, get) => ({
  tabs: new Map(),

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
}))
