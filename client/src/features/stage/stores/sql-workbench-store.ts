import { create } from 'zustand'
import type {
  ResolvedDataContext,
  SqlConfirmationInvalid,
  SqlConfirmationPayload,
  SqlExecuteRequest,
  SqlExecuteResponse,
  SqlExecuteResultItem,
} from '@/services/api/sql'

export type SqlWorkbenchExecuteStatus =
  | 'idle'
  | 'running'
  | 'success'
  | 'requires_confirmation'
  | 'confirming'
  | 'confirmation_invalid'
  | 'error'

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
  status: 'ok' | 'error' | 'requires_confirmation' | 'confirmation_invalid'
  resultCount?: number
  elapsedMs?: number
  resultKinds?: SqlExecuteResultItem['kind'][]
  errorSummary?: string
  confirmationReason?: string
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
  expectedText: string
}

export type SqlWorkbenchEditResult =
  | { ok: true; version: number; content: string }
  | { ok: false; code: 'version_conflict'; currentState: { version: number; content: string } }
  | {
      ok: false
      code: 'expected_text_mismatch'
      currentState: { version: number; content: string }
      details: { editIndex: number; expected: string; actual: string }
    }

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
  errorMessage: string | null
  confirmation: SqlConfirmationPayload | null
  confirmationInvalid: SqlConfirmationInvalid | null
  lastRequest: SqlExecuteRequest | null
  override: TabContextOverride | null
  history: HistoryEntry[]
  undoStates: Record<string, { status: 'idle' | 'confirming' | 'undoing' | 'undone' | 'error'; inverseSql?: string; error?: string }>
  savedSqlText: string
  limit: 10 | 100 | 1000 | null
  /**
   * @deprecated UI-derived value; equals (boundSessionId === activeSessionId && override == null) for AI editors,
   * and effectively `override == null` for user editors. Retained on the store for backward-compatible reads
   * during the migration. Mutators no longer treat it as the source of truth — they update `override` and
   * `boundSessionId` directly.
   */
  useSessionContext: boolean
  /**
   * Session this editor currently tracks. Updated by `rebindToActiveSession` (manual ON).
   * Null only for transient pre-hydrate states; the workbench tab back-fills from payload or origin.
   */
  boundSessionId: string | null
  cursor: { line: number; column: number }
}

type EnsureTabInput = Partial<Pick<SqlWorkbenchTabState, 'sqlText' | 'source' | 'useSessionContext' | 'boundSessionId'>>
type HydrateTabInput = Pick<SqlWorkbenchTabState, 'sqlText' | 'source' | 'useSessionContext' | 'boundSessionId' | 'override'>

