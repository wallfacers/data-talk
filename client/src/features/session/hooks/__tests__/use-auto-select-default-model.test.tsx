import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import * as api from '@/features/settings/shared/api'
import { useAutoSelectDefaultModel } from '../use-auto-select-default-model'

function wrapper(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('useAutoSelectDefaultModel', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('当 currentModelId 为 null 时，选中首个已连接 provider 的首个启用模型并 PATCH', async () => {
    vi.spyOn(api, 'fetchModels').mockResolvedValue({
      providers: [
        { id: 'a-broken', name: 'A', connected: false, models: [{ id: 'm0', name: 'M0', enabled: true }] },
        { id: 'b-ok', name: 'B', connected: true, models: [
          { id: 'm1', name: 'M1', enabled: false },
          { id: 'm2', name: 'M2', enabled: true },
        ]},
      ],
    })
    vi.spyOn(api, 'getCurrentModel').mockResolvedValue({ modelId: null })
    const patch = vi.spyOn(api, 'setCurrentModel').mockResolvedValue(undefined)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAutoSelectDefaultModel(), { wrapper: wrapper(qc) })

    await waitFor(() => expect(patch).toHaveBeenCalledWith('b-ok/m2'))
    expect(patch).toHaveBeenCalledTimes(1)
  })

  it('当 currentModelId 已有值时，不触发 PATCH', async () => {
    vi.spyOn(api, 'fetchModels').mockResolvedValue({
      providers: [
        { id: 'b-ok', name: 'B', connected: true, models: [{ id: 'm2', name: 'M2', enabled: true }]},
      ],
    })
    vi.spyOn(api, 'getCurrentModel').mockResolvedValue({ modelId: 'b-ok/m2' })
    const patch = vi.spyOn(api, 'setCurrentModel').mockResolvedValue(undefined)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAutoSelectDefaultModel(), { wrapper: wrapper(qc) })

    await new Promise((r) => setTimeout(r, 20))
    expect(patch).not.toHaveBeenCalled()
  })

  it('没有已连接 provider 或没有启用模型时，不触发 PATCH', async () => {
    vi.spyOn(api, 'fetchModels').mockResolvedValue({
      providers: [
        { id: 'a', name: 'A', connected: false, models: [{ id: 'm', name: 'M', enabled: true }] },
        { id: 'b', name: 'B', connected: true, models: [{ id: 'm', name: 'M', enabled: false }] },
      ],
    })
    vi.spyOn(api, 'getCurrentModel').mockResolvedValue({ modelId: null })
    const patch = vi.spyOn(api, 'setCurrentModel').mockResolvedValue(undefined)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAutoSelectDefaultModel(), { wrapper: wrapper(qc) })

    await new Promise((r) => setTimeout(r, 20))
    expect(patch).not.toHaveBeenCalled()
  })

  it('同一会话 providers 多次变化不会重复 PATCH', async () => {
    vi.spyOn(api, 'fetchModels').mockResolvedValue({
      providers: [
        { id: 'b', name: 'B', connected: true, models: [{ id: 'm', name: 'M', enabled: true }]},
      ],
    })
    vi.spyOn(api, 'getCurrentModel').mockResolvedValue({ modelId: null })
    const patch = vi.spyOn(api, 'setCurrentModel').mockResolvedValue(undefined)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { rerender } = renderHook(() => useAutoSelectDefaultModel(), { wrapper: wrapper(qc) })

    await waitFor(() => expect(patch).toHaveBeenCalledTimes(1))

    rerender()
    rerender()
    await new Promise((r) => setTimeout(r, 20))
    expect(patch).toHaveBeenCalledTimes(1)
  })
})
