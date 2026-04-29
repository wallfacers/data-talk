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

describe('<ErToolbar mode="designer">', () => {
  it('renders designer controls and dialect picker', () => {
    render(
      <ErToolbar
        mode="designer"
        dialect="mysql"
        hasTarget={false}
        onAddTable={vi.fn()}
        onAutoLayout={vi.fn()}
        onFitView={vi.fn()}
        onBindTarget={vi.fn()}
        onDiffVsDb={vi.fn()}
        onGenerateDdl={vi.fn()}
        onChangeDialect={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: /add table|添加表/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /bind target|绑定目标/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /diff vs db|对比数据库/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /generate ddl|生成 DDL/i })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /dialect|方言/i })).toBeInTheDocument()
  })

  it('disables target-dependent actions until a target is bound', () => {
    render(
      <ErToolbar
        mode="designer"
        dialect="mysql"
        hasTarget={false}
        onAddTable={vi.fn()}
        onAutoLayout={vi.fn()}
        onFitView={vi.fn()}
        onBindTarget={vi.fn()}
        onDiffVsDb={vi.fn()}
        onGenerateDdl={vi.fn()}
        onChangeDialect={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: /diff vs db|对比数据库/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /generate ddl|生成 DDL/i })).toBeDisabled()
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
