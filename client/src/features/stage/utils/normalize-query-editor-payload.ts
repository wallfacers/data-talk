export type QueryEditorEntryMode = 'manual' | 'resource' | 'direct_sql' | 'ai_generated'

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
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function normalizeString(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value : null
}

function normalizeSource(value: unknown): 'user' | 'ai' {
  return value === 'ai' ? 'ai' : 'user'
}

function normalizeEntryMode(value: unknown, source: 'user' | 'ai'): QueryEditorEntryMode {
  if (value === 'manual' || value === 'resource' || value === 'direct_sql' || value === 'ai_generated') {
    return value
  }
  return source === 'ai' ? 'ai_generated' : 'manual'
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

  return {
    entryMode: normalizeEntryMode(value.entryMode, source),
    initialSql: typeof value.initialSql === 'string' ? value.initialSql : typeof value.sql === 'string' ? value.sql : '',
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
    contextOverride: normalizeContextOverride(value.contextOverride),
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
    sameNormalizedContextOverride(record.contextOverride, normalized.contextOverride)
  )
}
