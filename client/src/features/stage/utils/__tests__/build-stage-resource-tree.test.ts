import { describe, expect, it } from 'vitest'
import type { Connection } from '@/services/api/connection'
import type { SessionDataContext } from '@/services/api/session-data-context'
import { buildStageResourceTree } from '../build-stage-resource-tree'

const connections: Connection[] = [
  {
    id: 'conn-a',
    name: 'orders-prod',
    kind: 'postgres',
    host: 'localhost',
    port: 5432,
    databaseName: 'orders',
    username: 'demo',
    createdAt: 1,
    connectTimeout: 3000,
    lastTestStatus: null,
    lastTestAt: null,
    oracleServiceType: null,
    sqlserverEncrypt: true,
    sqlserverTrustServerCertificate: true,
    sqlserverInstanceName: null,
  },
  {
    id: 'conn-b',
    name: 'warehouse',
    kind: 'mysql',
    host: 'localhost',
    port: 3306,
    databaseName: null,
    username: 'demo',
    createdAt: 2,
    connectTimeout: 3000,
    lastTestStatus: null,
    lastTestAt: null,
    oracleServiceType: null,
    sqlserverEncrypt: true,
    sqlserverTrustServerCertificate: true,
    sqlserverInstanceName: null,
  },
]

const currentContext: SessionDataContext = {
  sessionId: 'sess-1',
  connectionId: 'conn-a',
  connectionNameSnapshot: 'orders-prod',
  database: 'orders',
  schema: 'public',
  selectedLevel: 'schema',
  updatedAt: 123,
}

describe('buildStageResourceTree', () => {
  it('builds connection, database, schema, and tool action nodes for the first-version tree', () => {
    const tree = buildStageResourceTree(connections, currentContext, ['connection:conn-a', 'database:conn-a:orders'])

    expect(tree.connections).toHaveLength(2)
    expect(tree.connections[0]).toMatchObject({
      kind: 'connection',
      id: 'connection:conn-a',
      connectionId: 'conn-a',
      current: true,
      selected: false,
      expanded: true,
      children: [
        {
          kind: 'database',
          id: 'database:conn-a:orders',
          connectionId: 'conn-a',
          database: 'orders',
          current: true,
          selected: false,
          expanded: true,
          children: [
            {
              kind: 'schema',
              id: 'schema:conn-a:orders:public',
              connectionId: 'conn-a',
              database: 'orders',
              schema: 'public',
              current: true,
              selected: true,
              expanded: false,
              actions: [
                {
                  kind: 'tool_action',
                  tool: 'sql',
                  enabled: true,
                  comingSoon: false,
                },
                {
                  kind: 'tool_action',
                  tool: 'er',
                  enabled: false,
                  comingSoon: true,
                },
              ],
            },
          ],
        },
      ],
    })
    expect(tree.connections[1]).toMatchObject({
      kind: 'connection',
      id: 'connection:conn-b',
      connectionId: 'conn-b',
      label: 'warehouse',
      current: false,
      selected: false,
      expanded: false,
      children: [],
    })
  })

  it('does not create an empty schema layer when the connection has no schema context', () => {
    const tree = buildStageResourceTree([connections[1]], null, [])

    expect(tree.connections[0]).toMatchObject({
      kind: 'connection',
      id: 'connection:conn-b',
      children: [],
    })
  })

  it('prefers the live session data context over the connection default database', () => {
    const tree = buildStageResourceTree(
      [connections[0]],
      {
        ...currentContext,
        database: 'analytics',
        schema: 'reporting',
      },
      ['connection:conn-a', 'database:conn-a:analytics'],
    )

    expect(tree.connections[0].children[0]).toMatchObject({
      id: 'database:conn-a:analytics',
      database: 'analytics',
      children: [
        {
          id: 'schema:conn-a:analytics:reporting',
          schema: 'reporting',
        },
      ],
    })
  })
})
