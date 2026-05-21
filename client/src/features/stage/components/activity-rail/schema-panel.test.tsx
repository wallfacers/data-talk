import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { translateMessage } from '@/i18n/messages'
import { SchemaPanel, type SchemaPanelItem } from './schema-panel'

const connectionItem = {
  id: 'connection',
  kind: 'connection' as const,
  label: 'Primary Connection',
  context: {
    connectionId: 'conn-1',
    connectionName: 'Primary Connection',
    database: null,
    schema: null,
  },
}

const databaseItem = {
  id: 'database',
  kind: 'database' as const,
  label: 'analytics',
  context: {
    connectionId: 'conn-1',
    connectionName: 'Primary Connection',
    database: 'analytics',
    schema: null,
  },
}

const schemaItem = {
  id: 'schema',
  kind: 'schema' as const,
  label: 'public',
  context: {
    connectionId: 'conn-1',
    connectionName: 'Primary Connection',
    database: 'analytics',
    schema: 'public',
  },
}

const items: SchemaPanelItem[] = [
  connectionItem,
  databaseItem,
  schemaItem,
  {
    id: 'table',
    kind: 'table',
    label: 'users',
    insertText: 'users',
  },
  {
    id: 'column',
    kind: 'column',
    label: 'email',
    insertText: 'email',
  },
]

describe('SchemaPanel', () => {
  const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
    translateMessage('zh-CN', key, values)

  it('sets the selected override when a context node is clicked', () => {
    const onSetTabContext = vi.fn()
    const onInsertText = vi.fn()

    render(
      <SchemaPanel
        items={items}
        onSetTabContext={onSetTabContext}
        onInsertText={onInsertText}
      />,
    )

    expect(screen.getByRole('tree', { name: t('stage.activityRail.schema.tree') })).toBeTruthy()
    expect(screen.getAllByText(t('stage.activityRail.schema.kind.connection')).length).toBeGreaterThan(0)
    expect(screen.getAllByText(t('stage.activityRail.schema.kind.database')).length).toBeGreaterThan(0)
    expect(screen.getAllByText(t('stage.activityRail.schema.kind.schema')).length).toBeGreaterThan(0)
    expect(screen.getAllByText(t('stage.activityRail.schema.kind.table')).length).toBeGreaterThan(0)
    expect(screen.getAllByText(t('stage.activityRail.schema.kind.column')).length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('button', { name: 'Primary Connection' }))
    expect(onSetTabContext).toHaveBeenCalledWith(connectionItem.context)

    fireEvent.click(screen.getByRole('button', { name: 'analytics' }))
    expect(onSetTabContext).toHaveBeenCalledWith(databaseItem.context)

    fireEvent.click(screen.getByRole('button', { name: 'public' }))
    expect(onSetTabContext).toHaveBeenCalledWith(schemaItem.context)
    expect(onInsertText).not.toHaveBeenCalled()
  })

  it('inserts leaf nodes on double click', () => {
    const onSetTabContext = vi.fn()
    const onInsertText = vi.fn()

    render(
      <SchemaPanel
        items={items}
        onSetTabContext={onSetTabContext}
        onInsertText={onInsertText}
      />,
    )

    fireEvent.doubleClick(screen.getByRole('button', { name: 'users' }))
    fireEvent.doubleClick(screen.getByRole('button', { name: 'email' }))

    expect(onInsertText).toHaveBeenCalledWith('users')
    expect(onInsertText).toHaveBeenCalledWith('email')
  })

  it('renders the translated empty state when no schema items exist', () => {
    render(
      <SchemaPanel
        items={[]}
        onSetTabContext={vi.fn()}
        onInsertText={vi.fn()}
      />,
    )

    expect(screen.getByText(t('stage.activityRail.schema.empty'))).toBeTruthy()
  })
})
