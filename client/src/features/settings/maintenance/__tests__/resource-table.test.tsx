import { render, screen, waitFor, fireEvent, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ResourceDirectoryView } from '../maintenance-page'
import * as maintenanceApi from '@/services/api/maintenance'

vi.mock('@/services/api/maintenance')

// Override global i18n mock to return keys with param values (simplifies text matching)
vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'zh-CN',
    setLanguage: vi.fn(),
    t: (key: string, values?: Record<string, string | number>) => {
      if (values) {
        const entries = Object.entries(values).map(([k, v]) => `${k}:${v}`).join(',')
        return `${key}(${entries})`
      }
      return key
    },
  }),
}))

const BASE_OVERVIEW = {
  workdir: '/tmp/test',
  totalBytes: 500_000,
  breakdown: {},
  lastHousekeepingRunAt: null,
  resourceDirectories: {
    dashboards: { count: 3, sizeBytes: 120_000 },
    reports: { count: 2, sizeBytes: 80_000 },
    exports: { count: 4, sizeBytes: 40_000 },
    semantic: { count: 1, sizeBytes: 10_000 },
    uploads: { count: 5, sizeBytes: 250_000 },
  },
} as maintenanceApi.StorageOverviewDto

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

describe('ResourceDirectoryView', () => {
  let qc: QueryClient

  beforeEach(() => {
    qc = createQueryClient()
    vi.clearAllMocks()
  })

  function wrap(ui: React.ReactElement) {
    return <QueryClientProvider client={qc}>{ui}</QueryClientProvider>
  }

  // -------------------------------------------------------------------------
  // Per-resource-type column rendering
  // -------------------------------------------------------------------------

  it('renders dashboard columns: name, widgets, size, createdAt, actions', async () => {
    vi.mocked(maintenanceApi.getDashboards).mockResolvedValue([
      {
        id: 'd1', title: 'Sales Dashboard', filename: 'sales.json',
        sizeBytes: 45000, widgetCount: 5, originSessionId: null,
        createdAt: 1715700000000, updatedAt: 1715700000000,
      },
      {
        id: 'd2', title: null as unknown as string, filename: 'untitled.json',
        sizeBytes: 12000, widgetCount: 2, originSessionId: 'sess-1',
        createdAt: 1715600000000, updatedAt: 1715600000000,
      },
    ])

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    await waitFor(() => {
      expect(screen.getByText('Sales Dashboard')).toBeInTheDocument()
      expect(screen.getByText('untitled.json')).toBeInTheDocument()
    })

    // Scope numbers to the table to avoid matching summary card counts
    const table = screen.getByRole('table')
    expect(within(table).getByText('5')).toBeInTheDocument()
    expect(within(table).getByText('2')).toBeInTheDocument()

    // Second dashboard shows originSessionId
    expect(screen.getByText(/sess-1/)).toBeInTheDocument()
  })

  it('renders report columns: title, formats, size, createdAt, actions', async () => {
    vi.mocked(maintenanceApi.getReports).mockResolvedValue([
      {
        id: 'r1', title: 'Monthly Report', availableFormats: ['PDF', 'HTML', 'MD'],
        sizeBytes: 32000, originSessionId: null,
        createdAt: 1715700000000, updatedAt: 1715700000000,
      },
    ])

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    // Click reports tab (use button role to avoid matching summary card text)
    const reportsTab = await screen.findByRole('button', { name: /maintenance\.resources\.tab\.reports/ })
    fireEvent.click(reportsTab)

    await waitFor(() => {
      expect(screen.getByText('Monthly Report')).toBeInTheDocument()
      expect(screen.getByText('PDF')).toBeInTheDocument()
      expect(screen.getByText('HTML')).toBeInTheDocument()
    })
  })

  it('renders export columns: filename, format, size, rows, expiresAt, actions', async () => {
    vi.mocked(maintenanceApi.getExports).mockResolvedValue([
      {
        exportId: 'e1', filename: 'data.csv', format: 'csv',
        sizeBytes: 15000, rowCount: 1000, originSessionId: null,
        createdAt: 1715700000000, expiresAt: Date.now() + 3600000,
      },
    ])

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    fireEvent.click(screen.getByRole('button', { name: /maintenance\.resources\.tab\.exports/ }))

    await waitFor(() => {
      expect(screen.getByText('data.csv')).toBeInTheDocument()
      expect(screen.getByText('CSV')).toBeInTheDocument()
      expect(screen.getByText('1,000')).toBeInTheDocument()
    })
  })

  it('renders semantic columns: name, status, size, updatedAt, actions', async () => {
    vi.mocked(maintenanceApi.getSemantic).mockResolvedValue([
      {
        domain: 'sales', connectionId: 'conn-1', connectionName: 'Prod DB',
        status: 'active', sizeBytes: 5000, updatedAt: 1715700000000,
      },
    ])

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    fireEvent.click(screen.getByRole('button', { name: /maintenance\.resources\.tab\.semantic/ }))

    await waitFor(() => {
      expect(screen.getByText('sales')).toBeInTheDocument()
      expect(screen.getByText('Prod DB')).toBeInTheDocument()
    })
  })

  it('renders upload columns: filename, mimeType, size, expiresAt, actions', async () => {
    vi.mocked(maintenanceApi.getUploads).mockResolvedValue([
      {
        id: 'u1', filename: 'photo.png', mimeType: 'image/png',
        sizeBytes: 200000, originSessionId: null,
        createdAt: 1715700000000, expiresAt: Date.now() + 7200000,
      },
    ])

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    fireEvent.click(screen.getByRole('button', { name: /maintenance\.resources\.tab\.uploads/ }))

    await waitFor(() => {
      expect(screen.getByText('photo.png')).toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------------
  // Empty state
  // -------------------------------------------------------------------------

  it('shows empty state message when no data is loaded', async () => {
    vi.mocked(maintenanceApi.getDashboards).mockResolvedValue([])

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    await waitFor(() => {
      expect(screen.getByText(/maintenance\.resources\.empty\.dashboards/)).toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------------
  // Loading skeleton
  // -------------------------------------------------------------------------

  it('shows loading skeletons while data is loading', async () => {
    let resolveGet: (value: maintenanceApi.DashboardResourceDto[]) => void
    const deferred = new Promise<maintenanceApi.DashboardResourceDto[]>((resolve) => {
      resolveGet = resolve
    })
    vi.mocked(maintenanceApi.getDashboards).mockReturnValue(deferred)

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    // Wait for skeletons to appear via their data-slot attribute
    const skeletons = await waitFor(() => document.querySelectorAll('[data-slot="skeleton"]'))
    expect(skeletons.length).toBeGreaterThan(0)

    // Resolve and verify data loads
    resolveGet!([{ id: 'd1', title: 'Dash', filename: 'd.json', sizeBytes: 100, widgetCount: 1, originSessionId: null, createdAt: 1, updatedAt: 1 }])
    await waitFor(() => {
      expect(screen.getByText('Dash')).toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------------
  // Delete confirmation dialog
  // -------------------------------------------------------------------------

  it('opens delete confirmation dialog when delete button is clicked', async () => {
    vi.mocked(maintenanceApi.getDashboards).mockResolvedValue([
      {
        id: 'd1', title: 'My Dashboard', filename: 'my.json',
        sizeBytes: 5000, widgetCount: 3, originSessionId: null,
        createdAt: 1715700000000, updatedAt: 1715700000000,
      },
    ])
    vi.mocked(maintenanceApi.deleteDashboard).mockResolvedValue()

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    await waitFor(() => {
      expect(screen.getByText('My Dashboard')).toBeInTheDocument()
    })

    // Click delete button on the first row
    const deleteButtons = screen.getAllByText(/maintenance\.resources\.action\.delete/)
    fireEvent.click(deleteButtons[0])

    // Confirmation dialog should appear
    await waitFor(() => {
      expect(screen.getByText(/maintenance\.resources\.confirmDelete\.title/)).toBeInTheDocument()
    })

    // Click confirm
    fireEvent.click(screen.getByText(/maintenance\.resources\.confirmDelete\.confirm/))

    await waitFor(() => {
      expect(maintenanceApi.deleteDashboard).toHaveBeenCalledWith('d1')
    })
  })

  // -------------------------------------------------------------------------
  // Batch selection and bulk delete
  // -------------------------------------------------------------------------

  it('supports batch selection via checkboxes and bulk delete', async () => {
    vi.mocked(maintenanceApi.getDashboards).mockResolvedValue([
      {
        id: 'd1', title: 'Dash A', filename: 'a.json',
        sizeBytes: 1000, widgetCount: 1, originSessionId: null,
        createdAt: 1715700000000, updatedAt: 1715700000000,
      },
      {
        id: 'd2', title: 'Dash B', filename: 'b.json',
        sizeBytes: 2000, widgetCount: 2, originSessionId: null,
        createdAt: 1715700000000, updatedAt: 1715700000000,
      },
      {
        id: 'd3', title: 'Dash C', filename: 'c.json',
        sizeBytes: 3000, widgetCount: 3, originSessionId: null,
        createdAt: 1715700000000, updatedAt: 1715700000000,
      },
    ])
    vi.mocked(maintenanceApi.deleteDashboard).mockResolvedValue()

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    await waitFor(() => {
      expect(screen.getByText('Dash A')).toBeInTheDocument()
      expect(screen.getByText('Dash B')).toBeInTheDocument()
      expect(screen.getByText('Dash C')).toBeInTheDocument()
    })

    // Click the select-all checkbox in the table header
    const table = screen.getByRole('table')
    const headerCheckbox = within(table).getAllByRole('checkbox')[0]
    fireEvent.click(headerCheckbox)

    // Bulk delete button should appear
    await waitFor(() => {
      expect(screen.getByText(/maintenance\.resources\.action\.deleteSelected/)).toBeInTheDocument()
    })

    // Click bulk delete
    fireEvent.click(screen.getByText(/maintenance\.resources\.action\.deleteSelected/))

    // Confirmation dialog
    await waitFor(() => {
      expect(screen.getByText(/maintenance\.resources\.confirmDeleteBulk\.title/)).toBeInTheDocument()
    })

    // Confirm
    fireEvent.click(screen.getByText(/maintenance\.resources\.confirmDelete\.confirm/))

    await waitFor(() => {
      expect(maintenanceApi.deleteDashboard).toHaveBeenCalledTimes(3)
    })
  })

  // -------------------------------------------------------------------------
  // Tab switching clears selection
  // -------------------------------------------------------------------------

  it('clears selection when switching resource tabs', async () => {
    vi.mocked(maintenanceApi.getDashboards).mockResolvedValue([
      {
        id: 'd1', title: 'Dash A', filename: 'a.json',
        sizeBytes: 1000, widgetCount: 1, originSessionId: null,
        createdAt: 1715700000000, updatedAt: 1715700000000,
      },
    ])
    vi.mocked(maintenanceApi.getReports).mockResolvedValue([])

    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    await waitFor(() => {
      expect(screen.getByText('Dash A')).toBeInTheDocument()
    })

    // Select the row
    const checkboxes = screen.getAllByRole('checkbox')
    // First checkbox is select-all, second is the row checkbox
    fireEvent.click(checkboxes[1])

    await waitFor(() => {
      expect(screen.getByText(/maintenance\.resources\.action\.deleteSelected/)).toBeInTheDocument()
    })

    // Switch to reports tab - selection should clear
    fireEvent.click(screen.getByRole('button', { name: /maintenance\.resources\.tab\.reports/ }))

    await waitFor(() => {
      expect(screen.queryByText(/maintenance\.resources\.action\.deleteSelected/)).not.toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------------
  // Resource directory card summaries
  // -------------------------------------------------------------------------

  it('renders summary cards for each resource directory', () => {
    render(wrap(<ResourceDirectoryView overview={BASE_OVERVIEW} />))

    expect(screen.getByText('3')).toBeInTheDocument() // dashboards count
    expect(screen.getByText('2')).toBeInTheDocument() // reports count
    expect(screen.getByText('4')).toBeInTheDocument() // exports count
    expect(screen.getByText('1')).toBeInTheDocument() // semantic count
    expect(screen.getByText('5')).toBeInTheDocument() // uploads count
  })
})
