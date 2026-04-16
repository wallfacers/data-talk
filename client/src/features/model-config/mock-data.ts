import type { Provider } from './types'

export const MOCK_PROVIDERS: Provider[] = [
  {
    id: 'anthropic',
    name: 'Anthropic',
    type: 'builtin',
    connected: true,
    source: 'api',
    models: [
      { id: 'claude-4-opus', name: 'Claude 4 Opus', providerId: 'anthropic', visible: true, latest: true },
      { id: 'claude-4-sonnet', name: 'Claude 4 Sonnet', providerId: 'anthropic', visible: true },
      { id: 'claude-3-5-haiku', name: 'Claude 3.5 Haiku', providerId: 'anthropic', visible: false },
    ],
  },
  {
    id: 'openai',
    name: 'OpenAI',
    type: 'builtin',
    connected: false,
    models: [
      { id: 'gpt-4o', name: 'GPT-4o', providerId: 'openai', visible: true, latest: true },
      { id: 'gpt-4-turbo', name: 'GPT-4 Turbo', providerId: 'openai', visible: false },
      { id: 'gpt-3-5-turbo', name: 'GPT-3.5 Turbo', providerId: 'openai', visible: false },
    ],
  },
  {
    id: 'google',
    name: 'Google',
    type: 'builtin',
    connected: false,
    models: [
      { id: 'gemini-2-flash', name: 'Gemini 2.0 Flash', providerId: 'google', visible: true, latest: true },
      { id: 'gemini-1-5-pro', name: 'Gemini 1.5 Pro', providerId: 'google', visible: false },
    ],
  },
]

export const POPULAR_PROVIDER_ORDER = ['anthropic', 'openai', 'google', 'openrouter', 'vercel']

export function sortByProviderOrder<T extends { id: string }>(items: T[]): T[] {
  return items.sort((a, b) => {
    const aIdx = POPULAR_PROVIDER_ORDER.indexOf(a.id)
    const bIdx = POPULAR_PROVIDER_ORDER.indexOf(b.id)
    const aPopular = aIdx >= 0
    const bPopular = bIdx >= 0
    if (aPopular && !bPopular) return -1
    if (!aPopular && bPopular) return 1
    return aIdx - bIdx
  })
}
