import { describe, it, expect } from 'vitest'
import {
  TAB_TYPE_REGISTRY,
  getTabTypeDescriptor,
  isPersistent,
  getScope,
} from '../tab-type-registry'

describe('tab-type-registry', () => {
  it('query_editor is persistent and workspace-scoped', () => {
    const desc = getTabTypeDescriptor('query_editor')
    expect(desc.persistent).toBe(true)
    expect(desc.scope).toBe('workspace')
    expect(desc.type).toBe('query_editor')
  })

  it('artifact_preview is persistent and session-scoped', () => {
    const desc = getTabTypeDescriptor('artifact_preview')
    expect(desc.persistent).toBe(true)
    expect(desc.scope).toBe('session')
    expect(desc.type).toBe('artifact_preview')
  })

  it('file_preview is ephemeral', () => {
    const desc = getTabTypeDescriptor('file_preview')
    expect(desc.persistent).toBe(false)
  })

  it('extractContent on query_editor returns sqlText', () => {
    const desc = getTabTypeDescriptor('query_editor')
    expect(desc.extractContent({ sqlText: 'SELECT 1' })).toBe('SELECT 1')
  })

  it('extractContent is total — null/undefined/empty returns empty string', () => {
    const queryDesc = getTabTypeDescriptor('query_editor')
    expect(queryDesc.extractContent(null)).toBe('')
    expect(queryDesc.extractContent(undefined)).toBe('')
    expect(queryDesc.extractContent({})).toBe('')
    expect(queryDesc.extractContent({ sqlText: 42 })).toBe('')

    const artifactDesc = getTabTypeDescriptor('artifact_preview')
    expect(artifactDesc.extractContent(null)).toBe('')
    expect(artifactDesc.extractContent({})).toBe('')
  })

  it('diagnostic type is registered as workbench-scope persistent', () => {
    const desc = getTabTypeDescriptor('diagnostic')
    expect(desc.persistent).toBe(true)
    expect(desc.scope).toBe('workspace')
    expect(desc.type).toBe('diagnostic')
  })

  it('unknown type falls back to noop descriptor', () => {
    const desc = getTabTypeDescriptor('totally_unknown')
    expect(desc.type).toBe('unknown')
    expect(desc.persistent).toBe(false)
    expect(desc.extractContent({})).toBe('')
    expect(isPersistent('totally_unknown')).toBe(false)
    expect(getScope('totally_unknown')).toBeUndefined()
  })

  it('er_inspector is registered as workspace-scope persistent', () => {
    expect(TAB_TYPE_REGISTRY.er_inspector).toBeDefined()
    expect(isPersistent('er_inspector')).toBe(true)
    expect(getScope('er_inspector')).toBe('workspace')
  })

  it('extractContent indexes ER selection, virtual relations, notes, and table columns', () => {
    const desc = getTabTypeDescriptor('er_inspector')
    const text = desc.extractContent({
      kind: 'er_inspector',
      connectionId: 'c',
      selection: ['users', 'orders'],
      neighborDepth: 1,
      layout: 'dagre-LR',
      tablesSnapshot: [
        {
          name: 'users',
          columns: [
            { name: 'id', type: 'BIGINT', nullable: false, isPK: true, isFK: false },
            { name: 'email', type: 'VARCHAR(255)', nullable: false, isPK: false, isFK: false },
          ],
          fkOut: [],
        },
      ],
      positions: {},
      collapsed: [],
      virtualRelations: [
        {
          id: 'vr1',
          from: { table: 'orders', column: 'user_email' },
          to: { table: 'users', column: 'email' },
          type: 'many_to_one',
        },
      ],
      notes: { orders: '订单主表' },
      viewport: { x: 0, y: 0, zoom: 1 },
    })

    expect(text).toContain('users orders')
    expect(text).toContain('users id BIGINT email VARCHAR(255)')
    expect(text).toContain('orders.user_email users.email')
    expect(text).toContain('订单主表')
  })

  it('extractContent returns empty string for null er_inspector payload', () => {
    expect(getTabTypeDescriptor('er_inspector').extractContent(null)).toBe('')
  })
})
