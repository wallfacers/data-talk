import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ModelPicker } from '../model-picker'
import * as api from '@/features/settings/shared/api'

vi.mock('@/features/settings/shared/api')

describe('ModelPicker', () => {
  beforeEach(() => {
    vi.mocked(api.fetchModels).mockResolvedValue({
      providers: [{ id: 'openai', name: 'OpenAI', connected: true,
        models: [{ id: 'gpt-5', name: 'GPT-5', enabled: true }] }]
    })
    vi.mocked(api.getCurrentModel).mockResolvedValue({ modelId: null })
    vi.mocked(api.setCurrentModel).mockResolvedValue(undefined)
  })
  it('shows placeholder when nothing selected, opens popover on click', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><ModelPicker /></QueryClientProvider>)
    expect(await screen.findByText('选择模型')).toBeInTheDocument()
    fireEvent.click(screen.getByText('选择模型'))
    await waitFor(() => expect(screen.getByText('GPT-5')).toBeInTheDocument())
  })
})
