import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Connection } from '@/services/api/connection'
import type { SessionDataContext } from '@/services/api/session-data-context'
import { StageResourceBrowser } from './stage-resource-browser'
import { showErrorToast } from '@/services/http-error'

const setSessionDataContext = vi.fn()
const useSessionDataContextMock = vi.fn()
const onSelectionChange = vi.fn()
const onExpandedChange = vi.fn()
const onToolAction = vi.fn()

vi.mock('@/features/session/hooks/use-session-data-context', () => ({
  useSessionDataContext: (...args: unknown[]) => useSessionDataContextMock(...args),
}))

vi.mock('@/services/http-error', () => ({
  normalizeError: (error: unknown) => error,
  showErrorToast: vi.fn(),
}))

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
  },
]

const noChildrenConnections: Connection[] = [
  {
    id: 'conn-b',
    name: 'warehouse-prod',
    kind: 'postgres',
    host: 'localhost',
    port: 5432,
    databaseName: null,
    username: 'demo',
    createdAt: 2,
    connectTimeout: 3000,
    lastTestStatus: null,
    lastTestAt: null,
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

describe('StageResourceBrowser', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSessionDataContextMock.mockReturnValue({
      context: currentContext,
      setSessionDataContext: setSessionDataContext.mockResolvedValue(undefined),
    })
  })

  it('syncs session context when resource nodes are selected', () => {
    render(
      <StageResourceBrowser
        sessionId="sess-1"
        connections={connections}
        expandedNodeIds={['connection:conn-a', 'database:conn-a:orders']}
        selection={null}
        onSelectionChange={onSelectionChange}
        onExpandedChange={onExpandedChange}
        onToolAction={onToolAction}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'orders-prod' }))
    fireEvent.click(screen.getByRole('button', { name: 'orders' }))
    fireEvent.click(screen.getByRole('button', { name: 'public' }))

    expect(onSelectionChange).toHaveBeenCalledWith({
      kind: 'connection',
      connectionId: 'conn-a',
    })
    expect(onSelectionChange).toHaveBeenCalledWith({
      kind: 'database',
      connectionId: 'conn-a',
      database: 'orders',
    })
    expect(onSelectionChange).toHaveBeenCalledWith({
      kind: 'schema',
      connectionId: 'conn-a',
      database: 'orders',
      schema: 'public',
    })
    expect(onExpandedChange).toHaveBeenCalledWith('connection:conn-a', false)
    expect(onExpandedChange).toHaveBeenCalledWith('database:conn-a:orders', false)
    expect(setSessionDataContext).toHaveBeenCalledWith({
      connectionId: 'conn-a',
      database: null,
      schema: null,
      selectedLevel: 'connection',
    })
    expect(setSessionDataContext).toHaveBeenCalledWith({
      connectionId: 'conn-a',
      database: 'orders',
      schema: null,
      selectedLevel: 'database',
    })
    expect(setSessionDataContext).toHaveBeenCalledWith({
      connectionId: 'conn-a',
      database: 'orders',
      schema: 'public',
      selectedLevel: 'schema',
    })
    expect(onToolAction).not.toHaveBeenCalledWith(expect.objectContaining({ tool: 'sql' }))
  })

  it('awaits context sync before routing resource tool clicks through the integration callback', async () => {
    let resolveUpdate: (() => void) | undefined
    setSessionDataContext.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveUpdate = () => resolve()
        }),
    )

    render(
      <StageResourceBrowser
        sessionId="sess-1"
        connections={connections}
        expandedNodeIds={['connection:conn-a', 'database:conn-a:orders']}
        selection={null}
        onSelectionChange={onSelectionChange}
        onExpandedChange={onExpandedChange}
        onToolAction={onToolAction}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'SQL 编辑器' }))

    expect(onToolAction).not.toHaveBeenCalled()
    resolveUpdate?.()

    await waitFor(() => {
      expect(onToolAction).toHaveBeenCalledWith({
        kind: 'resource_tool',
        tool: 'sql',
        connectionId: 'conn-a',
        database: 'orders',
        schema: 'public',
      })
    })
    expect(setSessionDataContext).toHaveBeenCalledWith({
      connectionId: 'conn-a',
      database: 'orders',
      schema: 'public',
      selectedLevel: 'schema',
    })
  })

  it('shows an empty-children hint when connections exist but none expose database or schema descendants', () => {
    render(
      <StageResourceBrowser
        sessionId="sess-1"
        connections={noChildrenConnections}
        expandedNodeIds={[]}
        selection={null}
        onSelectionChange={onSelectionChange}
        onExpandedChange={onExpandedChange}
        onToolAction={onToolAction}
      />,
    )

    expect(screen.getByText('暂无 database/schema 资源')).toBeTruthy()
  })

  it('shows a toast when resource-node context sync fails', async () => {
    setSessionDataContext.mockRejectedValueOnce(new Error('sync failed'))

    render(
      <StageResourceBrowser
        sessionId="sess-1"
        connections={connections}
        expandedNodeIds={['connection:conn-a']}
        selection={null}
        onSelectionChange={onSelectionChange}
        onExpandedChange={onExpandedChange}
        onToolAction={onToolAction}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'orders-prod' }))

    await waitFor(() => expect(showErrorToast).toHaveBeenCalled())
  })
})
