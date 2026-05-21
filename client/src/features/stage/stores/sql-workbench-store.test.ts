import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSqlWorkbenchStore } from './sql-workbench-store'

const resultSet = {
  resultId: 'r-set',
  kind: 'result_set' as const,
  title: 'Result 1',
  statementIndex: 0,
  statementText: 'select 1',
  columns: ['id'],
  rows: [[1]],
  rowCount: 1,
  executionMs: 8,
  truncated: false,
}

const dmlSummary = {
  resultId: 'r-dml',
  kind: 'dml_summary' as const,
  title: 'DML Summary',
  statementIndex: 1,
  statementText: 'update t set a=1',
  columns: [],
  rows: [],
  rowCount: 0,
  executionMs: 3,
  truncated: false,
  affectedRows: 4,
}

describe('useSqlWorkbenchStore', () => {
  beforeEach(() => {
    useSqlWorkbenchStore.setState({ tabsById: {} })
    vi.restoreAllMocks()
  })

  it('stores SQL text independently per tab id', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a', { sqlText: 'select 1' })
    store.ensureTab('tab-b', { sqlText: 'select 2' })
    store.setSqlText('tab-a', 'select 3')

    const state = useSqlWorkbenchStore.getState()
    expect(state.tabsById['tab-a']?.sqlText).toBe('select 3')
    expect(state.tabsById['tab-b']?.sqlText).toBe('select 2')
  })

  it('replaces full content and increments version when baseVersion matches', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1' })

    const result = store.replaceSqlText('tab-1', 'select 2', 1)

    expect(result).toEqual({
      ok: true,
      version: 2,
      content: 'select 2',
    })
    expect(useSqlWorkbenchStore.getState().tabsById['tab-1']).toMatchObject({
      sqlText: 'select 2',
      version: 2,
    })
  })

  it('rejects stale baseVersion when replacing full content', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1' })
    store.setSqlText('tab-1', 'select 11')

    const result = store.replaceSqlText('tab-1', 'select 2', 1)

    expect(result).toEqual({
      ok: false,
      code: 'version_conflict',
      currentState: {
        version: 2,
        content: 'select 11',
      },
    })
    expect(useSqlWorkbenchStore.getState().tabsById['tab-1']).toMatchObject({
      sqlText: 'select 11',
      version: 2,
    })
  })

  it('applies an anchored edit on unique match and ignores an incorrect hint', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1\r\nfrom dual' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: 'dual', newText: 'table', hint: { line: 99 } }],
    })

    expect(result).toEqual({
      ok: true,
      version: 2,
      content: 'select 1\r\nfrom table',
      rebased: false,
    })
    expect(useSqlWorkbenchStore.getState().tabsById['tab-1']).toMatchObject({
      sqlText: 'select 1\r\nfrom table',
      version: 2,
    })
  })

  it('applies an anchored edit with only oldText and newText', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1 from dual' })

    const result = store.applyTextEdits('tab-1', {
      edits: [{ oldText: 'select 1', newText: 'select 2' }],
    })

    expect(result).toMatchObject({ ok: true, version: 2, content: 'select 2 from dual' })
  })

  it('disambiguates multiple matches with hint.line', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'id\nname\nid\nemail' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: 'id', newText: 'pk', hint: { line: 3 } }],
    })

    expect(result).toMatchObject({ ok: true, content: 'id\nname\npk\nemail' })
  })

  it('returns anchor_ambiguous when multiple matches and no hint', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'id\nname\nid\nemail' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: 'id', newText: 'pk' }],
    })

    expect(result).toEqual({
      ok: false,
      code: 'anchor_ambiguous',
      currentState: { version: 1, content: 'id\nname\nid\nemail' },
      details: { editIndex: 0, matchCount: 2 },
    })
    expect(useSqlWorkbenchStore.getState().tabsById['tab-1']).toMatchObject({ version: 1 })
  })

  it('returns anchor_not_found and current content when oldText is absent', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1\r\nfrom dual' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: 'WHERE x = 1', newText: 'WHERE x = 2' }],
    })

    expect(result).toEqual({
      ok: false,
      code: 'anchor_not_found',
      currentState: { version: 1, content: 'select 1\r\nfrom dual' },
      details: { editIndex: 0, oldText: 'WHERE x = 1' },
    })
  })

  it('tolerates indentation differences but keeps non-whitespace tokens strict', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'SELECT\n    COUNT(*)\nFROM t' })

    const tolerant = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: '  COUNT(*)', newText: '  SUM(x)' }],
    })
    expect(tolerant).toMatchObject({ ok: true, content: 'SELECT\n  SUM(x)\nFROM t' })

    store.replaceSqlText('tab-1', 'SELECT COUNT(*)', 2)
    const strict = store.applyTextEdits('tab-1', {
      baseVersion: 3,
      edits: [{ oldText: 'SELECT SUM(*)', newText: 'SELECT 1' }],
    })
    expect(strict).toMatchObject({ ok: false, code: 'anchor_not_found' })
  })

  it('preserves CRLF outside the matched span', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'a\r\nb\r\nc' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: 'b', newText: 'X' }],
    })

    expect(result).toMatchObject({ ok: true, content: 'a\r\nX\r\nc' })
  })

  it('matches a cross-line anchor through normalized newlines', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'SELECT 1\r\nFROM dual' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: '1\nFROM', newText: '2\nFROM' }],
    })

    expect(result).toMatchObject({ ok: true, content: 'SELECT 2\nFROM dual' })
  })

  it('rejects an empty oldText with invalid_params', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: '', newText: 'x' }],
    })

    expect(result).toEqual({
      ok: false,
      code: 'invalid_params',
      currentState: { version: 1, content: 'select 1' },
      details: { editIndex: 0, reason: 'empty_oldText' },
    })
  })

  it('applies multiple non-overlapping edits against the same snapshot', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select a, b from t' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [
        { oldText: 'a,', newText: 'x,' },
        { oldText: 'from t', newText: 'from u' },
      ],
    })

    expect(result).toMatchObject({ ok: true, content: 'select x, b from u' })
  })

  it('rejects overlapping edits with invalid_params and applies nothing', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select alpha from t' })

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [
        { oldText: 'select alpha', newText: 'select beta' },
        { oldText: 'alpha from', newText: 'gamma from' },
      ],
    })

    expect(result).toMatchObject({ ok: false, code: 'invalid_params' })
    if (result.ok || result.code !== 'invalid_params') throw new Error('expected invalid_params')
    expect(result.details.reason).toBe('overlapping_edits')
    expect(useSqlWorkbenchStore.getState().tabsById['tab-1']).toMatchObject({
      sqlText: 'select alpha from t',
      version: 1,
    })
  })

  it('auto-rebases when baseVersion drifted but the anchor is still unique', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1 from dual' })
    store.replaceSqlText('tab-1', 'select 1 from dual where x = 1', 1)

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: 'select 1', newText: 'select 2' }],
    })

    expect(result).toMatchObject({
      ok: true,
      version: 3,
      content: 'select 2 from dual where x = 1',
      rebased: true,
    })
  })

  it('fails with anchor_not_found when drifted and the anchor no longer exists', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1 from dual' })
    store.replaceSqlText('tab-1', 'select 99 from other', 1)

    const result = store.applyTextEdits('tab-1', {
      baseVersion: 1,
      edits: [{ oldText: 'from dual', newText: 'from t' }],
    })

    expect(result).toMatchObject({
      ok: false,
      code: 'anchor_not_found',
      currentState: { version: 2, content: 'select 99 from other' },
    })
  })

  it('preserves content and version when applyTextEdits is a no-op', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1', { sqlText: 'select 1\r\nfrom dual' })

    const result = store.applyTextEdits('tab-1', { baseVersion: 1, edits: [] })

    expect(result).toEqual({
      ok: true,
      version: 1,
      content: 'select 1\r\nfrom dual',
      rebased: false,
    })
  })

  it('rejects replaceSqlText for unknown tab ids', () => {
    expect(() => useSqlWorkbenchStore.getState().replaceSqlText('missing', 'select 1', 1)).toThrow(
      'Unknown sql workbench tab: missing',
    )
  })

  it('rejects applyTextEdits for unknown tab ids', () => {
    expect(() => useSqlWorkbenchStore.getState().applyTextEdits('missing', {
      baseVersion: 1,
      edits: [],
    })).toThrow('Unknown sql workbench tab: missing')
  })

  it('rejects setCursor for unknown tab ids', () => {
    expect(() => useSqlWorkbenchStore.getState().setCursor('missing', 3, 4)).toThrow(
      'Unknown sql workbench tab: missing',
    )
  })

  it('rejects setSelection for unknown tab ids', () => {
    expect(() => useSqlWorkbenchStore.getState().setSelection('missing', {
      startLine: 1,
      startColumn: 1,
      endLine: 1,
      endColumn: 3,
    })).toThrow('Unknown sql workbench tab: missing')
  })

  it('initializes savedSqlText from the initial SQL text', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a', { sqlText: 'select 1' })

    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.savedSqlText).toBe('select 1')
  })

  it('sets and resets tab context with a timestamp', () => {
    vi.spyOn(Date, 'now').mockReturnValue(123456789)

    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a')
    store.setTabContext('tab-a', {
      connectionId: 'conn-1',
      connectionName: 'Connection 1',
      database: 'db_1',
      schema: 'public',
      source: 'user_toolbar',
    })

    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.override).toEqual({
      connectionId: 'conn-1',
      connectionName: 'Connection 1',
      database: 'db_1',
      schema: 'public',
      source: 'user_toolbar',
      setAt: 123456789,
    })
    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.useSessionContext).toBe(false)

    store.resetTabContext('tab-a')

    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.override).toBeNull()
    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.useSessionContext).toBe(true)
  })

  it('defaults query editor tabs to session-following mode', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a')

    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.useSessionContext).toBe(true)
  })

  it('appends history entries and keeps only the newest 50', () => {
    const store = useSqlWorkbenchStore.getState()

    for (let index = 0; index < 51; index += 1) {
      store.appendHistoryEntry('tab-a', {
        id: `h-${index}`,
        at: index,
        sql: `select ${index}`,
        status: 'ok',
        resultCount: index,
      })
    }

    const history = useSqlWorkbenchStore.getState().tabsById['tab-a']?.history
    expect(history).toHaveLength(50)
    expect(history?.[0]?.id).toBe('h-1')
    expect(history?.[49]?.id).toBe('h-50')
  })

  it('clears tab history', () => {
    const store = useSqlWorkbenchStore.getState()
    store.appendHistoryEntry('tab-a', {
      id: 'h-1',
      at: 1,
      sql: 'select 1',
      status: 'ok',
    })

    store.clearHistory('tab-a')

    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.history).toEqual([])
  })

  it('marks the current SQL text as saved', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a', { sqlText: 'select 1' })
    store.setSqlText('tab-a', 'select 2')

    store.markSaved('tab-a')

    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.savedSqlText).toBe('select 2')
  })

  it('updates limit and cursor', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a')

    store.setLimit('tab-a', 1000)
    store.setCursor('tab-a', 7, 18)

    const tab = useSqlWorkbenchStore.getState().tabsById['tab-a']
    expect(tab?.limit).toBe(1000)
    expect(tab?.cursor).toEqual({ line: 7, column: 18 })
  })

  it('replaces results on execute success and selects the first result', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a')
    store.applyExecuteSuccess('tab-a', {
      status: 'executed',
      resolvedContext: null,
      contextNotice: 'session context',
      results: [resultSet, dmlSummary],
    })

    let state = useSqlWorkbenchStore.getState()
    expect(state.tabsById['tab-a']?.results).toHaveLength(2)
    expect(state.tabsById['tab-a']?.activeResultId).toBe('r-set')

    store.applyExecuteSuccess('tab-a', {
      status: 'executed',
      resolvedContext: null,
      contextNotice: null,
      results: [{ ...resultSet, resultId: 'r-new', title: 'Result 2' }],
    })

    state = useSqlWorkbenchStore.getState()
    expect(state.tabsById['tab-a']?.results.map((item) => item.resultId)).toEqual(['r-new'])
    expect(state.tabsById['tab-a']?.activeResultId).toBe('r-new')
  })

  it('switches the active result within a tab', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a')
    store.applyExecuteSuccess('tab-a', {
      status: 'executed',
      resolvedContext: null,
      contextNotice: null,
      results: [resultSet, dmlSummary],
    })

    store.setActiveResult('tab-a', 'r-dml')

    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.activeResultId).toBe('r-dml')
  })

  it('closes results and promotes a stable active result', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a')
    store.applyExecuteSuccess('tab-a', {
      status: 'executed',
      resolvedContext: null,
      contextNotice: null,
      results: [resultSet, dmlSummary, { ...resultSet, resultId: 'r-last', title: 'Result 3' }],
    })

    store.closeResult('tab-a', 'r-set')
    let tab = useSqlWorkbenchStore.getState().tabsById['tab-a']
    expect(tab?.results.map((item) => item.resultId)).toEqual(['r-dml', 'r-last'])
    expect(tab?.activeResultId).toBe('r-dml')

    store.closeOtherResults('tab-a', 'r-last')
    tab = useSqlWorkbenchStore.getState().tabsById['tab-a']
    expect(tab?.results.map((item) => item.resultId)).toEqual(['r-last'])
    expect(tab?.activeResultId).toBe('r-last')

    store.closeAllResults('tab-a')
    tab = useSqlWorkbenchStore.getState().tabsById['tab-a']
    expect(tab?.results).toEqual([])
    expect(tab?.activeResultId).toBeNull()
  })

  it('cleans up tab state when tabs are closed', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a', { sqlText: 'select 1' })
    store.ensureTab('tab-b', { sqlText: 'select 2' })

    store.cleanupTabs(['tab-b'])

    const state = useSqlWorkbenchStore.getState()
    expect(state.tabsById['tab-a']).toBeUndefined()
    expect(state.tabsById['tab-b']?.sqlText).toBe('select 2')
  })
})
