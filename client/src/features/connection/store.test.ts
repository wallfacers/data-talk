import { beforeEach, describe, expect, it } from 'vitest'
import type { Connection } from '@/services/api/connection'
import { useConnectionStore } from './store'

const connection: Connection = {
  id: 'conn-1',
  name: 'orders-prod',
  kind: 'postgres',
  host: 'localhost',
  port: 5432,
  databaseName: 'orders',
  username: 'demo',
  createdAt: 1,
  connectTimeout: 3000,
  lastTestStatus: 'ok',
  lastTestAt: 2,
  oracleServiceType: null,
    readOnly: false,
  sqlserverEncrypt: true,
  sqlserverTrustServerCertificate: true,
  sqlserverInstanceName: null,
  compatibilityMode: null,
  oceanbaseTenant: null,
  oceanbaseCluster: null,
}

describe('connection-store', () => {
  beforeEach(() => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [],
    } as unknown as Record<string, unknown>)
  })

  it('setActive is idempotent for the same connection id', () => {
    const store = useConnectionStore.getState()

    store.setActive('conn-1')
    const firstId = useConnectionStore.getState().activeConnectionId

    store.setActive('conn-1')

    expect(useConnectionStore.getState().activeConnectionId).toBe(firstId)
  })

  it('setConnections is idempotent for the same connection payloads', () => {
    const store = useConnectionStore.getState()

    store.setConnections([connection])
    const firstList = useConnectionStore.getState().connections

    store.setConnections([{ ...connection }])

    expect(useConnectionStore.getState().connections).toBe(firstList)
  })

  it('setConnections clears stale activeConnectionId when it is no longer present', () => {
    useConnectionStore.setState({
      activeConnectionId: 'stale-conn',
      connections: [],
    } as unknown as Record<string, unknown>)

    useConnectionStore.getState().setConnections([connection])

    expect(useConnectionStore.getState().connections).toEqual([connection])
    expect(useConnectionStore.getState().activeConnectionId).toBeNull()
  })
})
