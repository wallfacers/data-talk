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

    // Connection items are ContextMenuTrigger divs with aria-label, not role="button"
    const ordersBtn = screen.getByLabelText('orders-prod')
    const analyticsBtn = screen.getByLabelText('analytics-dev')
    expect(ordersBtn).toBeInTheDocument()
    expect(analyticsBtn).toBeInTheDocument()

    // Verify ranking by DOM order: c2 (orders-prod) should appear before c1 (analytics-dev)
    expect(ordersBtn.compareDocumentPosition(analyticsBtn)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
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
    expect(screen.getByLabelText('warehouse-stage')).toBeInTheDocument()
    expect(screen.queryByLabelText('orders-prod')).not.toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('搜索数据源'), {
      target: { value: 'prod.db.local' },
    })
    expect(screen.getByLabelText('orders-prod')).toBeInTheDocument()
    expect(screen.queryByLabelText('analytics-dev')).not.toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('搜索数据源'), {
      target: { value: 'analytics' },
    })
    expect(screen.getByLabelText('analytics-dev')).toBeInTheDocument()
    expect(screen.queryByLabelText('warehouse-stage')).not.toBeInTheDocument()
  })
})
