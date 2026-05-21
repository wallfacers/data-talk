import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { uiRouter } from '@/services/ui-router'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { useErTabsStore } from '../stores/er-tabs-store'
import { StageUIObjectRegistry } from './stage-ui-object-registry'

function resetStageStore() {
  useStageStore.setState({
    tabs: [],
    activeTabId: null,
    openTabIds: new Set(),
    openTabIdsOrdered: [],
  } as unknown as Record<string, unknown>)
  useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
}

function resetUiRouter() {
  uiRouter.unregisterInstance('workspace')
  uiRouter.unregisterInstance('q1')
  uiRouter.unregisterInstance('q2')
  uiRouter.unregisterInstance('b1')
  uiRouter.unregisterInstance('r1')
  uiRouter.unregisterInstance('er-1')
  uiRouter.unregisterInstance('d-1')
  uiRouter.setActiveTabIdProvider(() => null)
}

function seedTabs() {
  const sessionTab: StageTab = {
    tabId: 'q1',
    type: 'query_editor',
    title: 'Session SQL',
    originSessionId: 's1',
    connectionId: 'conn-1',
    payload: { sql: 'select 1' },
    createdAt: 0,
  }
  const activeSessionTab: StageTab = {
    tabId: 'q2',
    type: 'query_editor',
    title: 'Active SQL',
    originSessionId: 's1',
    connectionId: 'conn-2',
    payload: { sql: 'select 2' },
    createdAt: 0,
  }
  const workspaceTab: StageTab = {
    tabId: 'r1',
    type: 'report',
    title: 'Revenue',
    payload: {},
    createdAt: 0,
  }

  useStageStore.setState({
    tabs: [workspaceTab, sessionTab, activeSessionTab],
    activeTabId: 'q2',
    openTabIds: new Set(['r1', 'q1', 'q2']),
    openTabIdsOrdered: ['r1', 'q1', 'q2'],
  } as unknown as Record<string, unknown>)

  return { sessionTab, activeSessionTab, workspaceTab }
}

