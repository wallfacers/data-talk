import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ModelPicker } from '../model-picker'
import * as api from '@/features/settings/shared/api'

vi.mock('@/features/settings/shared/api')

function renderWithClient(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('ModelPicker', () => {
  beforeEach(() => {
    vi.mocked(api.fetchModels).mockResolvedValue({
      providers: [{
        id: 'openai', name: 'OpenAI', connected: true,
        models: [{ id: 'gpt-5', name: 'GPT-5', enabled: true }],
      }],
    })
    vi.mocked(api.getCurrentModel).mockResolvedValue({ modelId: null })
    vi.mocked(api.setCurrentModel).mockResolvedValue(undefined)
  })

  it('未选中模型时触发按钮显示占位符，点击后打开对话框并列出已启用模型', async () => {
    renderWithClient(<ModelPicker />)
    const trigger = await screen.findByRole('button', { name: /选择模型/ })
    fireEvent.click(trigger)
    await waitFor(() => {
      // 对话框里的"选择模型"标题
      expect(screen.getAllByText('选择模型').length).toBeGreaterThan(0)
      expect(screen.getByRole('button', { name: 'GPT-5' })).toBeInTheDocument()
    })
  })

  it('点击模型后触发 setCurrentModel 并关闭对话框', async () => {
    renderWithClient(<ModelPicker />)
    fireEvent.click(await screen.findByRole('button', { name: /选择模型/ }))
    fireEvent.click(await screen.findByRole('button', { name: 'GPT-5' }))
    await waitFor(() => {
      expect(api.setCurrentModel).toHaveBeenCalledWith('openai/gpt-5')
      expect(screen.queryByRole('button', { name: 'GPT-5' })).not.toBeInTheDocument()
    })
  })
})
