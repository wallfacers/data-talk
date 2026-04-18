import { create } from 'zustand'

const KEY = 'ui-settings'

function load(): Partial<Record<string, unknown>> {
  try {
    const v = localStorage.getItem(KEY)
    if (v) return JSON.parse(v) as Record<string, unknown>
  } catch { /* ignore */ }
  return {}
}

function persist(patch: Record<string, unknown>) {
  try {
    const prev = load()
    localStorage.setItem(KEY, JSON.stringify({ ...prev, ...patch }))
  } catch { /* ignore */ }
}

type UISettingsState = {
  splitResizable: boolean
  setSplitResizable: (v: boolean) => void
}

export const useUISettingsStore = create<UISettingsState>((set) => ({
  splitResizable: (load().splitResizable as boolean) ?? false,
  setSplitResizable: (v) => {
    persist({ splitResizable: v })
    set({ splitResizable: v })
  },
}))
