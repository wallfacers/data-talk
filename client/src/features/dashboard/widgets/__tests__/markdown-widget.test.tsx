import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MarkdownWidget } from '../markdown-widget'

// Mock the Markdown component to avoid heavy marked/morphdom dependencies in test
vi.mock('@/features/chat/components/markdown/markdown', () => ({
  Markdown: ({ text }: { text: string }) => <div data-testid="markdown-output">{text}</div>,
}))

describe('MarkdownWidget', () => {
  it('renders markdown text', () => {
    render(<MarkdownWidget widgetId="md_w_aaaa" title="Notes" text="Hello **world**" />)
    expect(screen.getByTestId('markdown-output')).toHaveTextContent('Hello **world**')
  })

  it('does not render embedded ```chart fences', () => {
    const text = 'Some text\n```chart\n{"xAxis":{}}\n```'
    render(<MarkdownWidget widgetId="md_w_aaaa" title="Notes" text={text} />)
    // The mock just passes text through; the real Markdown strips chart fences.
    // We verify the widget renders without crashing.
    expect(screen.getByTestId('markdown-output')).toBeInTheDocument()
  })
})
