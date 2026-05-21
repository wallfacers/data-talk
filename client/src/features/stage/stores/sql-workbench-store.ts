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
  oldText: string
  newText: string
  hint?: { line: number }
}

export type SqlWorkbenchEditResult =
  | { ok: true; version: number; content: string; rebased?: boolean }
  | { ok: false; code: 'version_conflict'; currentState: { version: number; content: string } }
  | {
      ok: false
      code: 'anchor_not_found'
      currentState: { version: number; content: string }
      details: { editIndex: number; oldText: string }
    }
  | {
      ok: false
      code: 'anchor_ambiguous'
      currentState: { version: number; content: string }
      details: { editIndex: number; matchCount: number }
    }
  | {
      ok: false
      code: 'invalid_params'
      currentState: { version: number; content: string }
      details: { editIndex: number; reason: string }
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
  applyTextEdits: (tabId: string, params: { baseVersion?: number; edits: SqlWorkbenchTextEdit[] }) => SqlWorkbenchEditResult
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

/**
 * Build a CRLF/CR-normalized projection of `content` plus a map from each
 * normalized character index to its original offset. `map` has length
 * `normalized.length + 1`; the final entry is the original content length so a
 * normalized range `[start, end)` maps to the original range `[map[start], map[end])`.
 */
function buildNormalizedProjection(content: string): { normalized: string; map: number[] } {
  let normalized = ''
  const map: number[] = []
  let index = 0
  while (index < content.length) {
    const char = content[index]
    if (char === '\r') {
      normalized += '\n'
      map.push(index)
      index += content[index + 1] === '\n' ? 2 : 1
    } else {
      normalized += char
      map.push(index)
      index += 1
    }
  }
  map.push(content.length)
  return { normalized, map }
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Compile a whitespace-flexible matcher from a normalized anchor: non-whitespace
 * tokens match literally and in order; internal whitespace runs match `\s+` (so a
 * cross-line anchor tolerates differing newlines); leading/trailing whitespace is
 * treated as indentation and matches only horizontal whitespace `[ \t]*` so the
 * match never swallows a preceding/following line break.
 */
function compileAnchor(normalizedOldText: string): RegExp {
  const segments = normalizedOldText.match(/\s+|\S+/g) ?? []
  const lastIndex = segments.length - 1
  const pattern = segments
    .map((segment, segmentIndex) => {
      if (!/\s/.test(segment)) {
        return escapeRegExp(segment)
      }
      const isEdge = segmentIndex === 0 || segmentIndex === lastIndex
      return isEdge ? '[ \\t]*' : '\\s+'
    })
    .join('')
  return new RegExp(pattern, 'g')
}

type AnchorMatch = { normStart: number; normEnd: number }

function findAnchorMatches(normalized: string, anchor: RegExp): AnchorMatch[] {
  const matches: AnchorMatch[] = []
  anchor.lastIndex = 0
  let match: RegExpExecArray | null = anchor.exec(normalized)
  while (match !== null) {
    matches.push({ normStart: match.index, normEnd: match.index + match[0].length })
    // Guard against zero-width matches looping forever.
    anchor.lastIndex = match[0].length === 0 ? match.index + 1 : anchor.lastIndex
    match = anchor.exec(normalized)
  }
  return matches
}

function lineOfOffset(normalized: string, offset: number): number {
  let line = 1
  for (let index = 0; index < offset && index < normalized.length; index += 1) {
    if (normalized[index] === '\n') line += 1
  }
  return line
}

type ResolvedAnchorEdit = { startOffset: number; endOffset: number; text: string }

type AnchorResolution =
  | { ok: true; resolved: ResolvedAnchorEdit[] }
  | { ok: false; code: 'anchor_not_found'; details: { editIndex: number; oldText: string } }
  | { ok: false; code: 'anchor_ambiguous'; details: { editIndex: number; matchCount: number } }
  | { ok: false; code: 'invalid_params'; details: { editIndex: number; reason: string } }

/**
 * Resolve every edit against the same original snapshot using content anchoring.
 * Returns original-offset ranges, or the first blocking failure.
 */
function resolveAnchoredEdits(content: string, edits: SqlWorkbenchTextEdit[]): AnchorResolution {
  const { normalized, map } = buildNormalizedProjection(content)
  const resolved: ResolvedAnchorEdit[] = []

  for (const [editIndex, edit] of edits.entries()) {
    const normalizedOldText = edit.oldText.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
    if (normalizedOldText.length === 0) {
      return { ok: false, code: 'invalid_params', details: { editIndex, reason: 'empty_oldText' } }
    }

    const matches = findAnchorMatches(normalized, compileAnchor(normalizedOldText))
    if (matches.length === 0) {
      return { ok: false, code: 'anchor_not_found', details: { editIndex, oldText: edit.oldText } }
    }

    let chosen: AnchorMatch
    if (matches.length === 1) {
      chosen = matches[0]
    } else {
      const hintLine = edit.hint?.line
      if (typeof hintLine !== 'number') {
        return { ok: false, code: 'anchor_ambiguous', details: { editIndex, matchCount: matches.length } }
      }
      const scored = matches.map((candidate) => ({
        candidate,
        distance: Math.abs(lineOfOffset(normalized, candidate.normStart) - hintLine),
      }))
      const minDistance = Math.min(...scored.map((entry) => entry.distance))
      const closest = scored.filter((entry) => entry.distance === minDistance)
      if (closest.length !== 1) {
        return { ok: false, code: 'anchor_ambiguous', details: { editIndex, matchCount: matches.length } }
      }
      chosen = closest[0].candidate
    }

    resolved.push({
      startOffset: map[chosen.normStart],
      endOffset: map[chosen.normEnd],
      text: edit.newText,
    })
  }

  const overlap = findOverlap(resolved)
  if (overlap !== null) {
    return { ok: false, code: 'invalid_params', details: { editIndex: overlap, reason: 'overlapping_edits' } }
  }

  return { ok: true, resolved }
}

/** Returns the editIndex (in original order) whose resolved range overlaps a prior one, else null. */
function findOverlap(resolved: ResolvedAnchorEdit[]): number | null {
  const ordered = resolved
    .map((edit, index) => ({ ...edit, index }))
    .sort((left, right) => left.startOffset - right.startOffset)
  for (let i = 1; i < ordered.length; i += 1) {
    if (ordered[i].startOffset < ordered[i - 1].endOffset) {
      return Math.max(ordered[i].index, ordered[i - 1].index)
    }
  }
  return null
}

function applyResolvedTextEditsToContent(content: string, edits: ResolvedAnchorEdit[]) {
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
    const currentState = { version: current.version, content: current.sqlText }
    // baseVersion is advisory: anchors locate the edit regardless of version drift.
    const rebased = typeof params.baseVersion === 'number' && params.baseVersion !== current.version

    const resolution = resolveAnchoredEdits(current.sqlText, params.edits)
    if (!resolution.ok) {
      return { ok: false, code: resolution.code, currentState, details: resolution.details } as SqlWorkbenchEditResult
    }

    const content = applyResolvedTextEditsToContent(current.sqlText, resolution.resolved)
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
      rebased,
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
