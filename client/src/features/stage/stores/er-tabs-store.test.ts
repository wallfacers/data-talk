import { beforeEach, describe, expect, it } from 'vitest'
import { useErTabsStore } from './er-tabs-store'
import type { ErInspectorPayload } from './er-tabs-payload-types'

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
    expect(useErTabsStore.getState().inspectors.get('tab-1')).toEqual(samplePayload)
  })

  it('hydrating an existing tabId replaces its payload', () => {
    useErTabsStore.getState().hydrateInspector('tab-1', samplePayload)
    const updated = { ...samplePayload, selection: ['products'] }
    useErTabsStore.getState().hydrateInspector('tab-1', updated)
    expect(useErTabsStore.getState().inspectors.get('tab-1')).toEqual(updated)
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
