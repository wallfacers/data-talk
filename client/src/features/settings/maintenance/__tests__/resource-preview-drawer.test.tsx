import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ResourcePreviewDrawer } from '../resource-preview-drawer'
import * as maintenanceApi from '@/services/api/maintenance'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock('@/services/api/maintenance')

vi.mock('@/components/ui/sheet', () => ({
  Sheet: ({ open, children }: { open: boolean; children: React.ReactNode; onOpenChange?: () => void }) =>
    open ? <div data-testid="sheet">{children}</div> : null,
  SheetContent: ({ children, className }: { children: React.ReactNode; className?: string; side?: string; showCloseButton?: boolean }) =>
    <div data-testid="sheet-content" className={className}>{children}</div>,
  SheetHeader: ({ children }: { children: React.ReactNode }) =>
    <div data-testid="sheet-header">{children}</div>,
  SheetTitle: ({ children }: { children: React.ReactNode }) =>
    <div data-testid="sheet-title">{children}</div>,
  SheetDescription: ({ children }: { children: React.ReactNode }) =>
    <div data-testid="sheet-description">{children}</div>,
}))

vi.mock('@/components/ui/skeleton', () => ({
  Skeleton: ({ className }: { className?: string }) =>
    <div data-testid="skeleton" className={className} role="status" aria-busy="true" />,
}))

vi.mock('@/components/ui/badge', () => ({
  Badge: ({ children, variant, className }: { children: React.ReactNode; variant?: string; className?: string }) =>
    <span data-testid="badge" data-variant={variant} className={className}>{children}</span>,
}))

vi.mock('@/components/ui/table', () => ({
  Table: ({ children }: { children: React.ReactNode; scrollContainer?: boolean }) =>
    <table data-testid="table">{children}</table>,
  TableHeader: ({ children }: { children: React.ReactNode }) =>
    <thead data-testid="table-header">{children}</thead>,
  TableBody: ({ children }: { children: React.ReactNode }) =>
    <tbody data-testid="table-body">{children}</tbody>,
  TableRow: ({ children, 'data-state': dataState }: { children: React.ReactNode; 'data-state'?: string }) =>
    <tr data-state={dataState}>{children}</tr>,
  TableHead: ({ children, className }: { children: React.ReactNode; className?: string }) =>
    <th className={className}>{children}</th>,
  TableCell: ({ children, className }: { children: React.ReactNode; className?: string }) =>
    <td className={className}>{children}</td>,
}))

