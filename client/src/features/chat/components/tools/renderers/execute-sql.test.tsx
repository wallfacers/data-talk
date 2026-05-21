import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ExecuteSql } from './execute-sql'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { ToolPart } from '@/services/channel/types'

vi.mock('@/stores/ui-settings-store', () => ({
  getCurrentLanguage: () => 'en-US',
}))

vi.mock('@/i18n/messages', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/i18n/messages')>()
  return {
    translateMessage: (_lang: string, key: string, values?: Record<string, unknown>) =>
      actual.translateMessage('en-US', key as never, values as Record<string, string | number>),
  }
})

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
      input: { sql: 'SELECT * FROM users' },
      output,
      metadata: {},
    },
    callID: 'call-abc',
    ...extra,
  }
}

describe('ExecuteSql renderer', () => {
  it('renders the legacy artifact view for executed L1 output', () => {
    const part = buildPart({
      rowCount: 42,
      rows: [{ id: 1, name: 'Alice' }],
      columns: ['id', 'name'],
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    expect(screen.getByText('42 rows affected')).toBeTruthy()
  })
})
