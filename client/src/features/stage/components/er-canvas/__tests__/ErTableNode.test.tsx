import { render, screen } from '@testing-library/react'
import { ReactFlowProvider } from '@xyflow/react'
import { describe, expect, it } from 'vitest'
import { ErTableNode } from '../ErTableNode'
import type { ErColumnMeta } from '@/features/stage/stores/er-tabs-payload-types'

const columns: ErColumnMeta[] = [
  { name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false },
  { name: 'account_id', type: 'BIGINT', isPK: false, isFK: true, nullable: false },
  { name: 'email', type: 'VARCHAR(255)', isPK: false, isFK: false, nullable: false },
]

const data = {
  table: { name: 'users', columns, fkOut: [] },
  columns,
  collapsed: false,
  mode: 'inspector' as const,
}

function renderNode(nodeData = data) {
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
