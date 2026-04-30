import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useErKeyboard } from '../useErKeyboard'

function fire(target: EventTarget, key: string, mods: { meta?: boolean; ctrl?: boolean } = {}) {
  target.dispatchEvent(new KeyboardEvent('keydown', {
    key,
    metaKey: mods.meta,
    ctrlKey: mods.ctrl,
    bubbles: true,
  }))
}

describe('useErKeyboard', () => {
  it('Cmd+L triggers onAutoLayout', () => {
    const onAutoLayout = vi.fn()
    renderHook(() => useErKeyboard({ enabled: true, onAutoLayout, onFitView: vi.fn() }))

    fire(window, 'l', { meta: true })

    expect(onAutoLayout).toHaveBeenCalled()
  })

  it('Cmd+0 triggers onFitView', () => {
    const onFitView = vi.fn()
    renderHook(() => useErKeyboard({ enabled: true, onAutoLayout: vi.fn(), onFitView }))

    fire(window, '0', { meta: true })

    expect(onFitView).toHaveBeenCalled()
  })

  it('does nothing when enabled=false', () => {
    const onAutoLayout = vi.fn()
    renderHook(() => useErKeyboard({ enabled: false, onAutoLayout, onFitView: vi.fn() }))

    fire(window, 'l', { meta: true })

    expect(onAutoLayout).not.toHaveBeenCalled()
  })

  it('triggers selection deletion on Delete and Backspace outside text inputs', () => {
    const onDelete = vi.fn()
    renderHook(() => useErKeyboard({
      enabled: true,
      onAutoLayout: vi.fn(),
      onFitView: vi.fn(),
      onDelete,
    }))

    fire(window, 'Delete')
    fire(window, 'Backspace')

    const input = document.createElement('input')
    document.body.append(input)
    fire(input, 'Backspace')
    input.remove()

    expect(onDelete).toHaveBeenCalledTimes(2)
  })
})
