import { fireEvent, render, screen } from '@testing-library/react'
import { ReactFlowProvider } from '@xyflow/react'
import { describe, expect, it, vi } from 'vitest'
import { ErTableNode, getColumnTypeOptions } from '../ErTableNode'
import type { ErColumnMeta } from '@/features/stage/stores/er-tabs-payload-types'
import type { ErTableNodeData } from '../ErTableNode'

const columns: ErColumnMeta[] = [
  { id: 'c_id', name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false } as ErColumnMeta & { id: string },
  { id: 'c_account_id', name: 'account_id', type: 'BIGINT', isPK: false, isFK: true, nullable: false } as ErColumnMeta & { id: string },
  { id: 'c_email', name: 'email', type: 'VARCHAR(255)', isPK: false, isFK: false, nullable: false } as ErColumnMeta & { id: string },
]

const data: ErTableNodeData = {
  table: { name: 'users', columns, fkOut: [] },
  columns,
  collapsed: false,
  mode: 'inspector' as const,
}

function renderNode(nodeData: ErTableNodeData = data) {
  return render(
    <ReactFlowProvider>
      <ErTableNode
        id="users"
        data={nodeData}
        selected={false}
        dragging={false}
        draggable={false}
        selectable={false}
        deletable={false}
        type="erTable"
        zIndex={0}
        isConnectable={false}
        positionAbsoluteX={0}
        positionAbsoluteY={0}
      />
    </ReactFlowProvider>,
  )
}

describe('<ErTableNode mode="inspector">', () => {
  it('renders the table name and read-only lock', () => {
    renderNode()

    expect(screen.getByText('users')).toBeInTheDocument()
    expect(screen.getByLabelText(/read-only/i)).toBeInTheDocument()
    expect(screen.getByTestId('er-mode-indicator')).toHaveAttribute('data-er-mode', 'inspector')
  })

  it('renders columns with type metadata and PK/FK icons', () => {
    renderNode()

    expect(screen.getByText('id')).toBeInTheDocument()
    expect(screen.getAllByText('BIGINT')).toHaveLength(2)
    expect(screen.getByText('account_id')).toBeInTheDocument()
    expect(screen.getByText('email')).toBeInTheDocument()
    expect(screen.getByText('VARCHAR(255)')).toBeInTheDocument()
    expect(screen.getByLabelText('primary key')).toBeInTheDocument()
    expect(screen.getByLabelText('foreign key')).toBeInTheDocument()
    expect(screen.getByTestId('er-row-id')).toHaveAttribute('data-er-row-role', 'pk')
    expect(screen.getByTestId('er-row-account_id')).toHaveAttribute('data-er-row-role', 'fk')
    expect(screen.getByTestId('er-row-email')).toHaveAttribute('data-er-row-role', 'regular')
    expect(document.querySelector('.react-flow__handle.react-flow__handle-left')?.firstElementChild?.className)
      .toContain('size-4')
    expect(document.querySelector('.react-flow__handle.react-flow__handle-right')?.firstElementChild?.className)
      .toContain('size-4')
  })

  it('hides column rows when collapsed', () => {
    renderNode({ ...data, collapsed: true })

    expect(screen.queryByText('id')).not.toBeInTheDocument()
    expect(screen.queryByText('BIGINT')).not.toBeInTheDocument()
  })

  it('uses a subtle header divider when collapsed in designer mode', () => {
    renderNode({
      ...data,
      mode: 'designer',
      collapsed: true,
      onAddColumn: vi.fn(),
      onUpdateColumn: vi.fn(),
      onDeleteColumn: vi.fn(),
    })

    const header = screen.getByLabelText('Table users').querySelector('header')

    expect(header?.className).toContain('border-border-subtle')
    expect(header?.className).not.toContain('border-border-default')
  })

  it('renders an N more button when columns exceed twelve', () => {
    const many = Array.from({ length: 15 }, (_, index): ErColumnMeta => ({
      name: `c${index + 1}`,
      type: 'INT',
      isPK: false,
      isFK: false,
      nullable: true,
    }))

    renderNode({ ...data, columns: many, table: { ...data.table, columns: many } })

    expect(screen.getByRole('button', { name: /3 more/i })).toBeInTheDocument()
    expect(screen.getByText('c12')).toBeInTheDocument()
    expect(screen.queryByText('c13')).not.toBeInTheDocument()
  })

  it('renders no add-column affordance in viewer mode', () => {
    renderNode()

    expect(screen.queryByRole('button', { name: /add column|添加列/i })).not.toBeInTheDocument()
  })

  it('renders an empty viewer state without add-column affordance', () => {
    renderNode({ ...data, columns: [], table: { ...data.table, columns: [] } })

    expect(screen.getByText(/no columns yet|此表暂无列/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /add column|添加列/i })).not.toBeInTheDocument()
  })
})

