import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { StageTab } from '@/stores/stage-store'
import { QueryEditorTab } from './query-editor-tab'

const sqlWorkbenchTabMock = vi.hoisted(() => vi.fn())

vi.mock('./sql-workbench-tab', () => ({
  SqlWorkbenchTab: (props: { tab: StageTab }) => {
    sqlWorkbenchTabMock(props)
    return <div data-testid="sql-workbench-tab">sql workbench tab</div>
  },
}))

describe('QueryEditorTab', () => {
  it('delegates rendering to SqlWorkbenchTab with the same tab payload', () => {
    const tab: StageTab = {
      tabId: 'q-1',
      type: 'query_editor',
      title: 'SQL',
      scope: 'workspace',
      createdAt: 0,
      payload: { initialSql: 'select 1', source: 'user' },
    }

    render(<QueryEditorTab tab={tab} />)

    expect(screen.getByTestId('sql-workbench-tab')).toBeTruthy()
    expect(sqlWorkbenchTabMock).toHaveBeenCalledWith({ tab })
  })
})
