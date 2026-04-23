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

export type SqlWorkbenchSelection = {
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

export type SqlWorkbenchTextEdit = {
  range: SqlWorkbenchSelection
  text: string
}

export type SqlWorkbenchEditResult =
  | { ok: true; version: number; content: string }
  | { ok: false; code: 'version_conflict'; currentState: { version: number; content: string } }

export type SqlWorkbenchTabState = {
  sqlText: string
  version: number
  selection: SqlWorkbenchSelection | null
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
  replaceSqlText: (tabId: string, sqlText: string) => { version: number }
  applyTextEdits: (tabId: string, params: { baseVersion: number; edits: SqlWorkbenchTextEdit[] }) => SqlWorkbenchEditResult
  setSelection: (tabId: string, selection: SqlWorkbenchSelection | null) => void
  setActiveResult: (tabId: string, resultId: string | null) => void
  closeResult: (tabId: string, resultId: string) => void
  closeOtherResults: (tabId: string, resultId: string) => void
  closeAllResults: (tabId: string) => void
  setRunning: (tabId: string) => void
  applyExecuteSuccess: (tabId: string, response: SqlExecuteResponse) => void
  setRiskBlocked: (tabId: string, risk: SqlRiskBlocked) => void
  setError: (tabId: string, message: string, result?: SqlExecuteResultItem | null) => void
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
    version: 1,
    selection: null,
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

function requireTabState(
  tabsById: Record<string, SqlWorkbenchTabState>,
  tabId: string,
) {
  const tabState = tabsById[tabId]
  if (!tabState) {
    throw new Error(`Unknown sql workbench tab: ${tabId}`)
  }
  return tabState
}

function promoteActiveResultId(results: SqlExecuteResultItem[], preferredId: string | null) {
  if (results.length === 0) return null
  if (preferredId && results.some((item) => item.resultId === preferredId)) {
    return preferredId
  }
  return results[0]?.resultId ?? null
}

function resolveOffset(content: string, line: number, column: number) {
  const lines = [] as Array<{ start: number; end: number }>
  let lineStart = 0

  for (let index = 0; index < content.length; index += 1) {
    const char = content[index]
    if (char !== '\n' && char !== '\r') continue

    lines.push({ start: lineStart, end: index })
    if (char === '\r' && content[index + 1] === '\n') {
      index += 1
    }
    lineStart = index + 1
  }
  lines.push({ start: lineStart, end: content.length })

  const lineIndex = Math.min(Math.max(line, 1), lines.length) - 1
  const currentLine = lines[lineIndex] ?? { start: 0, end: 0 }
  const lineLength = currentLine.end - currentLine.start
  const columnOffset = Math.min(Math.max(0, column - 1), lineLength)
  return currentLine.start + columnOffset
}

function applyTextEditsToContent(content: string, edits: SqlWorkbenchTextEdit[]) {
  const resolvedEdits = edits
    .map((edit) => ({
      ...edit,
      startOffset: resolveOffset(content, edit.range.startLine, edit.range.startColumn),
      endOffset: resolveOffset(content, edit.range.endLine, edit.range.endColumn),
    }))
    .sort((left, right) => right.startOffset - left.startOffset)

  let nextContent = content
  for (const edit of resolvedEdits) {
    nextContent = `${nextContent.slice(0, edit.startOffset)}${edit.text}${nextContent.slice(edit.endOffset)}`
  }

  return nextContent
}

function applySqlTextChange(tabState: SqlWorkbenchTabState, sqlText: string) {
  if (tabState.sqlText === sqlText) return tabState
  return {
    ...tabState,
    sqlText,
    version: tabState.version + 1,
  }
}

export const useSqlWorkbenchStore = create<SqlWorkbenchState>((set, get) => ({
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
      [tabId]: applySqlTextChange(ensureTabState(state.tabsById, tabId), sqlText),
    },
  })),

  replaceSqlText: (tabId, sqlText) => {
    const current = requireTabState(get().tabsById, tabId)
    const next = applySqlTextChange(current, sqlText)
    set((state) => ({
      tabsById: {
        ...state.tabsById,
        [tabId]: next,
      },
    }))
    return { version: next.version }
  },

  applyTextEdits: (tabId, params) => {
    const current = requireTabState(get().tabsById, tabId)
    if (params.baseVersion !== current.version) {
      return {
        ok: false,
        code: 'version_conflict',
        currentState: {
          version: current.version,
          content: current.sqlText,
        },
      }
    }

    const content = applyTextEditsToContent(current.sqlText, params.edits)
    const next = applySqlTextChange(current, content)
    set((state) => ({
      tabsById: {
        ...state.tabsById,
        [tabId]: next,
      },
    }))

    return {
      ok: true,
      version: next.version,
      content: next.sqlText,
    }
  },

  setSelection: (tabId, selection) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...requireTabState(state.tabsById, tabId),
        selection,
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

  closeResult: (tabId, resultId) => set((state) => {
    const tabState = ensureTabState(state.tabsById, tabId)
    const nextResults = tabState.results.filter((item) => item.resultId !== resultId)
    if (nextResults.length === tabState.results.length) return state

    const preferredActiveId = tabState.activeResultId === resultId ? null : tabState.activeResultId
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...tabState,
          results: nextResults,
          activeResultId: promoteActiveResultId(nextResults, preferredActiveId),
        },
      },
    }
  }),

  closeOtherResults: (tabId, resultId) => set((state) => {
    const tabState = ensureTabState(state.tabsById, tabId)
    const nextResults = tabState.results.filter((item) => item.resultId === resultId)
    if (nextResults.length === tabState.results.length) return state

    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...tabState,
          results: nextResults,
          activeResultId: promoteActiveResultId(nextResults, resultId),
        },
      },
    }
  }),

  closeAllResults: (tabId) => set((state) => {
    const tabState = ensureTabState(state.tabsById, tabId)
    if (tabState.results.length === 0 && tabState.activeResultId == null) return state

    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...tabState,
          results: [],
          activeResultId: null,
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

  setError: (tabId, message, result) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        executeStatus: 'error',
        results: result ? [result] : [],
        activeResultId: result?.resultId ?? null,
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
        ...requireTabState(state.tabsById, tabId),
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
