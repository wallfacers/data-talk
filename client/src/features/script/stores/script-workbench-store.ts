import { create } from 'zustand'

export type ScriptLanguage = 'python' | 'javascript'
export type ScriptExecuteStatus = 'idle' | 'running' | 'success' | 'error'

export interface EnvInfo {
  python: string | null
  node: string | null
}

export interface ConsoleOutputEntry {
  channel: 'stdout' | 'stderr'
  text: string
  timestamp: number
}

export interface ScriptWorkbenchTabState {
  scriptText: string
  version: number
  language: ScriptLanguage
  executeStatus: ScriptExecuteStatus
  consoleOutput: ConsoleOutputEntry[]
  connectionId: string | null
  currentRunId: string | null
  currentToken: string | null
  envInfo: EnvInfo | null
  envChecked: boolean
  errorMessage: string | null
}

type ScriptWorkbenchState = {
  tabsById: Record<string, ScriptWorkbenchTabState>
  envInfo: EnvInfo | null
  envChecked: boolean

  ensureTab: (tabId: string, initial?: Partial<Pick<ScriptWorkbenchTabState, 'scriptText' | 'language' | 'connectionId'>>) => void
  hydrateTab: (tabId: string, snapshot: { scriptText?: string; language?: ScriptLanguage; connectionId?: string | null }) => void
  setScriptText: (tabId: string, text: string) => void
  replaceScriptText: (tabId: string, text: string, baseVersion: number) => { ok: boolean; version: number }
  setLanguage: (tabId: string, language: ScriptLanguage) => void
  setConnectionId: (tabId: string, connectionId: string | null) => void
  setRunning: (tabId: string, runId: string, token: string) => void
  appendConsoleOutput: (tabId: string, entry: ConsoleOutputEntry) => void
  setCompleted: (tabId: string, exitCode: number) => void
  setError: (tabId: string, message: string) => void
  clearConsole: (tabId: string) => void
  setEnvInfo: (info: EnvInfo) => void
  cleanupTabs: (activeTabIds: string[]) => void
}

function createDefaultTabState(initial?: Partial<Pick<ScriptWorkbenchTabState, 'scriptText' | 'language' | 'connectionId'>>): ScriptWorkbenchTabState {
  return {
    scriptText: initial?.scriptText ?? '',
    version: 1,
    language: initial?.language ?? 'python',
    executeStatus: 'idle',
    consoleOutput: [],
    connectionId: initial?.connectionId ?? null,
    currentRunId: null,
    currentToken: null,
    envInfo: null,
    envChecked: false,
    errorMessage: null,
  }
}

export const useScriptWorkbenchStore = create<ScriptWorkbenchState>((set, get) => ({
  tabsById: {},
  envInfo: null,
  envChecked: false,

  ensureTab: (tabId, initial) =>
    set((s) => {
      if (s.tabsById[tabId]) return s
      return { tabsById: { ...s.tabsById, [tabId]: createDefaultTabState(initial) } }
    }),

  hydrateTab: (tabId, snapshot) =>
    set((s) => {
      const existing = s.tabsById[tabId]
      if (existing && existing.version > 1) return s
      const tab = existing ?? createDefaultTabState()
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: {
            ...tab,
            scriptText: snapshot.scriptText ?? tab.scriptText,
            language: snapshot.language ?? tab.language,
            connectionId: snapshot.connectionId ?? tab.connectionId,
          },
        },
      }
    }),

  setScriptText: (tabId, text) =>
    set((s) => {
      const tab = s.tabsById[tabId]
      if (!tab) return s
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: { ...tab, scriptText: text, version: tab.version + 1 },
        },
      }
    }),

  replaceScriptText: (tabId, text, baseVersion) => {
    const tab = get().tabsById[tabId]
    if (!tab) return { ok: false, version: 0 }
    if (tab.version !== baseVersion) {
      return { ok: false, version: tab.version }
    }
    set((s) => ({
      tabsById: {
        ...s.tabsById,
        [tabId]: { ...s.tabsById[tabId], scriptText: text, version: s.tabsById[tabId].version + 1 },
      },
    }))
    return { ok: true, version: baseVersion + 1 }
  },

  setLanguage: (tabId, language) =>
    set((s) => {
      const tab = s.tabsById[tabId]
      if (!tab) return s
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: { ...tab, language },
        },
      }
    }),

  setConnectionId: (tabId, connectionId) =>
    set((s) => {
      const tab = s.tabsById[tabId]
      if (!tab) return s
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: { ...tab, connectionId },
        },
      }
    }),

  setRunning: (tabId, runId, token) =>
    set((s) => {
      const tab = s.tabsById[tabId]
      if (!tab) return s
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: {
            ...tab,
            executeStatus: 'running',
            currentRunId: runId,
            currentToken: token,
            consoleOutput: [],
            errorMessage: null,
          },
        },
      }
    }),

  appendConsoleOutput: (tabId, entry) =>
    set((s) => {
      const tab = s.tabsById[tabId]
      if (!tab) return s
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: { ...tab, consoleOutput: [...tab.consoleOutput, entry] },
        },
      }
    }),

  setCompleted: (tabId, exitCode) =>
    set((s) => {
      const tab = s.tabsById[tabId]
      if (!tab) return s
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: {
            ...tab,
            executeStatus: exitCode === 0 ? 'success' : 'error',
            currentRunId: null,
            currentToken: null,
            errorMessage: exitCode !== 0 ? `Process exited with code ${exitCode}` : null,
          },
        },
      }
    }),

  setError: (tabId, message) =>
    set((s) => {
      const tab = s.tabsById[tabId]
      if (!tab) return s
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: {
            ...tab,
            executeStatus: 'error',
            currentRunId: null,
            currentToken: null,
            errorMessage: message,
          },
        },
      }
    }),

  clearConsole: (tabId) =>
    set((s) => {
      const tab = s.tabsById[tabId]
      if (!tab) return s
      return {
        tabsById: {
          ...s.tabsById,
          [tabId]: { ...tab, consoleOutput: [] },
        },
      }
    }),

  setEnvInfo: (info) =>
    set(() => ({ envInfo: info, envChecked: true })),

  cleanupTabs: (activeTabIds) =>
    set((s) => {
      const next: Record<string, ScriptWorkbenchTabState> = {}
      for (const id of activeTabIds) {
        if (s.tabsById[id]) next[id] = s.tabsById[id]
      }
      return { tabsById: next }
    }),
}))
