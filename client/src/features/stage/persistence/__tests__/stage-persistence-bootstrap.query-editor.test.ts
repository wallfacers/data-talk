import { beforeEach, describe, expect, it, vi } from 'vitest'
import { coordinator } from '../stage-persistence-bootstrap'
import { useStageStore } from '@/stores/stage-store'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'
import { useConnectionStore } from '@/features/connection/store'
import { setQueryEditorContext } from '@/features/stage/utils/query-editor-actions'

describe('stage-persistence-bootstrap - query editor payload subscription', () => {
  beforeEach(() => {
    useStageStore.setState({
      tabs: [],
      activeTabId: null,
      openTabIds: new Set<string>(),
      openTabIdsOrdered: [],
    })
    useSqlWorkbenchStore.setState({ tabsById: {} })
    useConnectionStore.setState({
      activeConnectionId: 'conn-1',
      connections: [
        { id: 'conn-1', name: 'Primary', kind: 'postgres', databaseName: 'db_main' } as never,
        { id: 'conn-2', name: 'Warehouse', kind: 'postgres', databaseName: 'warehouse' } as never,
      ],
    })
    vi.spyOn(coordinator, 'scheduleContentWrite').mockImplementation(() => undefined)
  })

  it('persists query editor payload when only context override changes', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
      connectionId: 'conn-1',
      connectionName: 'Primary',
      database: 'db_main',
      schema: 'public',
    })

    vi.mocked(coordinator.scheduleContentWrite).mockClear()

    setQueryEditorContext({
      tabId,
      connectionId: 'conn-2',
      database: 'warehouse',
      schema: 'analytics',
    })

    expect(coordinator.scheduleContentWrite).toHaveBeenCalledWith(
      tabId,
      expect.objectContaining({
        payload: expect.objectContaining({
          useSessionContext: false,
          contextOverride: {
            connectionId: 'conn-2',
            database: 'warehouse',
            schema: 'analytics',
          },
        }),
        contentText: 'select 1',
      }),
    )
  })

  it('persists session-following mode with a cleared context override', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
      connectionId: 'conn-1',
      connectionName: 'Primary',
      database: 'db_main',
      schema: 'public',
    })

    useSqlWorkbenchStore.getState().setTabContext(tabId, {
      connectionId: 'conn-2',
      connectionName: 'Warehouse',
      database: 'warehouse',
      schema: 'analytics',
      source: 'user_toolbar',
    })
    vi.mocked(coordinator.scheduleContentWrite).mockClear()

    useSqlWorkbenchStore.getState().resetTabContext(tabId)

    expect(coordinator.scheduleContentWrite).toHaveBeenCalledWith(
      tabId,
      expect.objectContaining({
        payload: expect.objectContaining({
          useSessionContext: true,
          contextOverride: null,
        }),
        contentText: 'select 1',
      }),
    )
  })

  it('preserves payload.contextOverride on the first content-write tick after open (BUG-0043)', () => {
    // openQueryEditor with an explicit connection takes the explicit-connection branch
    // (useSessionContext=false, contextOverride is written into payload). Internally it
    // also calls useSqlWorkbenchStore.ensureTab, which fires the workbench-store subscribe
    // → diffContentAndSchedule → buildPersistedQueryEditorPayload before the user has
    // touched any context controls. Without the fix, that first tick would observe
    // store.override === null and overwrite the freshly-written contextOverride with null.
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
      connectionId: 'conn-1',
      connectionName: 'Primary',
      database: 'db_main',
      schema: 'public',
    })

    const persistedPayload = vi.mocked(coordinator.scheduleContentWrite).mock.calls
      .find(([id]) => id === tabId)?.[1].payload
    expect(persistedPayload).toMatchObject({
      contextOverride: {
        connectionId: 'conn-1',
        database: 'db_main',
        schema: 'public',
      },
      useSessionContext: false,
    })
  })

  it('strips legacy contextPinMode when rewriting session-following payloads', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
      connectionId: 'conn-1',
      connectionName: 'Primary',
      database: 'db_main',
      schema: 'public',
    })
    useStageStore.getState().updateTabPayload(tabId, (payload) => ({
      ...(payload as Record<string, unknown>),
      contextPinMode: 'session',
    }))
    useSqlWorkbenchStore.getState().setTabContext(tabId, {
      connectionId: 'conn-2',
      connectionName: 'Warehouse',
      database: 'warehouse',
      schema: 'analytics',
      source: 'user_toolbar',
    })
    vi.mocked(coordinator.scheduleContentWrite).mockClear()

    useSqlWorkbenchStore.getState().resetTabContext(tabId)

    const scheduledPayload = vi.mocked(coordinator.scheduleContentWrite).mock.calls.at(-1)?.[1].payload
    expect(scheduledPayload).toEqual(expect.objectContaining({
      useSessionContext: true,
      contextOverride: null,
    }))
    expect(scheduledPayload).not.toHaveProperty('contextPinMode')
  })
})
