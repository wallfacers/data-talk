import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GeneralSettingsPanel } from './general-panel'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import * as preferencesApi from './preferences-api'

const openBlankSessionMock = vi.fn(async () => {})

vi.mock('@/features/session/hooks/use-open-blank-session', () => ({
  useOpenBlankSession: () => openBlankSessionMock,
}))

function renderPanel() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={qc}>
      <GeneralSettingsPanel />
    </QueryClientProvider>,
  )
  return qc
}

describe('Timezone and date format settings', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
    useUISettingsStore.setState({
      splitResizable: false,
      language: 'zh-CN',
      autoExpandReasoning: false,
    } as any)
  })

  it('renders timezone and date format sections', async () => {
    vi.spyOn(preferencesApi, 'fetchPreferences').mockResolvedValue({
      timezone: 'UTC',
      dateFormat: 'yyyy-MM-dd HH:mm:ss',
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByText('时区')).toBeDefined()
      expect(screen.getByText('日期格式')).toBeDefined()
    })
  })

  it('shows current timezone from API', async () => {
    vi.spyOn(preferencesApi, 'fetchPreferences').mockResolvedValue({
      timezone: 'Asia/Shanghai',
      dateFormat: 'yyyy-MM-dd HH:mm:ss',
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByText('Asia/Shanghai')).toBeDefined()
    })
  })

  it('calls updatePreferences when timezone changes', async () => {
    vi.spyOn(preferencesApi, 'fetchPreferences').mockResolvedValue({
      timezone: 'UTC',
      dateFormat: 'yyyy-MM-dd HH:mm:ss',
    })
    const updateSpy = vi.spyOn(preferencesApi, 'updatePreferences').mockResolvedValue({
      timezone: 'Asia/Tokyo',
      dateFormat: 'yyyy-MM-dd HH:mm:ss',
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByText('UTC')).toBeDefined()
    })

    // Open the timezone popover by clicking the UTC trigger button
    const allComboboxes = screen.getAllByRole('combobox')
    const tzTrigger = allComboboxes.find((el) => el.textContent?.includes('UTC'))
    fireEvent.click(tzTrigger!)

    // Type in search to filter to "Tokyo"
    const searchInput = screen.getByPlaceholderText('Search timezone...')
    fireEvent.change(searchInput, { target: { value: 'Tokyo' } })

    await waitFor(() => {
      const tokyoButton = screen.getByText('Asia/Tokyo')
      fireEvent.click(tokyoButton)
    })

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith({ timezone: 'Asia/Tokyo' })
    })
  })

  it('renders date format presets', async () => {
    vi.spyOn(preferencesApi, 'fetchPreferences').mockResolvedValue({
      timezone: 'UTC',
      dateFormat: 'yyyy-MM-dd HH:mm:ss',
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByText('CN (ISO)')).toBeDefined()
      expect(screen.getByText('US')).toBeDefined()
      expect(screen.getByText('EU')).toBeDefined()
      expect(screen.getByText('CN Long')).toBeDefined()
      expect(screen.getByText('Custom')).toBeDefined()
    })
  })

  it('calls updatePreferences when date format preset is clicked', async () => {
    vi.spyOn(preferencesApi, 'fetchPreferences').mockResolvedValue({
      timezone: 'UTC',
      dateFormat: 'yyyy-MM-dd HH:mm:ss',
    })
    const updateSpy = vi.spyOn(preferencesApi, 'updatePreferences').mockResolvedValue({
      timezone: 'UTC',
      dateFormat: 'MM/dd/yyyy hh:mm:ss a',
    })

    renderPanel()

    await waitFor(() => {
      fireEvent.click(screen.getByText('US'))
    })

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith({ dateFormat: 'MM/dd/yyyy hh:mm:ss a' })
    })
  })
})
