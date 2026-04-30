import { describe, expect, it } from 'vitest'
import { isNormalizedQueryEditorPayload, normalizeQueryEditorPayload } from '../normalize-query-editor-payload'

describe('normalizeQueryEditorPayload', () => {
  it('maps legacy sql/source payloads into the new normalized model', () => {
    expect(normalizeQueryEditorPayload({ sql: 'SELECT 1', source: 'user' })).toMatchObject({
      entryMode: 'blank',
      initialSql: 'SELECT 1',
      source: 'user',
      autoRun: false,
      initialResult: null,
      lastRun: null,
      contextNotice: null,
    })
  })

  it('accepts content as a query editor open payload alias', () => {
    expect(normalizeQueryEditorPayload({ content: 'DROP DATABASE ecommerce;' })).toMatchObject({
      initialSql: 'DROP DATABASE ecommerce;',
    })
  })

  it('maps legacy entry modes onto the canonical query editor entry modes', () => {
    expect(normalizeQueryEditorPayload({
      entryMode: 'manual',
      source: 'user',
    }).entryMode).toBe('blank')

    expect(normalizeQueryEditorPayload({
      entryMode: 'resource',
      source: 'user',
    }).entryMode).toBe('resource_sql')

    expect(normalizeQueryEditorPayload({
      entryMode: 'ai_generated',
      source: 'ai',
    }).entryMode).toBe('ai_open')

    expect(normalizeQueryEditorPayload({
      entryMode: 'direct_sql',
      source: 'user',
    }).entryMode).toBe('direct_sql')
  })

  it('accepts canonical query editor entry modes from openQueryEditor', () => {
    expect(normalizeQueryEditorPayload({
      entryMode: 'blank',
      source: 'user',
    }).entryMode).toBe('blank')

    expect(normalizeQueryEditorPayload({
      entryMode: 'resource_sql',
      source: 'user',
    }).entryMode).toBe('resource_sql')

    expect(normalizeQueryEditorPayload({
      entryMode: 'ui_exec',
      source: 'user',
    }).entryMode).toBe('ui_exec')

    expect(normalizeQueryEditorPayload({
      entryMode: 'ai_open',
      source: 'ai',
    }).entryMode).toBe('ai_open')
  })

  it('preserves direct_sql metadata and initial result payloads', () => {
    expect(normalizeQueryEditorPayload({
      entryMode: 'direct_sql',
      initialSql: 'select 1',
      source: 'ai',
      autoRun: true,
      contextNotice: 'Using reporting schema',
      initialResult: {
        columns: ['n'],
        rows: [[1]],
        rowCount: 1,
        executionMs: 3,
        truncated: false,
      },
      lastRun: {
        columns: ['n'],
        rowCount: 1,
        executionMs: 3,
        truncated: false,
      },
    })).toMatchObject({
      entryMode: 'direct_sql',
      initialSql: 'select 1',
      source: 'ai',
      autoRun: true,
      contextNotice: 'Using reporting schema',
      initialResult: { rowCount: 1, rows: [[1]] },
      lastRun: { rowCount: 1, executionMs: 3 },
    })
  })

  it('normalizes valid contextOverride payloads', () => {
    expect(normalizeQueryEditorPayload({
      sql: 'select 1',
      contextOverride: {
        connectionId: 'conn-1',
        database: 'reporting',
        schema: 'public',
      },
    })).toMatchObject({
      initialSql: 'select 1',
      contextOverride: {
        connectionId: 'conn-1',
        database: 'reporting',
        schema: 'public',
      },
    })
  })

  it('normalizes the SQL context empty sentinel to null', () => {
    expect(normalizeQueryEditorPayload({
      connectionId: 'conn-1',
      database: '__empty__',
      schema: '__empty__',
      contextOverride: {
        connectionId: 'conn-1',
        database: '__empty__',
        schema: '__empty__',
      },
    })).toMatchObject({
      connectionId: 'conn-1',
      database: null,
      schema: null,
      contextOverride: {
        connectionId: 'conn-1',
        database: null,
        schema: null,
      },
    })
  })

  it('preserves the explicit session context pin mode marker', () => {
    expect(normalizeQueryEditorPayload({
      contextOverride: null,
      contextPinMode: 'session',
    })).toMatchObject({
      contextOverride: null,
      contextPinMode: 'session',
    })

    expect(normalizeQueryEditorPayload({
      contextPinMode: 'override',
    }).contextPinMode).toBeNull()
  })

  it('falls back to null for invalid contextOverride payloads', () => {
    expect(normalizeQueryEditorPayload({
      sql: 'select 1',
      contextOverride: {},
    })).toMatchObject({
      initialSql: 'select 1',
      contextOverride: null,
    })

    expect(normalizeQueryEditorPayload({
      sql: 'select 1',
      contextOverride: {
        connectionId: '',
        database: 'reporting',
      },
    })).toMatchObject({
      initialSql: 'select 1',
      contextOverride: null,
    })
  })

  it('rejects malformed normalized-looking payloads at the authoritative gate', () => {
    const malformedPayload = {
      entryMode: 'direct_sql',
      initialSql: 'select 1',
      source: 'user',
      autoRun: false,
      initialResult: {
        columns: ['n'],
        rows: 'not-an-array',
        rowCount: 1,
        executionMs: 3,
        truncated: false,
      },
      lastRun: {
        columns: ['n'],
        rowCount: '1',
        executionMs: 3,
        truncated: false,
      },
      contextNotice: null,
      connectionId: null,
      connectionName: null,
      database: null,
      schema: null,
    }

    expect(isNormalizedQueryEditorPayload(malformedPayload)).toBe(false)
    expect(normalizeQueryEditorPayload(malformedPayload)).toMatchObject({
      entryMode: 'direct_sql',
      initialSql: 'select 1',
      initialResult: null,
      lastRun: null,
    })
  })
})
