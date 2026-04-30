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
  })

  it('hides column rows when collapsed', () => {
    renderNode({ ...data, collapsed: true })

    expect(screen.queryByText('id')).not.toBeInTheDocument()
    expect(screen.queryByText('BIGINT')).not.toBeInTheDocument()
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

  it('shows a pencil icon, editable columns, two-tone handles, and add-column affordance', () => {
    renderNode({
      ...data,
      mode: 'designer',
      onAddColumn: vi.fn(),
      onUpdateColumn: vi.fn(),
      onDeleteColumn: vi.fn(),
    })

    expect(screen.getByLabelText(/editable designer table/i)).toBeInTheDocument()
    expect(screen.getByDisplayValue('email')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /type for email/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add column|添加列/i })).toBeInTheDocument()

    const tableNode = screen.getByLabelText('Table users')
    expect(tableNode.className).toContain('w-80')

    // Outer Handle is a transparent anchor; the visible ball is an inner <span>
    // and is always rendered (no hover-to-reveal). Verify the inner ball uses
    // the success (target) / danger (source) color and pops on direct hover.
    const targetHandle = document.querySelector('.react-flow__handle.react-flow__handle-left')
    const sourceHandle = document.querySelector('.react-flow__handle.react-flow__handle-right')

    const targetBall = targetHandle?.firstElementChild as HTMLElement | null
    const sourceBall = sourceHandle?.firstElementChild as HTMLElement | null
    expect(targetBall?.className).toContain('bg-[var(--dt-status-success)]')
    expect(targetBall?.className).toContain('hover:scale-[1.15]')
    expect(sourceBall?.className).toContain('bg-[var(--dt-status-danger)]')
    expect(sourceBall?.className).toContain('hover:scale-[1.15]')
  })

  it('triggers designer callbacks for adding, updating, and deleting columns', () => {
    const onAddColumn = vi.fn()
    const onUpdateColumn = vi.fn()
    const onDeleteColumn = vi.fn()

    renderNode({
      ...data,
      mode: 'designer',
      onAddColumn,
      onUpdateColumn,
      onDeleteColumn,
    })

    fireEvent.click(screen.getByRole('button', { name: /add column|添加列/i }))
    fireEvent.change(screen.getByDisplayValue('email'), { target: { value: 'email_address' } })
    fireEvent.click(screen.getByRole('button', { name: /delete column email/i }))

    expect(onAddColumn).toHaveBeenCalled()
    expect(onUpdateColumn).toHaveBeenCalledWith('c_email', { name: 'email_address' })
    expect(onDeleteColumn).toHaveBeenCalledWith('c_email')
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
