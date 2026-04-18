import { describe, it, expect } from 'vitest'
import { render, fireEvent } from '@testing-library/react'
import { BasicTool } from '../basic-tool'

describe('BasicTool', () => {
  it('pending wraps title with shimmer and hides expand arrow', () => {
    const { container } = render(
      <BasicTool icon="mcp" status="pending" trigger={{ title: 'Reading' }}>
        detail
      </BasicTool>,
    )
    expect(
      container.querySelector('[data-component="text-shimmer"][data-active="true"]'),
    ).not.toBeNull()
    expect(container.querySelector('[data-slot="basic-tool-arrow"]')).toBeNull()
  })

  it('completed clicks trigger toggles open', () => {
    const { container } = render(
      <BasicTool icon="mcp" status="completed" trigger={{ title: 'Done' }}>
        body
      </BasicTool>,
    )
    expect(container.querySelector('[data-open="true"]')).toBeNull()
    fireEvent.click(container.querySelector('[data-component="tool-trigger"]')!)
    expect(container.querySelector('[data-open="true"]')).not.toBeNull()
  })

  it('locked blocks collapse click', () => {
    const { container } = render(
      <BasicTool
        icon="mcp"
        status="completed"
        trigger={{ title: 'Q' }}
        locked
        defaultOpen
      >
        body
      </BasicTool>,
    )
    fireEvent.click(container.querySelector('[data-component="tool-trigger"]')!)
    expect(container.querySelector('[data-open="true"]')).not.toBeNull()
  })
})
