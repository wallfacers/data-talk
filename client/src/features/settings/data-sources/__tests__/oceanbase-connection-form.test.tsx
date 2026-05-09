import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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

describe('OceanBase connection form', () => {
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

  it('populates default port 2881 when OceanBase is selected', async () => {
    await openCreateForm()
    await selectKind('oceanbase', 'OceanBase')
    expect(screen.getByLabelText('端口')).toHaveValue(2881)
  })

  it('shows MySQL compatibility mode by default', async () => {
    await openCreateForm()
    await selectKind('oceanbase', 'OceanBase')
    const mysqlChip = screen.getByRole('radio', { name: 'MySQL' })
    expect(mysqlChip).toHaveAttribute('aria-checked', 'true')
  })

  it('shows tenant and cluster fields for OceanBase', async () => {
    await openCreateForm()
    await selectKind('oceanbase', 'OceanBase')
    expect(screen.getByLabelText('租户')).toBeInTheDocument()
    expect(screen.getByLabelText('集群')).toBeInTheDocument()
  })

  it('marks tenant field as required', async () => {
    await openCreateForm()
    await selectKind('oceanbase', 'OceanBase')
    const tenantInput = screen.getByLabelText('租户')
    expect(tenantInput).toBeRequired()
  })

  it('disables Oracle mode option', async () => {
    await openCreateForm()
    await selectKind('oceanbase', 'OceanBase')
    const oracleChip = screen.getByRole('radio', { name: 'Oracle' })
    expect(oracleChip).toBeDisabled()
    expect(oracleChip).toHaveAttribute('aria-disabled', 'true')
  })

  it('includes oceanbase-specific fields in submit payload', async () => {
    await openCreateForm()
    await selectKind('oceanbase', 'OceanBase')
    await screen.findByLabelText('租户')

    fireEvent.change(screen.getByLabelText('名称'), { target: { value: 'OB Test' } })
    fireEvent.change(screen.getByLabelText('租户'), { target: { value: 'test_tenant' } })
    fireEvent.change(screen.getByLabelText('集群'), { target: { value: 'test_cluster' } })

    fireEvent.click(screen.getByRole('button', { name: /保存/ }))

    await waitFor(() => {
      expect(vi.mocked(api.createConnection)).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'oceanbase',
          compatibilityMode: 'mysql',
          oceanbaseTenant: 'test_tenant',
          oceanbaseCluster: 'test_cluster',
        }),
      )
    })
  })
})
