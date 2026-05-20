import { beforeEach, describe, expect, it } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { useQuestionStore } from './question-store'
import { buildEventSink } from '@/services/channel/use-channel'
import type { QuestionRequest } from '@/services/api/question'

const req = (id: string): QuestionRequest => ({
  id,
  questions: [{ question: 'Continue?', header: 'Confirm', options: [{ label: 'Yes' }] }],
})

describe('question-store', () => {
  beforeEach(() => {
    useQuestionStore.setState({ bySession: new Map() })
  })

  it('upsert dedupes by requestId', () => {
    const s = useQuestionStore.getState()
    s.upsert('sess', req('q1'))
    s.upsert('sess', req('q1')) // same id again
    expect(useQuestionStore.getState().bySession.get('sess')).toHaveLength(1)
  })

  it('upsert appends distinct requests', () => {
    const s = useQuestionStore.getState()
    s.upsert('sess', req('q1'))
    s.upsert('sess', req('q2'))
    expect(useQuestionStore.getState().bySession.get('sess')).toHaveLength(2)
  })

  it('removeByRequestId drops the entry and clears empty sessions', () => {
    const s = useQuestionStore.getState()
    s.upsert('sess', req('q1'))
    s.removeByRequestId('sess', 'q1')
    expect(useQuestionStore.getState().bySession.has('sess')).toBe(false)
  })

  it('setForSession replaces and clears on empty', () => {
    const s = useQuestionStore.getState()
    s.setForSession('sess', [req('q1'), req('q2')])
    expect(useQuestionStore.getState().bySession.get('sess')).toHaveLength(2)
    s.setForSession('sess', [])
    expect(useQuestionStore.getState().bySession.has('sess')).toBe(false)
  })
})

describe('buildEventSink question events', () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  beforeEach(() => {
    useQuestionStore.setState({ bySession: new Map() })
  })

  it('question.asked upserts into the session', () => {
    const sink = buildEventSink('sess', null, qc)
    sink({ id: 0, event: 'question.asked', data: { requestId: 'q1', questions: req('q1').questions } })
    expect(useQuestionStore.getState().bySession.get('sess')?.[0]?.id).toBe('q1')
  })

  it('question.replied removes the pending question', () => {
    useQuestionStore.getState().upsert('sess', req('q1'))
    const sink = buildEventSink('sess', null, qc)
    sink({ id: 0, event: 'question.replied', data: { requestId: 'q1' } })
    expect(useQuestionStore.getState().bySession.has('sess')).toBe(false)
  })

  it('question.rejected removes the pending question', () => {
    useQuestionStore.getState().upsert('sess', req('q1'))
    const sink = buildEventSink('sess', null, qc)
    sink({ id: 0, event: 'question.rejected', data: { requestId: 'q1' } })
    expect(useQuestionStore.getState().bySession.has('sess')).toBe(false)
  })
})
