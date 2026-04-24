import { act, render } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Connection } from '@/services/api/connection'
import { useConnectionStore } from '@/features/connection/store'
import { useDataSourcePickerStore } from '../data-source-picker-store'
import { DataSourcePickerDialogHost } from '../data-source-picker-dialog-host'

const connection: Connection = {
  id: 'c1',
  name: 'analytics-dev',
  kind: 'postgres',
  host: 'dev.db.local',
  port: 5432,
  databaseName: 'analytics',
  username: 'dev',
  createdAt: 1,
  connectTimeout: 3000,
  lastTestStatus: 'ok',
  lastTestAt: 10,
}

let connectionsData: Connection[] | undefined

vi.mock('@/features/connection/hooks/use-connections', () => ({
  useConnections: () => ({ data: connectionsData }),
}))

vi.mock('../data-source-picker-dialog', () => ({
  DataSourcePickerDialog: () => null,
}))

vi.mock('../recent-connections', () => ({
  readRecentConnectionIds: () => [],
  rememberRecentConnection: vi.fn(),
}))

describe('DataSourcePickerDialogHost', () => {
  beforeEach(() => {
    connectionsData = undefined
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [],
    })
    useDataSourcePickerStore.getState().reset()
  })

  it('keeps the persisted active connection while the connections query is still unresolved', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'c1',
      connections: [connection],
    })

    const view = render(<DataSourcePickerDialogHost />)

    await act(async () => {
      await Promise.resolve()
    })

    expect(useConnectionStore.getState().activeConnectionId).toBe('c1')
    expect(useConnectionStore.getState().connections).toEqual([connection])

    connectionsData = [connection]
    view.rerender(<DataSourcePickerDialogHost />)

    await act(async () => {
      await Promise.resolve()
    })

    expect(useConnectionStore.getState().activeConnectionId).toBe('c1')
    expect(useConnectionStore.getState().connections).toEqual([connection])
  })
})
