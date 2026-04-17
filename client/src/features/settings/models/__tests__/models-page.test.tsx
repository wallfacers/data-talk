import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ModelsPage } from '../models-page'
import * as api from '../../shared/api'

vi.mock('../../shared/api')

describe('ModelsPage', () => {
  beforeEach(() => {
    vi.mocked(api.fetchModels).mockResolvedValue({
      providers: [
        {
          id: 'openai',
          name: 'OpenAI',
          connected: true,
          models: [
            { id: 'gpt-5', name: 'GPT-5', enabled: true },
            { id: 'gpt-5-nano', name: 'GPT-5 Nano', enabled: false },
          ],
        },
      ],
    })
  })

  it('renders connected provider group and filters by search', async () => {
    const qc = new QueryClient()
    render(
      <QueryClientProvider client={qc}>
        <ModelsPage />
      </QueryClientProvider>,
    )
    expect(await screen.findByText('GPT-5')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('搜索模型'), {
      target: { value: 'nano' },
    })
    await waitFor(() =>
      expect(screen.queryByText('GPT-5')).not.toBeInTheDocument(),
    )
    expect(screen.getByText('GPT-5 Nano')).toBeInTheDocument()
  })
})
