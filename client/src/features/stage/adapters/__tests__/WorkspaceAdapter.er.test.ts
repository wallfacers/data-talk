import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { WorkspaceAdapter } from '../WorkspaceAdapter'

describe('WorkspaceAdapter.exec(open_er_inspector)', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
    useStageStore.setState({
      tabs: [],
      openTabIds: new Set(),
      openTabIdsOrdered: [],
      activeTabId: null,
    })
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        nodes: [
          {
            name: 'users',
            columns: [{ name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false }],
            fkOut: [],
          },
          {
            name: 'orders',
            columns: [{ name: 'user_id', type: 'BIGINT', isPK: false, isFK: true, nullable: false }],
            fkOut: [{ fromColumn: 'user_id', toTable: 'users', toColumn: 'id' }],
          },
        ],
        edges: [
          {
            sourceTable: 'orders',
            sourceColumn: 'user_id',
            targetTable: 'users',
            targetColumn: 'id',
            relationType: 'many_to_one',
            source: 'schema_fk',
          },
        ],
        summary: 'orders + 1 neighbor, 2 tables / 1 edge',
        warnings: [],
      }),
    }) as never
  })

  it('creates a new er_inspector tab and returns tab metadata', async () => {
    const adapter = new WorkspaceAdapter(() => null)

    const result = await adapter.exec('open_er_inspector', {
      connectionId: 'c1',
      tables: ['orders'],
      neighborDepth: 1,
    })

    expect(result.success).toBe(true)
    const data = result.data as { tabId: string; summary: string; edges: number; tables: string[] }
    expect(data.tabId).toMatch(/^er_inspector_/)
    expect(data.summary).toContain('tables')
    expect(data.edges).toBe(1)
    expect(data.tables).toEqual(['users', 'orders'])
    expect(useStageStore.getState().tabs.find((tab) => tab.tabId === data.tabId)).toBeDefined()
    expect(useErTabsStore.getState().inspectors.get(data.tabId)).toBeDefined()
  })

  it('returns dialect_unsupported detail for oracle', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        code: 'dialect_unsupported',
        kind: 'oracle',
        message: 'ER does not support dialect: oracle',
        aiHint: 'Use query_editor with read_schema for inspection.',
      }),
    }) as never
    const adapter = new WorkspaceAdapter(() => null)

    const result = await adapter.exec('open_er_inspector', {
      connectionId: 'c1',
      tables: ['orders'],
      neighborDepth: 1,
    })

    expect(result.success).toBe(false)
    expect(result.error).toMatch(/oracle/i)
    expect(result.data).toMatchObject({ code: 'dialect_unsupported' })
  })
})
