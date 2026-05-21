import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QuestionDock } from './question-dock'
import { useQuestionStore } from '@/stores/question-store'
import type { QuestionRequest } from '@/services/api/question'

vi.mock('@/services/api/question', async (orig) => ({
  ...(await orig<typeof import('@/services/api/question')>()),
  replyQuestion: vi.fn().mockResolvedValue(true),
  rejectQuestion: vi.fn().mockResolvedValue(true),
}))

vi.mock('@/services/http-error', () => ({
  normalizeError: (e: unknown) => e,
  showErrorToast: vi.fn(),
}))

import { replyQuestion, rejectQuestion } from '@/services/api/question'

const single: QuestionRequest = {
  id: 'q1',
  questions: [{
    question: '继续吗？', header: 'Confirm',
    options: [{ label: '是' }, { label: '否' }],
  }],
}

const multi: QuestionRequest = {
  id: 'q2',
  questions: [{
    question: '选择字段', header: 'Fields', multiple: true,
    options: [{ label: 'A' }, { label: 'B' }, { label: 'C' }],
  }],
}

describe('QuestionDock', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useQuestionStore.setState({ bySession: new Map([['sess', [single]]]) })
  })

  it('single question picks an option then submits via Submit', async () => {
    render(<QuestionDock sessionId="sess" request={single} />)
    // Picking an option selects it but never auto-submits — submission goes through Submit.
    fireEvent.click(screen.getByRole('radio', { name: /是/ }))
    expect(replyQuestion).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText('提交'))
    await waitFor(() => expect(replyQuestion).toHaveBeenCalledWith('q1', [['是']]))
    // resolved() optimistically clears the store → composer would restore
    await waitFor(() => expect(useQuestionStore.getState().bySession.has('sess')).toBe(false))
  })

  it('multi question toggles selections then submits via Submit', async () => {
    useQuestionStore.setState({ bySession: new Map([['sess', [multi]]]) })
    render(<QuestionDock sessionId="sess" request={multi} />)
    fireEvent.click(screen.getByRole('checkbox', { name: /A/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /C/ }))
    fireEvent.click(screen.getByText('提交'))
    await waitFor(() => expect(replyQuestion).toHaveBeenCalledWith('q2', [['A', 'C']]))
  })

  it('multi question untoggles a re-clicked option', async () => {
    useQuestionStore.setState({ bySession: new Map([['sess', [multi]]]) })
    render(<QuestionDock sessionId="sess" request={multi} />)
    const a = screen.getByRole('checkbox', { name: /A/ })
    fireEvent.click(a)
    fireEvent.click(a) // toggle off
    fireEvent.click(screen.getByText('提交'))
    await waitFor(() => expect(replyQuestion).toHaveBeenCalledWith('q2', [[]]))
  })

  it('Dismiss rejects the question', async () => {
    render(<QuestionDock sessionId="sess" request={single} />)
    fireEvent.click(screen.getByText('略过'))
    await waitFor(() => expect(rejectQuestion).toHaveBeenCalledWith('q1'))
  })

  it('Escape rejects the question', async () => {
    render(<QuestionDock sessionId="sess" request={single} />)
    fireEvent.keyDown(screen.getByRole('radio', { name: /是/ }), { key: 'Escape' })
    await waitFor(() => expect(rejectQuestion).toHaveBeenCalledWith('q1'))
  })

  it('custom answer row is selectable across its full width, not just the mark', async () => {
    render(<QuestionDock sessionId="sess" request={single} />)
    // The whole row (label text included) is the radio control — clicking it selects custom.
    fireEvent.click(screen.getByRole('radio', { name: '输入自定义答案' }))
    expect(await screen.findByPlaceholderText('输入你的答案…')).toBeInTheDocument()
  })
})
