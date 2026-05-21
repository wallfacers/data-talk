export type QueryEditorEntryMode = 'blank' | 'resource_sql' | 'direct_sql' | 'ui_exec' | 'ai_open'

export type QueryEditorResultSnapshot = {
  columns: string[]
  rows: unknown[][]
  rowCount: number
  executionMs: number
  truncated: boolean
}

export type QueryEditorLastRunSnapshot = {
  columns: string[]
  rowCount: number
  executionMs: number
  truncated: boolean
}

export type NormalizedQueryEditorContextOverride = {
  connectionId: string
  database: string | null
  schema: string | null
} | null

export type QueryEditorContextSource = 'session' | 'override' | 'tab'

export type QueryEditorEffectiveContext = {
  useSessionContext: boolean
  sessionId: string | null
  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
  contextSource: QueryEditorContextSource
}

export type NormalizedQueryEditorPayload = {
  entryMode: QueryEditorEntryMode
  initialSql: string
  source: 'user' | 'ai'
  autoRun: boolean
  initialResult: QueryEditorResultSnapshot | null
  lastRun: QueryEditorLastRunSnapshot | null
  contextNotice: string | null
  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
  contextOverride: NormalizedQueryEditorContextOverride
  /**
   * @deprecated Derived UI value; toggle = boundSessionId === activeSessionId && contextOverride == null.
   * Kept on the normalized shape so existing readers continue to compile during the migration. Future
   * cleanups SHOULD remove all reads of this field; do NOT persist it back to storage as the source of
   * truth.
   */
  useSessionContext: boolean
  /**
   * Session this editor currently tracks for its execution context.
   * `source = 'ai'`: updated on manual re-bind (user clicks the toggle ON).
   * `source = 'user'`: fixed at creation to the originating session id, never re-bound.
   * Null only for transient pre-hydrate payloads; the normalizer back-fills from `originSessionId` when omitted.
   */
  boundSessionId: string | null
}

const EMPTY_CONTEXT_SELECT_VALUE = '__empty__'

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function normalizeString(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  if (trimmed.length === 0 || trimmed === EMPTY_CONTEXT_SELECT_VALUE) return null
  return trimmed
}

function normalizeSource(value: unknown): 'user' | 'ai' {
  return value === 'ai' ? 'ai' : 'user'
}

function normalizeEntryMode(value: unknown, source: 'user' | 'ai'): QueryEditorEntryMode {
  switch (value) {
    case 'manual':
    case 'blank':
      return 'blank'
    case 'resource':
    case 'resource_sql':
      return 'resource_sql'
    case 'direct_sql':
      return 'direct_sql'
    case 'ui_exec':
      return 'ui_exec'
    case 'ai_generated':
    case 'ai_open':
      return 'ai_open'
    default:
      return source === 'ai' ? 'ai_open' : 'blank'
  }
}

function normalizeStringArray(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.some((item) => typeof item !== 'string')) return null
  return value
}

function normalizeRows(value: unknown): unknown[][] | null {
  if (!Array.isArray(value) || value.some((row) => !Array.isArray(row))) return null
  return value as unknown[][]
}

function normalizeResult(value: unknown): QueryEditorResultSnapshot | null {
  if (!isPlainObject(value)) return null
  const record = value
  const columns = normalizeStringArray(record.columns)
  const rows = normalizeRows(record.rows)
  if (!columns || !rows) return null
  if (typeof record.rowCount !== 'number' || typeof record.executionMs !== 'number' || typeof record.truncated !== 'boolean') {
    return null
  }
  return {
    columns,
    rows,
    rowCount: record.rowCount,
    executionMs: record.executionMs,
    truncated: record.truncated,
  }
}

function normalizeLastRun(value: unknown): QueryEditorLastRunSnapshot | null {
  if (!isPlainObject(value)) return null
  const record = value
  const columns = normalizeStringArray(record.columns)
  if (!columns) return null
  if (typeof record.rowCount !== 'number' || typeof record.executionMs !== 'number' || typeof record.truncated !== 'boolean') {
    return null
  }
  return {
    columns,
    rowCount: record.rowCount,
    executionMs: record.executionMs,
    truncated: record.truncated,
  }
}

