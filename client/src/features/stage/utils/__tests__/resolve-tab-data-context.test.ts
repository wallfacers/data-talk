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
  it('resolves session mode when useSessionContext is true', () => {
    const resolved = resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        payload: {
          useSessionContext: true,
          contextOverride: { connectionId: 'conn-override', database: 'override-db', schema: null },
        },
      },
      sessionContext,
      {
        inheritSessionContext: true,
        connectionNameLookup: (connectionId) => (connectionId === 'conn-session' ? 'lookup-session' : null),
      },
    )

    expect(resolved).toMatchObject({
      useSessionContext: true,
      sessionId: 'sess-1',
      connectionId: 'conn-session',
      connectionName: 'lookup-session',
      database: 'session-db',
      schema: 'session-schema',
      contextSource: 'session',
    })
  })

  it('resolves override mode when useSessionContext is false', () => {
    const resolved = resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        payload: {
          useSessionContext: false,
          contextOverride: { connectionId: 'conn-override', database: 'override-db', schema: null },
        },
      },
      sessionContext,
      {
        inheritSessionContext: true,
        connectionNameLookup: (connectionId) => (connectionId === 'conn-override' ? 'lookup-override' : null),
      },
    )

    expect(resolved).toMatchObject({
      useSessionContext: false,
      sessionId: 'sess-1',
      connectionId: 'conn-override',
      connectionName: 'lookup-override',
      database: 'override-db',
      schema: null,
      contextSource: 'override',
    })
  })

  it('defaults useSessionContext from contextOverride when the explicit boolean is absent', () => {
    expect(resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        payload: { contextOverride: { connectionId: 'conn-override', database: 'override-db', schema: null } },
      },
      sessionContext,
      { inheritSessionContext: true },
    )).toMatchObject({
      useSessionContext: false,
      connectionId: 'conn-override',
      database: 'override-db',
      contextSource: 'override',
    })

    expect(resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        payload: { contextOverride: null, contextPinMode: 'session' },
      },
      sessionContext,
      { inheritSessionContext: true },
    )).toMatchObject({
      useSessionContext: true,
      connectionId: 'conn-session',
      database: 'session-db',
      contextSource: 'session',
    })
  })

  it('treats raw contextOverride undefined as absent instead of forcing manual mode', () => {
    expect(resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        payload: { contextOverride: undefined },
      },
      sessionContext,
      { inheritSessionContext: true },
    )).toMatchObject({
      useSessionContext: true,
      connectionId: 'conn-session',
      database: 'session-db',
      contextSource: 'session',
    })
  })

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
      useSessionContext: false,
      sessionId: 'sess-1',
      connectionId: 'conn-tab',
      connectionName: 'lookup-name',
      database: 'tab-db',
      schema: 'tab-schema',
      contextSource: 'tab',
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
      useSessionContext: true,
      sessionId: 'sess-1',
      connectionId: 'conn-session',
      connectionName: 'session-name',
      database: 'session-db',
      schema: 'session-schema',
      contextSource: 'session',
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
      useSessionContext: false,
      sessionId: 'sess-1',
      connectionId: 'conn-tab',
      connectionName: 'snapshot-name',
      database: null,
      schema: null,
      contextSource: 'tab',
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
      useSessionContext: true,
      sessionId: 'sess-1',
      connectionId: 'conn-session',
      connectionName: 'lookup-session',
      database: 'session-db',
      schema: 'session-schema',
      contextSource: 'session',
      selectedLevel: 'schema',
    })
  })

  it('falls back to tab.database in session-follow mode when the session has no database pinned (BUG-0042)', () => {
    // openQueryEditor write-time fallback stamps StageTab.database with the connection's
    // configured databaseName. Without this fallback, the session-follow branch read
    // sessionContext.database directly and ignored the freshly-written tab field.
    const sessionWithoutDatabase: SessionDataContext = {
      sessionId: 'sess-1',
      connectionId: 'conn-session',
      connectionNameSnapshot: 'session-name',
      database: null,
      schema: null,
      selectedLevel: 'connection',
      updatedAt: 1,
    }
    const resolved = resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        connectionId: 'conn-session',
        database: 'analytics',
        payload: { useSessionContext: true },
      },
      sessionWithoutDatabase,
      { inheritSessionContext: true, preferSessionContext: true },
    )

    expect(resolved).toMatchObject({
      useSessionContext: true,
      connectionId: 'conn-session',
      database: 'analytics',
      contextSource: 'session',
      selectedLevel: 'database',
    })
  })

  it('still lets the session database win over tab.database when both are set in session-follow mode', () => {
    const resolved = resolveTabDataContext(
      {
        originSessionId: 'sess-1',
        connectionId: 'conn-session',
        database: 'tab-default',
        payload: { useSessionContext: true },
      },
      sessionContext,
      { inheritSessionContext: true, preferSessionContext: true },
    )

    expect(resolved).toMatchObject({
      database: 'session-db',
      contextSource: 'session',
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
