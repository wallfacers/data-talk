import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchProviders, fetchProviderAuth, fetchModels, patchModelEnabled,
         getCurrentModel, setCurrentModel, putCredentials } from '../api'

const mockResponse = <T>(data: T) => ({ json: async () => data })

vi.mock('@/services/http', () => ({
  http: {
    get: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  }
}))

import { http } from '@/services/http'

describe('settings api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetchProviders calls ai/providers', async () => {
    vi.mocked(http.get).mockReturnValue(mockResponse({ all: [], connected: [] }) as any)
    await fetchProviders()
    expect(http.get).toHaveBeenCalledWith('ai/providers')
  })

  it('fetchProviderAuth calls ai/providers/auth', async () => {
    vi.mocked(http.get).mockReturnValue(mockResponse({ openai: [{ type: 'api' }] }) as any)
    await fetchProviderAuth()
    expect(http.get).toHaveBeenCalledWith('ai/providers/auth')
  })

  it('patchModelEnabled patches correct path', async () => {
    vi.mocked(http.patch).mockReturnValue({} as any)
    await patchModelEnabled('openai', 'gpt-5', false)
    expect(http.patch).toHaveBeenCalledWith('ai/models/openai/gpt-5', { json: { enabled: false } })
  })

  it('setCurrentModel patches ai/current-model', async () => {
    vi.mocked(http.patch).mockReturnValue({} as any)
    await setCurrentModel('openai/gpt-5')
    expect(http.patch).toHaveBeenCalledWith('ai/current-model', { json: { modelId: 'openai/gpt-5' } })
  })

  it('fetchModels calls ai/models', async () => {
    vi.mocked(http.get).mockReturnValue(mockResponse({ providers: [] }) as any)
    await fetchModels()
    expect(http.get).toHaveBeenCalledWith('ai/models')
  })

  it('getCurrentModel calls ai/current-model', async () => {
    vi.mocked(http.get).mockReturnValue(mockResponse({ modelId: null }) as any)
    await getCurrentModel()
    expect(http.get).toHaveBeenCalledWith('ai/current-model')
  })

  it('putCredentials forwards payload', async () => {
    vi.mocked(http.put).mockReturnValue({} as any)
    await putCredentials('openai', { type: 'api', key: 'sk-x' })
    expect(http.put).toHaveBeenCalledWith('ai/providers/openai/credentials', { json: { type: 'api', key: 'sk-x' } })
  })
})
