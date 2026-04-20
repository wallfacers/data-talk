import { createContext, useEffect, useMemo, type ReactNode } from 'react'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import {
  translateMessage,
  type LanguageOption,
  type MessageKey,
  type MessageValues,
} from './messages'

export type TranslationFn = (key: MessageKey, values?: MessageValues) => string

type I18nContextValue = {
  language: LanguageOption
  setLanguage: (language: LanguageOption) => void
  t: TranslationFn
}

export const I18nContext = createContext<I18nContextValue | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const language = useUISettingsStore((s) => s.language)
  const setLanguage = useUISettingsStore((s) => s.setLanguage)

  useEffect(() => {
    document.documentElement.lang = language
  }, [language])

  const value = useMemo<I18nContextValue>(() => ({
    language,
    setLanguage,
    t: (key, values) => translateMessage(language, key, values),
  }), [language, setLanguage])

  return (
    <I18nContext.Provider value={value}>
      {children}
    </I18nContext.Provider>
  )
}
