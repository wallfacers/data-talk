import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { buildEventSink } from './use-channel'

describe('buildEventSink · session.meta.updated', () => {
  let qc: QueryClient
  beforeEach(() => {
    qc = new QueryClient()
  })

  it('triggers sessions query invalidation', () => {
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const sink = buildEventSink('s1', null, qc)
    sink({ event: 'session.meta.updated', data: { sessionId: 's1', title: 'AI', titleLocked: false, version: 2 } } as any)
    expect(spy).toHaveBeenCalledWith({ queryKey: ['sessions'] })
  })
})