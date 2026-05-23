import { describe, it, expect } from 'vitest'
import { selectGateView, type BackendPhase } from './startup-state'
import { selectBannerKind } from '@/features/session/opencode-status-banner'

describe('selectGateView', () => {
  it('shows the app when the backend is ready', () => {
    expect(selectGateView({ state: 'ready' })).toBe('app')
  })

  it('shows the welcome surface while starting', () => {
    expect(selectGateView({ state: 'starting' })).toBe('welcome')
  })

  it('shows the error surface when startup failed', () => {
    const phase: BackendPhase = { state: 'failed', message: 'boom', log_path: '/tmp/backend.log' }
    expect(selectGateView(phase)).toBe('error')
  })
})

describe('selectBannerKind', () => {
  it('returns none when the bridge is ok', () => {
    expect(selectBannerKind('ok')).toBe('none')
  })

  it('returns none when status is undefined (not yet loaded)', () => {
    expect(selectBannerKind(undefined)).toBe('none')
  })

  it('returns starting while the bridge is coming up', () => {
    expect(selectBannerKind('starting')).toBe('starting')
  })

  it('returns degraded when the bridge is degraded', () => {
    expect(selectBannerKind('degraded')).toBe('degraded')
  })
})
