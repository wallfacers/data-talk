import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardAdapter } from '../DashboardAdapter'
import { useDashboardTabsStore } from '../../stores/dashboard-tabs-store'
import { useStageStore } from '@/stores/stage-store'

const sampleDashboard = {
  schemaVersion: 1,
  id: 'dash_test1',
  title: 'Test Dashboard',
  parameters: [],
  widgets: [],
  layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
  version: 1,
  createdAt: 0,
  updatedAt: 0,
}

describe('DashboardAdapter', () => {
  beforeEach(() => {
    useDashboardTabsStore.setState({ tabs: new Map() })
    useStageStore.getState().resetSessionResources()
    global.fetch = vi.fn() as never
  })

  it('read("state") returns the dashboard payload', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-d1', sampleDashboard as never)
    const adapter = new DashboardAdapter('tab-d1', () => null)

    const state = adapter.read('state') as Record<string, unknown>
    expect(state).toMatchObject({ title: 'Test Dashboard', schemaVersion: 1 })
  })

  it('read("schema") returns type and patchCapabilities', () => {
    const adapter = new DashboardAdapter('tab-d1', () => null)
    const schema = adapter.read('schema') as { type: string; patchCapabilities: unknown[] }

    expect(schema.type).toBe('dashboard')
    expect(schema.patchCapabilities.length).toBeGreaterThan(0)
  })

  it('read("actions") returns create and archive', () => {
    const adapter = new DashboardAdapter('tab-d1', () => null)
    const actions = adapter.read('actions') as Array<{ name: string }>

    expect(actions.map((a) => a.name)).toContain('create')
  })

  it('patch applies ops via dashboard store', async () => {
    useDashboardTabsStore.getState().hydrateTab('tab-d1', sampleDashboard as never)
    const adapter = new DashboardAdapter('tab-d1', () => null)

    const result = await adapter.patch([
      { op: 'replace', path: '/title', value: 'Renamed Dashboard' },
    ])

    expect(result.status).toBe('applied')
    const tab = useDashboardTabsStore.getState().tabs.get('tab-d1')
    expect(tab!.dashboard.title).toBe('Renamed Dashboard')
  })

  it('patch returns error for missing tab', async () => {
    const adapter = new DashboardAdapter('nonexistent', () => null)
    const result = await adapter.patch([{ op: 'replace', path: '/title', value: 'X' }])

    expect(result.status).toBe('error')
  })

  it('exec("create") opens a new dashboard tab', async () => {
    const adapter = new DashboardAdapter('tab-d1', () => null)
    const result = await adapter.exec('create', { title: 'New Dashboard' })

    expect(result.success).toBe(true)
    const data = result.data as { tabId: string }
    expect(data.tabId).toMatch(/^dashboard_/)
    const tab = useDashboardTabsStore.getState().tabs.get(data.tabId)
    expect(tab).toBeDefined()
    expect(tab!.dashboard.title).toBe('New Dashboard')
  })

  it('exec("archive") returns deferred', async () => {
    const adapter = new DashboardAdapter('tab-d1', () => null)
    const result = await adapter.exec('archive')

    expect(result.success).toBe(true)
    expect((result.data as { status: string }).status).toBe('deferred')
  })

  it('exec unknown action returns error', async () => {
    const adapter = new DashboardAdapter('tab-d1', () => null)
    const result = await adapter.exec('unknown_action')

    expect(result.success).toBe(false)
    expect(result.error).toContain('unknown action')
  })
})
