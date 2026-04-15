import { create } from 'zustand'
import type { WorkspaceTab } from './types'

type WorkspaceState = {
  tabs: WorkspaceTab[]
  activeTabId: string | null
  openTab: (tab: WorkspaceTab) => void
  closeTab: (id: string) => void
  setActive: (id: string) => void
}

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  tabs: [],
  activeTabId: null,
  openTab: (tab) =>
    set((s) => {
      const exists = s.tabs.some((t) => t.id === tab.id)
      const tabs = exists ? s.tabs : [...s.tabs, tab]
      if (s.activeTabId === tab.id) {
        return { tabs }
      }
      return { tabs, activeTabId: tab.id }
    }),
  closeTab: (id) =>
    set((s) => {
      const tabs = s.tabs.filter((t) => t.id !== id)
      const activeTabId =
        s.activeTabId === id ? (tabs.at(-1)?.id ?? null) : s.activeTabId
      return { tabs, activeTabId }
    }),
  setActive: (id) => set({ activeTabId: id }),
}))
