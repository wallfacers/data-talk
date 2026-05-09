import { Page, Locator, expect } from '@playwright/test'
import type { DashboardTabsState } from '@/features/dashboard/stores/dashboard-tabs-store'

export class DashboardPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // Dashboard preview block (chat message area)
  dashboardPreview(): Locator {
    return this.page.getByTestId('dashboard-preview')
  }

  // Loading skeleton state
  skeleton(): Locator {
    return this.page.getByTestId('dashboard-skeleton')
  }

  // Error state
  error(): Locator {
    return this.page.getByTestId('dashboard-error')
  }

  // "Open to workbench" button in the dashboard preview
  openToWorkbenchButton(): Locator {
    return this.dashboardPreview().getByRole('button', { name: 'Open to workbench' })
  }

  // Dashboard canvas (rendered in Stage workbench tab)
  canvas(): Locator {
    return this.page.getByTestId('dashboard-canvas')
  }

  // Widget by title text — locates a WidgetShell container whose visible title matches
  widgetByTitle(title: string): Locator {
    return this.canvas().getByText(title, { exact: true })
  }

  // Stage tab by name (uses role=tab with name)
  stageTabByName(name: string): Locator {
    return this.page.getByRole('tab', { name })
  }

  // Assert that the workbench has a tab open with the given title
  async expectWorkbenchOpen(title: string): Promise<void> {
    const tab = this.stageTabByName(title)
    await expect(tab).toBeVisible()
  }

  // Assert that the dashboard canvas is visible
  async expectCanvasVisible(): Promise<void> {
    await expect(this.canvas()).toBeVisible()
  }

  // Assert dashboard preview is visible
  async expectPreviewVisible(): Promise<void> {
    await expect(this.dashboardPreview()).toBeVisible()
  }

  // Assert dashboard skeleton is visible (loading state)
  async expectSkeletonVisible(): Promise<void> {
    await expect(this.skeleton()).toBeVisible()
  }

  // Assert dashboard error is visible
  async expectErrorVisible(): Promise<void> {
    await expect(this.error()).toBeVisible()
  }

  // Click the "Open to workbench" button
  async clickOpenToWorkbench(): Promise<void> {
    await this.openToWorkbenchButton().click()
  }

  // Wait for a dashboard tab to appear in the stage via the Zustand store
  async waitForDashboardTab(title: string, timeout = 10_000): Promise<void> {
    await expect.poll(
      async () => {
        const state = await this.page.evaluate(() => {
          const store = (window as any).__DT_E2E__?.dashboard() as DashboardTabsState | undefined
          if (!store) return { tabs: [] }
          return {
            tabs: Array.from(store.tabs.values()).map((t) => t.dashboard.title),
          }
        })
        return state.tabs
      },
      { timeout },
    ).toContain(title)
  }

  // Get titles of all open dashboard tabs from the store
  async getDashboardTabTitles(): Promise<string[]> {
    return this.page.evaluate(() => {
      const store = (window as any).__DT_E2E__?.dashboard() as DashboardTabsState | undefined
      if (!store) return []
      return Array.from(store.tabs.values()).map((t) => t.dashboard.title)
    })
  }
}
