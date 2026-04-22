import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { translateMessage } from '@/i18n/messages'
import { SqlErrorResultPanel } from './sql-error-result-panel'

describe('SqlErrorResultPanel', () => {
  it('falls back to the translated default error message', () => {
    render(
      <SqlErrorResultPanel
        result={{
          resultId: 'r-1',
          kind: 'error',
          title: 'Error 1',
          statementIndex: 1,
          statementText: 'select 1',
          columns: [],
          rows: [],
          rowCount: 0,
          executionMs: 0,
          truncated: false,
          errorMessage: null,
        }}
      />,
    )

    expect(screen.getByText(translateMessage('zh-CN', 'stage.result.error.default'))).toBeTruthy()
  })
})
