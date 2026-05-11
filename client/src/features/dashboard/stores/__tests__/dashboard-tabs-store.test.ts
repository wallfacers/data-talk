import { describe, expect, it, beforeEach } from 'vitest'
import { useDashboardTabsStore } from '../dashboard-tabs-store'
import type { Dashboard } from '../../schema'

const sampleDashboard: Dashboard = {
  schemaVersion: 2,
  id: 'dash_test1',
  title: 'Test Dashboard',
  theme: 'industry-default',
  renderer: 'bezel',
  parameters: [],
  widgets: [],
  layout: { engine: 'free' },
  version: 1,
  createdAt: 0,
  updatedAt: 0,
}

describe('dashboard-tabs-store', () => {
  beforeEach(() => {
    useDashboardTabsStore.setState({ tabs: new Map() })
  })

  it('hydrates a tab', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-1', sampleDashboard)
    const tab = useDashboardTabsStore.getState().tabs.get('tab-1')
    expect(tab).toBeDefined()
    expect(tab!.dashboard.title).toBe('Test Dashboard')
    expect(tab!.dirtySinceVersion).toBeNull()
  })

  it('applies replace patch op', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-1', sampleDashboard)
    useDashboardTabsStore.getState().applyPatchOps('tab-1', [
      { op: 'replace', path: '/title', value: 'Renamed' },
    ])
    const tab = useDashboardTabsStore.getState().tabs.get('tab-1')
    expect(tab!.dashboard.title).toBe('Renamed')
    expect(tab!.dirtySinceVersion).toBe(1)
  })

  it('applies replace via matchKey path', () => {
    const dash: Dashboard = {
      ...sampleDashboard,
      widgets: [{
        id: 'chart_w_aaaa', type: 'chart', patternId: 'test.pattern',
        position: { x: 0, y: 0, w: 6, h: 4 },
        options: { echartsOption: {}, dataMapping: { rowsAsDataset: true } },
      }],
    }
    useDashboardTabsStore.getState().hydrateTab('tab-1', dash)
    useDashboardTabsStore.getState().applyPatchOps('tab-1', [
      { op: 'replace', path: '/widgets[id=chart_w_aaaa]', value: { id: 'chart_w_aaaa', type: 'chart', patternId: 'test.pattern', position: { x: 2, y: 0, w: 6, h: 4 }, options: { echartsOption: {}, dataMapping: { rowsAsDataset: true } } } },
    ])
    const tab = useDashboardTabsStore.getState().tabs.get('tab-1')
    expect(tab!.dashboard.widgets[0].position.x).toBe(2)
  })

  it('applies add via /widgets/-', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-1', sampleDashboard)
    useDashboardTabsStore.getState().applyPatchOps('tab-1', [
      {
        op: 'add', path: '/widgets/-',
        value: { id: 'chart_w_aaaa', type: 'chart', patternId: 'test.pattern', position: { x: 0, y: 0, w: 6, h: 4 }, options: { echartsOption: {}, dataMapping: { rowsAsDataset: true } } },
      },
    ])
    const tab = useDashboardTabsStore.getState().tabs.get('tab-1')
    expect(tab!.dashboard.widgets).toHaveLength(1)
    expect(tab!.dashboard.widgets[0].id).toBe('chart_w_aaaa')
  })
})
