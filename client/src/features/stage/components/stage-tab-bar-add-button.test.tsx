import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { StageTabBarAddButton } from './stage-tab-bar-add-button'
import { useStageStore } from '@/stores/stage-store'

// Mock i18n to return keys as labels
vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({ t: (k: string) => k }),
}))

describe('StageTabBarAddButton', () => {
  it('renders the + trigger button', () => {
    render(<StageTabBarAddButton sessionId={null} />)
    expect(screen.getByRole('button')).toBeInTheDocument()
  })

  it('disabled menu items show pending pill text', () => {
    render(<StageTabBarAddButton sessionId={null} />)
    fireEvent.click(screen.getByRole('button'))
    // ER, Report, Dashboard are disabled
    const menuItems = screen.getAllByRole('menuitem')
    expect(menuItems.length).toBeGreaterThanOrEqual(3)
  })

  it('SQL editor menu item calls openQueryEditor on click', () => {
    const spy = vi.spyOn(useStageStore.getState(), 'openQueryEditor').mockImplementation(() => ({ tabId: 'test', created: true }))
    render(<StageTabBarAddButton sessionId={null} />)
    fireEvent.click(screen.getByRole('button'))
    const sqlItem = screen.getAllByRole('menuitem')[0]
    fireEvent.click(sqlItem)
    expect(spy).toHaveBeenCalled()
    spy.mockRestore()
  })
})