type SqlWorkbenchState = {
  tabsById: Record<string, SqlWorkbenchTabState>
  ensureTab: (tabId: string, initial?: EnsureTabInput) => void
  hydrateTab: (tabId: string, snapshot: HydrateTabInput) => void
  setSqlText: (tabId: string, sqlText: string) => void
  replaceSqlText: (tabId: string, sqlText: string, baseVersion: number) => SqlWorkbenchEditResult
  applyTextEdits: (tabId: string, params: { baseVersion: number; edits: SqlWorkbenchTextEdit[] }) => SqlWorkbenchEditResult
  setSelection: (tabId: string, selection: SqlWorkbenchSelection | null) => void
  setActiveResult: (tabId: string, resultId: string | null) => void
  closeResult: (tabId: string, resultId: string) => void
  closeOtherResults: (tabId: string, resultId: string) => void
  closeAllResults: (tabId: string) => void
  setRunning: (tabId: string) => void
  applyExecuteSuccess: (tabId: string, response: SqlExecuteResponse) => void
  setRequiresConfirmation: (tabId: string, confirmation: SqlConfirmationPayload, lastRequest: SqlExecuteRequest) => void
  setConfirming: (tabId: string) => void
  setConfirmationInvalid: (tabId: string, invalid: SqlConfirmationInvalid, lastRequest: SqlExecuteRequest) => void
  cancelConfirmation: (tabId: string) => void
  setError: (tabId: string, message: string, result?: SqlExecuteResultItem | null) => void
  setTabContext: (tabId: string, ctx: Omit<TabContextOverride, 'setAt'>) => void
  resetTabContext: (tabId: string) => void
  rebindToSession: (tabId: string, sessionId: string | null) => void
  appendHistoryEntry: (tabId: string, entry: HistoryEntry) => void
  clearHistory: (tabId: string) => void
  markSaved: (tabId: string) => void
  setLimit: (tabId: string, limit: 10 | 100 | 1000 | null) => void
  setCursor: (tabId: string, line: number, column: number) => void
  resetExecutionState: (tabId: string) => void
  cleanupTabs: (activeTabIds: string[]) => void
  setUndoConfirming: (tabId: string, resultId: string, inverseSql: string) => void
  setUndoResult: (tabId: string, resultId: string, status: 'undone' | 'error', error?: string) => void
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
    errorMessage: null,
    confirmation: null,
    confirmationInvalid: null,
    lastRequest: null,
    override: null,
    undoStates: {},
    history: [],
    savedSqlText: initialSqlText,
    limit: 100,
    useSessionContext: initial?.useSessionContext ?? true,
    boundSessionId: initial?.boundSessionId ?? null,
    cursor: { line: 1, column: 1 },
  }
}

