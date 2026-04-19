import { describe, it, expect, beforeEach } from 'vitest'
import { useChannelStore } from '../channel-store'

describe('channel-store', () => {
  beforeEach(() => {
    useChannelStore.setState({
      isConnected: false,
      lastEventIdBySession: new Map(),
    })
    sessionStorage.clear()
  })

  it('setLastEventId writes to the session-keyed map', () => {
    useChannelStore.getState().setLastEventId('ses_a', 42)
    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(42)
  })

  it('setLastEventId is monotonic — smaller ids do not overwrite', () => {
    useChannelStore.getState().setLastEventId('ses_a', 42)
    useChannelStore.getState().setLastEventId('ses_a', 10)
    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(42)
  })

  it('different sessions have independent cursors', () => {
    useChannelStore.getState().setLastEventId('ses_a', 10)
    useChannelStore.getState().setLastEventId('ses_b', 99)
    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(10)
    expect(useChannelStore.getState().lastEventIdBySession.get('ses_b')).toBe(99)
  })

  it('persists lastEventIdBySession across a fresh import (sessionStorage round-trip)', async () => {
    useChannelStore.getState().setLastEventId('ses_a', 7)
    const raw = sessionStorage.getItem('data-talk.channel')
    expect(raw).toBeTruthy()
    const parsed = JSON.parse(raw!)
    expect(parsed.state.lastEventIdBySession).toMatchObject({ ses_a: 7 })
  })
})
