import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { ModelPickerDialog } from '../model-picker-dialog'
import type { ProviderDto } from '@/features/settings/shared/api'

const providers: ProviderDto[] = [
  { id: 'openai', name: 'OpenAI', connected: true, models: [
    { id: 'gpt-5', name: 'GPT-5', enabled: true },
  ]},
  { id: 'anthropic', name: 'Anthropic', connected: true, models: [
    { id: 'claude-opus-4-7', name: 'Claude Opus 4.7', enabled: true },
  ]},
]

describe('ModelPickerDialog', () => {
  it('打开时默认选中当前模型所属 provider，右侧展示该 provider 的模型', () => {
    render(
      <ModelPickerDialog
        open
        onOpenChange={vi.fn()}
        providers={providers}
        currentModelId="anthropic/claude-opus-4-7"
        onPick={vi.fn()}
      />,
    )
    // 标题
    expect(screen.getByText('选择模型')).toBeInTheDocument()
    // 右侧主区应显示 Anthropic 模型
    expect(screen.getByRole('button', { name: 'Claude Opus 4.7' })).toBeInTheDocument()
    // 不应显示另一家 provider 的模型
    expect(screen.queryByRole('button', { name: 'GPT-5' })).not.toBeInTheDocument()
  })
})
