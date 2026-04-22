import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SqlWorkbenchStatusBar } from './sql-workbench-status-bar'

describe('SqlWorkbenchStatusBar', () => {
  it.each([
    ['running', 'Running'],
    ['success', 'Success'],
    ['error', 'Error'],
    ['risk_blocked', 'Risk blocked'],
  ] as const)('maps %s to %s', (status, label) => {
    render(
      <SqlWorkbenchStatusBar
        errorMessage={status === 'error' ? 'network failed' : null}
        riskReason={status === 'risk_blocked' ? 'bulk delete' : null}
        status={status}
      />,
    )

    expect(screen.getByText(label)).toBeTruthy()
  })

  it('does not render in idle status', () => {
    render(
      <SqlWorkbenchStatusBar
        errorMessage={null}
        riskReason={null}
        status="idle"
      />,
    )

    expect(screen.queryByTestId('sql-workbench-status-bar')).toBeNull()
  })
})
