import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ProvidersPage } from '../providers-page'
import * as api from '../../shared/api'

vi.mock('../../shared/api')

function mockProviders() {
  vi.mocked(api.fetchProviders).mockResolvedValue({
    all: [
      { id: 'openai', name: 'OpenAI' },
      { id: 'anthropic', name: 'Anthropic' }
    ],
    connected: ['openai']
  })
}

describe('ProvidersPage', () => {
  beforeEach(() => {
    mockProviders()
  })
  it('separates connected and popular', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><ProvidersPage /></QueryClientProvider>)
    expect(await screen.findByText('已连接的提供商')).toBeInTheDocument()
    expect(screen.getByText('OpenAI')).toBeInTheDocument()
    expect(screen.getByText('Anthropic')).toBeInTheDocument()
  })
})
