import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SqlOutlineStatement } from '@/features/stage/utils/parse-sql-outline'
import { translateMessage } from '@/i18n/messages'
import { OutlinePanel } from './outline-panel'

const statements: SqlOutlineStatement[] = [
  { line: 2, kind: 'SELECT', summary: 'select 1', highRiskHint: false },
  { line: 5, kind: 'UPDATE', summary: 'update users set active = 0', highRiskHint: true },
]

describe('OutlinePanel', () => {
  const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
    translateMessage('zh-CN', key, values)

  it('renders the parsed statements and jumps to a line on click', () => {
    const onJumpToLine = vi.fn()

    render(<OutlinePanel statements={statements} onJumpToLine={onJumpToLine} />)
    expect(screen.getByText(t('stage.activityRail.outline.highRisk'))).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: `${t('stage.activityRail.outline.line', { line: 5 })} UPDATE` }))

    expect(onJumpToLine).toHaveBeenCalledWith(5)
  })
})
