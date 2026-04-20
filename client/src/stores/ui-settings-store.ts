import { create } from 'zustand'
import { DEFAULT_LANGUAGE, resolveLanguage, type LanguageOption } from '@/i18n/messages'

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
  language: LanguageOption
  setSplitResizable: (v: boolean) => void
  setLanguage: (v: LanguageOption) => void
}

function detectLanguage(): LanguageOption {
  if (typeof navigator === 'undefined') return DEFAULT_LANGUAGE
  return resolveLanguage(navigator.language)
}

export const useUISettingsStore = create<UISettingsState>((set) => ({
  splitResizable: (load().splitResizable as boolean) ?? false,
  language: (() => {
    const saved = load().language as string | undefined
    return saved ? resolveLanguage(saved) : detectLanguage()
  })(),
  setSplitResizable: (v) => {
    persist({ splitResizable: v })
    set({ splitResizable: v })
  },
  setLanguage: (language) => {
    persist({ language })
    set({ language })
  },
}))

export function getCurrentLanguage(): LanguageOption {
  return useUISettingsStore.getState().language
}
