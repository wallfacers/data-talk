import { Page, expect } from '@playwright/test'
import { StagePage } from './stage.page'

/**
 * Page Object Model for the Files Library tab within the Stage workbench.
 *
 * Covers interactions with the FilesLibraryTab component:
 * - Opening the tab
 * - Searching and filtering files
 * - Interacting with file rows (discard, copy path)
 * - Asserting on status badges
 */
export class FilesLibraryTabPom {
  readonly page: Page
  readonly stage: StagePage

  constructor(page: Page) {
    this.page = page
    this.stage = new StagePage(page)
  }

  /**
   * Open the Stage panel and navigate to the Files Library tab.
   * The Files tab is identified by its role=tab with the translated label.
   */
  async openFilesLibraryTab(): Promise<void> {
    await this.stage.openStage()

    // Look for the files tab by its icon or accessible name
    const filesTab = this.page.getByRole('tab', { name: /files|文件/i })
    await expect(filesTab).toBeVisible({ timeout: 10_000 })
    await filesTab.click()
  }

  /**
   * Search for files by query string.
   */
  async searchFiles(query: string): Promise<void> {
    const searchInput = this.page.getByPlaceholder(/files\.library\.search\.placeholder|search files/i)
      .or(this.page.locator('input[type="text"]'))
    await searchInput.fill(query)
  }

  /**
   * Filter files by kind using the kind selector.
   */
  async filterByKind(kind: string): Promise<void> {
    const kindSelect = this.page.getByLabel(/files\.library\.filter\.label|filter by kind/i)
    await kindSelect.click()
    await this.page.getByRole('option', { name: kind }).click()
  }

  /**
   * Click the discard button for a file with the given filename.
   */
  async discardFile(filename: string): Promise<void> {
    const fileRow = this.page.locator('div').filter({ hasText: filename }).first()
    const discardBtn = fileRow.getByRole('button', { name: /files\.action\.discard|discard|discard/i })
    await expect(discardBtn).toBeVisible()
    await discardBtn.click()
  }

  /**
   * Assert that a status badge is visible for a file.
   */
  async expectStatusBadgeVisible(): Promise<void> {
    const badge = this.page.getByTestId('file-artifact-status-badge')
    await expect(badge.first()).toBeVisible()
  }

  /**
   * Get the text content of the first visible status badge.
   */
  async getStatusBadgeText(): Promise<string> {
    const badge = this.page.getByTestId('file-artifact-status-badge')
    return (await badge.first().textContent()) ?? ''
  }

  /**
   * Assert that the files library shows the empty state (no archived files).
   */
  async expectEmptyState(): Promise<void> {
    const emptyState = this.page.getByText(/files\.empty\.noArchived|no archived files/i)
    await expect(emptyState).toBeVisible()
  }

  /**
   * Get the count of visible discard buttons.
   */
  async getDiscardButtonCount(): Promise<number> {
    const btns = this.page.getByRole('button', { name: /files\.action\.discard/i })
    return btns.count()
  }
}
