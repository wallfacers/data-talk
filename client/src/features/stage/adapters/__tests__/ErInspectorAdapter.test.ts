import { beforeEach, describe, expect, it } from 'vitest'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
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

  it('patch immutable_path returns error', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)

    const result = await adapter.patch([{ op: 'add', path: '/tables/-', value: {} }])

    expect(result.status).toBe('error')
    expect(result.message).toMatch(/immutable_path_in_inspector/)
  })
})
