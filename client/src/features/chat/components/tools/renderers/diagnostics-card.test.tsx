import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { ToolPart } from '@/services/channel/types'

// Mock the ToolRegistry.register calls inside diagnostics-card.tsx
vi.mock('@/stores/stage-store', () => ({
  useStageStore: Object.assign(
    () => ({}),
    { getState: () => ({}) },
  ),
}))

// Must import AFTER mocks so the module-level register calls don't fail
import { DiagnosticsCard } from './diagnostics-card'

const descriptor: ActionDescriptor = {
  id: 'datatalk.explain_query',
  executor: 'SERVER',
  description: 'Explain Query',
  inputSchema: {},
  outputSchema: {},
  produces: [],
  sideEffects: [],
  requiresConnection: false,
  timeoutMs: 5000,
}

function buildPart(output: Record<string, unknown>, extra?: Partial<ToolPart>): ToolPart {
  return {
    id: 'part-1',
    type: 'tool',
    sessionID: 'sess-1',
    messageID: 'msg-1',
    tool: 'datatalk.explain_query',
    state: {
      status: 'completed',
      input: { sql: 'SELECT * FROM users' },
      output,
      metadata: {},
    },
    callID: 'call-1',
    ...extra,
  }
}

/** Expand the BasicTool body by clicking its trigger button */
function expandTool() {
  const trigger = screen.getByRole('button', { name: /diagnostics/i })
  fireEvent.click(trigger)
}

describe('DiagnosticsCard', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('shows unsupported message when unsupported flag is set', () => {
    const part = buildPart({ unsupported: true, reason: 'Unsupported dialect' })

    render(<DiagnosticsCard part={part} descriptor={descriptor} />)
    expandTool()

    expect(screen.getByText(/not supported for this query/i)).toBeInTheDocument()
  })

  it('shows error message when diagnostics returns error payload', () => {
    const part = buildPart({ error: { type: 'EXPLAIN_ERROR', message: 'Syntax error near FROM' } })

    render(<DiagnosticsCard part={part} descriptor={descriptor} />)
    expandTool()

    expect(screen.getByText(/syntax error near from/i)).toBeInTheDocument()
  })

  it('shows warning count when warnings present', () => {
    const part = buildPart({
      unsupported: false,
      warnings: ['Full table scan detected', 'No index on join column'],
    })

    render(<DiagnosticsCard part={part} descriptor={descriptor} />)

    // Warning count is shown in the subtitle (visible even when collapsed)
    expect(screen.getByText(/2 warnings/)).toBeInTheDocument()
  })

  it('shows Open in Workbench button', () => {
    const part = buildPart({ unsupported: false, warnings: [] })

    render(<DiagnosticsCard part={part} descriptor={descriptor} />)
    expandTool()

    expect(screen.getByRole('button', { name: /open in workbench/i })).toBeInTheDocument()
  })

  it('does not render recommendation section when empty', () => {
    const part = buildPart({ unsupported: false, warnings: [] })

    render(<DiagnosticsCard part={part} descriptor={descriptor} />)
    expandTool()

    expect(screen.queryByText(/recommendations/i)).not.toBeInTheDocument()
  })

  it('renders recommendations when present', () => {
    const part = buildPart({
      unsupported: false,
      warnings: [],
      recommendations: [
        { table: 'users', columns: ['email'], indexType: 'BTREE', impact: 'HIGH', rationale: 'Speed up lookup' },
      ],
    })

    render(<DiagnosticsCard part={part} descriptor={descriptor} />)
    expandTool()

    expect(screen.getByText(/recommendations \(1\)/i)).toBeInTheDocument()
    expect(screen.getByText(/users \(email\) — speed up lookup/i)).toBeInTheDocument()
  })
})
