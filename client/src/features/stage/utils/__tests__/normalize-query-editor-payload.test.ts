import { describe, expect, it } from 'vitest'
import { isNormalizedQueryEditorPayload, normalizeQueryEditorPayload } from '../normalize-query-editor-payload'

describe('normalizeQueryEditorPayload', () => {
  it('maps legacy sql/source payloads into the new normalized model', () => {
    expect(normalizeQueryEditorPayload({ sql: 'SELECT 1', source: 'user' })).toMatchObject({
      entryMode: 'manual',
      initialSql: 'SELECT 1',
      source: 'user',
      autoRun: false,
      initialResult: null,
      lastRun: null,
      contextNotice: null,
    })
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
