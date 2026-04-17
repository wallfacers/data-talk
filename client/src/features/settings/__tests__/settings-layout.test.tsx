import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { SettingsNav } from '../settings-nav'

describe('SettingsNav', () => {
  it('renders all top-level nav entries', () => {
    render(<SettingsNav activeSection="general" onSectionChange={() => {}} />)
    expect(screen.getByText('通用')).toBeInTheDocument()
    expect(screen.getByText('数据源')).toBeInTheDocument()
    expect(screen.getByText('提供商')).toBeInTheDocument()
    expect(screen.getByText('模型')).toBeInTheDocument()
  })

  it('highlights the active section', () => {
    render(<SettingsNav activeSection="data-sources" onSectionChange={() => {}} />)
    const dataSourcesBtn = screen.getByRole('button', { name: /数据源/ })
    const generalBtn = screen.getByRole('button', { name: /通用/ })
    expect(dataSourcesBtn).toHaveClass('bg-accent')
    expect(generalBtn).not.toHaveClass('bg-accent')
  })

  it('calls onSectionChange when a nav item is clicked', () => {
    const handler = vi.fn()
    render(<SettingsNav activeSection="general" onSectionChange={handler} />)
    fireEvent.click(screen.getByRole('button', { name: /模型/ }))
    expect(handler).toHaveBeenCalledWith('models')
  })

  it('shows section groups with titles', () => {
    render(<SettingsNav activeSection="general" onSectionChange={() => {}} />)
    expect(screen.getByText('桌面')).toBeInTheDocument()
    expect(screen.getByText('服务器')).toBeInTheDocument()
  })
})
