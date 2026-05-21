import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useSqlExecute } from './use-sql-execute'
import * as sqlApi from '@/services/api/sql'

vi.mock('@/services/api/sql', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/api/sql')>()
  return { ...actual, executeSql: vi.fn() }
})

const mockExecutedResponse: sqlApi.SqlExecuteResponse = {
  status: 'executed',
  resolvedContext: null,
  contextNotice: null,
  results: [
    {
      resultId: 'r-1',
      kind: 'result_set',
      title: 'Result 1',
      statementIndex: 0,
      statementText: 'SELECT 1',
      columns: ['id'],
      rows: [[1]],
      rowCount: 1,
      executionMs: 10,
      truncated: false,
    },
  ],
}

const mockRequiresConfirmationResponse: sqlApi.SqlExecuteResponse = {
  status: 'requires_confirmation',
  resolvedContext: null,
  contextNotice: null,
  confirmation: {
    level: 'L2',
    reason: 'This statement modifies data',
    affectedObjects: ['public.users'],
    sqlPreview: 'UPDATE users SET active = false',
  },
}

const mockConfirmationInvalidResponse: sqlApi.SqlExecuteResponse = {
  status: 'confirmation_invalid',
  resolvedContext: null,
  contextNotice: null,
  invalidConfirmation: {
    reason: 'risk_ack_insufficient',
    ackedRisk: 'L1',
    currentRisk: 'L2',
    message: 'Insufficient risk acknowledgment level',
  },
}

