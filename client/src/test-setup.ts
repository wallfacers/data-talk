import '@testing-library/jest-dom'
import { vi } from 'vitest'
import { translateMessage } from '@/i18n/messages'

// jsdom missing matchMedia (needed by sonner Toaster)
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'zh-CN',
    setLanguage: vi.fn(),
    t: (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
      translateMessage('zh-CN', key, values),
  }),
}))

vi.mock('react-grid-layout', () => {
  const MockGridLayout = (_props: unknown) => null
  const WidthProvider = (_Component: unknown) => MockGridLayout
  return { Responsive: MockGridLayout, WidthProvider }
})
