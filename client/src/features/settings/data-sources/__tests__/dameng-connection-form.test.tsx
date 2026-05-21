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

describe('Dameng connection form', () => {
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

  it('populates default port 5236 when Dameng is selected', async () => {
    await openCreateForm()
    await selectKind('dameng', 'Dameng (DM 8)')
    expect(screen.getByLabelText('端口')).toHaveValue(5236)
  })

  it('does not render compatibility mode selector for Dameng', async () => {
    await openCreateForm()
    await selectKind('dameng', 'Dameng (DM 8)')
    // Dameng is not oceanbase, so MultiModeConnectionFields should not appear
    expect(screen.queryByRole('radio', { name: 'MySQL' })).not.toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: 'Oracle' })).not.toBeInTheDocument()
  })

  it('uses schema label for the database field', async () => {
    await openCreateForm()
    await selectKind('dameng', 'Dameng (DM 8)')
    // Dameng uses schemaOptional label instead of generic database label
    const schemaInput = screen.getByLabelText('初始模式名（可选）')
    expect(schemaInput).toBeInTheDocument()
  })

  it('shows schema help text below the input', async () => {
    await openCreateForm()
    await selectKind('dameng', 'Dameng (DM 8)')
    // Help text should be visible
    expect(screen.getByText('留空时使用当前用户的默认模式。')).toBeInTheDocument()
  })

  it('submit payload uses type dameng without OceanBase-only fields', async () => {
    await openCreateForm()
    await selectKind('dameng', 'Dameng (DM 8)')

    fireEvent.change(screen.getByLabelText('名称'), { target: { value: 'DM Test' } })
    fireEvent.change(screen.getByLabelText('初始模式名（可选）'), { target: { value: 'my_schema' } })

    fireEvent.click(screen.getByRole('button', { name: /保存/ }))

    await waitFor(() => {
      const callArgs = vi.mocked(api.createConnection).mock.calls[0][0]
      expect(callArgs.kind).toBe('dameng')
      expect(callArgs).not.toHaveProperty('compatibilityMode')
      expect(callArgs).not.toHaveProperty('oceanbaseTenant')
      expect(callArgs).not.toHaveProperty('oceanbaseCluster')
    })
  })
})