describe('useSqlExecute', () => {
  beforeEach(() => vi.clearAllMocks())

  it('transitions idle -> running -> success on executed', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockExecutedResponse)
    const { result } = renderHook(() => useSqlExecute())

    expect(result.current.state.kind).toBe('idle')

    await act(async () => {
      await result.current.execute('SELECT 1', 'c-1', 'ai')
    })

    expect(result.current.state.kind).toBe('success')
    if (result.current.state.kind === 'success') {
      expect(result.current.state.results).toHaveLength(1)
      expect(result.current.state.results[0].resultId).toBe('r-1')
    }
  })

  it('transitions running -> requires_confirmation on L2', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockRequiresConfirmationResponse)
    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await result.current.execute('UPDATE users SET active = false', 'c-1', 'user')
    })

    expect(result.current.state.kind).toBe('requires_confirmation')
    if (result.current.state.kind === 'requires_confirmation') {
      expect(result.current.state.confirmation.level).toBe('L2')
      expect(result.current.state.confirmation.reason).toBe('This statement modifies data')
      expect(result.current.state.lastRequest.sql).toBe('UPDATE users SET active = false')
    }
  })

  it('confirmAndRun re-executes with confirmed=true and riskAck', async () => {
    vi.mocked(sqlApi.executeSql)
      .mockResolvedValueOnce(mockRequiresConfirmationResponse)
      .mockResolvedValueOnce(mockExecutedResponse)

    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await result.current.execute('UPDATE users SET active = false', 'c-1', 'user')
    })
    expect(result.current.state.kind).toBe('requires_confirmation')

    await act(async () => {
      await result.current.confirmAndRun('L2')
    })

    expect(result.current.state.kind).toBe('success')
    expect(sqlApi.executeSql).toHaveBeenCalledTimes(2)
    expect(sqlApi.executeSql).toHaveBeenNthCalledWith(2, expect.objectContaining({
      confirmed: true,
      riskAck: 'L2',
      sql: 'UPDATE users SET active = false',
    }))
  })

  it('confirmation_invalid transitions back from confirming', async () => {
    vi.mocked(sqlApi.executeSql)
      .mockResolvedValueOnce(mockRequiresConfirmationResponse)
      .mockResolvedValueOnce(mockConfirmationInvalidResponse)

    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await result.current.execute('UPDATE users SET active = false', 'c-1', 'user')
    })
    expect(result.current.state.kind).toBe('requires_confirmation')

    await act(async () => {
      await result.current.confirmAndRun('L2')
    })

    expect(result.current.state.kind).toBe('confirmation_invalid')
    if (result.current.state.kind === 'confirmation_invalid') {
      expect(result.current.state.invalid.reason).toBe('risk_ack_insufficient')
      expect(result.current.state.invalid.currentRisk).toBe('L2')
    }
  })

  it('cancelConfirmation returns to idle', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockRequiresConfirmationResponse)
    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await result.current.execute('UPDATE users SET active = false', 'c-1', 'user')
    })
    expect(result.current.state.kind).toBe('requires_confirmation')

    act(() => {
      result.current.cancelConfirmation()
    })

    expect(result.current.state.kind).toBe('idle')
  })

  it('network error: sets error status', async () => {
    vi.mocked(sqlApi.executeSql).mockRejectedValue(new Error('network'))
    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await expect(result.current.execute('SELECT 1', 'c-1', 'user')).rejects.toThrow('network')
    })

    expect(result.current.state.kind).toBe('error')
    if (result.current.state.kind === 'error') {
      expect(result.current.state.message).toBe('network')
    }
  })

  it('reset: clears all state', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockExecutedResponse)
    const { result } = renderHook(() => useSqlExecute())

    await act(async () => { await result.current.execute('SELECT 1', 'c-1', 'ai') })
    act(() => result.current.reset())

    expect(result.current.state.kind).toBe('idle')
  })

  it('passes an AbortSignal to executeSql', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockExecutedResponse)
    const controller = new AbortController()
    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await result.current.execute('SELECT 1', 'c-1', 'user', undefined, controller.signal)
    })

    expect(sqlApi.executeSql).toHaveBeenCalledWith(
      { sql: 'SELECT 1', connectionId: 'c-1', source: 'user' },
      controller.signal,
    )
  })

  it('returns to idle after an aborted execution', async () => {
    const abortError = new DOMException('The operation was aborted.', 'AbortError')
    vi.mocked(sqlApi.executeSql).mockRejectedValue(abortError)
    const controller = new AbortController()
    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await expect(
        result.current.execute('SELECT 1', 'c-1', 'user', undefined, controller.signal),
      ).rejects.toMatchObject({ name: 'AbortError' })
    })

    expect(result.current.state.kind).toBe('idle')
  })

  it('confirmAndRun does nothing when not in requires_confirmation or confirmation_invalid state', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockExecutedResponse)
    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await result.current.execute('SELECT 1', 'c-1', 'ai')
    })
    expect(result.current.state.kind).toBe('success')

    await act(async () => {
      await result.current.confirmAndRun('L2')
    })

    // Should remain success, no extra executeSql calls
    expect(result.current.state.kind).toBe('success')
    expect(sqlApi.executeSql).toHaveBeenCalledTimes(1)
  })

  it('confirmAndRun works from confirmation_invalid state', async () => {
    vi.mocked(sqlApi.executeSql)
      .mockResolvedValueOnce(mockRequiresConfirmationResponse)
      .mockResolvedValueOnce(mockConfirmationInvalidResponse)
      .mockResolvedValueOnce(mockExecutedResponse)

    const { result } = renderHook(() => useSqlExecute())

    await act(async () => {
      await result.current.execute('UPDATE users SET active = false', 'c-1', 'user')
    })
    await act(async () => {
      await result.current.confirmAndRun('L2')
    })
    expect(result.current.state.kind).toBe('confirmation_invalid')

    await act(async () => {
      await result.current.confirmAndRun('L3')
    })

    expect(result.current.state.kind).toBe('success')
    expect(sqlApi.executeSql).toHaveBeenCalledTimes(3)
    expect(sqlApi.executeSql).toHaveBeenNthCalledWith(3, expect.objectContaining({
      confirmed: true,
      riskAck: 'L3',
    }))
  })
})
