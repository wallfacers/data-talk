import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSqlWorkbenchStore } from './sql-workbench-store'

describe('useSqlWorkbenchStore undo actions', () => {
  beforeEach(() => {
    useSqlWorkbenchStore.setState({ tabsById: {} })
  })

  it('setUndoConfirming sets the undo state for the result to confirming with inverseSql', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1')

    store.setUndoConfirming('tab-1', 'result-1', 'DELETE FROM users WHERE id = 1')

    const undoState = useSqlWorkbenchStore.getState().tabsById['tab-1']?.undoStates['result-1']
    expect(undoState).toEqual({
      status: 'confirming',
      inverseSql: 'DELETE FROM users WHERE id = 1',
    })
  })

  it('setUndoResult sets the undo state to undone', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1')

    store.setUndoResult('tab-1', 'result-1', 'undone')

    const undoState = useSqlWorkbenchStore.getState().tabsById['tab-1']?.undoStates['result-1']
    expect(undoState).toEqual({ status: 'undone' })
  })

  it('setUndoResult sets the undo state to error with error message', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1')

    store.setUndoResult('tab-1', 'result-1', 'error', 'some error')

    const undoState = useSqlWorkbenchStore.getState().tabsById['tab-1']?.undoStates['result-1']
    expect(undoState).toEqual({ status: 'error', error: 'some error' })
  })

  it('isolates undo states per result within the same tab', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1')

    store.setUndoConfirming('tab-1', 'result-a', 'DELETE FROM t1')
    store.setUndoResult('tab-1', 'result-b', 'undone')

    const tab = useSqlWorkbenchStore.getState().tabsById['tab-1']
    expect(tab?.undoStates['result-a']).toEqual({
      status: 'confirming',
      inverseSql: 'DELETE FROM t1',
    })
    expect(tab?.undoStates['result-b']).toEqual({ status: 'undone' })
  })

  it('isolates undo states across different tabs', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('tab-1')
    store.ensureTab('tab-2')

    store.setUndoConfirming('tab-1', 'result-1', 'DELETE FROM t1')
    store.setUndoResult('tab-2', 'result-1', 'error', 'Connection lost')

    const state = useSqlWorkbenchStore.getState()
    expect(state.tabsById['tab-1']?.undoStates['result-1']).toEqual({
      status: 'confirming',
      inverseSql: 'DELETE FROM t1',
    })
    expect(state.tabsById['tab-2']?.undoStates['result-1']).toEqual({
      status: 'error',
      error: 'Connection lost',
    })
  })
})

describe('undoDml API', () => {
  const originalFetch = global.fetch

  beforeEach(() => {
    global.fetch = originalFetch
    useSqlWorkbenchStore.setState({ tabsById: {} })
  })

  afterEach(() => {
    global.fetch = originalFetch
  })

  it('calls POST /api/sql/undo with the correct body and returns the response', async () => {
    const mockResponse = {
      status: 'requires_confirmation',
      inverseSql: 'DELETE FROM t WHERE id = 1',
      affectedRows: 3,
      tableName: 'users',
    }

    global.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(mockResponse),
    })

    const { undoDml } = await import('@/services/api/sql')
    const result = await undoDml({ undoLogId: 'log-1', confirmed: false })

    expect(global.fetch).toHaveBeenCalledTimes(1)
    const [url, options] = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(url).toBe('/api/sql/undo')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({
      undoLogId: 'log-1',
      confirmed: false,
    })
    expect(result).toEqual(mockResponse)
  })

  it('calls POST /api/sql/undo with confirmed and riskAck when provided', async () => {
    const mockResponse = {
      status: 'undone',
      affectedRows: 3,
    }

    global.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(mockResponse),
    })

    const { undoDml } = await import('@/services/api/sql')
    const result = await undoDml({ undoLogId: 'log-2', confirmed: true, riskAck: 'L2' })

    expect(global.fetch).toHaveBeenCalledTimes(1)
    const [, options] = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(JSON.parse(options.body)).toEqual({
      undoLogId: 'log-2',
      confirmed: true,
      riskAck: 'L2',
    })
    expect(result).toEqual(mockResponse)
  })

  it('throws an error when the server responds with non-ok status', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce({
      ok: false,
      json: () => Promise.resolve({ message: 'Undo log expired' }),
    })

    const { undoDml } = await import('@/services/api/sql')

    await expect(undoDml({ undoLogId: 'log-3' })).rejects.toThrow('Undo log expired')
  })
})
