import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'

export const RECOMMENDED_PROVIDERS: ReadonlySet<string> = new Set([
  'opencode-go',
  'opencode-zen',
])

export const EXCLUDED_PROVIDERS: ReadonlySet<string> = new Set([
  'anthropic',
])

const DESCRIPTION_KEYS: Record<string, Parameters<typeof translateMessage>[1]> = {
  'opencode-zen': 'providers.desc.opencode-zen',
  'opencode-go': 'providers.desc.opencode-go',
  'github-copilot': 'providers.desc.github-copilot',
  'openai': 'providers.desc.openai',
  'google': 'providers.desc.google',
}

export function getProviderDescription(id: string, fallbackKey: 'providers.desc.default' | 'providers.desc.defaultShort' = 'providers.desc.default') {
  const language = getCurrentLanguage()
  const key = DESCRIPTION_KEYS[id] ?? fallbackKey
  return translateMessage(language, key)
}
