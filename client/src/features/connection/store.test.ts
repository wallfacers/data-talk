import { beforeEach, describe, expect, it } from 'vitest'
import { useConnectionStore } from './store'

describe('connection-store', () => {
  beforeEach(() => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [],
    })
    localStorage.clear()
  })

  it('persists activeConnectionId across refresh boundaries', () => {
    useConnectionStore.getState().setActive('c1')

    const raw = localStorage.getItem('data-talk.connection')
    expect(raw).toBeTruthy()

    const parsed = JSON.parse(raw!)
    expect(parsed.state.activeConnectionId).toBe('c1')
    expect(parsed.state.connections).toBeUndefined()
  })
})
