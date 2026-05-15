import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the http module before importing the module under test
vi.mock('@/services/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import { http } from '@/services/http'
import { listOpLogs, getOpLogDetail, batchUndoOpLogs } from './connection-op-log'

const mockedGet = vi.mocked(http.get)
const mockedPost = vi.mocked(http.post)

describe('connection-op-log API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('listOpLogs', () => {
    it('fetches paginated op-logs with default params', async () => {
      const mockResponse = { items: [], total: 0, page: 0, size: 50 }
      mockedGet.mockReturnValue({
        json: () => Promise.resolve(mockResponse),
      } as any)

      const result = await listOpLogs('conn-1')
      expect(mockedGet).toHaveBeenCalledWith('connections/conn-1/op-logs', {
        searchParams: { page: '0', size: '50' },
      })
      expect(result).toEqual(mockResponse)
    })

    it('passes filters as comma-joined search params for arrays', async () => {
      const mockResponse = { items: [], total: 0, page: 0, size: 50 }
      mockedGet.mockReturnValue({
        json: () => Promise.resolve(mockResponse),
      } as any)

      await listOpLogs('conn-1', 1, 25, {
        status: ['active'],
        operation: ['INSERT'],
        table: 'orders',
        from: 1000,
        to: 2000,
        q: 'SELECT',
      })

      expect(mockedGet).toHaveBeenCalledWith('connections/conn-1/op-logs', {
        searchParams: {
          page: '1',
          size: '25',
          status: 'active',
          operation: 'INSERT',
          table: 'orders',
          from: '1000',
          to: '2000',
          q: 'SELECT',
        },
      })
    })

    it('joins multiple status/operation values with commas', async () => {
      const mockResponse = { items: [], total: 0, page: 0, size: 50 }
      mockedGet.mockReturnValue({
        json: () => Promise.resolve(mockResponse),
      } as any)

      await listOpLogs('conn-1', 0, 50, {
        status: ['active', 'pending'],
        operation: ['INSERT', 'DELETE'],
      })

      expect(mockedGet).toHaveBeenCalledWith('connections/conn-1/op-logs', {
        searchParams: {
          page: '0',
          size: '50',
          status: 'active,pending',
          operation: 'INSERT,DELETE',
        },
      })
    })

    it('omits filter params when filters are undefined or empty', async () => {
      const mockResponse = { items: [], total: 0, page: 0, size: 50 }
      mockedGet.mockReturnValue({
        json: () => Promise.resolve(mockResponse),
      } as any)

      await listOpLogs('conn-1', 0, 50, {})

      const callArgs = mockedGet.mock.calls[0]
      const searchParams = (callArgs![1] as any).searchParams
      expect(searchParams).toEqual({ page: '0', size: '50' })
      expect(searchParams).not.toHaveProperty('status')
      expect(searchParams).not.toHaveProperty('operation')
    })
  })

  describe('getOpLogDetail', () => {
    it('fetches a single op-log detail', async () => {
      const mockDetail = { id: 'log-1', originalSql: 'INSERT INTO ...' } as any
      mockedGet.mockReturnValue({
        json: () => Promise.resolve(mockDetail),
      } as any)

      const result = await getOpLogDetail('conn-1', 'log-1')
      expect(mockedGet).toHaveBeenCalledWith('connections/conn-1/op-logs/log-1')
      expect(result).toEqual(mockDetail)
    })
  })

  describe('batchUndoOpLogs', () => {
    it('sends batch undo request', async () => {
      const mockResult = { results: [{ id: 'log-1', status: 'undone' }] }
      mockedPost.mockReturnValue({
        json: () => Promise.resolve(mockResult),
      } as any)

      const result = await batchUndoOpLogs('conn-1', ['log-1', 'log-2'])
      expect(mockedPost).toHaveBeenCalledWith('connections/conn-1/op-logs/batch-undo', {
        json: { undoLogIds: ['log-1', 'log-2'] },
      })
      expect(result).toEqual(mockResult)
    })
  })
})
