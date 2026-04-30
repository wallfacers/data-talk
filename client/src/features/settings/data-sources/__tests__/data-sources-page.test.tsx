import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DataSourcesPage } from '../data-sources-page'
import * as api from '../api'
import { useConnectionStore } from '@/features/connection/store'

vi.mock('../api', () => ({
  listConnections: vi.fn(),
  createConnection: vi.fn(),
  updateConnection: vi.fn(),
  deleteConnection: vi.fn(),
  testConnection: vi.fn(),
  connectionsKey: ['connections'] as const,
}))

describe('DataSourcesPage', () => {
  beforeEach(() => {
    vi.mocked(api.listConnections).mockResolvedValue([])
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
  })
  it('shows empty state when no connections', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)
    expect(await screen.findByText(/还没有数据源/)).toBeInTheDocument()
  })
  it('displays_connection_name_in_table', async () => {
    const qc = new QueryClient()
    const connection = { id: 'c1', name: '测试数据源', kind: 'mysql', host: 'localhost', port: 3306, databaseName: 'test', username: 'root', createdAt: 0, connectTimeout: 3000, lastTestStatus: null, lastTestAt: null }
    vi.mocked(api.listConnections).mockResolvedValue([connection])
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)
    expect(await screen.findByText('测试数据源')).toBeInTheDocument()
    expect(screen.getByText('mysql')).toBeInTheDocument()
    expect(useConnectionStore.getState().connections).toEqual([connection])
  })
  it('offers SQLite as a file-scoped connection type', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)

    fireEvent.click(await screen.findByRole('button', { name: /新增/ }))
    fireEvent.click(screen.getByRole('combobox', { name: '类型' }))
    const sqliteOption = await screen.findByRole('option', { name: 'SQLite' })
    fireEvent.mouseMove(sqliteOption)
    fireEvent.pointerEnter(sqliteOption, { pointerType: 'mouse' })
    fireEvent.click(sqliteOption)

    expect(screen.getByLabelText('SQLite 文件路径')).toBeInTheDocument()
    expect(screen.queryByLabelText('主机')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('端口')).not.toBeInTheDocument()
  })
})
