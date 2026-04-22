import { create } from 'zustand'
import type {
  ResolvedDataContext,
  SqlExecuteResponse,
  SqlExecuteResultItem,
  SqlRiskBlocked,
} from '@/services/api/sql'

export type SqlWorkbenchExecuteStatus = 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'

export type TabContextOverride = {
  connectionId: string
  connectionName?: string | null
  database?: string | null
  schema?: string | null
  source: 'user_toolbar' | 'user_schema_panel' | 'ai_action' | 'api' | 'open_payload'
  setAt: number
}

export type HistoryEntry = {
  id: string
  at: number
  sql: string
  status: 'ok' | 'error' | 'risk_blocked'
  resultCount?: number
  elapsedMs?: number
  resultKinds?: SqlExecuteResultItem['kind'][]
  errorSummary?: string
}

export type SqlWorkbenchTabState = {
  sqlText: string
  source: 'ai' | 'user'
  executeStatus: SqlWorkbenchExecuteStatus
  results: SqlExecuteResultItem[]
  activeResultId: string | null
  resolvedContext: ResolvedDataContext | null
  contextNotice: string | null
  risk: SqlRiskBlocked | null
  errorMessage: string | null
  override: TabContextOverride | null
  history: HistoryEntry[]
  savedSqlText: string
  limit: 10 | 100 | 1000 | null
  cursor: { line: number; column: number }
}

type EnsureTabInput = Partial<Pick<SqlWorkbenchTabState, 'sqlText' | 'source'>>

type SqlWorkbenchState = {
  tabsById: Record<string, SqlWorkbenchTabState>
  ensureTab: (tabId: string, initial?: EnsureTabInput) => void
  setSqlText: (tabId: string, sqlText: string) => void
  setActiveResult: (tabId: string, resultId: string | null) => void
  setRunning: (tabId: string) => void
  applyExecuteSuccess: (tabId: string, response: SqlExecuteResponse) => void
  setRiskBlocked: (tabId: string, risk: SqlRiskBlocked) => void
  setError: (tabId: string, message: string) => void
  setTabContext: (tabId: string, ctx: Omit<TabContextOverride, 'setAt'>) => void
  resetTabContext: (tabId: string) => void
  appendHistoryEntry: (tabId: string, entry: HistoryEntry) => void
  clearHistory: (tabId: string) => void
  markSaved: (tabId: string) => void
  setLimit: (tabId: string, limit: 10 | 100 | 1000 | null) => void
  setCursor: (tabId: string, line: number, column: number) => void
  cleanupTabs: (activeTabIds: string[]) => void
}

function createDefaultTabState(initial?: EnsureTabInput): SqlWorkbenchTabState {
  const initialSqlText = initial?.sqlText ?? ''
  return {
    sqlText: initialSqlText,
    source: initial?.source ?? 'user',
    executeStatus: 'idle',
    results: [],
    activeResultId: null,
    resolvedContext: null,
    contextNotice: null,
    risk: null,
    errorMessage: null,
    override: null,
    history: [],
    savedSqlText: initialSqlText,
    limit: 100,
    cursor: { line: 1, column: 1 },
  }
}

function ensureTabState(
  tabsById: Record<string, SqlWorkbenchTabState>,
  tabId: string,
  initial?: EnsureTabInput,
) {
  return tabsById[tabId] ?? createDefaultTabState(initial)
}

export const useSqlWorkbenchStore = create<SqlWorkbenchState>((set) => ({
  tabsById: {},

  ensureTab: (tabId, initial) => set((state) => {
    if (state.tabsById[tabId]) return state
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: createDefaultTabState(initial),
      },
    }
  }),

  setSqlText: (tabId, sqlText) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        sqlText,
      },
    },
  })),

  setActiveResult: (tabId, resultId) => set((state) => {
    const tabState = ensureTabState(state.tabsById, tabId)
    const hasResult = resultId == null || tabState.results.some((item) => item.resultId === resultId)
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...tabState,
          activeResultId: hasResult ? resultId : tabState.activeResultId,
        },
      },
    }
  }),

  setRunning: (tabId) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        executeStatus: 'running',
        risk: null,
        errorMessage: null,
      },
    },
  })),

  applyExecuteSuccess: (tabId, response) => set((state) => {
    const previous = ensureTabState(state.tabsById, tabId)
    const activeResultId = response.results[0]?.resultId ?? null
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...previous,
          executeStatus: 'success',
          results: response.results,
          activeResultId,
          resolvedContext: response.resolvedContext,
          contextNotice: response.contextNotice,
          risk: null,
          errorMessage: null,
        },
      },
    }
  }),

  setRiskBlocked: (tabId, risk) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        executeStatus: 'risk_blocked',
        risk,
        errorMessage: null,
      },
    },
  })),

  setError: (tabId, message) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        executeStatus: 'error',
        risk: null,
        errorMessage: message,
      },
    },
  })),

  setTabContext: (tabId, ctx) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        override: {
          ...ctx,
          setAt: Date.now(),
        },
      },
    },
  })),

  resetTabContext: (tabId) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        override: null,
      },
    },
  })),

  appendHistoryEntry: (tabId, entry) => set((state) => {
    const tabState = ensureTabState(state.tabsById, tabId)
    const history = tabState.history.length >= 50
      ? [...tabState.history.slice(1), entry]
      : [...tabState.history, entry]
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...tabState,
          history,
        },
      },
    }
  }),

  clearHistory: (tabId) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        history: [],
      },
    },
  })),

  markSaved: (tabId) => set((state) => {
    const tabState = ensureTabState(state.tabsById, tabId)
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...tabState,
          savedSqlText: tabState.sqlText,
        },
      },
    }
  }),

  setLimit: (tabId, limit) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        limit,
      },
    },
  })),

  setCursor: (tabId, line, column) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        cursor: { line, column },
      },
    },
  })),

  cleanupTabs: (activeTabIds) => set((state) => {
    const activeSet = new Set(activeTabIds)
    const nextTabsById = Object.fromEntries(
      Object.entries(state.tabsById).filter(([tabId]) => activeSet.has(tabId)),
    ) as Record<string, SqlWorkbenchTabState>
    if (Object.keys(nextTabsById).length === Object.keys(state.tabsById).length) {
      return state
    }
    return { tabsById: nextTabsById }
  }),
}))
