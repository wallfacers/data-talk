import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { HomePage } from './home-page'

vi.mock('@/features/actions/use-bootstrap-actions', () => ({
  useBootstrapActions: vi.fn(),
}))

vi.mock('@/features/session/session-canvas', () => ({
  SessionCanvas: () => <div data-testid="session-canvas" />,
}))

vi.mock('./components/app-sidebar', () => ({
  AppSidebar: () => <aside data-testid="app-sidebar" />,
}))

describe('HomePage', () => {
  // 还原回归：HomePage 之前给 AppSidebar 传 variant="inset"，shadcn 由此把
  // bg-sidebar/25 作用到主屏，亮色主题下与会话列表色阶接近。
  // 修复：去掉 inset 变体，主屏走 bg-background → --dt-bg-canvas，
  // 与会话列表的 --dt-bg-subtle 拉开 Δ-L≈0.03（亮色）/0.06（暗色）。
  it('chat 主屏使用 bg.canvas 语义，与会话列表的 bg.subtle 拉开主题色差异', () => {
    const { container } = render(<HomePage />)

    const inset = container.querySelector('[data-slot="sidebar-inset"]')
    expect(inset?.className).toContain('bg-background')
  })
})
