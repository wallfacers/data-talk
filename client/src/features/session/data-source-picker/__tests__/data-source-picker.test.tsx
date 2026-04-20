import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as connectionApi from '@/services/api/connection'
import { I18nContext } from '@/i18n/provider'
import { translateMessage } from '@/i18n/messages'
import { useConnectionStore } from '@/features/connection/store'
import { useDataSourcePickerStore } from '../data-source-picker-store'
import { DataSourcePicker } from '../data-source-picker'

vi.mock('@/services/api/connection')

function renderWithClient(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <I18nContext.Provider
      value={{
        language: 'zh-CN',
        setLanguage: vi.fn(),
        t: (key, values) => translateMessage('zh-CN', key, values),
      }}
    >
      <QueryClientProvider client={qc}>{ui}</QueryClientProvider>
    </I18nContext.Provider>,
  )
}

describe('DataSourcePicker', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
    useDataSourcePickerStore.getState().reset()
    vi.mocked(connectionApi.listConnections).mockResolvedValue([
      {
        id: 'c1',
        name: 'analytics-dev',
        kind: 'postgres',
        host: 'dev.db.local',
        port: 5432,
        databaseName: 'analytics',
        username: 'dev',
        createdAt: 1,
        connectTimeout: 3000,
        lastTestStatus: 'ok',
        lastTestAt: 1,
      },
    ])
  })

  it('shows placeholder when no active connection', async () => {
    renderWithClient(<DataSourcePicker />)
    expect(await screen.findByRole('button', { name: /选择数据源/ })).toBeInTheDocument()
  })

  it('selects chosen connection from chooser request', async () => {
    const requestPick = vi.spyOn(useDataSourcePickerStore.getState(), 'requestPick')
      .mockResolvedValue({ connectionId: 'c1', connectionName: 'analytics-dev' })

    renderWithClient(<DataSourcePicker />)
    fireEvent.click(await screen.findByRole('button', { name: /选择数据源/ }))

    await waitFor(() => {
      expect(requestPick).toHaveBeenCalled()
      expect(useConnectionStore.getState().activeConnectionId).toBe('c1')
      expect(screen.getByRole('button', { name: /analytics-dev/ })).toBeInTheDocument()
    })
  })
})
