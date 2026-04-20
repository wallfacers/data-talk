import { create } from 'zustand'

export type Theme = 'light' | 'dark' | 'system'

const KEY = 'theme-settings'

function load(): Partial<Record<string, unknown>> {
  try {
    const v = localStorage.getItem(KEY)
    if (v) return JSON.parse(v) as Record<string, unknown>
  } catch {
    /* ignore */
  }
  return {}
}

function persist(patch: Record<string, unknown>) {
  try {
    const prev = load()
    localStorage.setItem(KEY, JSON.stringify({ ...prev, ...patch }))
  } catch {
    /* ignore */
  }
}

type ThemeState = {
  theme: Theme
  setTheme: (theme: Theme) => void
}

export const useThemeStore = create<ThemeState>((set) => ({
  theme: (load().theme as Theme) ?? 'system',
  setTheme: (theme) => {
    persist({ theme })
    set({ theme })
  },
}))
