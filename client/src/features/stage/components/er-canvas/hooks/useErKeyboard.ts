import { useEffect } from 'react'

export interface UseErKeyboardOptions {
  enabled: boolean
  onAutoLayout: () => void
  onFitView: () => void
}

export function useErKeyboard(options: UseErKeyboardOptions): void {
  useEffect(() => {
    if (!options.enabled) return

    const onKeyDown = (event: KeyboardEvent) => {
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
