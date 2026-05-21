import { beforeEach, describe, expect, it } from 'vitest'
import { useErTabsStore } from './er-tabs-store'
import type { ErDesignerPayload, ErInspectorPayload } from './er-tabs-payload-types'

const samplePayload: ErInspectorPayload = {
  kind: 'er_inspector',
  connectionId: 'c1',
  selection: ['users', 'orders'],
  neighborDepth: 1,
  layout: 'dagre-LR',
  tablesSnapshot: [],
  positions: {},
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('useErTabsStore - Inspector hydration', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
  })

  it('stores hydrated inspector payloads under tabId', () => {
    useErTabsStore.getState().hydrateInspector('tab-1', samplePayload)
    expect(useErTabsStore.getState().inspectors.get('tab-1')).toEqual({ ...samplePayload, __v: 1 })
  })

  it('hydrating an existing tabId replaces its payload', () => {
    useErTabsStore.getState().hydrateInspector('tab-1', samplePayload)
    const updated = { ...samplePayload, selection: ['products'] }
    useErTabsStore.getState().hydrateInspector('tab-1', updated)
    expect(useErTabsStore.getState().inspectors.get('tab-1')).toEqual({ ...updated, __v: 2 })
  })

  it('getInspectorView returns null for unknown tabId', () => {
    expect(useErTabsStore.getState().getInspectorView('missing')).toBeNull()
  })
})

describe('useErTabsStore - applyInspectorPatch', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map([['t-1', { ...samplePayload }]]),
      designers: new Map(),
    })
  })

  it('applies replace ops on /selection', () => {
    const { newVersion } = useErTabsStore.getState().applyInspectorPatch('t-1', [
      { op: 'replace', path: '/selection', value: ['products'] },
    ])

    expect(newVersion).toBeGreaterThan(0)
    expect(useErTabsStore.getState().inspectors.get('t-1')?.selection).toEqual(['products'])
  })

  it('add op on /virtualRelations/- assigns vr_<id>', () => {
    const { assignedIds } = useErTabsStore.getState().applyInspectorPatch('t-1', [
      {
        op: 'add',
        path: '/virtualRelations/-',
        value: {
          from: { table: 'a', column: 'x' },
          to: { table: 'b', column: 'y' },
          type: 'many_to_one',
        },
      },
    ])

    expect(assignedIds['/virtualRelations/0']).toMatch(/^vr_/)
    const stored = useErTabsStore.getState().inspectors.get('t-1')?.virtualRelations
    expect(stored).toHaveLength(1)
    expect(stored?.[0]?.id).toMatch(/^vr_/)
  })

  it('remove op via [id=<vrId>] addressing works', () => {
    useErTabsStore.getState().applyInspectorPatch('t-1', [
      {
        op: 'add',
        path: '/virtualRelations/-',
        value: {
          from: { table: 'a', column: 'x' },
          to: { table: 'b', column: 'y' },
          type: 'many_to_one',
        },
      },
    ])
    const id = useErTabsStore.getState().inspectors.get('t-1')?.virtualRelations[0]?.id
    expect(id).toBeDefined()

    useErTabsStore.getState().applyInspectorPatch('t-1', [
      { op: 'remove', path: `/virtualRelations[id=${id}]` },
    ])

    expect(useErTabsStore.getState().inspectors.get('t-1')?.virtualRelations).toHaveLength(0)
  })

  it('throws on unknown tabId', () => {
    expect(() => useErTabsStore.getState().applyInspectorPatch('missing', [
      { op: 'replace', path: '/selection', value: [] },
    ])).toThrow(/tab not found/i)
  })

  it('rejects unsupported path /tables (immutable_path_in_inspector)', () => {
    expect(() => useErTabsStore.getState().applyInspectorPatch('t-1', [
      { op: 'add', path: '/tables/-', value: {} },
    ])).toThrow(/immutable_path_in_inspector|immutable|not patchable/i)
  })
})

