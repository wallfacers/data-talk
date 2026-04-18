import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DataSourcesPage } from '../data-sources-page'
import * as api from '../api'

vi.mock('../api', () => ({
  listConnections: vi.fn(),
  deleteConnection: vi.fn(),
  testConnection: vi.fn(),
  connectionsKey: ['connections'] as const,
}))

describe('DataSourcesPage', () => {
  beforeEach(() => {
    vi.mocked(api.listConnections).mockResolvedValue([])
  })
  it('shows empty state when no connections', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)
    expect(await screen.findByText(/还没有数据源/)).toBeInTheDocument()
  })
  it('displays_connection_name_in_table', async () => {
    const qc = new QueryClient()
    vi.mocked(api.listConnections).mockResolvedValue([
      { id: 'c1', name: '测试数据源', kind: 'mysql', host: 'localhost', port: 3306, databaseName: 'test', username: 'root', createdAt: 0, connectTimeout: 3000, lastTestStatus: null, lastTestAt: null }
    ])
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)
    expect(await screen.findByText('测试数据源')).toBeInTheDocument()
    expect(screen.getByText('mysql')).toBeInTheDocument()
  })
})
