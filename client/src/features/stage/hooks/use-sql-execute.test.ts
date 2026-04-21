import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useSqlExecute } from './use-sql-execute'
import * as sqlApi from '@/services/api/sql'

vi.mock('@/services/api/sql', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/api/sql')>()
  return { ...actual, executeSql: vi.fn() }
})

const mockResult: sqlApi.SqlResult = {
  columns: ['id'], rows: [[1]], rowCount: 1, executionMs: 10, truncated: false,
}

describe('useSqlExecute', () => {
  beforeEach(() => vi.clearAllMocks())

  it('ai source: executes and sets success', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockResult)
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.execute('SELECT 1', 'c-1', 'ai') })
    expect(sqlApi.executeSql).toHaveBeenCalledWith({ sql: 'SELECT 1', connectionId: 'c-1', source: 'ai' })
    expect(result.current.status).toBe('success')
    expect(result.current.result).toEqual(mockResult)
  })

  it('user source low risk: executes and sets success', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockResult)
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.execute('SELECT 1', 'c-1', 'user') })
    expect(result.current.status).toBe('success')
  })

  it('user source high risk: sets risk_blocked', async () => {
    vi.mocked(sqlApi.executeSql).mockRejectedValue(
      new sqlApi.SqlRiskError({ riskLevel: 'HIGH', riskReason: 'bulk_delete' })
    )
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => {
      await expect(result.current.execute('DELETE FROM orders', 'c-1', 'user')).rejects.toBeInstanceOf(sqlApi.SqlRiskError)
    })
    expect(result.current.status).toBe('risk_blocked')
    expect(result.current.risk).toEqual({ riskLevel: 'HIGH', riskReason: 'bulk_delete' })
    expect(result.current.result).toBeNull()
  })

  it('network error: sets error status', async () => {
    vi.mocked(sqlApi.executeSql).mockRejectedValue(new Error('network'))
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => {
      await expect(result.current.execute('SELECT 1', 'c-1', 'user')).rejects.toThrow('network')
    })
    expect(result.current.status).toBe('error')
    expect(result.current.errorMessage).toBe('network')
  })

  it('reset: clears all state', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockResult)
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.execute('SELECT 1', 'c-1', 'ai') })
    act(() => result.current.reset())
    expect(result.current.status).toBe('idle')
    expect(result.current.result).toBeNull()
  })
})
