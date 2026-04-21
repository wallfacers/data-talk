import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { uiRouter } from '@/services/ui-router'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { StageUIObjectRegistry } from './stage-ui-object-registry'

function resetStageStore() {
  useStageStore.setState({
    workspaceTabs: [],
    tabsBySession: new Map(),
    activeWorkspaceTabId: null,
    activeTabIdBySession: new Map(),
  } as unknown as Record<string, unknown>)
}

function resetUiRouter() {
  uiRouter.unregisterInstance('workspace')
  uiRouter.unregisterInstance('q1')
  uiRouter.unregisterInstance('q2')
  uiRouter.unregisterInstance('b1')
  uiRouter.unregisterInstance('r1')
  uiRouter.setActiveTabIdProvider(() => null)
}

function seedTabs() {
  const sessionTab: StageTab = {
    tabId: 'q1',
    type: 'query_editor',
    title: 'Session SQL',
    scope: 'session',
    originSessionId: 's1',
    connectionId: 'conn-1',
    payload: { sql: 'select 1' },
    createdAt: 0,
  }
  const activeSessionTab: StageTab = {
    tabId: 'q2',
    type: 'query_editor',
    title: 'Active SQL',
    scope: 'session',
    originSessionId: 's1',
    connectionId: 'conn-2',
    payload: { sql: 'select 2' },
    createdAt: 0,
  }
  const workspaceTab: StageTab = {
    tabId: 'r1',
    type: 'report',
    title: 'Revenue',
    scope: 'workspace',
    payload: {},
    createdAt: 0,
  }

  useStageStore.setState({
    workspaceTabs: [workspaceTab],
    tabsBySession: new Map([['s1', [sessionTab, activeSessionTab]]]),
    activeWorkspaceTabId: 'r1',
    activeTabIdBySession: new Map([['s1', 'q2']]),
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

    const view = render(<StageUIObjectRegistry sessionId="s1" tabs={tabs} />)

    const workspaceList = await uiRouter.handle({
      tool: 'ui_list',
      object: '',
      target: '',
      payload: { filter: { type: 'workspace' } },
    })
    expect(workspaceList.data).toEqual([
      expect.objectContaining({ objectId: 'workspace', type: 'workspace' }),
    ])

    const queryEditors = await uiRouter.handle({
      tool: 'ui_list',
      object: '',
      target: '',
      payload: { filter: { type: 'query_editor' } },
    })
    expect(queryEditors.data).toEqual(expect.arrayContaining([
      expect.objectContaining({ objectId: 'q1', title: 'Session SQL' }),
      expect.objectContaining({ objectId: 'q2', title: 'Active SQL' }),
    ]))

    const workspaceState = await uiRouter.handle({
      tool: 'ui_read',
      object: 'workspace',
      target: 'workspace',
      payload: { mode: 'state' },
    })
    expect(workspaceState.data).toEqual(expect.objectContaining({
      activeTabId: 'q2',
      tabs: expect.arrayContaining([
        expect.objectContaining({ tabId: 'r1', type: 'report' }),
        expect.objectContaining({ tabId: 'q1', type: 'query_editor' }),
        expect.objectContaining({ tabId: 'q2', type: 'query_editor' }),
      ]),
    }))

    view.unmount()

    const clearedWorkspace = await uiRouter.handle({
      tool: 'ui_list',
      object: '',
      target: '',
      payload: { filter: { type: 'workspace' } },
    })
    expect(clearedWorkspace.data).toEqual([])

    const clearedQueryEditors = await uiRouter.handle({
      tool: 'ui_list',
      object: '',
      target: '',
      payload: { filter: { type: 'query_editor' } },
    })
    expect(clearedQueryEditors.data).toEqual([])
  })

  it('routes target=active to the current active query_editor tab', async () => {
    const { sessionTab, activeSessionTab, workspaceTab } = seedTabs()

    render(<StageUIObjectRegistry sessionId="s1" tabs={[workspaceTab, sessionTab, activeSessionTab]} />)

    const response = await uiRouter.handle({
      tool: 'ui_read',
      object: 'query_editor',
      target: 'active',
      payload: { mode: 'state' },
    })

    expect(response.data).toEqual(expect.objectContaining({
      sql: 'select 2',
      connectionId: 'conn-2',
    }))
  })

  it('registers report tabs and does not misresolve them as active query_editor tabs', async () => {
    const reportTab: StageTab = {
      tabId: 'r1',
      type: 'report',
      title: 'Direct SQL',
      scope: 'workspace',
      payload: {},
      createdAt: 0,
    }
    const sessionQueryEditor: StageTab = {
      tabId: 'q1',
      type: 'query_editor',
      title: 'Session SQL',
      scope: 'session',
      originSessionId: 's1',
      connectionId: 'conn-1',
      payload: { sql: 'select 1' },
      createdAt: 0,
    }

    useStageStore.setState({
      workspaceTabs: [reportTab],
      tabsBySession: new Map([['s1', [sessionQueryEditor]]]),
      activeWorkspaceTabId: 'r1',
      activeTabIdBySession: new Map([['s1', null]]),
    } as unknown as Record<string, unknown>)

    render(<StageUIObjectRegistry sessionId="s1" tabs={[reportTab, sessionQueryEditor]} />)

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
    expect(wrongQueryEditor.error).toContain('No query_editor')
  })
})
