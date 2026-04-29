import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { ErInspectorAdapter } from '../ErInspectorAdapter'

const samplePayload = {
  kind: 'er_inspector' as const,
  connectionId: 'c1',
  selection: ['users'],
  neighborDepth: 1 as const,
  layout: 'dagre-LR' as const,
  tablesSnapshot: [],
  positions: {},
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('ErInspectorAdapter', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map([['t-1', { ...samplePayload }]]),
      designers: new Map(),
    })
  })

  it('read("state") returns the current payload', () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    expect(adapter.read('state')).toMatchObject({ kind: 'er_inspector', selection: ['users'] })
  })

  it('read("schema") returns capabilities and patch path whitelist', () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    const schema = adapter.read('schema') as { patchCapabilities: { pathPattern: string }[] }

    expect(schema.patchCapabilities.some((capability) => capability.pathPattern === '/selection')).toBe(true)
    expect(schema.patchCapabilities.some((capability) => capability.pathPattern === '/virtualRelations/-')).toBe(true)
  })

  it('patch /selection replace mutates payload and returns applied', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.patch([{ op: 'replace', path: '/selection', value: ['products'] }])

    expect(result.status).toBe('applied')
    expect(useErTabsStore.getState().inspectors.get('t-1')?.selection).toEqual(['products'])
  })

  it('patch returns assignedIds for newly-added virtual relations', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.patch([
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

    expect(result.status).toBe('applied')
    expect(result.newVersion).toBeGreaterThan(0)
    expect(result.assignedIds?.['/virtualRelations/0']).toMatch(/^vr_/)
  })

  it('patch immutable_path returns error', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.patch([{ op: 'add', path: '/tables/-', value: {} }])

    expect(result.status).toBe('error')
    expect(result.message).toMatch(/immutable_path_in_inspector/)
  })
})

describe('ErInspectorAdapter.exec', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map([['t-1', { ...samplePayload }]]),
      designers: new Map(),
    })
    useStageStore.getState().resetSessionResources()
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        nodes: [{ name: 'users', columns: [], fkOut: [] }],
        edges: [],
        summary: 'users + 0 neighbors, 1 table / 0 edges',
        warnings: [],
      }),
    }) as never
  })

  it('refresh fetches /api/er/seed-inspector and writes tablesSnapshot', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.exec('refresh')

    expect(result.success).toBe(true)
    expect(useErTabsStore.getState().inspectors.get('t-1')?.tablesSnapshot).toHaveLength(1)
  })

  it('add_neighbors with table issues a refresh with selection union table', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.exec('add_neighbors', { table: 'orders' })

    expect(result.success).toBe(true)
    expect((global.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body).toContain('"orders"')
  })

  it('fit_view writes /viewport via patch', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.exec('fit_view')

    expect(result.success).toBe(true)
    expect(useErTabsStore.getState().inspectors.get('t-1')?.viewport).toEqual({ x: 0, y: 0, zoom: 1 })
  })

  it('returns success false with error for unknown action', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.exec('does_not_exist')

    expect(result.success).toBe(false)
    expect(result.error).toMatch(/unknown/i)
  })

  it('fork_to_designer creates a new er_designer tab seeded with the current schema', async () => {
    useErTabsStore.setState({
      inspectors: new Map([[
        't-1',
        {
          ...samplePayload,
          tablesSnapshot: [
            {
              name: 'users',
              columns: [{ name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false }],
              fkOut: [],
            },
          ],
        },
      ]]),
      designers: new Map(),
    })
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.exec('fork_to_designer', { title: 'Fork of Order ER' })

    expect(result.success).toBe(true)
    const data = result.data as { newTabId: string }
    expect(data.newTabId).toMatch(/^er_designer_/)
    const designerPayload = useErTabsStore.getState().designers.get(data.newTabId)
    expect(designerPayload?.targetConnectionId).toBe('c1')
    expect(designerPayload?.tables).toHaveLength(1)
    expect((designerPayload?.tables[0] as { name?: string }).name).toBe('users')
  })
})
