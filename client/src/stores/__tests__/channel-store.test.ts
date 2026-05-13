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

  it('synchronously writes the cursor to sessionStorage so CTRL+R can resume past the replay tail', () => {
    // Mirror BUG-0037 fix pattern on chat-parts-store: the cursor must land
    // in sessionStorage on the same microtask as the set(), without waiting
    // for the Zustand persist middleware's async flush.
    useChannelStore.getState().setLastEventId('s1', 5)
    const parsed = JSON.parse(sessionStorage.getItem('data-talk.channel')!)
    expect(parsed.state.lastEventIdBySession.s1).toBe(5)

    useChannelStore.getState().setLastEventId('s1', 9)
    const parsedAfter = JSON.parse(sessionStorage.getItem('data-talk.channel')!)
    expect(parsedAfter.state.lastEventIdBySession.s1).toBe(9)
  })

  it('does not rewrite sessionStorage on a no-op (id <= current cursor)', () => {
    useChannelStore.getState().setLastEventId('s1', 10)
    const before = sessionStorage.getItem('data-talk.channel')
    // No-op call — should not advance the cursor or rewrite storage payload.
    useChannelStore.getState().setLastEventId('s1', 3)
    const after = sessionStorage.getItem('data-talk.channel')
    expect(after).toBe(before)
    const parsed = JSON.parse(after!)
    expect(parsed.state.lastEventIdBySession.s1).toBe(10)
  })
})
