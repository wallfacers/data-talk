import { describe, it, expect, beforeEach } from 'vitest'
import { act, render, screen, fireEvent, waitFor } from '@testing-library/react'
import { StageToggleButton } from './stage-toggle-button'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'

describe('StageToggleButton', () => {
  beforeEach(() => {
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
    })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
    })
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
      version: 0,
    })
  })

  it('无 activeSessionId → 按钮 aria-disabled="true"', () => {
    render(<StageToggleButton />)
    const btn = screen.getByRole('button')
    expect(btn.getAttribute('aria-disabled')).toBe('true')
    expect(btn).toHaveClass('aria-disabled:opacity-50')
  })

  it('HERO 模式下点击 → 进入 SPLIT + 打开 Stage', () => {
    useSessionStore.getState().openSession('s1', false)
    render(<StageToggleButton />)
    fireEvent.click(screen.getByRole('button'))
    expect(useSessionStore.getState().modeBySession.get('s1')).toBe('SPLIT')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('SPLIT 关闭态下点击 → 打开', () => {
    useSessionStore.getState().openSession('s1', true)
    render(<StageToggleButton />)
    fireEvent.click(screen.getByRole('button'))
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('SPLIT 打开态下点击 → 关闭', () => {
    useSessionStore.getState().openSession('s1', true)
    useStageStore.getState().openStage('s1')
    render(<StageToggleButton />)
    fireEvent.click(screen.getByRole('button'))
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('aria-pressed 反映打开状态', () => {
    useSessionStore.getState().openSession('s1', true)
    useStageStore.getState().openStage('s1')
    render(<StageToggleButton />)
    expect(screen.getByRole('button').getAttribute('aria-pressed')).toBe('true')
  })

  it('点击后 revealOrigin 被写入按钮中心视口坐标', () => {
    useSessionStore.getState().openSession('s1', true)
    render(<StageToggleButton />)
    const btn = screen.getByRole('button')
    // jsdom 默认 getBoundingClientRect 返回全零，这里 stub 成已知矩形
    btn.getBoundingClientRect = () => ({
      left: 100, top: 200, right: 120, bottom: 220,
      width: 20, height: 20, x: 100, y: 200, toJSON: () => ({}),
    }) as DOMRect
    fireEvent.click(btn)
    expect(useStageStore.getState().revealOrigin).toEqual({ x: 110, y: 210 })
  })

  it('open=true 且 mode 被重置为 HERO 时，点击仍可关闭 Stage', () => {
    useSessionStore.getState().openSession('s1', false)
    useStageStore.getState().openStage('s1')
    useSessionStore.getState().setSessionMode('s1', 'HERO')

    render(<StageToggleButton />)
    fireEvent.click(screen.getByRole('button'))

    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
    expect(useSessionStore.getState().modeBySession.get('s1')).toBe('HERO')
  })

  it('切回空会话且 Stage 仍打开时，mode 会自动同步回 SPLIT', () => {
    useSessionStore.getState().openSession('s1', false)
    useStageStore.getState().openStage('s1')

    render(<StageToggleButton />)

    act(() => {
      useSessionStore.getState().openSession('s2', false)
      useSessionStore.getState().openSession('s1', false)
    })

    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    return waitFor(() => {
      expect(useSessionStore.getState().modeBySession.get('s1')).toBe('SPLIT')
    })
  })
})
