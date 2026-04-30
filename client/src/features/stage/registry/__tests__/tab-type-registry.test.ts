import { describe, it, expect } from 'vitest'
import { NetworkIcon } from 'lucide-react'
import {
  TAB_TYPE_REGISTRY,
  getTabTypeDescriptor,
  isPersistent,
  getScope,
} from '../tab-type-registry'
import { useErTabsStore } from '../../stores/er-tabs-store'
import { useSqlWorkbenchStore } from '../../stores/sql-workbench-store'

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

  it('rehydrates query_editor useSessionContext from normalized payloads', () => {
    useSqlWorkbenchStore.getState().cleanupTabs([])
    const desc = getTabTypeDescriptor('query_editor')

    desc.rehydrate?.('q-registry', {
      sqlText: 'select 1',
      contextOverride: {
        connectionId: 'conn-1',
        database: 'analytics',
        schema: null,
      },
    })

    expect(useSqlWorkbenchStore.getState().tabsById['q-registry']).toMatchObject({
      sqlText: 'select 1',
      useSessionContext: false,
    })
  })

  it('rehydrates query_editor payloads into an already-mounted pristine tab', () => {
    useSqlWorkbenchStore.getState().cleanupTabs([])
    useSqlWorkbenchStore.getState().ensureTab('q-mounted', {
      sqlText: '',
      source: 'user',
      useSessionContext: true,
    })

    getTabTypeDescriptor('query_editor').rehydrate?.('q-mounted', {
      sqlText: 'select 42',
      source: 'ai',
      useSessionContext: false,
    })

    expect(useSqlWorkbenchStore.getState().tabsById['q-mounted']).toMatchObject({
      sqlText: 'select 42',
      savedSqlText: 'select 42',
      source: 'ai',
      useSessionContext: false,
    })
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

  it('er_designer is registered as workspace-scope persistent', () => {
    expect(TAB_TYPE_REGISTRY.er_designer).toBeDefined()
    expect(isPersistent('er_designer')).toBe(true)
    expect(getScope('er_designer')).toBe('workspace')
    expect(TAB_TYPE_REGISTRY.er_designer.icon).toBe(NetworkIcon)
    expect(TAB_TYPE_REGISTRY.er_designer.railLabelKey).toBe('tabType.erDesigner.short')
  })

  it('extractContent indexes ER designer target, tables, columns, comments, and relations', () => {
    const desc = getTabTypeDescriptor('er_designer')
    const text = desc.extractContent({
      kind: 'er_designer',
      dialect: 'mysql',
      targetConnectionId: 'conn-1',
      targetDatabase: 'sales',
      targetSchema: 'public',
      tables: [
        {
          id: 't_orders',
          name: 'orders',
          comment: '订单主表',
          columns: [
            { id: 'c_order_id', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true },
            { id: 'c_user_id', name: 'user_id', type: 'BIGINT', nullable: false, isPrimaryKey: false, isAutoIncrement: false, comment: 'owner' },
          ],
          indexes: [{ name: 'idx_orders_user', columns: ['user_id'] }],
          uniques: [{ columns: ['id'] }],
        },
        {
          id: 't_users',
          name: 'users',
          columns: [
            { id: 'c_users_id', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true },
          ],
          indexes: [],
          uniques: [],
        },
      ],
      relations: [
        {
          id: 'r_orders_users',
          fromTableId: 't_orders',
          fromColumnId: 'c_user_id',
          toTableId: 't_users',
          toColumnId: 'c_users_id',
          type: 'many_to_one',
          constraintMethod: 'database_fk',
        },
      ],
      positions: {},
      collapsed: [],
      viewport: { x: 0, y: 0, zoom: 1 },
    })

    expect(text).toContain('target conn-1 sales public mysql')
    expect(text).toContain('orders id BIGINT user_id BIGINT 订单主表 owner')
    expect(text).toContain('relation orders.user_id -> users.id many_to_one database_fk')
    expect(text).toContain('index idx_orders_user user_id')
    expect(text).toContain('unique id')
  })

  it('rehydrates er_designer payloads into the ER tabs store', () => {
    const payload = {
      kind: 'er_designer',
      dialect: 'mysql',
      tables: [],
      relations: [],
      positions: {},
      collapsed: [],
      viewport: { x: 0, y: 0, zoom: 1 },
    }

    getTabTypeDescriptor('er_designer').rehydrate?.('d-registry', payload)

    expect((useErTabsStore.getState().designers.get('d-registry') as { kind?: string } | undefined)?.kind).toBe('er_designer')
  })
})