// Helper to create a Response-like object for API functions returning Response
function mockTextResponse(text: string): Response {
  return {
    text: () => Promise.resolve(text),
    headers: new Headers({ 'content-type': 'text/plain' }),
    json: () => Promise.reject(new Error('not json')),
    url: '',
  } as unknown as Response
}

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ResourcePreviewDrawer', () => {
  let qc: QueryClient

  beforeEach(() => {
    qc = createQueryClient()
    vi.clearAllMocks()
  })

  function wrap(ui: React.ReactElement) {
    return <QueryClientProvider client={qc}>{ui}</QueryClientProvider>
  }

  // -------------------------------------------------------------------------
  // HTML preview (dashboards, reports)
  // -------------------------------------------------------------------------

  it('renders HTML preview with iframe sandbox for dashboards', async () => {
    const html = '<html><body><h1>Dashboard</h1></body></html>'
    vi.mocked(maintenanceApi.previewDashboard).mockResolvedValue(mockTextResponse(html))

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="dashboards"
        resourceId="d1"
        resourceName="Sales Dashboard"
      />
    ))

    await waitFor(() => {
      expect(screen.getByText('Sales Dashboard')).toBeInTheDocument()
      const iframe = screen.getByTitle('Preview')
      expect(iframe.tagName).toBe('IFRAME')
      expect(iframe.getAttribute('sandbox')).toBe('allow-scripts')
    })
  })

  it('renders HTML preview with iframe sandbox for reports', async () => {
    const html = '<html><body><p>Monthly Summary</p></body></html>'
    vi.mocked(maintenanceApi.previewReport).mockResolvedValue(mockTextResponse(html))

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="reports"
        resourceId="r1"
        resourceName="Monthly Report"
      />
    ))

    await waitFor(() => {
      const iframe = screen.getByTitle('Preview')
      expect(iframe).toBeInTheDocument()
      expect(iframe.getAttribute('sandbox')).toBe('allow-scripts')
    })
  })

  // -------------------------------------------------------------------------
  // Table preview (exports)
  // -------------------------------------------------------------------------

  it('renders table preview with columns and rows for exports', async () => {
    vi.mocked(maintenanceApi.previewExport).mockResolvedValue({
      format: 'csv',
      columns: ['id', 'name', 'value'],
      rows: [
        [1, 'Alice', '100'],
        [2, 'Bob', '200'],
      ],
      totalRows: 500,
      previewRows: 2,
    })

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="exports"
        resourceId="e1"
        resourceName="data.csv"
      />
    ))

    await waitFor(() => {
      expect(screen.getByText('id')).toBeInTheDocument()
      expect(screen.getByText('name')).toBeInTheDocument()
      expect(screen.getByText('value')).toBeInTheDocument()
      expect(screen.getByText('Alice')).toBeInTheDocument()
      expect(screen.getByText('Bob')).toBeInTheDocument()
      expect(screen.getByText(/Showing 2 of 500 rows/)).toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------------
  // Code preview (semantic)
  // -------------------------------------------------------------------------

  it('renders code preview in pre/code for semantic models', async () => {
    const yaml = 'domain: sales\nconnection: prod-db'
    const resp = mockTextResponse(yaml)
    vi.mocked(maintenanceApi.previewSemantic).mockResolvedValue(resp)

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="semantic"
        resourceId="sales"
        connectionId="conn-1"
        resourceName="Sales Model"
      />
    ))

    await waitFor(() => {
      // Multiline text: getByText treats newlines as text-node boundaries, so use a function match
      const code = screen.getByText((content) => content.includes('domain: sales'))
      expect(code.tagName).toBe('CODE')
    })
  })

  // -------------------------------------------------------------------------
  // Image preview (uploads)
  // -------------------------------------------------------------------------

  it('renders image preview with img tag for image uploads', async () => {
    const imageUrl = '/api/maintenance/uploads/u1/preview'
    const imageHeaders = new Headers({ 'content-type': 'image/png' })
    const resp = {
      headers: imageHeaders,
      url: imageUrl,
    }
    vi.mocked(maintenanceApi.previewUpload).mockResolvedValue(resp as unknown as Response)

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="uploads"
        resourceId="u1"
        resourceName="photo.png"
      />
    ))

    await waitFor(() => {
      const img = screen.getByAltText('Preview')
      expect(img.tagName).toBe('IMG')
      expect(img.getAttribute('src')).toBe(imageUrl)
    })
  })

  // -------------------------------------------------------------------------
  // Code preview (text uploads)
  // -------------------------------------------------------------------------

  it('renders code preview for text uploads', async () => {
    const content = 'SELECT * FROM users'
    const textHeaders = new Headers({ 'content-type': 'text/plain' })
    const resp = {
      headers: textHeaders,
      text: () => Promise.resolve(content),
      url: '/api/uploads/u1/preview',
    }
    vi.mocked(maintenanceApi.previewUpload).mockResolvedValue(resp as unknown as Response)

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="uploads"
        resourceId="u1"
        resourceName="query.sql"
      />
    ))

    await waitFor(() => {
      const code = screen.getByText(content)
      expect(code.tagName).toBe('CODE')
    })
  })

  // -------------------------------------------------------------------------
  // Unavailable preview (binary/non-previewable uploads)
  // -------------------------------------------------------------------------

  it('renders unavailable state for non-previewable uploads', async () => {
    const meta = { mimeType: 'application/octet-stream', sizeBytes: 10240, previewable: false, message: 'Binary files cannot be previewed' }
    const resp = {
      headers: new Headers({ 'content-type': 'application/octet-stream' }),
      text: () => Promise.resolve(JSON.stringify(meta)),
      json: () => Promise.resolve(meta),
      url: '/api/uploads/u1/preview',
    }
    vi.mocked(maintenanceApi.previewUpload).mockResolvedValue(resp as unknown as Response)

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="uploads"
        resourceId="u1"
        resourceName="data.bin"
      />
    ))

    await waitFor(() => {
      expect(screen.getByText('Binary files cannot be previewed')).toBeInTheDocument()
      expect(screen.getByText('application/octet-stream')).toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------------
  // Loading state
  // -------------------------------------------------------------------------

  it('shows loading skeleton while preview data is loading', async () => {
    let resolve: (value: Response) => void
    const deferred = new Promise<Response>((r) => { resolve = r })
    vi.mocked(maintenanceApi.previewDashboard).mockReturnValue(deferred)

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="dashboards"
        resourceId="d1"
      />
    ))

    await waitFor(() => {
      const skeletons = screen.getAllByRole('status', { busy: true })
      expect(skeletons.length).toBeGreaterThan(0)
    })

    // Resolve and verify skeleton disappears
    resolve!(mockTextResponse('<html></html>'))
    await waitFor(() => {
      expect(screen.getByTitle('Preview')).toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------------
  // Error state
  // -------------------------------------------------------------------------

  it('shows error message when preview fetch fails', async () => {
    vi.mocked(maintenanceApi.previewDashboard).mockRejectedValue(new Error('Network error'))

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="dashboards"
        resourceId="d1-error"
      />
    ))

    await waitFor(() => {
      expect(screen.getByText('Network error')).toBeInTheDocument()
    })
  })

  // -------------------------------------------------------------------------
  // Closed state
  // -------------------------------------------------------------------------

  it('does not fetch preview when drawer is closed', () => {
    render(wrap(
      <ResourcePreviewDrawer
        open={false}
        onOpenChange={vi.fn()}
        resourceType="dashboards"
        resourceId="d1-closed"
      />
    ))

    expect(maintenanceApi.previewDashboard).not.toHaveBeenCalled()
  })

  // -------------------------------------------------------------------------
  // Resource name fallback
  // -------------------------------------------------------------------------

  it('uses resourceId as fallback title when resourceName is not provided', async () => {
    vi.mocked(maintenanceApi.previewDashboard).mockResolvedValue(mockTextResponse('<html></html>'))

    render(wrap(
      <ResourcePreviewDrawer
        open={true}
        onOpenChange={vi.fn()}
        resourceType="dashboards"
        resourceId="d1"
      />
    ))

    await waitFor(() => {
      expect(screen.getByText('d1')).toBeInTheDocument()
      expect(screen.getByText('Dashboard')).toBeInTheDocument()
    })
  })
})
