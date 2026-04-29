import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErToolbar } from '../ErToolbar'

const baseProps = {
  mode: 'inspector' as const,
  neighborDepth: 1 as const,
  onRefresh: vi.fn(),
  onAutoLayout: vi.fn(),
  onFitView: vi.fn(),
  onChangeNeighborDepth: vi.fn(),
  onAddVirtualRelation: vi.fn(),
  onForkToDesigner: vi.fn(),
}

describe('<ErToolbar mode="inspector">', () => {
  it('renders inspector controls', () => {
    render(<ErToolbar {...baseProps} />)

    expect(screen.getByRole('button', { name: /刷新/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /自动布局/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /适应视图/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /添加虚拟关系/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /派生到设计器/ })).toBeInTheDocument()
    expect(screen.getByLabelText(/邻居深度/)).toBeInTheDocument()
  })

  it('triggers onAutoLayout when its button is clicked', () => {
    const onAutoLayout = vi.fn()
    render(<ErToolbar {...baseProps} onAutoLayout={onAutoLayout} />)

    fireEvent.click(screen.getByRole('button', { name: /自动布局/ }))

    expect(onAutoLayout).toHaveBeenCalled()
  })
})
