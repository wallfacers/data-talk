import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { ErDesignerAdapter } from '../ErDesignerAdapter'

const samplePayload = {
  kind: 'er_designer' as const,
  dialect: 'mysql' as const,
  targetConnectionId: null,
  targetDatabase: null,
  targetSchema: null,
  tables: [],
  relations: [],
  positions: {},
  collapsed: [],
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('ErDesignerAdapter', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map(),
      designers: new Map([['d-1', { ...samplePayload }]]),
    })
    useStageStore.getState().resetSessionResources()
    global.fetch = vi.fn() as never
  })

  it('read("state") returns the designer payload', () => {
    const adapter = new ErDesignerAdapter('d-1', () => null)

    expect(adapter.read('state')).toMatchObject({ kind: 'er_designer', dialect: 'mysql' })
  })

  it('patch delegates to applyDesignerPatch', async () => {
    const applyDesignerPatch = vi.fn().mockReturnValue({ newVersion: 2, assignedIds: {} })
    useErTabsStore.setState({ applyDesignerPatch } as never)
    const adapter = new ErDesignerAdapter('d-1', () => null)

    const result = await adapter.patch([{ op: 'replace', path: '/viewport', value: { x: 1, y: 2, zoom: 0.8 } }])

    expect(result.status).toBe('applied')
    expect(applyDesignerPatch).toHaveBeenCalledWith('d-1', [
      { op: 'replace', path: '/viewport', value: { x: 1, y: 2, zoom: 0.8 } },
    ])
  })

  it('patch returns newVersion + assignedIds so AI can address newly created tables/columns', async () => {
    const applyDesignerPatch = vi.fn().mockReturnValue({
      newVersion: 7,
      assignedIds: { '/tables/0': 't_users_abc', '/tables/0/columns/0': 'c_users_id' },
    })
    useErTabsStore.setState({ applyDesignerPatch } as never)
    const adapter = new ErDesignerAdapter('d-1', () => null)

    const result = await adapter.patch([
      {
        op: 'add',
        path: '/tables/-',
        value: { name: 'users', columns: [{ name: 'id', type: 'BIGINT' }], indexes: [], uniques: [] },
      },
    ])

    expect(result).toMatchObject({
      status: 'applied',
      newVersion: 7,
      assignedIds: { '/tables/0': 't_users_abc', '/tables/0/columns/0': 'c_users_id' },
    })
  })

  it('exec("sync_from_db") preserves view fields and replaces tables/relations from server payload', async () => {
    useErTabsStore.setState({
      designers: new Map([[
        'd-1',
        {
          ...samplePayload,
          targetConnectionId: 'c1',
          targetDatabase: 'sales',
          positions: { t_old: { x: 100, y: 200 } },
          collapsed: ['t_old'],
          viewport: { x: 5, y: 6, zoom: 0.5 },
          tables: [{
            id: 't_old',
            name: 'old_users',
            columns: [],
            indexes: [],
            uniques: [],
          }],
          relations: [],
        },
      ]]),
    })
    ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({
        payload: {
          dialect: 'mysql',
          targetConnectionId: 'c1',
          targetDatabase: 'sales',
          targetSchema: null,
          tables: [{
            id: 't_users',
            name: 'users',
            columns: [{ id: 'c_users_id', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true }],
            indexes: [],
            uniques: [],
          }],
          relations: [],
        },
      }),
    })
    const adapter = new ErDesignerAdapter('d-1', () => null)

    const result = await adapter.exec('sync_from_db', { tables: ['users'] })

    expect(result.success).toBe(true)
    const stored = useErTabsStore.getState().designers.get('d-1')!
    // View fields preserved from local payload, even though server response has no positions/collapsed/viewport.
    expect(stored.positions).toEqual({ t_old: { x: 100, y: 200 } })
    expect(stored.collapsed).toEqual(['t_old'])
    expect(stored.viewport).toEqual({ x: 5, y: 6, zoom: 0.5 })
    expect(stored.kind).toBe('er_designer')
    // Tables/relations replaced from server.
    expect(stored.tables.map((t) => t.name)).toEqual(['users'])
  })

  it('exec("sync_from_db") surfaces a clear error if server omits payload', async () => {
    useErTabsStore.setState({
      designers: new Map([['d-1', { ...samplePayload, targetConnectionId: 'c1' }]]),
    })
    ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true, json: async () => ({}) })
    const adapter = new ErDesignerAdapter('d-1', () => null)

    const result = await adapter.exec('sync_from_db', { tables: [] })

    expect(result.success).toBe(false)
    expect(result.error).toMatch(/payload/i)
  })

  it('exec("bind_target") writes target connection context', async () => {
    const adapter = new ErDesignerAdapter('d-1', () => null)

    const result = await adapter.exec('bind_target', { connectionId: 'c1', database: 'sales', schema: 'public' })

    expect(result.success).toBe(true)
    expect(useErTabsStore.getState().designers.get('d-1')).toMatchObject({
      targetConnectionId: 'c1',
      targetDatabase: 'sales',
      targetSchema: 'public',
    })
  })

  it('exec("generate_ddl") fails when no target is bound', async () => {
    const adapter = new ErDesignerAdapter('d-1', () => null)

    const result = await adapter.exec('generate_ddl')

    expect(result.success).toBe(false)
    expect(result.error).toMatch(/target_required_for_apply|bind_target/i)
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('exec("generate_ddl") opens a query_editor tab without executing SQL', async () => {
    useErTabsStore.setState({
      designers: new Map([[
        'd-1',
        {
          ...samplePayload,
          targetConnectionId: 'c1',
          targetDatabase: 'sales',
          targetSchema: 'public',
        },
      ]]),
    })
    ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({
        ddl: 'CREATE TABLE users (id BIGINT);',
        statements: [{ sql: 'CREATE TABLE users (id BIGINT);', kind: 'CREATE_TABLE', table: 'users' }],
        skipped: [],
      }),
    })
    const adapter = new ErDesignerAdapter('d-1', () => null)

    const result = await adapter.exec('generate_ddl')

    expect(result.success).toBe(true)
    expect(global.fetch).toHaveBeenCalledWith('/api/er/generate-ddl', expect.objectContaining({ method: 'POST' }))
    expect(JSON.stringify((global.fetch as ReturnType<typeof vi.fn>).mock.calls)).not.toContain('/api/sql')
    const data = result.data as { queryEditorTabId: string; ddl: string; skippedOps: unknown[] }
    expect(data.queryEditorTabId).toMatch(/^query_editor_/)
    expect(data.ddl).toContain('CREATE TABLE')
    const tab = useStageStore.getState().tabs.find((candidate) => candidate.tabId === data.queryEditorTabId)
    expect(tab).toMatchObject({
      type: 'query_editor',
      connectionId: 'c1',
      database: 'sales',
      schema: 'public',
    })
    expect(tab?.payload).toMatchObject({ entryMode: 'ui_exec', autoRun: false })
  })

  it('exec("diff_against_db") returns structured diff without opening a tab', async () => {
    useErTabsStore.setState({
      designers: new Map([['d-1', { ...samplePayload, targetConnectionId: 'c1' }]]),
    })
    ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ diff: [{ kind: 'TableAdded', tableName: 'users' }] }),
    })
    const adapter = new ErDesignerAdapter('d-1', () => null)

    const result = await adapter.exec('diff_against_db')

    expect(result.success).toBe(true)
    expect(result.data).toMatchObject({ diff: [{ kind: 'TableAdded', tableName: 'users' }] })
    expect(useStageStore.getState().tabs).toHaveLength(0)
  })
})
