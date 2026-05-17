import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { FileChip } from './file-chip'

describe('FileChip', () => {
  it('default state className uses neutral tokens, no accent-primary classes', () => {
    render(
      <FileChip filename="test.csv" sizeBytes={1024} mimeType="text/csv" />,
    )
    const button = screen.getByRole('button')
    expect(button.className).toContain('border-border-default')
    expect(button.className).toContain('bg-bg-soft')
    expect(button.className).toContain('text-text-base')
    // Hover/focus-visible/active tokens (only present when not disabled)
    expect(button.className).toContain('hover:bg-bg-subtle')
    expect(button.className).toContain('hover:border-border-strong')
    expect(button.className).toContain('focus-visible:ring-2')
    expect(button.className).toContain('focus-visible:ring-accent-primary/40')
    expect(button.className).toContain('focus-visible:ring-offset-bg-canvas')
    expect(button.className).toContain('active:bg-accent-primary/8')
    expect(button.className).toContain('active:border-accent-primary/40')
  })

  it('disabled state: shows disabled tokens, sets aria-disabled, blocks onClick', () => {
    const onClick = vi.fn()
    render(
      <FileChip
        filename="test.csv"
        sizeBytes={1024}
        mimeType="text/csv"
        disabled
        onClick={onClick}
      />,
    )
    const button = screen.getByRole('button') as HTMLButtonElement
    expect(button.className).toContain('opacity-60')
    expect(button.className).toContain('cursor-not-allowed')
    expect(button.getAttribute('aria-disabled')).toBe('true')
    expect(button.disabled).toBe(true)
    // Disabled state must not include hover/focus-visible/active variants
    expect(button.className).not.toContain('hover:bg-bg-subtle')
    expect(button.className).not.toContain('focus-visible:ring-2')
    expect(button.className).not.toContain('active:bg-accent-primary/8')

    fireEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('renders <img> with empty alt when thumbnailUrl is provided, hides icon span', () => {
    render(
      <FileChip
        filename="photo.png"
        sizeBytes={2048}
        mimeType="image/png"
        thumbnailUrl="blob:mock-url"
      />,
    )
    const img = document.querySelector('img')
    expect(img).not.toBeNull()
    expect(img?.getAttribute('alt')).toBe('')
    expect(img?.getAttribute('src')).toBe('blob:mock-url')
    // Lucide icon (rendered as <svg>) must not appear when a thumbnail is shown
    expect(document.querySelector('svg')).toBeNull()
  })

  it('non-image mimeType (no thumbnailUrl) does not render <img>, renders icon', () => {
    const { container } = render(
      <FileChip filename="data.csv" sizeBytes={1024} mimeType="text/csv" />,
    )
    expect(container.querySelector('img')).toBeNull()
    // Lucide icons render as <svg>
    const svg = container.querySelector('svg')
    expect(svg).not.toBeNull()
  })

  it('onClick fires once when clicked and not disabled', () => {
    const onClick = vi.fn()
    render(
      <FileChip
        filename="test.csv"
        sizeBytes={1024}
        mimeType="text/csv"
        onClick={onClick}
      />,
    )
    fireEvent.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('default aria-label is "{filename}, 预览"; custom ariaLabel overrides it', () => {
    const { rerender } = render(
      <FileChip filename="report.pdf" sizeBytes={1024} mimeType="application/pdf" />,
    )
    expect(screen.getByRole('button').getAttribute('aria-label')).toBe('report.pdf, 预览')

    rerender(
      <FileChip
        filename="report.pdf"
        sizeBytes={1024}
        mimeType="application/pdf"
        ariaLabel="自定义标签"
      />,
    )
    expect(screen.getByRole('button').getAttribute('aria-label')).toBe('自定义标签')
  })
})
