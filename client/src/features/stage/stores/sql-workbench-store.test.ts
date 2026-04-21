import { beforeEach, describe, expect, it } from 'vitest'
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

  it('replaces results on execute success and selects the first result', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-a')
    store.applyExecuteSuccess('tab-a', {
      resolvedContext: null,
      contextNotice: 'session context',
      results: [resultSet, dmlSummary],
    })

    let state = useSqlWorkbenchStore.getState()
    expect(state.tabsById['tab-a']?.results).toHaveLength(2)
    expect(state.tabsById['tab-a']?.activeResultId).toBe('r-set')

    store.applyExecuteSuccess('tab-a', {
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
      resolvedContext: null,
      contextNotice: null,
      results: [resultSet, dmlSummary],
    })

    store.setActiveResult('tab-a', 'r-dml')

    expect(useSqlWorkbenchStore.getState().tabsById['tab-a']?.activeResultId).toBe('r-dml')
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
