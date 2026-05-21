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

describe('TiDB connection form', () => {
  beforeEach(() => {
    vi.mocked(api.listConnections).mockResolvedValue([])
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
  })

  async function openCreateForm() {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)
    fireEvent.click(await screen.findByRole('button', { name: /新增/ }))
    return qc
  }

  async function selectKind(_kind: string, label: string) {
    fireEvent.click(screen.getByRole('combobox', { name: '类型' }))
    const option = await screen.findByRole('option', { name: label })
    fireEvent.mouseMove(option)
    fireEvent.pointerEnter(option, { pointerType: 'mouse' })
    fireEvent.click(option)
  }

  it('populates default port 4000 when TiDB is selected', async () => {
    await openCreateForm()
    await selectKind('tidb', 'TiDB')
    expect(screen.getByLabelText('端口')).toHaveValue(4000)
  })

  it('shows host, port, username, and password fields for TiDB', async () => {
    await openCreateForm()
    await selectKind('tidb', 'TiDB')
    expect(screen.getByLabelText('主机')).toBeInTheDocument()
    expect(screen.getByLabelText('端口')).toBeInTheDocument()
    expect(screen.getByLabelText('用户名')).toBeInTheDocument()
    expect(screen.getByLabelText('密码')).toBeInTheDocument()
  })

  it('renders the database field as optional for TiDB', async () => {
    await openCreateForm()
    await selectKind('tidb', 'TiDB')
    const dbField = screen.getByLabelText(/数据库.*可选/)
    expect(dbField).toBeInTheDocument()
    expect(dbField).toHaveAttribute('placeholder', expect.stringContaining('留空'))
  })

  it('does not show TLS toggle for TiDB', async () => {
    await openCreateForm()
    await selectKind('tidb', 'TiDB')
    expect(screen.queryByText(/HTTP/)).not.toBeInTheDocument()
    expect(screen.queryByText(/HTTPS/)).not.toBeInTheDocument()
  })

  it('shows TiDB in the kind dropdown', async () => {
    await openCreateForm()
    fireEvent.click(screen.getByRole('combobox', { name: '类型' }))
    expect(await screen.findByRole('option', { name: 'TiDB' })).toBeInTheDocument()
  })
})
