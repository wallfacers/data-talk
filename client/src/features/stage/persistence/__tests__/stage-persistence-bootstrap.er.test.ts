import { beforeEach, describe, expect, it, vi } from 'vitest'
import { coordinator } from '../stage-persistence-bootstrap'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import type { ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'

const inspectorPayload: ErInspectorPayload = {
  kind: 'er_inspector',
  connectionId: 'c1',
  selection: ['users'],
  neighborDepth: 1,
  layout: 'dagre-LR',
  tablesSnapshot: [
    {
      name: 'users',
      columns: [{ name: 'id', type: 'BIGINT', nullable: false, isPK: true, isFK: false }],
      fkOut: [],
    },
  ],
  positions: {},
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('stage-persistence-bootstrap - ER content subscription', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
    useStageStore.setState({
      tabs: [{
        tabId: 'er-1',
        type: 'er_inspector',
        title: 'Order ER',
        payload: {},
        payloadVersion: 1,
        createdAt: Date.now(),
        lastTouchedAt: Date.now(),
      }],
      activeTabId: 'er-1',
      openTabIds: new Set(['er-1']),
      openTabIdsOrdered: ['er-1'],
    })
    vi.spyOn(coordinator, 'scheduleContentWrite').mockImplementation(() => undefined)
  })

  it('schedules content write when an inspector payload is hydrated', () => {
    useErTabsStore.getState().hydrateInspector('er-1', inspectorPayload)

    expect(coordinator.scheduleContentWrite).toHaveBeenCalledWith(
      'er-1',
      expect.objectContaining({
        payload: inspectorPayload,
        contentText: expect.stringContaining('users id BIGINT'),
        expectedVersion: 1,
      }),
    )
  })

  it('does not schedule for unknown tabs', () => {
    useErTabsStore.getState().hydrateInspector('unknown-tab', inspectorPayload)

    expect(coordinator.scheduleContentWrite).not.toHaveBeenCalled()
  })
})
