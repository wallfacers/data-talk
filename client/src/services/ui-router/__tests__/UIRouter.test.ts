import { describe, it, expect, beforeEach } from 'vitest'
import { UIRouter } from '../UIRouter'
import type { UIObject, PatchCapability } from '../types'

const QUERY_EDITOR_ACTIONS = [
  {
    name: 'apply_text_edits',
    description: 'Apply versioned text edits to the SQL content',
    paramsSchema: {
      type: 'object' as const,
      required: ['baseVersion', 'edits'],
      properties: {
        baseVersion: { type: 'number' },
        edits: { type: 'array' },
      },
    },
  },
  {
    name: 'set_context',
    description: 'Set the query execution context',
    paramsSchema: {
      type: 'object' as const,
      properties: {
        connectionId: { type: ['string', 'null'] },
        database: { type: ['string', 'null'] },
        schema: { type: ['string', 'null'] },
      },
    },
  },
  {
    name: 'run_sql',
    description: 'Run the current SQL',
    paramsSchema: {
      type: 'object' as const,
      properties: {
        limit: { type: ['number', 'null'] },
      },
    },
  },
  {
    name: 'format_sql',
    description: 'Format the current SQL',
    paramsSchema: { type: 'object' as const, properties: {} },
  },
  {
    name: 'focus',
    description: 'Focus this query editor',
    paramsSchema: { type: 'object' as const, properties: {} },
  },
  {
    name: 'close',
    description: 'Close this query editor',
    paramsSchema: { type: 'object' as const, properties: {} },
  },
]

function makeStub(objectId: string, opts: {
  stateValue?: unknown,
  actions?: unknown,
  patchCaps?: PatchCapability[],
  type?: string,
  connectionId?: string,
  database?: string,
} = {}): UIObject {
  return {
    type: opts.type ?? 'query_editor',
    objectId,
    title: `Query ${objectId}`,
    connectionId: opts.connectionId,
    database: opts.database,
    patchCapabilities: opts.patchCaps,
    read: (mode) => {
      if (mode === 'state') return opts.stateValue ?? { content: '' }
      if (mode === 'actions') return opts.actions ?? []
      return {}
    },
    patch: async () => ({ status: 'applied' }),
    exec: async () => ({ success: true }),
  }
}

describe('UIRouter', () => {
  let router: UIRouter
  beforeEach(() => { router = new UIRouter() })

  it('resolves target by objectId', async () => {
    router.registerInstance('q1', makeStub('q1', { stateValue: { content: 'sql1' } }))
    const res = await router.handle({ tool: 'ui_read', object: 'query_editor', target: 'q1', payload: { mode: 'state' } })
    expect(res.data).toEqual({ content: 'sql1' })
  })

  it('returns error for unknown target', async () => {
    const res = await router.handle({ tool: 'ui_read', object: 'query_editor', target: 'nope', payload: { mode: 'state' } })
    expect(res.error).toContain('No query_editor')
  })

  it('does not resolve an explicit target id to the wrong object type', async () => {
    router.registerInstance('b1', makeStub('b1', { type: 'report', stateValue: { sql: 'select 1' } }))

    const res = await router.handle({ tool: 'ui_read', object: 'query_editor', target: 'b1', payload: { mode: 'state' } })

    expect(res.error).toContain('No query_editor')
  })

  it('resolves target=active via provider', async () => {
    router.registerInstance('q2', makeStub('q2', { stateValue: { content: 'active!' } }))
    router.setActiveTabIdProvider(() => 'q2')
    const res = await router.handle({ tool: 'ui_read', object: 'query_editor', target: 'active', payload: { mode: 'state' } })
    expect(res.data).toEqual({ content: 'active!' })
  })

  it('does not fall back to an unrelated object type for target=active', async () => {
    router.registerInstance('b1', makeStub('b1', { type: 'report', stateValue: { sql: 'select 1' } }))
    router.registerInstance('q2', makeStub('q2', { stateValue: { content: 'other query' } }))
    router.setActiveTabIdProvider(() => 'b1')

    const res = await router.handle({ tool: 'ui_read', object: 'query_editor', target: 'active', payload: { mode: 'state' } })

    expect(res.error).toContain('No query_editor')
  })

  it('validates patch capability', async () => {
    router.registerInstance('q3', makeStub('q3', {
      patchCaps: [{ pathPattern: '/content', ops: ['replace'] }],
      actions: QUERY_EDITOR_ACTIONS,
    }))
    const bad = await router.handle({ tool: 'ui_patch', object: 'query_editor', target: 'q3',
      payload: { ops: [{ op: 'replace', path: '/forbidden', value: 1 }] } })
    expect(bad.error).toContain('Unsupported')
    expect(bad.data).toEqual(expect.objectContaining({
      code: 'unsupported_patch',
      hint: expect.stringContaining('/content'),
      availableActions: QUERY_EDITOR_ACTIONS.map((action) => action.name),
    }))
  })

  it('validates exec action exists', async () => {
    router.registerInstance('q4', makeStub('q4', {
      actions: QUERY_EDITOR_ACTIONS,
    }))
    const bad = await router.handle({ tool: 'ui_exec', object: 'query_editor', target: 'q4',
      payload: { action: 'nuke', params: {} } })
    expect(bad.error).toContain('Unknown action')
    expect(bad.data).toEqual(expect.objectContaining({
      code: 'unknown_action',
      availableActions: QUERY_EDITOR_ACTIONS.map((action) => action.name),
    }))
  })

  it('returns expected schema for missing required exec params', async () => {
    router.registerInstance('q5', makeStub('q5', {
      actions: QUERY_EDITOR_ACTIONS,
    }))

    const bad = await router.handle({ tool: 'ui_exec', object: 'query_editor', target: 'q5',
      payload: { action: 'apply_text_edits', params: {} } })

    expect(bad.error).toContain('Missing required params')
    expect(bad.data).toEqual(expect.objectContaining({
      code: 'invalid_params',
      expectedSchema: QUERY_EDITOR_ACTIONS[0].paramsSchema,
    }))
  })

  it('ui_list filters by type', async () => {
    router.registerInstance('a', makeStub('a'))
    router.registerInstance('b', { ...makeStub('b'), type: 'artifact' })
    const res = await router.handle({ tool: 'ui_list', object: '', target: '', payload: { filter: { type: 'artifact' } } })
    expect((res.data as unknown[]).length).toBe(1)
  })

  it('ui_list filters by database', async () => {
    router.registerInstance('q1', makeStub('q1', { database: 'analytics' }))
    router.registerInstance('q2', makeStub('q2', { database: 'sales' }))

    const res = await router.handle({
      tool: 'ui_list',
      object: '',
      target: '',
      payload: { filter: { type: 'query_editor', database: 'analytics' } },
    })

    expect(res.data).toEqual([
      expect.objectContaining({ objectId: 'q1', database: 'analytics' }),
    ])
  })
})
