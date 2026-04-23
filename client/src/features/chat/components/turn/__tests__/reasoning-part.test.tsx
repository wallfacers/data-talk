import { beforeEach, describe, it, expect } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { ReasoningPart } from '../reasoning-part'
import type { MessageInfo, ReasoningPart as RPartType } from '@/services/channel/types'
import { useUISettingsStore } from '@/stores/ui-settings-store'

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

  beforeEach(() => {
    useUISettingsStore.setState({
      splitResizable: false,
      language: 'zh-CN',
      autoExpandReasoning: false,
    } as any)
  })

  it('stays collapsed by default when streaming and auto expand is disabled', () => {
    render(<ReasoningPart part={part} info={info} />)

    expect(screen.queryByText('Thought process content')).not.toBeInTheDocument()
    expect(screen.getByLabelText('思考中…')).toBeInTheDocument()
  })

  it('is expanded by default when streaming and auto expand is enabled', async () => {
    useUISettingsStore.setState({ autoExpandReasoning: true } as any)

    render(<ReasoningPart part={part} info={info} />)

    expect(await screen.findByText('Thought process content')).toBeInTheDocument()
    expect(screen.getByLabelText('思考中…')).toBeInTheDocument()
  })

  it('collapses when clicking the heading', async () => {
    useUISettingsStore.setState({ autoExpandReasoning: true } as any)

    render(<ReasoningPart part={part} info={info} />)
    expect(await screen.findByText('Thought process content')).toBeInTheDocument()

    const button = screen.getByRole('button')
    fireEvent.click(button)

    expect(screen.queryByText('Thought process content')).not.toBeInTheDocument()
  })

  it('is collapsed by default when not streaming (e.g. history)', () => {
    const completedInfo: MessageInfo = {
      ...info,
      time: { created: Date.now(), completed: Date.now() },
    }
    render(<ReasoningPart part={part} info={completedInfo} />)

    expect(screen.queryByText('Thought process content')).not.toBeInTheDocument()
    expect(screen.getByText(/已深度思考/)).toBeInTheDocument()
  })

  it('automatically collapses when streaming finishes', async () => {
    useUISettingsStore.setState({ autoExpandReasoning: true } as any)

    const { rerender } = render(<ReasoningPart part={part} info={info} />)
    expect(await screen.findByText('Thought process content')).toBeInTheDocument()

    const completedInfo: MessageInfo = {
      ...info,
      time: { created: Date.now(), completed: Date.now() },
    }
    rerender(<ReasoningPart part={part} info={completedInfo} />)

    expect(screen.queryByText('Thought process content')).not.toBeInTheDocument()
    expect(screen.getByText(/已深度思考/)).toBeInTheDocument()
  })

  it('closes after completion even if the user manually expanded it while auto expand is disabled', async () => {
    const { rerender } = render(<ReasoningPart part={part} info={info} />)

    const button = screen.getByRole('button')
    fireEvent.click(button)
    expect(await screen.findByText('Thought process content')).toBeInTheDocument()

    const completedInfo: MessageInfo = {
      ...info,
      time: { created: Date.now(), completed: Date.now() },
    }
    rerender(<ReasoningPart part={part} info={completedInfo} />)

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

  it('renders fenced code in reasoning with the shared code window wrapper', async () => {
    useUISettingsStore.setState({ autoExpandReasoning: true } as any)

    const codePart = { ...part, text: '```js\nconsole.log(1)\n```' }
    const { container } = render(<ReasoningPart part={codePart} info={info} />)
    const renderedCode = await screen.findByText(/console\.log\(1\)/)
    await waitFor(() =>
      expect(
        container.querySelector('[data-component="reasoning-part"] [data-component="markdown-code"]'),
      ).not.toBeNull(),
    )
    expect(screen.getByLabelText('思考中…')).toBeInTheDocument()
    expect(renderedCode.closest('[data-component="reasoning-part"]')).not.toBeNull()
    expect(renderedCode.closest('pre')).not.toBeNull()
    expect(container.querySelector('[data-component="reasoning-part"]')?.textContent).not.toContain(
      '```js',
    )
  })
})
