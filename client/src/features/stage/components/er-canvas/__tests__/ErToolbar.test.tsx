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

    const badge = screen.getByTestId('er-mode-badge')
    expect(badge).toHaveAttribute('role', 'status')
    expect(badge).toHaveAttribute('data-er-mode', 'inspector')
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

describe('<ErToolbar mode="designer">', () => {
  it('renders designer controls and dialect picker', () => {
    renderDesignerToolbar({ hasTarget: false })

    const badge = screen.getByTestId('er-mode-badge')
    expect(badge).toHaveAttribute('role', 'status')
    expect(badge).toHaveAttribute('aria-live', 'off')
    expect(badge).toHaveAttribute('data-er-mode', 'designer')
    expect(badge.querySelector('svg')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add table|添加表/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /bind target|绑定目标/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /diff vs db|对比数据库/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /generate ddl|生成 DDL/i })).toBeInTheDocument()
    const dialectPicker = screen.getByTestId('er-toolbar-dialect')
    expect(dialectPicker).toBeInTheDocument()
    expect(dialectPicker.tagName).toBe('BUTTON')
    expect(dialectPicker).toHaveAttribute('data-slot', 'select-trigger')
  })

  it('disables Diff vs DB and Generate DDL when hasTarget is false', () => {
    renderDesignerToolbar({ hasTarget: false })

    const diffButton = screen.getByRole('button', { name: /diff vs db|对比数据库/i })
    const ddlButton = screen.getByRole('button', { name: /generate ddl|生成 DDL/i })

    expect(diffButton).toBeDisabled()
    expect(ddlButton).toBeDisabled()
    expect(diffButton).not.toHaveAttribute('title')
    expect(ddlButton).not.toHaveAttribute('title')
  })

  it('keeps disabled toolbar hints available to pointer and assistive tech', () => {
    renderDesignerToolbar({ hasTarget: false })

    const diffButton = screen.getByRole('button', { name: /diff vs db|对比数据库/i })
    const hintId = diffButton.getAttribute('aria-describedby')

    expect(hintId).toBeTruthy()
    expect(document.getElementById(hintId ?? '')).toHaveTextContent(/bind a target|绑定/i)
    expect(diffButton.parentElement?.className).toContain('pointer-events-auto')
    expect(diffButton.className).toContain('pointer-events-none')
  })

  it('enables Diff vs DB and Generate DDL when hasTarget is true', () => {
    renderDesignerToolbar({ hasTarget: true })

    expect(screen.getByRole('button', { name: /diff vs db|对比数据库/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /generate ddl|生成 DDL/i })).toBeEnabled()
  })

  it('emits designer callbacks', () => {
    const onAddTable = vi.fn()
    const onGenerateDdl = vi.fn()

    render(
      <ErToolbar
        mode="designer"
        dialect="mysql"
        hasTarget
        onAddTable={onAddTable}
        onAutoLayout={vi.fn()}
        onFitView={vi.fn()}
        onBindTarget={vi.fn()}
        onDiffVsDb={vi.fn()}
        onGenerateDdl={onGenerateDdl}
        onChangeDialect={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /add table|添加表/i }))
    fireEvent.click(screen.getByRole('button', { name: /generate ddl|生成 DDL/i }))

    expect(onAddTable).toHaveBeenCalled()
    expect(onGenerateDdl).toHaveBeenCalled()
  })
})

function renderDesignerToolbar({ hasTarget }: { hasTarget: boolean }) {
  return render(
    <ErToolbar
      mode="designer"
      dialect="mysql"
      hasTarget={hasTarget}
      onAddTable={vi.fn()}
      onAutoLayout={vi.fn()}
      onFitView={vi.fn()}
      onBindTarget={vi.fn()}
      onDiffVsDb={vi.fn()}
      onGenerateDdl={vi.fn()}
      onChangeDialect={vi.fn()}
    />,
  )
}
