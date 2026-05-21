import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PartRenderer } from './part-renderer'

describe('PartRenderer', () => {
  const basePart = { id: 'p1', sessionID: 's1', messageID: 'm1' }

  it('renders text part', () => {
    render(<PartRenderer part={{ ...basePart, type: 'text', text: 'hello' }} />)
    expect(screen.getByText('hello')).toBeTruthy()
  })

  it('renders reasoning part with toggle', () => {
    render(<PartRenderer part={{ ...basePart, type: 'reasoning', text: 'thinking...' }} />)
    expect(screen.getByText('思考中…')).toBeTruthy()
  })

  it('renders step divider', () => {
    const { container } = render(<PartRenderer part={{ ...basePart, type: 'step-start' }} />)
    expect(container.querySelector('.border-t')).toBeTruthy()
  })

  it('returns null for unknown part type', () => {
    const { container } = render(<PartRenderer part={{ ...basePart, type: 'unknown' }} />)
    expect(container.firstChild).toBeNull()
  })
})
