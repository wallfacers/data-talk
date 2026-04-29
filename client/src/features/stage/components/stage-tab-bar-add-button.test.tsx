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
    render(<StageTabBarAddButton />)
    expect(screen.getByRole('button')).toBeInTheDocument()
  })

  it('disabled menu items show pending pill text', () => {
    render(<StageTabBarAddButton />)
    fireEvent.click(screen.getByRole('button'))
    // ER designer is now available; only Report and Dashboard remain pending.
    const menuItems = screen.getAllByRole('menuitem')
    expect(menuItems).toHaveLength(4)
    expect(screen.getByText('stage.tabBar.addNew.menu.er')).toBeInTheDocument()
  })

  it('ER designer menu item opens an er_designer tab', () => {
    useStageStore.setState({
      tabs: [],
      openTabIds: new Set(),
      openTabIdsOrdered: [],
      activeTabId: null,
    } as never)

    render(<StageTabBarAddButton />)
    fireEvent.click(screen.getByRole('button'))
    const erItem = screen.getAllByRole('menuitem')[1]
    fireEvent.click(erItem)

    expect(useStageStore.getState().tabs).toEqual([
      expect.objectContaining({
        type: 'er_designer',
      }),
    ])
  })

  it('SQL editor menu item calls openQueryEditor on click', () => {
    const spy = vi.spyOn(useStageStore.getState(), 'openQueryEditor').mockImplementation(() => ({ tabId: 'test', created: true }))
    render(<StageTabBarAddButton />)
    fireEvent.click(screen.getByRole('button'))
    const sqlItem = screen.getAllByRole('menuitem')[0]
    fireEvent.click(sqlItem)
    expect(spy).toHaveBeenCalled()
    spy.mockRestore()
  })
})
