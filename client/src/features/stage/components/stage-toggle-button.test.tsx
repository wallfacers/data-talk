import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StageToggleButton } from './stage-toggle-button'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'

describe('StageToggleButton', () => {
  beforeEach(() => {
    useStageStore.setState({ openBySession: new Map(), autoOpenedSessions: new Set() })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
      pendingConnectionPrompt: false,
    })
  })

  it('HERO 模式下按钮 disabled', () => {
    useSessionStore.getState().openSession('s1', false)
    render(<StageToggleButton />)
    expect(screen.getByRole('button')).toBeDisabled()
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
})
