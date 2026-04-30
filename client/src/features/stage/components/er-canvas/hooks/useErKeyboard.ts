import { useEffect } from 'react'

export interface UseErKeyboardOptions {
  enabled: boolean
  onAutoLayout: () => void
  onFitView: () => void
  onDelete?: () => void
}

export function useErKeyboard(options: UseErKeyboardOptions): void {
  useEffect(() => {
    if (!options.enabled) return

    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.key === 'Delete' || event.key === 'Backspace') && options.onDelete) {
        if (isTextEditingTarget(event.target)) return
        event.preventDefault()
        options.onDelete()
        return
      }

      const hasModifier = event.metaKey || event.ctrlKey
      if (!hasModifier) return

      if (event.key.toLowerCase() === 'l') {
        event.preventDefault()
        options.onAutoLayout()
        return
      }

      if (event.key === '0') {
        event.preventDefault()
        options.onFitView()
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [options])
}

function isTextEditingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  if (target.isContentEditable) return true

  const tagName = target.tagName.toLowerCase()
  return tagName === 'input'
    || tagName === 'textarea'
    || tagName === 'select'
    || target.getAttribute('role') === 'textbox'
}
