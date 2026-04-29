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
