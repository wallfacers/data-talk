import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as connectionApi from '@/services/api/connection'
import { I18nContext } from '@/i18n/provider'
import { translateMessage } from '@/i18n/messages'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useDataSourcePickerStore } from '../data-source-picker-store'
import { DataSourcePicker } from '../data-source-picker'
import * as sessionDataContextApi from '@/services/api/session-data-context'

vi.mock('@/services/api/connection')
vi.mock('@/services/api/session-data-context')

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
    useSessionStore.setState({ dataContextBySession: new Map() } as any)
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
        oracleServiceType: null,
    readOnly: false,
        sqlserverEncrypt: true,
        sqlserverTrustServerCertificate: true,
        sqlserverInstanceName: null,
        compatibilityMode: null,
        oceanbaseTenant: null,
        oceanbaseCluster: null,
      },
      {
        id: 'c2',
        name: 'local-database',
        kind: 'h2',
        host: 'local',
        port: 0,
        databaseName: 'local',
        username: 'sa',
        createdAt: 2,
        connectTimeout: 3000,
        lastTestStatus: 'ok',
        lastTestAt: 2,
        oracleServiceType: null,
    readOnly: false,
        sqlserverEncrypt: true,
        sqlserverTrustServerCertificate: true,
        sqlserverInstanceName: null,
        compatibilityMode: null,
        oceanbaseTenant: null,
        oceanbaseCluster: null,
      },
      {
        id: 'c3',
        name: 'local-sqlite',
        kind: 'sqlite',
        host: '',
        port: 0,
        databaseName: '/tmp/app.db',
        username: '',
        createdAt: 3,
        connectTimeout: 3000,
        lastTestStatus: 'ok',
        lastTestAt: 3,
        oracleServiceType: null,
    readOnly: false,
        sqlserverEncrypt: true,
        sqlserverTrustServerCertificate: true,
        sqlserverInstanceName: null,
        compatibilityMode: null,
        oceanbaseTenant: null,
        oceanbaseCluster: null,
      },
    ])
    vi.mocked(sessionDataContextApi.getSessionDataContext).mockResolvedValue({
      sessionId: 'sess-1',
      connectionId: null,
      connectionNameSnapshot: null,
      database: null,
      schema: null,
      selectedLevel: null,
      updatedAt: 1,
    })
    vi.mocked(sessionDataContextApi.setSessionDataContext).mockImplementation(async (sessionId, update) => ({
      sessionId,
      connectionId: update.connectionId ?? null,
      connectionNameSnapshot: update.connectionId === 'c1' ? 'analytics-dev' : 'local-database',
      database: update.database ?? null,
      schema: update.schema ?? null,
      selectedLevel: update.selectedLevel ?? null,
      updatedAt: 2,
    }))
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

  it('shows the active session data context instead of the global active connection', async () => {
    useConnectionStore.setState({ activeConnectionId: 'c2', connections: [] })
    vi.mocked(sessionDataContextApi.getSessionDataContext).mockResolvedValueOnce({
      sessionId: 'sess-1',
      connectionId: 'c1',
      connectionNameSnapshot: 'analytics-dev',
      database: null,
      schema: null,
      selectedLevel: 'connection',
      updatedAt: 1,
    })

    renderWithClient(<DataSourcePicker sessionId="sess-1" />)

    expect(await screen.findByRole('button', { name: /analytics-dev/ })).toBeInTheDocument()
  })

  it('shows saved SQLite connections without schema assumptions', async () => {
    useConnectionStore.setState({ activeConnectionId: 'c3', connections: [] })

    renderWithClient(<DataSourcePicker />)

    expect(await screen.findByRole('button', { name: /local-sqlite/ })).toBeInTheDocument()
  })

  it('persists manual picks to the active session data context', async () => {
    const requestPick = vi.spyOn(useDataSourcePickerStore.getState(), 'requestPick')
      .mockResolvedValue({ connectionId: 'c1', connectionName: 'analytics-dev' })

    renderWithClient(<DataSourcePicker sessionId="sess-1" />)
    fireEvent.click(await screen.findByRole('button', { name: /选择数据源/ }))

    await waitFor(() => {
      expect(requestPick).toHaveBeenCalled()
      expect(sessionDataContextApi.setSessionDataContext).toHaveBeenCalledWith('sess-1', {
        connectionId: 'c1',
        database: null,
        schema: null,
        selectedLevel: 'connection',
      })
      expect(useConnectionStore.getState().activeConnectionId).toBe('c1')
    })
  })
})
