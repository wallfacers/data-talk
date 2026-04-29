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
})