describe('StageUIObjectRegistry', () => {
  beforeEach(() => {
    resetStageStore()
    resetUiRouter()
  })

  afterEach(() => {
    cleanup()
    resetUiRouter()
    resetStageStore()
  })

  it('registers and unregisters workspace plus open query_editor tabs in uiRouter', async () => {
    const { sessionTab, activeSessionTab, workspaceTab } = seedTabs()
    const tabs = [workspaceTab, sessionTab, activeSessionTab]

    const view = render(<StageUIObjectRegistry tabs={tabs} />)

    // Verify workspace is registered and readable
    const workspaceRead = await uiRouter.handle({
      tool: 'ui_read',
      object: 'workspace',
      target: 'workspace',
      payload: { mode: 'state' },
    })
    expect(workspaceRead.data).toEqual(expect.objectContaining({
      activeTabId: 'q2',
      tabs: expect.arrayContaining([
        expect.objectContaining({ tabId: 'r1', type: 'report' }),
        expect.objectContaining({ tabId: 'q1', type: 'query_editor' }),
        expect.objectContaining({ tabId: 'q2', type: 'query_editor' }),
      ]),
    }))

    // Verify query editor is reachable
    const q1Read = await uiRouter.handle({
      tool: 'ui_read',
      object: 'query_editor',
      target: 'q1',
      payload: { mode: 'state' },
    })
    expect(q1Read.data).toEqual(expect.objectContaining({
      content: 'select 1',
      connectionId: 'conn-1',
    }))

    view.unmount()

    // After unmount, workspace should no longer be reachable
    const clearedWorkspace = await uiRouter.handle({
      tool: 'ui_read',
      object: 'workspace',
      target: 'workspace',
      payload: { mode: 'state' },
    })
    expect(clearedWorkspace.error).toContain('No workspace')

    const clearedQueryEditor = await uiRouter.handle({
      tool: 'ui_read',
      object: 'query_editor',
      target: 'q1',
      payload: { mode: 'state' },
    })
    expect(clearedQueryEditor.error).toContain('No query_editor')
  })

  it('routes target=active to the current active query_editor tab', async () => {
    const { sessionTab, activeSessionTab, workspaceTab } = seedTabs()

    render(<StageUIObjectRegistry tabs={[workspaceTab, sessionTab, activeSessionTab]} />)

    const response = await uiRouter.handle({
      tool: 'ui_read',
      object: 'query_editor',
      target: 'active',
      payload: { mode: 'state' },
    })

    expect(response.data).toEqual(expect.objectContaining({
      content: 'select 2',
      connectionId: 'conn-2',
    }))
  })

  it('registers report tabs and does not misresolve them as active query_editor tabs', async () => {
    const reportTab: StageTab = {
      tabId: 'r1',
      type: 'report',
      title: 'Direct SQL',
      payload: {},
      createdAt: 0,
    }
    const sessionQueryEditor: StageTab = {
      tabId: 'q1',
      type: 'query_editor',
      title: 'Session SQL',
      originSessionId: 's1',
      connectionId: 'conn-1',
      payload: { sql: 'select 1' },
      createdAt: 0,
    }

    useStageStore.setState({
      tabs: [reportTab, sessionQueryEditor],
      activeTabId: 'r1',
      openTabIds: new Set(['r1', 'q1']),
      openTabIdsOrdered: ['r1', 'q1'],
    } as unknown as Record<string, unknown>)

    render(<StageUIObjectRegistry tabs={[reportTab, sessionQueryEditor]} />)

    const workspaceState = await uiRouter.handle({
      tool: 'ui_read',
      object: 'workspace',
      target: 'workspace',
      payload: { mode: 'state' },
    })
    expect(workspaceState.data).toEqual(expect.objectContaining({
      tabs: expect.arrayContaining([
        expect.objectContaining({ tabId: 'r1', type: 'report' }),
      ]),
    }))

    const wrongQueryEditor = await uiRouter.handle({
      tool: 'ui_read',
      object: 'query_editor',
      target: 'active',
      payload: { mode: 'state' },
    })
    expect(wrongQueryEditor.error).toBe('No active query_editor tab')
    expect((wrongQueryEditor.data as { code?: string }).code).toBe('no_active_query_editor')
  })

  it('registers er_inspector tabs in uiRouter', async () => {
    const erInspectorTab: StageTab = {
      tabId: 'er-1',
      type: 'er_inspector',
      title: 'ER',
      originSessionId: 's1',
      connectionId: 'conn-1',
      payload: {},
      createdAt: 0,
      payloadVersion: 1,
    }

    useStageStore.setState({
      tabs: [erInspectorTab],
      activeTabId: 'er-1',
      openTabIds: new Set(['er-1']),
      openTabIdsOrdered: ['er-1'],
    } as unknown as Record<string, unknown>)

    render(<StageUIObjectRegistry tabs={[erInspectorTab]} />)

    const response = await uiRouter.handle({
      tool: 'ui_read',
      object: 'er_inspector',
      target: 'er-1',
      payload: { mode: 'state' },
    })

    expect(response.error).toBeUndefined()
    expect(response.data).toBeNull()
  })

  it('registers er_designer tabs in uiRouter through the planned adapter', async () => {
    const erDesignerTab: StageTab = {
      tabId: 'd-1',
      type: 'er_designer',
      title: 'ER Diagram Designer',
      originSessionId: 's1',
      connectionId: 'conn-1',
      payload: {},
      createdAt: 0,
      payloadVersion: 1,
    }

    useStageStore.setState({
      tabs: [erDesignerTab],
      activeTabId: 'd-1',
      openTabIds: new Set(['d-1']),
      openTabIdsOrdered: ['d-1'],
    } as unknown as Record<string, unknown>)
    useErTabsStore.getState().hydrateDesigner('d-1', {
      kind: 'er_designer',
      dialect: 'mysql',
      targetConnectionId: 'conn-1',
      targetDatabase: null,
      targetSchema: null,
      tables: [],
      relations: [],
      positions: {},
      collapsed: [],
      viewport: { x: 0, y: 0, zoom: 1 },
    })

    render(<StageUIObjectRegistry tabs={[erDesignerTab]} />)

    const response = await uiRouter.handle({
      tool: 'ui_read',
      object: 'er_designer',
      target: 'd-1',
      payload: { mode: 'state' },
    })

    expect(response.error).toBeUndefined()
    expect(response.data).toEqual(expect.objectContaining({
      kind: 'er_designer',
      dialect: 'mysql',
      targetConnectionId: 'conn-1',
    }))
  })
})
