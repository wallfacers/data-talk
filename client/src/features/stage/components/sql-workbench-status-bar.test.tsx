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
    ['risk_blocked', 'stage.status.riskBlocked'],
  ] as const)('maps %s to %s', (status, label) => {
    render(
      <SqlWorkbenchStatusBar
        riskReason={status === 'risk_blocked' ? 'bulk delete' : null}
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
