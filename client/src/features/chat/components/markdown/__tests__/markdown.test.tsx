import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Markdown } from '../markdown'

describe('Markdown', () => {
  it('sanitizes script tags', () => {
    const { container } = render(<Markdown text="<script>alert(1)</script>hello" cacheKey="t1" />)
    expect(container.querySelector('script')).toBeNull()
  })

  it('renders headings', async () => {
    render(<Markdown text="# Title" cacheKey="t2" />)
    await screen.findByText('Title')
  })

  it('wraps pre blocks with copy button', async () => {
    const { container } = render(<Markdown text={'```js\nconst x = 1\n```'} cacheKey="t3" />)
    await new Promise((r) => setTimeout(r, 20))
    expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
    expect(container.querySelector('[data-slot="markdown-copy-button"]')).not.toBeNull()
  })
})