describe('<ErTableNode mode="designer">', () => {
  it('offers dialect-aware native MySQL column types while preserving custom values', () => {
    expect(getColumnTypeOptions('mysql')).toEqual(expect.arrayContaining([
      'TINYINT',
      'MEDIUMINT',
      'DOUBLE',
      'JSON',
      "ENUM('value')",
      'GEOMETRY',
      'MULTIPOLYGON',
    ]))

    expect(getColumnTypeOptions('mysql', 'CUSTOM_DOMAIN')).toEqual(expect.arrayContaining([
      'CUSTOM_DOMAIN',
      'VARCHAR(255)',
    ]))
  })

  it('shows editable columns, tighter left alignment, and add-column affordance', () => {
    renderNode({
      ...data,
      mode: 'designer',
      onUpdateTable: vi.fn(),
      onAddColumn: vi.fn(),
      onUpdateColumn: vi.fn(),
      onDeleteColumn: vi.fn(),
    } as unknown as ErTableNodeData)

    expect(screen.queryByRole('button', { name: /rename|重命名/i })).not.toBeInTheDocument()
    expect(screen.getByDisplayValue('users')).toBeInTheDocument()
    expect(screen.getByDisplayValue('email')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /type for email/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add column|添加列/i })).toBeInTheDocument()

    const tableNode = screen.getByLabelText('Table users')
    expect(tableNode.className).toContain('w-80')

    // Outer Handle is a transparent anchor; the visible ball is an inner <span>
    // and is always rendered. Verify the inner ball uses the neutral
    // solid (target) / ring (source) shape contract and pops on direct hover.
    const targetHandle = document.querySelector('.react-flow__handle.react-flow__handle-left')
    const sourceHandle = document.querySelector('.react-flow__handle.react-flow__handle-right')

    const targetBall = targetHandle?.firstElementChild as HTMLElement | null
    const sourceBall = sourceHandle?.firstElementChild as HTMLElement | null
    expect(targetBall).toHaveAttribute('data-er-handle-shape', 'solid')
    expect(targetBall?.className).toContain('bg-border-strong')
    expect(targetBall?.className).toContain('size-[18px]')
    expect(targetBall?.className).toContain('hover:scale-125')
    expect(sourceBall).toHaveAttribute('data-er-handle-shape', 'ring')
    expect(sourceBall?.className).toContain('border-border-strong')
    expect(sourceBall?.className).toContain('size-[18px]')
    expect(sourceBall?.className).toContain('hover:scale-125')
    expect(screen.getByTestId('er-row-email').className).toContain('pl-0')
    expect(screen.getByTestId('er-row-email').className).toContain('pr-8')
    expect(screen.getByTestId('er-row-email').className).toContain('gap-2.5')
    expect(screen.getByLabelText('primary key').parentElement?.className).toContain('w-6')
    expect(screen.getByRole('combobox', { name: /type for email/i }).className).toContain('w-32')
  })

  it('renders NN pills only for non-null columns', () => {
    renderNode({
      ...data,
      mode: 'designer',
      columns: [
        { ...columns[0], nullable: false },
        { ...columns[1], nullable: true },
        { ...columns[2], nullable: true },
      ],
      table: {
        ...data.table,
        columns: [
          { ...columns[0], nullable: false },
          { ...columns[1], nullable: true },
          { ...columns[2], nullable: true },
        ],
      },
    })

    expect(document.querySelectorAll('[data-er-nn-pill]')).toHaveLength(1)
    expect(screen.getByTestId('er-row-id').querySelector('[data-er-nn-pill]')).toBeInTheDocument()
    expect(screen.getByTestId('er-row-account_id').querySelector('[data-er-nn-pill]')).not.toBeInTheDocument()
    expect(screen.getByTestId('er-row-email').querySelector('[data-er-nn-pill]')).not.toBeInTheDocument()
  })

  it('keeps the delete button hidden until row hover or keyboard focus', () => {
    renderNode({
      ...data,
      mode: 'designer',
      onDeleteColumn: vi.fn(),
    })

    const deleteButton = screen.getByRole('button', { name: /delete column email/i })

    expect(deleteButton.className).toContain('opacity-0')
    expect(deleteButton.className).toContain('group-hover:opacity-100')
    expect(deleteButton.className).toContain('focus-visible:opacity-100')
  })

  it('renders an empty designer state with add-column affordance', () => {
    renderNode({
      ...data,
      mode: 'designer',
      columns: [],
      table: { ...data.table, columns: [] },
      onAddColumn: vi.fn(),
    })

    expect(screen.getByText(/no columns yet|此表暂无列/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add column|添加列/i })).toBeInTheDocument()
  })

  it('triggers designer callbacks for adding, updating, and deleting columns', () => {
    const onAddColumn = vi.fn()
    const onUpdateTable = vi.fn()
    const onUpdateColumn = vi.fn()
    const onDeleteColumn = vi.fn()

    renderNode({
      ...data,
      mode: 'designer',
      onUpdateTable,
      onAddColumn,
      onUpdateColumn,
      onDeleteColumn,
    } as unknown as ErTableNodeData)

    fireEvent.click(screen.getByRole('button', { name: /add column|添加列/i }))
    fireEvent.change(screen.getByDisplayValue('users'), { target: { value: 'accounts' } })
    fireEvent.change(screen.getByDisplayValue('email'), { target: { value: 'email_address' } })
    fireEvent.click(screen.getByRole('button', { name: /delete column email/i }))

    expect(onAddColumn).toHaveBeenCalled()
    expect(onUpdateTable).toHaveBeenCalledWith('users', { name: 'accounts' })
    expect(onUpdateColumn).toHaveBeenCalledWith('c_email', { name: 'email_address' })
    expect(onDeleteColumn).toHaveBeenCalledWith('c_email')
  })

  it('focuses and selects the table name when rename is requested', () => {
    const onNameFocusHandled = vi.fn()

    renderNode({
      ...data,
      mode: 'designer',
      shouldFocusName: true,
      onNameFocusHandled,
    } as ErTableNodeData)

    const tableNameInput = screen.getByDisplayValue('users')

    expect(tableNameInput).toHaveFocus()
    expect(onNameFocusHandled).toHaveBeenCalledWith('users')
  })

  it('dispatches table context menu coordinates on right click', () => {
    const onOpenContextMenu = vi.fn()
    renderNode({
      ...data,
      mode: 'designer',
      onOpenContextMenu,
    })

    fireEvent.contextMenu(screen.getByLabelText('Table users'), {
      clientX: 42,
      clientY: 84,
    })

    expect(onOpenContextMenu).toHaveBeenCalledWith({
      tableId: 'users',
      x: 42,
      y: 84,
    })
  })

  it('uses the React Flow node id as tableId, not the display name', () => {
    const onOpenContextMenu = vi.fn()
    render(
      <ReactFlowProvider>
        <ErTableNode
          id="t_users_42"
          data={{ ...data, mode: 'designer', onOpenContextMenu }}
          selected={false}
          dragging={false}
          draggable={false}
          selectable={false}
          deletable={false}
          type="erTable"
          zIndex={0}
          isConnectable={false}
          positionAbsoluteX={0}
          positionAbsoluteY={0}
        />
      </ReactFlowProvider>,
    )

    fireEvent.contextMenu(screen.getByLabelText('Table users'), {
      clientX: 10,
      clientY: 20,
    })

    expect(onOpenContextMenu).toHaveBeenCalledWith({
      tableId: 't_users_42',
      x: 10,
      y: 20,
    })
  })
})
