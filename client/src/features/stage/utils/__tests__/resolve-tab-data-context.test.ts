import { describe, expect, it } from 'vitest'
import { resolveTabDataContext, type ResolveTabDataContextOptions } from '../resolve-tab-data-context'
import type { SessionDataContext } from '@/services/api/session-data-context'

const sessionContext: SessionDataContext = {
  sessionId: 'sess-1',
  connectionId: 'conn-session',
  connectionNameSnapshot: 'session-name',
  database: 'session-db',
  schema: 'session-schema',
  selectedLevel: 'schema',
  updatedAt: 1,
}

describe('resolveTabDataContext', () => {
  it('lets tab overrides win while still inheriting missing fields from the session context', () => {
    const resolved = resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        connectionId: 'conn-tab',
        connectionName: 'tab-name',
        database: 'tab-db',
        payload: { schema: 'tab-schema' },
      },
      sessionContext,
      {
        inheritSessionContext: true,
        connectionNameLookup: (connectionId) => (connectionId === 'conn-tab' ? 'lookup-name' : null),
      } satisfies ResolveTabDataContextOptions,
    )

    expect(resolved).toEqual({
      sessionId: 'sess-1',
      connectionId: 'conn-tab',
      connectionName: 'lookup-name',
      database: 'tab-db',
      schema: 'tab-schema',
      selectedLevel: 'schema',
    })
  })

  it('only falls back to session context when inheritance is enabled', () => {
    const inherited = resolveTabDataContext(
      { originSessionId: 'sess-1' },
      sessionContext,
      { inheritSessionContext: true },
    )
    expect(inherited).toEqual({
      sessionId: 'sess-1',
      connectionId: 'conn-session',
      connectionName: 'session-name',
      database: 'session-db',
      schema: 'session-schema',
      selectedLevel: 'schema',
    })

    const snapshotOnly = resolveTabDataContext(
      { originSessionId: 'sess-1', connectionId: 'conn-tab' },
      sessionContext,
      {
        inheritSessionContext: false,
        connectionNameLookup: (connectionId) => (connectionId === 'conn-tab' ? 'snapshot-name' : null),
      },
    )
    expect(snapshotOnly).toEqual({
      sessionId: 'sess-1',
      connectionId: 'conn-tab',
      connectionName: 'snapshot-name',
      database: null,
      schema: null,
      selectedLevel: 'connection',
    })
  })

  it('can prefer the latest session context over tab and payload snapshots', () => {
    const preferred = resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        connectionId: 'conn-tab',
        connectionName: 'tab-name',
        database: 'tab-db',
        schema: 'tab-schema',
        payload: {
          connectionId: 'conn-payload',
          connectionName: 'payload-name',
          database: 'payload-db',
          schema: 'payload-schema',
        },
      },
      sessionContext,
      {
        inheritSessionContext: true,
        preferSessionContext: true,
        connectionNameLookup: (connectionId) => (connectionId === 'conn-session' ? 'lookup-session' : null),
      },
    )

    expect(preferred).toEqual({
      sessionId: 'sess-1',
      connectionId: 'conn-session',
      connectionName: 'lookup-session',
      database: 'session-db',
      schema: 'session-schema',
      selectedLevel: 'schema',
    })
  })

  it('treats the SQL context empty sentinel as an unset database and schema', () => {
    const resolved = resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        connectionId: 'conn-tab',
        database: '__empty__',
        schema: '__empty__',
        payload: {
          database: '__empty__',
          schema: '__empty__',
        },
      },
      null,
      { inheritSessionContext: true },
    )

    expect(resolved).toMatchObject({
      connectionId: 'conn-tab',
      database: null,
      schema: null,
      selectedLevel: 'connection',
    })
  })
})
