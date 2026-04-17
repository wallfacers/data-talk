import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchProviders, fetchProviderAuth, fetchModels, patchModelEnabled,
         getCurrentModel, setCurrentModel, putCredentials } from '../api'

describe('settings api', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('fetchProviders calls /api/ai/providers', async () => {
    (globalThis.fetch as any).mockResolvedValue({
      ok: true, json: async () => ({ all: [], connected: [] })
    })
    await fetchProviders()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/ai/providers')
  })

  it('fetchProviderAuth calls /api/ai/providers/auth', async () => {
    (globalThis.fetch as any).mockResolvedValue({
      ok: true, json: async () => ({ openai: [{ type: 'api' }] })
    })
    await fetchProviderAuth()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/ai/providers/auth')
  })

  it('patchModelEnabled posts to correct path', async () => {
    (globalThis.fetch as any).mockResolvedValue({ ok: true })
    await patchModelEnabled('openai', 'gpt-5', false)
    const [url, opts] = (globalThis.fetch as any).mock.calls[0]
    expect(url).toBe('/api/ai/models/openai/gpt-5')
    expect(opts.method).toBe('PATCH')
    expect(JSON.parse(opts.body)).toEqual({ enabled: false })
  })

  it('setCurrentModel patches /api/ai/current-model', async () => {
    (globalThis.fetch as any).mockResolvedValue({ ok: true })
    await setCurrentModel('openai/gpt-5')
    const [url, opts] = (globalThis.fetch as any).mock.calls[0]
    expect(url).toBe('/api/ai/current-model')
    expect(JSON.parse(opts.body)).toEqual({ modelId: 'openai/gpt-5' })
  })

  it('fetchModels calls /api/ai/models', async () => {
    (globalThis.fetch as any).mockResolvedValue({
      ok: true, json: async () => ({ providers: [] })
    })
    await fetchModels()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/ai/models')
  })

  it('getCurrentModel calls /api/ai/current-model', async () => {
    (globalThis.fetch as any).mockResolvedValue({
      ok: true, json: async () => ({ modelId: null })
    })
    await getCurrentModel()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/ai/current-model')
  })

  it('putCredentials forwards payload', async () => {
    (globalThis.fetch as any).mockResolvedValue({ ok: true })
    await putCredentials('openai', { type: 'api', key: 'sk-x' })
    const [url, opts] = (globalThis.fetch as any).mock.calls[0]
    expect(url).toBe('/api/ai/providers/openai/credentials')
    expect(opts.method).toBe('PUT')
  })
})
