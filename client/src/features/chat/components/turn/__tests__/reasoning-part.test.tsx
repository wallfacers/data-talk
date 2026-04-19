import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ReasoningPart } from '../reasoning-part'
import type { MessageInfo, ReasoningPart as RPartType } from '@/services/channel/types'

describe('ReasoningPart', () => {
  const info: MessageInfo = {
    id: 'm1',
    role: 'assistant',
    sessionID: 's1',
    time: { created: Date.now() },
  }

  const part: RPartType = {
    id: 'p1',
    type: 'reasoning',
    sessionID: 's1',
    messageID: 'm1',
    text: 'Thought process content',
  }

  it('is expanded by default when streaming', async () => {
    render(<ReasoningPart part={part} info={info} />)
    // When streaming, it should be open, so we should see the content.
    expect(await screen.findByText('Thought process content')).toBeInTheDocument()
    expect(screen.getByLabelText('思考中…')).toBeInTheDocument()
  })

  it('collapses when clicking the heading', async () => {
    render(<ReasoningPart part={part} info={info} />)
    // Wait for it to be visible first
    expect(await screen.findByText('Thought process content')).toBeInTheDocument()
    
    const button = screen.getByRole('button')
    fireEvent.click(button)
    // Now it should be collapsed
    expect(screen.queryByText('Thought process content')).not.toBeInTheDocument()
  })

  it('is collapsed by default when not streaming (e.g. history)', () => {
    const completedInfo: MessageInfo = {
      ...info,
      time: { created: Date.now(), completed: Date.now() },
    }
    render(<ReasoningPart part={part} info={completedInfo} />)
    // Not streaming, so it should be collapsed by default.
    expect(screen.queryByText('Thought process content')).not.toBeInTheDocument()
    expect(screen.getByText(/已深度思考/)).toBeInTheDocument()
  })

  it('automatically collapses when streaming finishes', async () => {
    const { rerender } = render(<ReasoningPart part={part} info={info} />)
    expect(await screen.findByText('Thought process content')).toBeInTheDocument()

    const completedInfo: MessageInfo = {
      ...info,
      time: { created: Date.now(), completed: Date.now() },
    }
    rerender(<ReasoningPart part={part} info={completedInfo} />)
    
    // Should auto-collapse
    expect(screen.queryByText('Thought process content')).not.toBeInTheDocument()
    expect(screen.getByText(/已深度思考/)).toBeInTheDocument()
  })

  it('shows duration if time properties are present', () => {
    const completedInfo: MessageInfo = {
      ...info,
      time: { created: 1000, completed: 5000 },
    }
    const partWithTime: RPartType = {
      ...part,
      time: { start: 1000, end: 4000 }
    }
    render(<ReasoningPart part={partWithTime} info={completedInfo} />)
    expect(screen.getByText(/已深度思考（3 秒）/)).toBeInTheDocument()
  })
})