const designerPayload: ErDesignerPayload = {
  kind: 'er_designer',
  dialect: 'mysql',
  targetConnectionId: null,
  targetDatabase: null,
  targetSchema: null,
  tables: [],
  relations: [],
  positions: {},
  collapsed: [],
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('useErTabsStore - Designer hydration and view', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
  })

  it('stores hydrated designer payloads and projects nodes/edges by IDs', () => {
    const payload: ErDesignerPayload = {
      ...designerPayload,
      tables: [
        {
          id: 't_users',
          name: 'users',
          columns: [
            {
              id: 'c_users_id',
              name: 'id',
              type: 'BIGINT',
              nullable: false,
              isPrimaryKey: true,
              isAutoIncrement: true,
            },
          ],
          indexes: [],
          uniques: [],
        },
        {
          id: 't_orders',
          name: 'orders',
          columns: [
            {
              id: 'c_orders_user_id',
              name: 'user_id',
              type: 'BIGINT',
              nullable: false,
              isPrimaryKey: false,
              isAutoIncrement: false,
            },
          ],
          indexes: [],
          uniques: [],
        },
      ],
      relations: [
        {
          id: 'r_orders_users',
          fromTableId: 't_orders',
          fromColumnId: 'c_orders_user_id',
          toTableId: 't_users',
          toColumnId: 'c_users_id',
          type: 'many_to_one',
          constraintMethod: 'database_fk',
        },
      ],
    }

    useErTabsStore.getState().hydrateDesigner('d-1', payload)

    expect(useErTabsStore.getState().designers.get('d-1')).toEqual({ ...payload, __v: 1 })
    expect(useErTabsStore.getState().getDesignerView('d-1')).toEqual({
      nodes: [
        {
          id: 't_users',
          label: 'users',
          columns: [{ name: 'id', type: 'BIGINT', isPK: true, isFK: false }],
        },
        {
          id: 't_orders',
          label: 'orders',
          columns: [{ name: 'user_id', type: 'BIGINT', isPK: false, isFK: true }],
        },
      ],
      edges: [
        {
          id: 'r_orders_users',
          source: 't_orders',
          target: 't_users',
          sourceColumn: 'user_id',
          targetColumn: 'id',
          kind: 'fk',
        },
      ],
    })
  })
})

