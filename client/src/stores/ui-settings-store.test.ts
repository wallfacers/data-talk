import { beforeEach, describe, expect, it, vi } from 'vitest'

describe('useUISettingsStore', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.resetModules()
  })

  it('defaults autoExpandReasoning to false', async () => {
    const { useUISettingsStore } = await import('./ui-settings-store')

    expect((useUISettingsStore.getState() as any).autoExpandReasoning).toBe(false)
  })

  it('persists autoExpandReasoning when changed', async () => {
    const { useUISettingsStore } = await import('./ui-settings-store')

    ;(useUISettingsStore.getState() as any).setAutoExpandReasoning(true)

    expect((useUISettingsStore.getState() as any).autoExpandReasoning).toBe(true)
    expect(JSON.parse(window.localStorage.getItem('ui-settings') ?? '{}')).toMatchObject({
      autoExpandReasoning: true,
    })
  })
})
