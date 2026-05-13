import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TAB_TYPE_REGISTRY, getTabTypeDescriptor, isPersistent, getScope } from '@/features/stage/registry/tab-type-registry'
import { DownloadIcon, LibraryIcon } from 'lucide-react'
import type { StageTab } from '@/stores/stage-store'
import type { IngestionJobView } from '../api/ingestion-api'

function renderWithClient(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>{ui}</QueryClientProvider>,
  )
}

// ── Mocks ──────────────────────────────────────────────────────────────────

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string, values?: Record<string, string | number>) => {
      // Simple interpolation for test readability
      let result = key
      if (values) {
        for (const [k, v] of Object.entries(values)) {
          result = result.replace(`{${k}}`, String(v))
        }
      }
      return result
    },
  }),
}))

// Mock ingestion API to avoid real HTTP calls
const mockGetIngestionJob = vi.fn<(id: string) => Promise<IngestionJobView>>()
const mockListIngestionJobs = vi.fn<(params?: Record<string, unknown>) => Promise<{ items: IngestionJobView[]; total: number }>>()

vi.mock('../api/ingestion-api', () => ({
  getIngestionJob: (id: string) => mockGetIngestionJob(id),
  listIngestionJobs: (params?: Record<string, unknown>) => mockListIngestionJobs(params),
  getPayloadPreview: vi.fn().mockResolvedValue({ columns: [], rows: [], totalRows: 0 }),
  confirmIngestionJob: vi.fn(),
  cancelIngestionJob: vi.fn(),
  ingestionJobsKey: ['ingestion-jobs'] as const,
  ingestionJobKey: (id: string) => ['ingestion-jobs', id] as const,
  payloadPreviewKey: (id: string) => ['ingestion-jobs', id, 'payload-preview'] as const,
}))

// Mock useStageStore.openTab for IngestionLibraryTab
vi.mock('@/stores/stage-store', () => ({
  useStageStore: <T,>(selector: (s: { openTab: (tab: StageTab) => void }) => T) =>
    selector({ openTab: vi.fn() }),
}))

// ── Fixtures ───────────────────────────────────────────────────────────────

function makeJob(overrides: Partial<IngestionJobView> = {}): IngestionJobView {
  return {
    id: 'job_abc123def456',
    name: 'smoke test job',
    sourceUrl: 'https://example.com/data.csv',
    status: 'fetching',
    payloadFormat: 'csv',
    payloadArtifactId: 'artifact_001',
    connectionId: 'conn_001',
    targetSchema: 'public',
    targetTable: 'ingested_data',
    rowCount: null,
    rowsInserted: null,
    bytesFetched: null,
    mappingHash: null,
    mapping: null,
    createdBy: { kind: 'ai', sessionId: 'sess_mock', label: 'AI · smoke' },
    heartbeatAt: null,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    completedAt: null,
    errorMessage: null,
    ...overrides,
  }
}

// ── Tests ──────────────────────────────────────────────────────────────────

describe('Ingestion E2E smoke — tab type registry', () => {
  it('ingestion_job tab type is registered', () => {
    expect(TAB_TYPE_REGISTRY.ingestion_job).toBeDefined()
    const desc = getTabTypeDescriptor('ingestion_job')
    expect(desc.type).toBe('ingestion_job')
    expect(desc.persistent).toBe(true)
    expect(desc.scope).toBe('workspace')
    expect(desc.payloadSource).toBe('stage_tab')
    expect(desc.icon).toBe(DownloadIcon)
    expect(desc.labelKey).toBe('ingestion.job.title')
  })

  it('ingestion_library tab type is registered', () => {
    expect(TAB_TYPE_REGISTRY.ingestion_library).toBeDefined()
    const desc = getTabTypeDescriptor('ingestion_library')
    expect(desc.type).toBe('ingestion_library')
    expect(desc.persistent).toBe(true)
    expect(desc.scope).toBe('workspace')
    expect(desc.icon).toBe(LibraryIcon)
    expect(desc.labelKey).toBe('ingestion.library.title')
  })

  it('ingestion_job extractContent returns sourceUrl and id from payload', () => {
    const desc = getTabTypeDescriptor('ingestion_job')
    const content = desc.extractContent({
      sourceUrl: 'https://example.com/data.csv',
      id: 'job_abc',
    })
    expect(content).toContain('https://example.com/data.csv')
    expect(content).toContain('job_abc')
  })

  it('ingestion_job extractContent is total for null/undefined/empty', () => {
    const desc = getTabTypeDescriptor('ingestion_job')
    expect(desc.extractContent(null)).toBe('')
    expect(desc.extractContent(undefined)).toBe('')
    expect(desc.extractContent({})).toBe('')
  })

  it('ingestion_library extractContent returns empty string', () => {
    const desc = getTabTypeDescriptor('ingestion_library')
    expect(desc.extractContent(null)).toBe('')
    expect(desc.extractContent({ someData: 'test' })).toBe('')
  })

  it('isPersistent and getScope helpers work for ingestion types', () => {
    expect(isPersistent('ingestion_job')).toBe(true)
    expect(isPersistent('ingestion_library')).toBe(true)
    expect(getScope('ingestion_job')).toBe('workspace')
    expect(getScope('ingestion_library')).toBe('workspace')
  })
})