describe('useErTabsStore - applyDesignerPatch', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map(),
      designers: new Map([['d-1', { ...designerPayload }]]),
    })
  })

  it('add /tables/- assigns t_<id> and missing c_<id> column IDs', () => {
    const { assignedIds, newVersion } = useErTabsStore.getState().applyDesignerPatch('d-1', [
      {
        op: 'add',
        path: '/tables/-',
        value: {
          name: 'users',
          columns: [
            { name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true },
            { id: 'c_email_existing', name: 'email', type: 'VARCHAR(255)', nullable: false, isPrimaryKey: false, isAutoIncrement: false },
          ],
          indexes: [],
          uniques: [],
        },
      },
    ], { baseVersion: 0 })

    const tab = useErTabsStore.getState().designers.get('d-1')
    expect(newVersion).toBe(1)
    expect(assignedIds['/tables/0']).toMatch(/^t_/)
    expect(assignedIds['/tables/0/columns/0']).toMatch(/^c_/)
    expect(assignedIds['/tables/0/columns/1']).toBeUndefined()
    expect(tab?.tables[0]?.id).toBe(assignedIds['/tables/0'])
    expect(tab?.tables[0]?.columns[0]?.id).toBe(assignedIds['/tables/0/columns/0'])
    expect(tab?.tables[0]?.columns[1]?.id).toBe('c_email_existing')
  })

  it('add /tables[id=<tid>]/columns/- and /relations/- assign IDs', () => {
    useErTabsStore.getState().hydrateDesigner('d-1', {
      ...designerPayload,
      tables: [
        {
          id: 't_users',
          name: 'users',
          columns: [
            { id: 'c_users_id', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true },
          ],
          indexes: [],
          uniques: [],
        },
        {
          id: 't_orders',
          name: 'orders',
          columns: [],
          indexes: [],
          uniques: [],
        },
      ],
    })

    const columnResult = useErTabsStore.getState().applyDesignerPatch('d-1', [
      {
        op: 'add',
        path: '/tables[id=t_orders]/columns/-',
        value: { name: 'user_id', type: 'BIGINT', nullable: false, isPrimaryKey: false, isAutoIncrement: false },
      },
    ], { baseVersion: 1 })
    const cOrdersUserId = columnResult.assignedIds['/tables[id=t_orders]/columns/0']

    const relationResult = useErTabsStore.getState().applyDesignerPatch('d-1', [
      {
        op: 'add',
        path: '/relations/-',
        value: {
          fromTableId: 't_orders',
          fromColumnId: cOrdersUserId,
          toTableId: 't_users',
          toColumnId: 'c_users_id',
          type: 'many_to_one',
          constraintMethod: 'database_fk',
        },
      },
    ], { baseVersion: 2 })

    const tab = useErTabsStore.getState().designers.get('d-1')
    expect(cOrdersUserId).toMatch(/^c_/)
    expect(relationResult.assignedIds['/relations/0']).toMatch(/^r_/)
    expect(tab?.tables[1]?.columns[0]?.id).toBe(cOrdersUserId)
    expect(tab?.relations[0]?.id).toBe(relationResult.assignedIds['/relations/0'])
  })

  it('supports replace and remove with [id=<id>] addressing', () => {
    useErTabsStore.getState().hydrateDesigner('d-1', {
      ...designerPayload,
      tables: [
        {
          id: 't_users',
          name: 'users',
          columns: [
            { id: 'c_email', name: 'email', type: 'TEXT', nullable: true, isPrimaryKey: false, isAutoIncrement: false },
          ],
          indexes: [],
          uniques: [],
        },
      ],
    })

    useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'replace', path: '/tables[id=t_users]/columns[id=c_email]/type', value: 'VARCHAR(255)' },
      { op: 'remove', path: '/tables[id=t_users]/columns[id=c_email]' },
    ], { baseVersion: 1 })

    expect(useErTabsStore.getState().designers.get('d-1')?.tables[0]?.columns).toEqual([])
  })

  it('throws missing_base_version when a structural op has no baseVersion', () => {
    expect(() => useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/tables/-', value: { name: 'a', columns: [], indexes: [], uniques: [] } },
    ])).toThrowError(/missing_base_version/)

    try {
      useErTabsStore.getState().applyDesignerPatch('d-1', [
        { op: 'add', path: '/tables/-', value: { name: 'a', columns: [], indexes: [], uniques: [] } },
      ])
      throw new Error('expected missing_base_version')
    } catch (error) {
      expect(error).toMatchObject({
        code: 'missing_base_version',
        currentVersion: 0,
        aiHint: expect.stringContaining('baseVersion'),
      })
    }
  })

  it('rejects baseVersion="auto" on structural paths (strict only — see design doc Q12)', () => {
    expect(() => useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/tables/-', value: { name: 'a', columns: [], indexes: [], uniques: [] }, baseVersion: 'auto' },
    ])).toThrowError(/missing_base_version/)
  })

  it('view path patches accept baseVersion="auto" silently (last-write-wins)', () => {
    const { newVersion } = useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'replace', path: '/viewport', value: { x: 1, y: 2, zoom: 0.9 }, baseVersion: 'auto' },
    ])

    expect(newVersion).toBe(1)
    expect(useErTabsStore.getState().designers.get('d-1')?.viewport).toEqual({ x: 1, y: 2, zoom: 0.9 })
  })

  it('throws a structured conflict error with English aiHint for stale strict-path versions', () => {
    useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/tables/-', value: { name: 'a', columns: [], indexes: [], uniques: [] } },
    ], { baseVersion: 0 })

    expect(() => useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/tables/-', value: { name: 'b', columns: [], indexes: [], uniques: [] }, baseVersion: 0 },
    ])).toThrow(/conflict_with_concurrent_edit/)

    try {
      useErTabsStore.getState().applyDesignerPatch('d-1', [
        { op: 'add', path: '/tables/-', value: { name: 'b', columns: [], indexes: [], uniques: [] }, expectedVersion: 0 },
      ])
      throw new Error('expected conflict')
    } catch (error) {
      expect(error).toMatchObject({
        code: 'conflict_with_concurrent_edit',
        currentVersion: 1,
        baseVersion: 0,
        aiHint: expect.stringContaining('Re-read'),
      })
    }
  })

  it('allows view path patches without a baseVersion and rejects invalid paths', () => {
    const { newVersion } = useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'replace', path: '/viewport', value: { x: 10, y: 20, zoom: 0.75 } },
      { op: 'replace', path: '/collapsed', value: ['t_users'] },
    ])

    expect(newVersion).toBe(1)
    expect(useErTabsStore.getState().designers.get('d-1')?.viewport).toEqual({ x: 10, y: 20, zoom: 0.75 })
    expect(() => useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/notes/-', value: {} },
    ])).toThrow(/invalid_path/)
  })

  it('throws on unknown designer tabId', () => {
    expect(() => useErTabsStore.getState().applyDesignerPatch('missing', [
      { op: 'replace', path: '/viewport', value: { x: 0, y: 0, zoom: 1 } },
    ])).toThrow(/tab not found/i)
  })
})
