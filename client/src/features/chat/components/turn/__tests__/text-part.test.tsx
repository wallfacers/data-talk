import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { TextPart } from '../text-part'
import type { PartComponentProps } from '../part-dispatcher'

function buildProps(overrides: {
  streaming: boolean
  text: string
}): PartComponentProps {
  const { streaming, text } = overrides
  return {
    part: {
      type: 'text',
      id: 'p1',
      sessionID: 's1',
      messageID: 'm1',
      text,
      metadata: {},
    } as any,
    info: {
      id: 'm1',
      role: 'assistant',
      sessionID: 's1',
      time: streaming ? { created: 0 } : { created: 0, completed: 1 },
    } as any,
    showCopy: false,
    turnDurationMs: undefined,
  } as unknown as PartComponentProps
}

describe('TextPart · stable tree across streaming flip', () => {
  it('keeps the markdown container node identity when streaming ends', () => {
    const { container, rerender } = render(
      <TextPart {...buildProps({ streaming: true, text: '```ts\nconst a = 1\n```' })} />,
    )

    const streamingRoot = container.querySelector('[data-component="markdown"]')
    expect(streamingRoot).not.toBeNull()

    rerender(
      <TextPart {...buildProps({ streaming: false, text: '```ts\nconst a = 1\n```' })} />,
    )

    const completedRoot = container.querySelector('[data-component="markdown"]')
    expect(completedRoot).toBe(streamingRoot)
  })
})
