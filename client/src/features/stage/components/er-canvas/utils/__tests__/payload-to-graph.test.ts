import { describe, expect, it } from 'vitest'
import { inspectorToGraph } from '../payload-to-graph'
import type { ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'

const payload: ErInspectorPayload = {
  kind: 'er_inspector',
  connectionId: 'c1',
  selection: ['users', 'orders'],
  neighborDepth: 1,
  layout: 'dagre-LR',
  tablesSnapshot: [
    {
      name: 'users',
      columns: [
        { name: 'id', type: 'BIGINT', nullable: false, isPK: true, isFK: false },
        { name: 'email', type: 'VARCHAR(255)', nullable: false, isPK: false, isFK: false },
      ],
      fkOut: [],
    },
    {
      name: 'orders',
      columns: [
        { name: 'id', type: 'BIGINT', nullable: false, isPK: true, isFK: false },
        { name: 'user_id', type: 'BIGINT', nullable: false, isPK: false, isFK: true },
      ],
      fkOut: [{ fromColumn: 'user_id', toTable: 'users', toColumn: 'id' }],
    },
  ],
  positions: { users: { x: 0, y: 0 }, orders: { x: 320, y: 0 } },
  collapsed: ['users'],
  virtualRelations: [
    {
      id: 'vr1',
      from: { table: 'orders', column: 'email' },
      to: { table: 'users', column: 'email' },
      type: 'many_to_one',
    },
  ],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('inspectorToGraph', () => {
  it('emits one node per snapshot table with collapsed flag and position', () => {
    const { nodes } = inspectorToGraph(payload)

    expect(nodes).toHaveLength(2)
    expect(nodes[0]).toMatchObject({
      id: 'users',
      position: { x: 0, y: 0 },
      data: expect.objectContaining({ collapsed: true, columns: expect.any(Array) }),
    })
    expect(nodes[1].data.collapsed).toBe(false)
  })

  it('emits one fk edge and one virtual edge with correct kinds', () => {
    const { edges } = inspectorToGraph(payload)

    expect(edges).toHaveLength(2)
    expect(edges[0]).toMatchObject({
      source: 'orders',
      target: 'users',
      sourceHandle: 'user_id-source',
      targetHandle: 'id-target',
      data: expect.objectContaining({ kind: 'fk' }),
    })
    expect(edges[1]).toMatchObject({
      source: 'orders',
      target: 'users',
      data: expect.objectContaining({ kind: 'virtual' }),
    })
  })
})
