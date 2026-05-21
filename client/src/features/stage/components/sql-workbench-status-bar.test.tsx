import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { translateMessage } from '@/i18n/messages'
import { SqlWorkbenchStatusBar } from './sql-workbench-status-bar'

describe('SqlWorkbenchStatusBar', () => {
  const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
    translateMessage('zh-CN', key, values)

  it.each([
    ['running', 'stage.status.running'],
    ['success', 'stage.status.success'],
    ['requires_confirmation', 'stage.status.requiresConfirmation'],
    ['confirmation_invalid', 'stage.status.requiresConfirmation'],
  ] as const)('maps %s to %s', (status, label) => {
    render(
      <SqlWorkbenchStatusBar
        riskReason={status === 'requires_confirmation' ? 'bulk delete' : null}
        status={status}
      />,
    )

    expect(screen.getByText(t(label))).toBeTruthy()
  })

  it.each([
    ['idle'],
    ['error'],
  ] as const)('does not render in %s status', (status) => {
    render(
      <SqlWorkbenchStatusBar
        riskReason={null}
        status={status}
      />,
    )

    expect(screen.queryByTestId('sql-workbench-status-bar')).toBeNull()
  })
})
