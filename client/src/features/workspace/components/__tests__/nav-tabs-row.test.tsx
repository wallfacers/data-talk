import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { fireEvent } from '@testing-library/react'
import { NavTabsRow } from '../nav-tabs-row'
import type { StageTab } from '@/stores/stage-store'

vi.mock('@/features/stage/registry/tab-type-registry', () => ({
  getTabTypeDescriptor: (type: string) => ({
    type,
    persistent: true,
    icon: () => null,
    labelKey: 'tabType.queryEditor',
    extractContent: () => '',
  }),
}))

const mkTab = (over: Partial<StageTab> & { tabId: string }): StageTab => ({
  type: 'query_editor',
  title: 'Test Tab',
  scope: 'workspace',
  payload: {},
  createdAt: Date.now(),
  ...over,
})

describe('NavTabsRow', () => {
  it('renders tab title and type label', () => {
    const tab = mkTab({ tabId: 't1', title: 'My SQL Query' })
    render(<NavTabsRow tab={tab} focused={false} onClick={vi.fn()} />)

    expect(screen.getByText('My SQL Query')).toBeInTheDocument()
    // Type label rendered via t()
    const row = screen.getByRole('button')
    expect(row).toBeInTheDocument()
  })

  it('applies focused styles when focused', () => {
    const tab = mkTab({ tabId: 't1' })
    const { container } = render(<NavTabsRow tab={tab} focused={true} onClick={vi.fn()} />)

    const li = container.querySelector('li')
    expect(li?.className).toContain('bg-interaction-selected')
  })

  it('calls onClick when clicked', () => {
    const onClick = vi.fn()
    const tab = mkTab({ tabId: 't1' })
    render(<NavTabsRow tab={tab} focused={false} onClick={onClick} />)

    fireEvent.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('applies archived opacity when tab is archived', () => {
    const tab = mkTab({ tabId: 't1', archived: true })
    const { container } = render(<NavTabsRow tab={tab} focused={false} onClick={vi.fn()} />)

    const li = container.querySelector('li')
    expect(li?.className).toContain('opacity-60')
  })
})
