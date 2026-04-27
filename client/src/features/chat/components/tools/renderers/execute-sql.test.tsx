import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { translateMessage } from '@/i18n/messages'
import { ExecuteSql } from './execute-sql'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { ToolPart } from '@/services/channel/types'

const mockActionResult = vi.fn()

// Real translation function for en-US
const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
  translateMessage('en-US', key, values)

vi.mock('@/services/channel/use-channel', () => ({
  useChannel: () => ({
    client: {
      actionResult: mockActionResult,
    },
  }),
}))

vi.mock('@/stores/ui-settings-store', () => ({
  getCurrentLanguage: () => 'en-US',
}))

// Mock translateMessage for the execute-sql component (uses getCurrentLanguage + translateMessage)
vi.mock('@/i18n/messages', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/i18n/messages')>()
  return {
    translateMessage: (_lang: string, key: string, values?: Record<string, unknown>) =>
      actual.translateMessage('en-US', key as never, values as Record<string, string | number>),
  }
})

// Mock useI18n for SqlConfirmationCard (uses useI18n().t)
vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    setLanguage: vi.fn(),
    t: (key: string, values?: Record<string, string | number>) => t(key as never, values),
  }),
}))

const descriptor: ActionDescriptor = {
  id: 'datatalk_execute_sql',
  executor: 'CLIENT',
  description: 'Execute SQL',
  inputSchema: {},
  outputSchema: {},
  produces: [],
  sideEffects: [],
  requiresConnection: true,
  timeoutMs: 30000,
  category: 'query',
}

function buildPart(output: Record<string, unknown>, extra?: Partial<ToolPart>): ToolPart {
  return {
    id: 'part-1',
    type: 'tool',
    sessionID: 'sess-1',
    messageID: 'msg-1',
    tool: 'datatalk_execute_sql',
    state: {
      status: 'completed',
      input: { sql: 'DELETE FROM users WHERE id = 1' },
      output,
      metadata: {},
    },
    callID: 'call-abc',
    ...extra,
  }
}

describe('ExecuteSql renderer', () => {
  beforeEach(() => {
    mockActionResult.mockClear()
  })

  it('renders confirmation card for requires_confirmation output', () => {
    const part = buildPart({
      status: 'requires_confirmation',
      risk: {
        level: 'L2',
        reason: 'INSERT statement',
        affectedObjects: ['public.users'],
      },
      sqlPreview: "INSERT INTO users (name) VALUES ('test')",
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    // Should show the SQL preview in the confirmation card
    expect(screen.getByText(/INSERT INTO users/)).toBeTruthy()
    // Should show affected object
    expect(screen.getByText('public.users')).toBeTruthy()
    // Should have Execute and Cancel buttons (rendered by SqlConfirmationCard via en-US i18n)
    expect(screen.getByRole('button', { name: 'Execute' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy()
  })

  it('sends actionResult with confirmed:true and riskAck on Execute', () => {
    const part = buildPart({
      status: 'requires_confirmation',
      risk: {
        level: 'L3',
        reason: 'DELETE statement',
        affectedObjects: ['public.users'],
      },
      sqlPreview: 'DELETE FROM users',
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    const executeBtn = screen.getByRole('button', { name: 'Execute' })
    fireEvent.click(executeBtn)

    expect(mockActionResult).toHaveBeenCalledWith(
      'call-abc',
      true,
      { confirmed: true, riskAck: 'L3' },
    )
  })

  it('sends confirmed:false on Cancel', () => {
    const part = buildPart({
      status: 'requires_confirmation',
      risk: {
        level: 'L2',
        reason: 'UPDATE statement',
        affectedObjects: ['public.orders'],
      },
      sqlPreview: 'UPDATE orders SET status = 1',
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    const cancelBtn = screen.getByRole('button', { name: 'Cancel' })
    fireEvent.click(cancelBtn)

    expect(mockActionResult).toHaveBeenCalledWith(
      'call-abc',
      true,
      { confirmed: false },
    )
  })

  it('renders the legacy artifact view for executed output', () => {
    const part = buildPart({
      rowCount: 42,
      rows: [{ id: 1, name: 'Alice' }],
      columns: ['id', 'name'],
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    // Should render the row count in the subtitle
    expect(screen.getByText('42 rows affected')).toBeTruthy()
  })

  it('renders confirmation_invalid status with reason', () => {
    const part = buildPart({
      status: 'confirmation_invalid',
      reason: 'Risk escalated from L2 to L3',
      ackedRisk: 'L2',
      currentRisk: 'L3',
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    expect(screen.getByText(/Risk escalated from L2 to L3/)).toBeTruthy()
  })

  it('uses part.id fallback when callID is missing', () => {
    const part = buildPart({
      status: 'requires_confirmation',
      risk: { level: 'L2', reason: 'test', affectedObjects: [] },
      sqlPreview: 'UPDATE t SET x=1',
    }, { callID: undefined })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    const executeBtn = screen.getByRole('button', { name: 'Execute' })
    fireEvent.click(executeBtn)

    expect(mockActionResult).toHaveBeenCalledWith(
      'part-1',
      true,
      { confirmed: true, riskAck: 'L2' },
    )
  })
})