describe('Ingestion E2E smoke — IngestionJobTab rendering', () => {
  // Must be imported after mocks are set up
  let IngestionJobTab: React.ComponentType<{ tab: StageTab }>

  beforeEach(async () => {
    // Dynamic import so mocks are active before module evaluation
    const mod = await import('../ingestion-job-tab')
    IngestionJobTab = mod.IngestionJobTab
  })

  it('renders loading state when jobId is present but job data is loading', async () => {
    // Simulate a pending promise (never resolves)
    mockGetIngestionJob.mockReturnValue(new Promise(() => {}))

    const tab: StageTab = {
      tabId: 'tab_job_1',
      type: 'ingestion_job',
      title: 'Job abc123de',
      payload: { id: 'job_abc123def456' },
      createdAt: Date.now(),
    }

    renderWithClient(<IngestionJobTab tab={tab} />)

    // Should show a loading indicator (the component renders "Loading..." text)
    await waitFor(() => {
      expect(screen.getByText('Loading...')).toBeInTheDocument()
    })
  })

  it('renders nothing when tab payload has no id', () => {
    const tab: StageTab = {
      tabId: 'tab_job_empty',
      type: 'ingestion_job',
      title: 'Empty Job',
      payload: {},
      createdAt: Date.now(),
    }

    const { container } = renderWithClient(<IngestionJobTab tab={tab} />)
    expect(container.innerHTML).toBe('')
  })

  it('renders fetching phase when job is in fetching status', async () => {
    const job = makeJob({ status: 'fetching' })
    mockGetIngestionJob.mockResolvedValue(job)

    const tab: StageTab = {
      tabId: 'tab_job_fetching',
      type: 'ingestion_job',
      title: 'Job fetching',
      payload: { id: job.id },
      createdAt: Date.now(),
    }

    renderWithClient(<IngestionJobTab tab={tab} />)

    await waitFor(() => {
      // The fetching phase renders a status badge with the status text
      expect(screen.getByText('fetching')).toBeInTheDocument()
    })
  })

  it('renders completed phase when job is completed', async () => {
    const job = makeJob({
      status: 'completed',
      rowCount: 1500,
      rowsInserted: 1500,
      completedAt: Date.now(),
    })
    mockGetIngestionJob.mockResolvedValue(job)

    const tab: StageTab = {
      tabId: 'tab_job_completed',
      type: 'ingestion_job',
      title: 'Job completed',
      payload: { id: job.id },
      createdAt: Date.now(),
    }

    renderWithClient(<IngestionJobTab tab={tab} />)

    await waitFor(() => {
      expect(screen.getByText('completed')).toBeInTheDocument()
    })
  })

  it('renders failed phase with error message when job has failed', async () => {
    const job = makeJob({
      status: 'failed',
      errorMessage: 'Connection refused: example.com:443',
    })
    mockGetIngestionJob.mockResolvedValue(job)

    const tab: StageTab = {
      tabId: 'tab_job_failed',
      type: 'ingestion_job',
      title: 'Job failed',
      payload: { id: job.id },
      createdAt: Date.now(),
    }

    renderWithClient(<IngestionJobTab tab={tab} />)

    await waitFor(() => {
      expect(screen.getByText('failed')).toBeInTheDocument()
      expect(screen.getByText('Connection refused: example.com:443')).toBeInTheDocument()
    })
  })

  it('renders writing phase with progress info', async () => {
    const job = makeJob({
      status: 'writing',
      rowCount: 1000,
      rowsInserted: 500,
    })
    mockGetIngestionJob.mockResolvedValue(job)

    const tab: StageTab = {
      tabId: 'tab_job_writing',
      type: 'ingestion_job',
      title: 'Job writing',
      payload: { id: job.id },
      createdAt: Date.now(),
    }

    renderWithClient(<IngestionJobTab tab={tab} />)

    await waitFor(() => {
      expect(screen.getByText('writing')).toBeInTheDocument()
      // WritingPhase shows row counts
      expect(screen.getByText(/500.*1,000/)).toBeInTheDocument()
    })
  })

  it('renders phase stepper dots for all active phases', async () => {
    const job = makeJob({ status: 'writing', rowCount: 100, rowsInserted: 50 })
    mockGetIngestionJob.mockResolvedValue(job)

    const tab: StageTab = {
      tabId: 'tab_job_writing_dots',
      type: 'ingestion_job',
      title: 'Job writing',
      payload: { id: job.id },
      createdAt: Date.now(),
    }

    renderWithClient(<IngestionJobTab tab={tab} />)

    await waitFor(() => {
      // Phase stepper has 6 dots (PHASE_ORDER.slice(0, 6))
      const dots = screen.getAllByTitle(/^(fetching|fetched|mapped|confirmed|writing|completed)$/)
      expect(dots).toHaveLength(6)
    })
  })
})

