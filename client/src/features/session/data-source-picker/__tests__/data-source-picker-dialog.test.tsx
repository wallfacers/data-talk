import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Connection } from '@/services/api/connection'
import { I18nContext } from '@/i18n/provider'
import { translateMessage } from '@/i18n/messages'
import { DataSourcePickerDialog } from '../data-source-picker-dialog'

const connections: Connection[] = [
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
    lastTestAt: 10,
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
    name: 'orders-prod',
    kind: 'mysql',
    host: 'prod.db.local',
    port: 3306,
    databaseName: 'orders',
    username: 'root',
    createdAt: 2,
    connectTimeout: 3000,
    lastTestStatus: 'fail',
    lastTestAt: 20,
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
    name: 'warehouse-stage',
    kind: 'postgres',
    host: 'stage.db.local',
    port: 5432,
    databaseName: 'warehouse',
    username: 'etl',
    createdAt: 3,
    connectTimeout: 3000,
    lastTestStatus: null,
    lastTestAt: null,
    oracleServiceType: null,
    readOnly: false,
    sqlserverEncrypt: true,
    sqlserverTrustServerCertificate: true,
    sqlserverInstanceName: null,
    compatibilityMode: null,
    oceanbaseTenant: null,
    oceanbaseCluster: null,
  },
]

function renderWithI18n(ui: React.ReactElement) {
  return render(
    <I18nContext.Provider
      value={{
        language: 'zh-CN',
        setLanguage: vi.fn(),
        t: (key, values) => translateMessage('zh-CN', key, values),
      }}
    >
      {ui}
    </I18nContext.Provider>,
  )
}

describe('DataSourcePickerDialog', () => {
  it('recent connections are ranked first', () => {
    renderWithI18n(
      <DataSourcePickerDialog
        open
        onOpenChange={vi.fn()}
        connections={connections}
        recentConnectionIds={['c2']}
        preferredConnectionId={null}
        onPick={vi.fn()}
      />,
    )

    const buttons = screen.getAllByRole('button')
      .map((button) => button.textContent ?? '')
      .filter((text) =>
        text.includes('orders-prod') || text.includes('analytics-dev') || text.includes('warehouse-stage'),
      )
    expect(buttons[0]).toContain('orders-prod')
    expect(buttons[1]).toContain('analytics-dev')
  })

  it('filters connections by name, host, and database name', () => {
    renderWithI18n(
      <DataSourcePickerDialog
        open
        onOpenChange={vi.fn()}
        connections={connections}
        recentConnectionIds={[]}
        preferredConnectionId={null}
        onPick={vi.fn()}
      />,
    )

    fireEvent.change(screen.getByPlaceholderText('搜索数据源'), {
      target: { value: 'warehouse' },
    })
    expect(screen.getByRole('button', { name: 'warehouse-stage' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'orders-prod' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('搜索数据源'), {
      target: { value: 'prod.db.local' },
    })
    expect(screen.getByRole('button', { name: 'orders-prod' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'analytics-dev' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('搜索数据源'), {
      target: { value: 'analytics' },
    })
    expect(screen.getByRole('button', { name: 'analytics-dev' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'warehouse-stage' })).not.toBeInTheDocument()
  })
})
