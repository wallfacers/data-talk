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

  it('uses a compact select-style chevron for the details toggle', () => {
    const { container } = render(
      <BasicTool icon="mcp" status="completed" trigger={{ title: 'Done' }}>
        body
      </BasicTool>,
    )

    const arrow = container.querySelector('[data-slot="basic-tool-arrow"]')

    expect(arrow).not.toBeNull()
    expect(arrow!.querySelector('svg')).not.toBeNull()
    expect(arrow!.textContent).not.toContain('▼')
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

  it('wraps long trigger args inside the tool card', () => {
    const { container } = render(
      <BasicTool
        icon="mcp"
        status="completed"
        trigger={{
          title: 'datatalk_ui_list',
          args: [
            '__dtOpenCodeSessionId=ses_241b8620bffeURHQFdER7THDyw__dtCallId=call_83a2bab1d31b4b5fbf4d6556__dtBridgeNonce=89a6862c-4940-4539-ab6b-942e5d3d01fc',
          ],
        }}
      >
        body
      </BasicTool>,
    )

    const trigger = container.querySelector('[data-component="tool-trigger"]')
    const arg = container.querySelector('[data-slot="basic-tool-tool-arg"]')

    expect(trigger).not.toBeNull()
    expect(arg).not.toBeNull()
    expect(trigger!).toHaveClass('items-start')
    expect(arg!.parentElement).toHaveClass('flex-wrap')
    expect(arg!).toHaveClass('break-all')
  })
})