function canHydratePristineTab(tabState: SqlWorkbenchTabState) {
  return tabState.version === 1
    && tabState.sqlText === tabState.savedSqlText
    && tabState.executeStatus === 'idle'
    && tabState.results.length === 0
    && tabState.activeResultId === null
    && tabState.resolvedContext === null
    && tabState.contextNotice === null
    && tabState.errorMessage === null
    && tabState.confirmation === null
    && tabState.confirmationInvalid === null
    && tabState.lastRequest === null
    && tabState.override === null
    && tabState.history.length === 0
    && tabState.selection === null
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

type ResolvedSqlWorkbenchTextEdit = SqlWorkbenchTextEdit & {
  startOffset: number
  endOffset: number
}

function normalizeLineEndings(content: string) {
  return content.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}

function resolveTextEdits(content: string, edits: SqlWorkbenchTextEdit[]): ResolvedSqlWorkbenchTextEdit[] {
  return edits.map((edit) => ({
    ...edit,
    startOffset: resolveOffset(content, edit.range.startLine, edit.range.startColumn),
    endOffset: resolveOffset(content, edit.range.endLine, edit.range.endColumn),
  }))
}

function applyResolvedTextEditsToContent(content: string, edits: ResolvedSqlWorkbenchTextEdit[]) {
  const resolvedEdits = [...edits].sort((left, right) => right.startOffset - left.startOffset)

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

  hydrateTab: (tabId, snapshot) => set((state) => {
    const current = state.tabsById[tabId]
    if (!current) {
      return {
        tabsById: {
          ...state.tabsById,
          [tabId]: createDefaultTabState(snapshot),
        },
      }
    }
    if (!canHydratePristineTab(current)) return state

    const next = {
      ...current,
      sqlText: snapshot.sqlText,
      savedSqlText: snapshot.sqlText,
      source: snapshot.source,
      useSessionContext: snapshot.useSessionContext,
      boundSessionId: snapshot.boundSessionId,
      override: snapshot.override ?? current.override,
    }
    if (
      next.sqlText === current.sqlText
      && next.savedSqlText === current.savedSqlText
      && next.source === current.source
      && next.useSessionContext === current.useSessionContext
      && next.boundSessionId === current.boundSessionId
      && next.override === current.override
    ) {
      return state
    }
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: next,
      },
    }
  }),

  setSqlText: (tabId, sqlText) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: applySqlTextChange(ensureTabState(state.tabsById, tabId), sqlText),
    },
  })),

  replaceSqlText: (tabId, sqlText, baseVersion) => {
    const current = requireTabState(get().tabsById, tabId)
    if (baseVersion !== current.version) {
      return {
        ok: false,
        code: 'version_conflict',
        currentState: {
          version: current.version,
          content: current.sqlText,
        },
      }
    }

    const next = applySqlTextChange(current, sqlText)
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

    const resolvedEdits = resolveTextEdits(current.sqlText, params.edits)
    for (const [editIndex, edit] of resolvedEdits.entries()) {
      const actual = normalizeLineEndings(current.sqlText.slice(edit.startOffset, edit.endOffset))
      const expected = normalizeLineEndings(edit.expectedText)
      if (actual !== expected) {
        return {
          ok: false,
          code: 'expected_text_mismatch',
          currentState: {
            version: current.version,
            content: current.sqlText,
          },
          details: {
            editIndex,
            expected,
            actual,
          },
        }
      }
    }

    const content = applyResolvedTextEditsToContent(current.sqlText, resolvedEdits)
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
        results: [],
        activeResultId: null,
        errorMessage: null,
        confirmation: null,
        confirmationInvalid: null,
        lastRequest: null,
      },
    },
  })),

  applyExecuteSuccess: (tabId, response) => set((state) => {
    const previous = ensureTabState(state.tabsById, tabId)
    const results = response.status === 'executed' ? response.results : []
    const activeResultId = results[0]?.resultId ?? null
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...previous,
          executeStatus: 'success',
          results,
          activeResultId,
          resolvedContext: response.resolvedContext ?? null,
          contextNotice: response.contextNotice ?? null,
          errorMessage: null,
          confirmation: null,
          confirmationInvalid: null,
          lastRequest: null,
        },
      },
    }
  }),

  setRequiresConfirmation: (tabId, confirmation, lastRequest) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        executeStatus: 'requires_confirmation',
        confirmation,
        confirmationInvalid: null,
        lastRequest,
        errorMessage: null,
      },
    },
  })),

  setConfirming: (tabId) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        executeStatus: 'confirming',
      },
    },
  })),

  setConfirmationInvalid: (tabId, invalid, lastRequest) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        executeStatus: 'confirmation_invalid',
        confirmationInvalid: invalid,
        lastRequest,
      },
    },
  })),

  cancelConfirmation: (tabId) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        executeStatus: 'idle',
        confirmation: null,
        confirmationInvalid: null,
        lastRequest: null,
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
        confirmation: null,
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
        useSessionContext: false,
      },
    },
  })),

  resetTabContext: (tabId) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        override: null,
        useSessionContext: true,
      },
    },
  })),

  rebindToSession: (tabId, sessionId) => set((state) => ({
    tabsById: {
      ...state.tabsById,
      [tabId]: {
        ...ensureTabState(state.tabsById, tabId),
        boundSessionId: sessionId,
        override: null,
        useSessionContext: true,
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

  resetExecutionState: (tabId) => set((state) => {
    const current = state.tabsById[tabId]
    if (!current) return state
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...current,
          executeStatus: 'idle',
          errorMessage: null,
          confirmation: null,
          confirmationInvalid: null,
          lastRequest: null,
        },
      },
    }
  }),

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

  setUndoConfirming: (tabId, resultId, inverseSql) => set((state) => {
    const tabState = ensureTabState(state.tabsById, tabId)
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...tabState,
          undoStates: { ...tabState.undoStates, [resultId]: { status: 'confirming', inverseSql } },
        },
      },
    }
  }),

  setUndoResult: (tabId, resultId, status, error) => set((state) => {
    const tabState = ensureTabState(state.tabsById, tabId)
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...tabState,
          undoStates: { ...tabState.undoStates, [resultId]: { status, error } },
        },
      },
    }
  }),
}))