describe('Ingestion E2E smoke — IngestionLibraryTab rendering', () => {
  let IngestionLibraryTab: React.ComponentType

  beforeEach(async () => {
    const mod = await import('../ingestion-library-tab')
    IngestionLibraryTab = mod.IngestionLibraryTab
  })

  it('renders without crashing when API returns empty list', async () => {
    mockListIngestionJobs.mockResolvedValue({ items: [], total: 0 })

    renderWithClient(<IngestionLibraryTab />)

    // Should show the "No ingestion jobs found" empty state
    await waitFor(() => {
      expect(screen.getByText('No ingestion jobs found')).toBeInTheDocument()
    })
  })

  it('renders job rows when API returns items', async () => {
    const jobs = [
      makeJob({
        id: 'job_001',
        sourceUrl: 'https://example.com/sales.csv',
        status: 'completed',
        targetTable: 'sales_data',
        rowCount: 5000,
        createdAt: new Date('2026-05-10T10:00:00Z').getTime(),
      }),
      makeJob({
        id: 'job_002',
        sourceUrl: 'https://example.com/users.json',
        status: 'failed',
        targetTable: 'users_import',
        rowCount: null,
        createdAt: new Date('2026-05-11T15:30:00Z').getTime(),
      }),
    ]
    mockListIngestionJobs.mockResolvedValue({ items: jobs, total: 2 })

    renderWithClient(<IngestionLibraryTab />)

    await waitFor(() => {
      expect(screen.getByText('https://example.com/sales.csv')).toBeInTheDocument()
      expect(screen.getByText('https://example.com/users.json')).toBeInTheDocument()
    })

    // Status badges
    expect(screen.getByText('completed')).toBeInTheDocument()
    expect(screen.getByText('failed')).toBeInTheDocument()

    // Footer showing total count
    expect(screen.getByText(/2 jobs total/)).toBeInTheDocument()
  })

  it('renders title and filter controls', async () => {
    mockListIngestionJobs.mockResolvedValue({ items: [], total: 0 })

    renderWithClient(<IngestionLibraryTab />)

    // Title rendered via t() key
    expect(screen.getByText('ingestion.library.title')).toBeInTheDocument()
  })
})

describe('Ingestion E2E smoke — IngestionJobsStore', () => {
  // Must be imported after mocks are set up
  let useIngestionJobsStore: typeof import('../stores/use-ingestion-jobs-store').useIngestionJobsStore

  beforeEach(async () => {
    const mod = await import('../stores/use-ingestion-jobs-store')
    useIngestionJobsStore = mod.useIngestionJobsStore
  })

  it('initializes with empty editing map', () => {
    const state = useIngestionJobsStore.getState()
    expect(state.editingMapping.size).toBe(0)
  })

  it('hydrates from a job with mapping columns', () => {
    const job = makeJob({
      id: 'job_hydrate',
      mapping: {
        mappingId: 'map_1',
        columns: [
          { sourcePath: '$.name', targetName: 'user_name', type: 'VARCHAR(255)', skip: false, sampleValues: ['Alice', 'Bob'], nullable: false },
          { sourcePath: '$.age', targetName: 'user_age', type: 'INTEGER', skip: false, sampleValues: ['30', '25'], nullable: true },
        ],
      },
    })

    useIngestionJobsStore.getState().hydrateFromJob(job)

    const edit = useIngestionJobsStore.getState().editingMapping.get('job_hydrate')
    expect(edit).toBeDefined()
    expect(edit!.columns).toHaveLength(2)
    expect(edit!.columns[0].targetName).toBe('user_name')
    expect(edit!.targetTable).toBe('ingested_data')
  })

  it('does not overwrite existing edits on re-hydration', () => {
    const job = makeJob({ id: 'job_preserve' })

    // Set initial edit
    useIngestionJobsStore.getState().setMappingEdit('job_preserve', {
      columns: [{ sourcePath: '$.x', targetName: 'col_x', type: 'TEXT', skip: false, sampleValues: [], nullable: false }],
      targetTable: 'my_table',
      targetSchema: null,
    })

    // Hydrate should not overwrite
    useIngestionJobsStore.getState().hydrateFromJob({
      ...job,
      mapping: {
        mappingId: 'map_2',
        columns: [{ sourcePath: '$.y', targetName: 'col_y', type: 'TEXT', skip: false, sampleValues: [], nullable: false }],
      },
    })

    const edit = useIngestionJobsStore.getState().editingMapping.get('job_preserve')
    expect(edit!.columns).toHaveLength(1)
    expect(edit!.columns[0].targetName).toBe('col_x')
  })

  it('clears mapping edits', () => {
    useIngestionJobsStore.getState().setMappingEdit('job_clear', {
      columns: [],
      targetTable: 't',
      targetSchema: null,
    })

    useIngestionJobsStore.getState().clearMappingEdit('job_clear')

    expect(useIngestionJobsStore.getState().editingMapping.has('job_clear')).toBe(false)
  })
})
