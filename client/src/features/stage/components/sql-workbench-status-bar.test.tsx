import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SqlWorkbenchStatusBar } from './sql-workbench-status-bar'

describe('SqlWorkbenchStatusBar', () => {
  it.each([
    ['idle', 'Idle'],
    ['running', 'Running'],
    ['success', 'Success'],
    ['error', 'Error'],
    ['risk_blocked', 'Risk blocked'],
  ] as const)('maps %s to %s', (status, label) => {
    render(
      <SqlWorkbenchStatusBar
        cursor={{ line: 12, column: 3 }}
        errorMessage={status === 'error' ? 'network failed' : null}
        riskReason={status === 'risk_blocked' ? 'bulk delete' : null}
        status={status}
      />,
    )

    expect(screen.getByText(label)).toBeTruthy()
    expect(screen.getByText('Ln 12, Col 3')).toBeTruthy()
  })
})
