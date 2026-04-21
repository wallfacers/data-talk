import { create } from 'zustand'
import type {
  ResolvedDataContext,
  SqlExecuteResponse,
  SqlExecuteResultItem,
  SqlRiskBlocked,
} from '@/services/api/sql'

export type SqlWorkbenchExecuteStatus = 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'

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
  cleanupTabs: (activeTabIds: string[]) => void
}

function createDefaultTabState(initial?: EnsureTabInput): SqlWorkbenchTabState {
  return {
    sqlText: initial?.sqlText ?? '',
    source: initial?.source ?? 'user',
    executeStatus: 'idle',
    results: [],
    activeResultId: null,
    resolvedContext: null,
    contextNotice: null,
    risk: null,
    errorMessage: null,
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