function normalizeContextOverride(value: unknown): NormalizedQueryEditorContextOverride {
  if (!isPlainObject(value)) return null
  const record = value
  const connectionId = normalizeString(record.connectionId)
  if (!connectionId) return null
  return {
    connectionId,
    database: normalizeString(record.database),
    schema: normalizeString(record.schema),
  }
}

export function normalizeQueryEditorPayload(payload: unknown): NormalizedQueryEditorPayload {
  const value = isPlainObject(payload) ? payload : {}
  const source = normalizeSource(value.source)
  const initialResult = normalizeResult(value.initialResult)
  const contextOverride = normalizeContextOverride(value.contextOverride)
  const boundSessionId = normalizeString(value.boundSessionId)
    ?? normalizeString(value.originSessionId)
  const useSessionContext = contextOverride == null
  const initialSql = typeof value.initialSql === 'string'
    ? value.initialSql
    : typeof value.content === 'string'
      ? value.content
      : typeof value.sql === 'string' ? value.sql : ''

  return {
    entryMode: normalizeEntryMode(value.entryMode, source),
    initialSql,
    source,
    autoRun: value.autoRun === true,
    initialResult,
    lastRun: normalizeLastRun(value.lastRun) ?? (initialResult
      ? {
          columns: initialResult.columns,
          rowCount: initialResult.rowCount,
          executionMs: initialResult.executionMs,
          truncated: initialResult.truncated,
        }
      : null),
    contextNotice: typeof value.contextNotice === 'string' ? value.contextNotice : null,
    connectionId: normalizeString(value.connectionId),
    connectionName: normalizeString(value.connectionName),
    database: normalizeString(value.database),
    schema: normalizeString(value.schema),
    contextOverride,
    useSessionContext,
    boundSessionId,
  }
}

function sameNormalizedResult(
  actual: unknown,
  expected: QueryEditorResultSnapshot | null,
) {
  if (expected === null) return actual === null
  if (!isPlainObject(actual)) return false
  return (
    normalizeStringArray(actual.columns) === expected.columns &&
    normalizeRows(actual.rows) === expected.rows &&
    actual.rowCount === expected.rowCount &&
    actual.executionMs === expected.executionMs &&
    actual.truncated === expected.truncated
  )
}

function sameNormalizedLastRun(
  actual: unknown,
  expected: QueryEditorLastRunSnapshot | null,
) {
  if (expected === null) return actual === null
  if (!isPlainObject(actual)) return false
  return (
    normalizeStringArray(actual.columns) === expected.columns &&
    actual.rowCount === expected.rowCount &&
    actual.executionMs === expected.executionMs &&
    actual.truncated === expected.truncated
  )
}

function sameNormalizedContextOverride(
  actual: unknown,
  expected: NormalizedQueryEditorContextOverride,
) {
  if (expected === null) return actual === null
  if (!isPlainObject(actual)) return false
  return (
    actual.connectionId === expected.connectionId &&
    normalizeString(actual.database) === expected.database &&
    normalizeString(actual.schema) === expected.schema
  )
}

export function isNormalizedQueryEditorPayload(payload: unknown): payload is NormalizedQueryEditorPayload {
  if (!isPlainObject(payload)) return false
  const record = payload
  const normalized = normalizeQueryEditorPayload(payload)
  return (
    record.entryMode === normalized.entryMode &&
    record.initialSql === normalized.initialSql &&
    record.source === normalized.source &&
    record.autoRun === normalized.autoRun &&
    sameNormalizedResult(record.initialResult, normalized.initialResult) &&
    sameNormalizedLastRun(record.lastRun, normalized.lastRun) &&
    record.contextNotice === normalized.contextNotice &&
    record.connectionId === normalized.connectionId &&
    record.connectionName === normalized.connectionName &&
    record.database === normalized.database &&
    record.schema === normalized.schema &&
    sameNormalizedContextOverride(record.contextOverride, normalized.contextOverride) &&
    record.useSessionContext === normalized.useSessionContext &&
    record.boundSessionId === normalized.boundSessionId
  )
}
